package dev.bleu.usbaudiopoc.audio

import java.io.Closeable

interface PlaybackBackend : Closeable {
    val backendName: String

    fun start(format: WavFormat)

    fun write(buffer: ByteArray, size: Int)

    fun pause()

    fun resume()

    fun stop()
}
