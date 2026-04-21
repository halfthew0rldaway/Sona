package dev.bleu.usbaudiopoc.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.max

class AudioTrackBackend : PlaybackBackend {

    private var audioTrack: AudioTrack? = null

    override val backendName: String = "AudioTrack"

    override fun start(format: WavFormat) {
        stop()
        val channelMask = when (format.channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> throw IllegalArgumentException("AudioTrack fallback supports mono/stereo in Milestone 1")
        }
        val encoding = when (format.bitsPerSample) {
            16 -> AudioFormat.ENCODING_PCM_16BIT
            24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
            else -> throw IllegalArgumentException("Unsupported PCM width: ${format.bitsPerSample}")
        }
        val minBufferSize = AudioTrack.getMinBufferSize(format.sampleRate, channelMask, encoding)
        require(minBufferSize > 0) {
            "AudioTrack could not create a buffer for ${format.sampleRate} Hz"
        }
        val bufferSizeBytes = max(minBufferSize, format.blockAlign * 1024)
        Log.i(TAG, "Starting AudioTrack sr=${format.sampleRate} ch=${format.channels} bits=${format.bitsPerSample} buffer=$bufferSizeBytes")
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setChannelMask(channelMask)
                    .setSampleRate(format.sampleRate)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSizeBytes)
            .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
            .build()
        check(track.state == AudioTrack.STATE_INITIALIZED) {
            "AudioTrack failed to initialize"
        }
        track.play()
        audioTrack = track
    }

    override fun write(buffer: ByteArray, size: Int) {
        val track = audioTrack ?: throw IllegalStateException("AudioTrack backend is not started")
        var offset = 0
        while (offset < size) {
            val written = track.write(buffer, offset, size - offset, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                throw IllegalStateException("AudioTrack write failed: $written")
            }
            offset += written
        }
    }

    override fun pause() {
        audioTrack?.pause()
    }

    override fun resume() {
        audioTrack?.play()
    }

    override fun stop() {
        audioTrack?.let { track ->
            Log.i(TAG, "Stopping AudioTrack backend")
            runCatching {
                if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
                    track.stop()
                }
            }
            runCatching { track.flush() }
            track.release()
        }
        audioTrack = null
    }

    override fun close() {
        stop()
    }

    private companion object {
        const val TAG = "AudioTrackBackend"
    }
}
