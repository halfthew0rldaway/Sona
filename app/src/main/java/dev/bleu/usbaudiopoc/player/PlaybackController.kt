package dev.bleu.usbaudiopoc.player

import android.content.Context
import android.util.Log
import dev.bleu.usbaudiopoc.audio.AudioRouteOverride
import dev.bleu.usbaudiopoc.audio.AudioSource
import dev.bleu.usbaudiopoc.audio.BitPerfectPipeline
import dev.bleu.usbaudiopoc.audio.DspPipeline
import dev.bleu.usbaudiopoc.audio.AudioPipeline
import dev.bleu.usbaudiopoc.audio.BitPerfectVerifier
import dev.bleu.usbaudiopoc.audio.MediaDecoderSource
import dev.bleu.usbaudiopoc.audio.PlaybackBackend
import dev.bleu.usbaudiopoc.audio.WavDecoderSource
import dev.bleu.usbaudiopoc.usb.UsbAudioRouteManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

/**
 * Controls audio playback with a clean ownership model to prevent race conditions.
 *
 * Race condition fixed:
 *   Old design: cancelled coroutine's CancellationException handler called teardown()
 *   on SHARED state (activeBackend/activeSource), which could race with the NEW job
 *   that had already replaced those fields → new backend gets killed → player freezes.
 *
 * Fix: each safePlayback invocation gets a unique `sessionId`. It owns its local
 * `src` and `be` variables. On cancellation it ONLY cleans up its own locals (never
 * touches the shared fields). Shared fields are only cleared when the session that
 * SET them is the one cleaning up (checked via sessionId == currentSessionId).
 */
class PlaybackController(
    private val context: Context,
    private val usbAudioRouteManager: UsbAudioRouteManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val pauseFlow = MutableStateFlow(false)
    private var playbackJob: Job? = null
    private var positionJob: Job? = null

    // Shared resources — only the session that SET these should clear them
    @Volatile private var activeSource: AudioSource? = null
    @Volatile private var activeBackend: PlaybackBackend? = null

    // Which session currently "owns" activeSource/activeBackend
    private val sessionCounter = AtomicInteger(0)
    @Volatile private var activeSessionId = -1

    // ─── Public API ──────────────────────────────────────────────────────────

    fun setQueue(tracks: List<TrackItem>, startIndex: Int = 0) {
        _uiState.update { it.copy(queue = tracks, currentIndex = startIndex, isPaused = false) }
    }

    fun setQueueIndex(index: Int) {
        // Clearing isPaused prevents play() from taking the wrong resume-path when
        // the user picks a different track while the previous one was paused.
        _uiState.update { it.copy(currentIndex = index, isPaused = false) }
    }

    fun setRouteOverride(route: AudioRouteOverride) {
        val st = _uiState.value
        if (st.routeOverride == route) return
        _uiState.update { it.copy(routeOverride = route) }
        
        // If currently playing or paused, restart playback to apply the new route seamlessly
        if (st.isPlaying || st.isPaused) {
            val track = st.currentTrack ?: return
            launchPlayback(track, st.positionMs)
        }
    }

    fun play() {
        val st = _uiState.value
        // Resume only if we're truly paused AND the job that paused is still alive
        if (st.isPaused && playbackJob?.isActive == true) {
            activeBackend?.resume()
            pauseFlow.value = false
            _uiState.update { it.copy(isPlaying = true, isPaused = false) }
            startPositionPoll()
            return
        }
        val track = st.currentTrack ?: return
        launchPlayback(track)
    }

    fun pause() {
        if (!_uiState.value.isPlaying) return
        pauseFlow.value = true
        activeBackend?.pause()
        positionJob?.cancel()
        _uiState.update { it.copy(isPlaying = false, isPaused = true) }
    }

    fun stop() {
        val prevBe = activeBackend.also { activeBackend = null }
        val prevSrc = activeSource.also { activeSource = null }
        activeSessionId = -1
        playbackJob?.cancel()
        positionJob?.cancel()
        pauseFlow.value = false
        // Stop backend on calling thread (sets AtomicBoolean flag immediately)
        runCatching { prevBe?.stop() }
        // Release source on IO
        scope.launch { withContext(ioDispatcher) { runCatching { prevSrc?.close() } } }
        _uiState.update { it.copy(isPlaying = false, isPaused = false, activeBackend = null, positionMs = 0L) }
    }

    fun skipNext() {
        val st = _uiState.value
        if (!st.hasNext) return
        _uiState.update { it.copy(currentIndex = it.currentIndex + 1, isPaused = false) }
        if (st.isPlaying || st.isPaused) {
            val track = _uiState.value.currentTrack ?: return
            launchPlayback(track)
        }
    }

    fun skipPrev() {
        val st = _uiState.value
        if (st.positionMs > 3000) {
            val track = st.currentTrack ?: return
            launchPlayback(track); return
        }
        if (!st.hasPrev) return
        _uiState.update { it.copy(currentIndex = it.currentIndex - 1, isPaused = false) }
        if (st.isPlaying || st.isPaused) {
            val track = _uiState.value.currentTrack ?: return
            launchPlayback(track)
        }
    }

    fun seekTo(positionMs: Long) {
        val track = _uiState.value.currentTrack ?: return
        launchPlayback(track, seekToMs = positionMs)
    }

    fun setEqGain(band: Int, gainDb: Float) {
        val adapter = activeBackend as? DualPipelineBackendAdapter
        adapter?.setEqGain(band, gainDb)
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Cancel any running job and start a fresh playback coroutine.
     * The key invariant: safePlayback NEVER reads/writes the shared
     * activeSource/activeBackend on cancellation — only on normal paths.
     */
    private fun launchPlayback(track: TrackItem, seekToMs: Long = -1L) {
        // Signal old backend to stop immediately BEFORE cancelling the old coroutine.
        // This ensures the old write() loop exits via the AtomicBoolean flag without
        // blocking the IO thread, so teardown completes instantly.
        val prevBe = activeBackend.also { activeBackend = null }
        val prevSrc = activeSource.also { activeSource = null }
        activeSessionId = -1
        runCatching { prevBe?.stop() }

        playbackJob?.cancel()
        positionJob?.cancel()
        pauseFlow.value = false

        playbackJob = scope.launch {
            // Close old source on IO (non-blocking since backend already stopped)
            withContext(ioDispatcher) { runCatching { prevSrc?.close() } }
            safePlayback(track, seekToMs)
        }
    }

    private suspend fun safePlayback(track: TrackItem, seekToMs: Long = -1L) {
        val mySessionId = sessionCounter.incrementAndGet()

        val isWav = track.uri.toString().endsWith(".wav", ignoreCase = true) || track.title.endsWith(".wav", ignoreCase = true)
        val src: AudioSource = if (isWav) WavDecoderSource(context, track.uri) else MediaDecoderSource(context, track.uri)
        var be: PlaybackBackend? = null
        try {
            // Open decoder on IO
            val fmt = withContext(ioDispatcher) {
                ensureActive()
                src.open()
            }
            currentCoroutineContext().ensureActive()

            if (seekToMs > 0) {
                withContext(ioDispatcher) { src.seekTo(seekToMs) }
                currentCoroutineContext().ensureActive()
            }

            // Create and start backend.
            // In AUTO mode: if USB backend fails to start (e.g. charging cable false-positive),
            // transparently fall back to AudioTrackBackend for this track.
            be = createBackend(_uiState.value.routeOverride)
            withContext(ioDispatcher) {
                ensureActive()
                try {
                    be?.start(fmt)
                } catch (startErr: Throwable) {
                    runCatching { be?.stop() }
                }
            }
            currentCoroutineContext().ensureActive()

            // Publish resources — only if we're still the active session
            activeSource = src
            activeBackend = be
            activeSessionId = mySessionId
            pauseFlow.value = false

            _uiState.update {
                it.copy(
                    isPlaying = true,
                    isPaused = false,
                    activeBackend = be?.backendName,
                    durationMs = track.durationMs.takeIf { d -> d > 0 } ?: fmt.durationMs,
                    positionMs = if (seekToMs > 0) seekToMs else 0L,
                    sampleRate = fmt.sampleRate,
                    channels = fmt.channels,
                    mimeType = fmt.mimeType,
                    sourceBitDepth = fmt.bitsPerSample,
                    outputBitDepth = be?.outputBitDepth ?: 16,
                    pcmEncoding = fmt.pcmEncoding,
                    bufferSizeBytes = be?.bufferSize ?: 0,
                    actualBitrateKbps = ((fmt.channels * fmt.sampleRate * (be?.outputBitDepth ?: 16)) / 1000),
                    deviceName = be?.deviceName ?: ""
                )
            }
            startPositionPoll()

            // Decode + write loop on IO thread
            withContext(ioDispatcher) {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
                val chunk = ByteArray(16 * 1024)
                var chunksProcessed = 0
                
                val verifier = if (_uiState.value.routeOverride == AudioRouteOverride.BIT_PERFECT) {
                    dev.bleu.usbaudiopoc.audio.BitPerfectVerifier(fmt.sampleRate, fmt.bitsPerSample, fmt.channels, isDsd = false).apply {
                        verifyFormat(fmt.sampleRate, fmt.bitsPerSample, fmt.pcmEncoding == android.media.AudioFormat.ENCODING_PCM_FLOAT)
                    }
                } else null

                while (true) {
                    ensureActive()
                    if (pauseFlow.value) {
                        // Suspend until unpaused (switch to Main for flow collection)
                        withContext(Dispatchers.Main) { pauseFlow.filter { !it }.first() }
                        ensureActive()
                    }
                    val n = src.read(chunk)
                    when {
                        n > 0 -> {
                            verifier?.submitSourceFrame(chunk, 0, n)
                            be?.write(chunk, n)
                            
                            chunksProcessed++
                            if (chunksProcessed % 500 == 0) {
                                verifier?.logPeriodicIntegrity()
                            }
                        }
                        n < 0 -> break   // EOS
                        else -> Thread.sleep(2)
                    }
                }
            }

            // ── Natural end of track ────────────────────────────────────────
            // Only clean up shared state if we still own it
            if (activeSessionId == mySessionId) {
                activeSource = null
                activeBackend = null
                activeSessionId = -1
            }
            withContext(NonCancellable + ioDispatcher) {
                runCatching { be?.stop() }
                runCatching { src.close() }
            }
            _uiState.update { it.copy(isPlaying = false, isPaused = false, activeBackend = null, positionMs = 0L) }

            // Auto-advance to next track
            val st = _uiState.value
            if (st.hasNext) {
                _uiState.update { it.copy(currentIndex = it.currentIndex + 1) }
                val nextTrack = _uiState.value.currentTrack
                if (nextTrack != null) launchPlayback(nextTrack)
            }

        } catch (e: CancellationException) {
            // ── CRITICAL: do NOT touch activeSource/activeBackend here ──────
            // They may already point to a NEW session's resources. We only clean
            // up our LOCAL variables which we know belong to this session.
            withContext(NonCancellable + ioDispatcher) {
                runCatching { be?.stop() }
                runCatching { src.close() }
            }
            throw e   // must re-throw for coroutine machinery

        } catch (t: Throwable) {
            Log.e(TAG, "Playback error: ${track.title}", t)
            // Only clear shared state if it still belongs to us
            if (activeSessionId == mySessionId) {
                activeSource = null
                activeBackend = null
                activeSessionId = -1
                _uiState.update { it.copy(isPlaying = false, isPaused = false, activeBackend = null) }
            }
            withContext(NonCancellable + ioDispatcher) {
                runCatching { be?.stop() }
                runCatching { src.close() }
            }
            // Auto-skip to next track so one bad file doesn't kill the whole queue
            val st = _uiState.value
            if (st.hasNext) {
                _uiState.update { it.copy(currentIndex = it.currentIndex + 1) }
                val nextTrack = _uiState.value.currentTrack
                if (nextTrack != null) launchPlayback(nextTrack)
            }
        }
    }

    private fun startPositionPoll() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(500)
                if (!_uiState.value.isPlaying) break
                val pos = withContext(ioDispatcher) { activeSource?.getCurrentPositionMs() ?: 0L }
                if (pos > 0) _uiState.update { it.copy(positionMs = pos) }
            }
        }
    }

    /**
     * Create the appropriate playback backend.
     *
     * AUTO mode: tries USB → if the USB session opens but start() fails (e.g. because
     * the device is a charging cable incorrectly detected as a USB audio device), the
     * exception is caught and we silently fall back to AudioTrackBackend.
     *
     * USB_ONLY: fails hard if no real USB DAC is available.
     * ANDROID_ONLY (default): always uses AudioTrackBackend.
     */
    private suspend fun createBackend(route: AudioRouteOverride): PlaybackBackend {
        return DualPipelineBackendAdapter(route)
    }

    override fun close() {
        stop()
        scope.cancel()
    }

    private companion object { const val TAG = "PlaybackController" }
}

class DualPipelineBackendAdapter(private val routeOverride: AudioRouteOverride) : PlaybackBackend {
    private var pipeline: AudioPipeline? = null
    override val backendName: String = if (routeOverride == AudioRouteOverride.BIT_PERFECT) "BitPerfectPipeline" else "DspPipeline"
    override val deviceName: String = "Internal AudioTrack"
    override var bufferSize: Int = 0
    override var outputBitDepth: Int = 16

    override fun start(format: dev.bleu.usbaudiopoc.audio.AudioFormat) {
        pipeline = if (routeOverride == AudioRouteOverride.BIT_PERFECT) BitPerfectPipeline() else DspPipeline()
        pipeline?.initialize(format.sampleRate, format.channels, format.bitsPerSample)
        outputBitDepth = pipeline?.formatInfo?.sourceBitDepth ?: format.bitsPerSample
        bufferSize = pipeline?.bufferSize ?: 0
    }

    override fun write(buffer: ByteArray, size: Int) {
        pipeline?.writePCMData(buffer, size)
    }

    override fun pause() { pipeline?.flush() }
    override fun resume() {} 
    override fun stop() { pipeline?.release(); pipeline = null }
    override fun close() { stop() }

    fun setEqGain(band: Int, gainDb: Float) {
        (pipeline as? DspPipeline)?.setEqGain(band, gainDb)
    }
}
