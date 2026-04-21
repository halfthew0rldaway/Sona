package dev.bleu.usbaudiopoc.audio

enum class AudioRouteOverride(val displayName: String) {
    AUTO("Auto (USB if available)"),
    USB_ONLY("USB DAC only"),
    ANDROID_ONLY("Android AudioTrack");

    companion object {
        fun fromPosition(position: Int): AudioRouteOverride {
            return entries.getOrElse(position) { AUTO }
        }
    }
}
