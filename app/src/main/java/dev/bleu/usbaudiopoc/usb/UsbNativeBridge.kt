package dev.bleu.usbaudiopoc.usb

import java.io.Closeable
import java.nio.ByteBuffer

data class UsbStreamConfig(
    val rawDescriptors: ByteArray,
    val vendorId: Int,
    val productId: Int,
)

class UsbNativeBridge : Closeable {

    private var nativeHandle: Long = 0L

    fun initUsb(deviceFd: Int, config: UsbStreamConfig) {
        release()
        nativeHandle = nativeInitUsb(
            deviceFd = deviceFd,
            rawDescriptors = config.rawDescriptors,
            vendorId = config.vendorId,
            productId = config.productId,
        )
        check(nativeHandle != 0L) {
            "nativeInitUsb returned an invalid handle"
        }
    }

    fun startStream(sampleRate: Int, bitDepth: Int, channels: Int) {
        require(nativeHandle != 0L) { "USB bridge has not been initialized" }
        check(nativeStartStream(nativeHandle, sampleRate, bitDepth, channels)) {
            "USB stream failed to start with exact PCM format"
        }
    }

    fun writePcm(buffer: ByteBuffer, size: Int) {
        require(nativeHandle != 0L) { "USB bridge has not been initialized" }
        require(buffer.isDirect) { "USB PCM buffer must be a direct ByteBuffer" }
        check(nativeWritePcm(nativeHandle, buffer, size) >= 0) {
            "USB PCM write failed"
        }
    }

    fun stopStream() {
        if (nativeHandle != 0L) {
            nativeStopStream(nativeHandle)
        }
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
    }

    override fun close() {
        release()
    }

    private external fun nativeInitUsb(
        deviceFd: Int,
        rawDescriptors: ByteArray,
        vendorId: Int,
        productId: Int,
    ): Long

    private external fun nativeStartStream(
        nativeHandle: Long,
        sampleRate: Int,
        bitDepth: Int,
        channels: Int,
    ): Boolean

    private external fun nativeWritePcm(
        nativeHandle: Long,
        buffer: ByteBuffer,
        size: Int,
    ): Int

    private external fun nativeStopStream(nativeHandle: Long)

    private external fun nativeRelease(nativeHandle: Long)

    companion object {
        init {
            System.loadLibrary("usb-audio-poc")
        }
    }
}
