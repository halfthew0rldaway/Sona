package dev.bleu.usbaudiopoc.audio

import android.media.AudioFormat as AndroidAudioFormat

data class AudioFormat(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val durationMs: Long,
    val mimeType: String,
    /** Actual PCM encoding produced by MediaCodec — ENCODING_PCM_16BIT or ENCODING_PCM_FLOAT. */
    val pcmEncoding: Int = AndroidAudioFormat.ENCODING_PCM_16BIT,
) {
    fun toWavFormat(): WavFormat {
        val blockAlign = channels * (bitsPerSample / 8)
        val byteRate = sampleRate * blockAlign
        return WavFormat(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            blockAlign = blockAlign,
            byteRate = byteRate,
            dataSizeBytes = 0L,
        )
    }
}
