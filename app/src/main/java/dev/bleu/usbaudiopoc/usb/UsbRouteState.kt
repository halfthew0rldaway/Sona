package dev.bleu.usbaudiopoc.usb

import android.hardware.usb.UsbDevice

data class UsbRouteState(
    val compatibleDevice: UsbDevice? = null,
    val hasPermission: Boolean = false,
    val description: String = "USB route: none",
)
