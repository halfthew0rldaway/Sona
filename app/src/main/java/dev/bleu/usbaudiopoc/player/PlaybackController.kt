package dev.bleu.usbaudiopoc.player

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import dev.bleu.usbaudiopoc.audio.AudioRouteOverride
import dev.bleu.usbaudiopoc.audio.AudioTrackBackend
import dev.bleu.usbaudiopoc.audio.PlaybackBackend
import dev.bleu.usbaudiopoc.audio.UsbAudioBackend
import dev.bleu.usbaudiopoc.audio.WavStreamingSource
import dev.bleu.usbaudiopoc.usb.UsbAudioRouteManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable

class PlaybackController(
    private val contentResolver: ContentResolver,
    private val usbAudioRouteManager: UsbAudioRouteManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val pauseRequests = MutableStateFlow(false)

    private var selectedUri: Uri? = null
    private var selectedFileLabel: String = "none selected"
    private var playbackJob: Job? = null
    private var currentSource: WavStreamingSource? = null
    private var currentBackend: PlaybackBackend? = null

    fun setSelectedFile(uri: Uri, label: String) {
        selectedUri = uri
        selectedFileLabel = label
        _uiState.update {
            it.copy(
                selectedFileLabel = label,
                statusMessage = if (it.isPlaying || it.isPaused) it.statusMessage else "Ready",
            )
        }
    }

    fun setRouteOverride(routeOverride: AudioRouteOverride) {
        _uiState.update { it.copy(routeOverride = routeOverride) }
    }

    fun play() {
        if (_uiState.value.isPaused && playbackJob?.isActive == true) {
            currentBackend?.resume()
            pauseRequests.value = false
            _uiState.update {
                it.copy(
                    statusMessage = "Playing",
                    isPlaying = true,
                    isPaused = false,
                )
            }
            return
        }

        val uri = selectedUri ?: run {
            _uiState.update { it.copy(statusMessage = "Select a WAV file first") }
            return
        }
        if (playbackJob?.isActive == true) {
            return
        }

        playbackJob = controllerScope.launch {
            startPlayback(uri)
        }
    }

    fun pause() {
        if (playbackJob?.isActive != true || _uiState.value.isPaused) {
            return
        }
        pauseRequests.value = true
        currentBackend?.pause()
        _uiState.update {
            it.copy(
                statusMessage = "Paused",
                isPlaying = false,
                isPaused = true,
            )
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        controllerScope.launch {
            teardown("Stopped")
        }
    }

    override fun close() {
        playbackJob?.cancel()
        playbackJob = null
        runCatching { currentBackend?.stop() }
        runCatching { currentSource?.close() }
        currentBackend = null
        currentSource = null
        _uiState.value = _uiState.value.copy(
            statusMessage = "Stopped",
            activeBackend = null,
            isPlaying = false,
            isPaused = false,
        )
        controllerScope.cancel()
    }

    private suspend fun startPlayback(uri: Uri) = withContext(ioDispatcher) {
        val source = WavStreamingSource(contentResolver, uri)
        try {
            val format = source.open()
            val backend = createBackend(_uiState.value.routeOverride)
            currentSource = source
            currentBackend = backend
            pauseRequests.value = false

            backend.start(format)
            _uiState.update {
                it.copy(
                    statusMessage = "Playing",
                    selectedFileLabel = selectedFileLabel,
                    activeBackend = backend.backendName,
                    isPlaying = true,
                    isPaused = false,
                )
            }

            val pcmChunk = ByteArray(DEFAULT_CHUNK_BYTES)
            while (true) {
                if (pauseRequests.value) {
                    pauseRequests.filter { paused -> !paused }.first()
                }
                val read = source.read(pcmChunk)
                if (read <= 0) {
                    break
                }
                backend.write(pcmChunk, read)
            }
            teardown("Completed")
        } catch (cancelled: CancellationException) {
            Log.i(TAG, "Playback cancelled")
            teardown("Stopped")
            throw cancelled
        } catch (t: Throwable) {
            Log.e(TAG, "Playback failed", t)
            teardown("Error: ${t.message ?: "unknown"}")
        }
    }

    private suspend fun createBackend(routeOverride: AudioRouteOverride): PlaybackBackend {
        if (routeOverride != AudioRouteOverride.ANDROID_ONLY) {
            val session = usbAudioRouteManager.openPlaybackSessionOrNull()
            if (session != null) {
                return UsbAudioBackend(session)
            }
            check(routeOverride != AudioRouteOverride.USB_ONLY) {
                "USB DAC is unavailable or permission was not granted"
            }
        }
        return AudioTrackBackend()
    }

    private suspend fun teardown(status: String) = withContext(ioDispatcher) {
        val backend = currentBackend
        currentBackend = null
        val source = currentSource
        currentSource = null
        runCatching { backend?.stop() }
        runCatching { source?.close() }
        _uiState.update {
            it.copy(
                statusMessage = status,
                activeBackend = null,
                isPlaying = false,
                isPaused = false,
            )
        }
    }

    private companion object {
        const val TAG = "PlaybackController"
        const val DEFAULT_CHUNK_BYTES = 16 * 1024
    }
}
