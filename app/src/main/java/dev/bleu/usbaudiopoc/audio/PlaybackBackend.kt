package dev.bleu.usbaudiopoc.audio

import java.io.Closeable

interface PlaybackBackend : Closeable {
    val backendName: String
    val deviceName: String
    val bufferSize: Int
    val outputBitDepth: Int

    fun start(format: AudioFormat)

    fun write(buffer: ByteArray, size: Int)

    fun pause()

    fun resume()

    fun stop()
}
