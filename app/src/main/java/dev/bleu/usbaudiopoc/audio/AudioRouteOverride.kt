package dev.bleu.usbaudiopoc.audio

enum class AudioRouteOverride(val displayName: String) {
    BIT_PERFECT("Bit-Perfect Mode"),
    DSP("DSP Mode");

    companion object {
        fun fromPosition(position: Int): AudioRouteOverride {
            return entries.getOrElse(position) { BIT_PERFECT }
        }
    }
}
