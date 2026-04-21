package dev.bleu.usbaudiopoc.audio

import android.util.Log
import dev.bleu.usbaudiopoc.usb.UsbNativeBridge
import dev.bleu.usbaudiopoc.usb.UsbPlaybackSession
import dev.bleu.usbaudiopoc.usb.UsbStreamConfig
import java.nio.ByteBuffer

class UsbAudioBackend(
    private val session: UsbPlaybackSession,
) : PlaybackBackend {

    private val nativeBridge = UsbNativeBridge()
    private var directBuffer: ByteBuffer = ByteBuffer.allocateDirect(DEFAULT_DIRECT_BUFFER_SIZE)

    override val backendName: String = "USB"

    override fun start(format: WavFormat) {
        Log.i(TAG, "Starting USB backend for ${session.device.deviceName}")
        nativeBridge.initUsb(
            deviceFd = session.fileDescriptor,
            config = UsbStreamConfig(
                rawDescriptors = session.rawDescriptors,
                vendorId = session.device.vendorId,
                productId = session.device.productId,
            ),
        )
        nativeBridge.startStream(
            sampleRate = format.sampleRate,
            bitDepth = format.bitsPerSample,
            channels = format.channels,
        )
    }

    override fun write(buffer: ByteArray, size: Int) {
        ensureDirectBuffer(size)
        directBuffer.clear()
        directBuffer.put(buffer, 0, size)
        directBuffer.flip()
        nativeBridge.writePcm(directBuffer, size)
    }

    override fun pause() {
        Log.i(TAG, "Pause requested for USB backend; source stops feeding PCM while native stream drains")
    }

    override fun resume() {
        Log.i(TAG, "Resume requested for USB backend")
    }

    override fun stop() {
        Log.i(TAG, "Stopping USB backend")
        runCatching { nativeBridge.stopStream() }
        nativeBridge.release()
        session.close()
    }

    override fun close() {
        stop()
    }

    private fun ensureDirectBuffer(requiredBytes: Int) {
        if (directBuffer.capacity() >= requiredBytes) {
            return
        }
        directBuffer = ByteBuffer.allocateDirect(requiredBytes)
    }

    private companion object {
        const val TAG = "UsbAudioBackend"
        const val DEFAULT_DIRECT_BUFFER_SIZE = 64 * 1024
    }
}
