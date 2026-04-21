package dev.bleu.usbaudiopoc.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import java.io.Closeable

class UsbPlaybackSession(
    val device: UsbDevice,
    val connection: UsbDeviceConnection,
    val fileDescriptor: Int,
    val rawDescriptors: ByteArray,
) : Closeable {

    private var closed = false

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        Log.i(TAG, "Closing USB session for ${device.deviceName}")
        connection.close()
    }

    private companion object {
        const val TAG = "UsbPlaybackSession"
    }
}
