package dev.bleu.usbaudiopoc.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.media.AudioFormat as AndroidAudioFormat
import android.util.Log
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * Decodes any Android-supported audio format to raw PCM via MediaCodec.
 *
 * The actual PCM encoding (16-bit int or 32-bit float) is detected from the codec's
 * output format and stored in the returned AudioFormat.pcmEncoding field. Callers
 * must configure their AudioTrack to match.
 */
class MediaDecoderSource(
    private val context: Context,
    private val uri: Uri,
) : AudioSource {

    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var _format: AudioFormat? = null
    val format: AudioFormat? get() = _format

    private var inputDone = false
    private var outputDone = false

    // Detected output PCM encoding (resolved after codec starts and produces first output)
    @Volatile private var resolvedEncoding: Int = AndroidAudioFormat.ENCODING_PCM_16BIT

    // Small ring buffer so read() can return exactly the requested byte count
    private var pending: ByteArray? = null
    private var pendingOffset = 0

    override fun open(): AudioFormat {
        close()
        val ext = MediaExtractor()
        ext.setDataSource(context, uri, null)

        var trackIndex = -1
        var trackFormat: MediaFormat? = null
        for (i in 0 until ext.trackCount) {
            val fmt = ext.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) { trackIndex = i; trackFormat = fmt; break }
        }
        check(trackIndex >= 0) { "No audio track found in $uri" }
        val fmt = trackFormat!!
        ext.selectTrack(trackIndex)

        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(fmt, null, null, 0)
        codec.start()

        // Detect initial output encoding from output format.
        val outputFmt = codec.outputFormat
        resolvedEncoding = if (outputFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            outputFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            // Extractor fallback
            if (fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AndroidAudioFormat.ENCODING_PCM_16BIT
            }
        }

        extractor = ext
        this.codec = codec
        inputDone = false
        outputDone = false

        val bitsPerSample = when (resolvedEncoding) {
            AndroidAudioFormat.ENCODING_PCM_FLOAT -> 32
            // API 31+ 24-bit and 32-bit integer support
            21 /* ENCODING_PCM_24BIT_PACKED */ -> 24
            22 /* ENCODING_PCM_32BIT */ -> 32
            AndroidAudioFormat.ENCODING_PCM_8BIT -> 8
            else -> 16
        }

        val resolved = AudioFormat(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            durationMs = durationUs / 1000,
            mimeType = mime,
            pcmEncoding = resolvedEncoding,
        )
        _format = resolved
        Log.i(TAG, "Opened: mime=$mime sr=$sampleRate ch=$channels encoding=$resolvedEncoding dur=${resolved.durationMs}ms")
        return resolved
    }

    /**
     * Returns decoded PCM bytes filling [target] up to [size] bytes.
     * Returns -1 at end of stream, 0 if no bytes available yet.
     *
     * If codec outputs FLOAT and caller requested 16-bit (check AudioFormat.pcmEncoding),
     * pass convertFloatToInt16=true to down-convert on the fly.
     */
    override fun read(target: ByteArray, size: Int): Int {
        if (pending != null) return drainPending(target, size)
        if (outputDone) return -1

        val ext = extractor ?: return -1
        val codec = codec ?: return -1

        if (!inputDone) {
            val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex >= 0) {
                val inBuf: ByteBuffer = codec.getInputBuffer(inIndex)!!
                val sampleSize = ext.readSampleData(inBuf, 0)
                if (sampleSize < 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    codec.queueInputBuffer(inIndex, 0, sampleSize, ext.sampleTime, 0)
                    ext.advance()
                }
            }
        }

        val info = MediaCodec.BufferInfo()
        val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
        when {
            outIndex >= 0 -> {
                // Re-check encoding on format change events (output format may update mid-stream)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                if (info.size > 0) {
                    val outBuf: ByteBuffer = codec.getOutputBuffer(outIndex)!!
                    val bytes = ByteArray(info.size)
                    outBuf.get(bytes)
                    codec.releaseOutputBuffer(outIndex, false)
                    pending = bytes
                    pendingOffset = 0
                    return drainPending(target, size)
                } else {
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }
            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val newFmt = codec.outputFormat
                val newEncoding = if (newFmt.containsKey(MediaFormat.KEY_PCM_ENCODING))
                    newFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                else
                    AndroidAudioFormat.ENCODING_PCM_16BIT
                if (newEncoding != resolvedEncoding) {
                    Log.w(TAG, "Output encoding changed mid-stream: $resolvedEncoding -> $newEncoding")
                    resolvedEncoding = newEncoding
                    _format = _format?.copy(pcmEncoding = newEncoding)
                }
                Log.d(TAG, "Output format changed: $newFmt")
            }
            outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> { /* nothing ready yet */ }
        }
        if (outputDone && pending == null) return -1
        return 0
    }

    private fun drainPending(target: ByteArray, size: Int): Int {
        val buf = pending ?: return 0
        val available = buf.size - pendingOffset
        val toCopy = minOf(available, size)
        buf.copyInto(target, 0, pendingOffset, pendingOffset + toCopy)
        pendingOffset += toCopy
        if (pendingOffset >= buf.size) { pending = null; pendingOffset = 0 }
        return toCopy
    }

    /**
     * Seek to [positionMs]. Must be called on IO thread.
     * Resets codec input/output state so the decode loop restarts cleanly.
     */
    override fun seekTo(positionMs: Long) {
        val ext = extractor ?: return
        val codec = codec ?: return
        ext.seekTo(positionMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        codec.flush()
        inputDone = false
        outputDone = false
        pending = null
        pendingOffset = 0
    }

    override fun getCurrentPositionMs(): Long =
        extractor?.sampleTime?.let { if (it >= 0) it / 1000 else 0L } ?: 0L

    override fun close() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor?.release() }
        codec = null
        extractor = null
        _format = null
        pending = null
        pendingOffset = 0
        inputDone = false
        outputDone = false
        resolvedEncoding = AndroidAudioFormat.ENCODING_PCM_16BIT
    }

    private companion object {
        const val TAG = "MediaDecoderSource"
        const val TIMEOUT_US = 10_000L
    }
}
