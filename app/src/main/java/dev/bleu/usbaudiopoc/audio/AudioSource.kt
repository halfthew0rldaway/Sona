package dev.bleu.usbaudiopoc.audio

import java.io.Closeable

interface AudioSource : Closeable {
    fun open(): AudioFormat
    fun read(target: ByteArray, size: Int = target.size): Int
    fun seekTo(positionMs: Long)
    fun getCurrentPositionMs(): Long
    override fun close()
}
