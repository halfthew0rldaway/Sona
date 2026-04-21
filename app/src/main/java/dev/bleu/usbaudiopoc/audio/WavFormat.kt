package dev.bleu.usbaudiopoc.audio

data class WavFormat(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val blockAlign: Int,
    val byteRate: Int,
    val dataSizeBytes: Long,
) {
    val bytesPerSample: Int = bitsPerSample / 8
}
