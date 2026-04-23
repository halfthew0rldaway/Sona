package dev.bleu.usbaudiopoc.player

import dev.bleu.usbaudiopoc.audio.AudioRouteOverride

data class PlayerUiState(
    val statusMessage: String = "Idle",
    val routeOverride: AudioRouteOverride = AudioRouteOverride.BIT_PERFECT,
    val activeBackend: String? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val sampleRate: Int = 0,
    val outputSampleRate: Int? = null,
    val isTransitioning: Boolean = false,
    val channels: Int = 0,
    val mimeType: String = "",
    val sourceBitDepth: Int = 16,
    val outputBitDepth: Int = 16,
    val pcmEncoding: Int = 2, // ENCODING_PCM_16BIT by default
    val bufferSizeBytes: Int = 0,
    val actualBitrateKbps: Int = 0,
    val deviceName: String = "",
    val queue: List<TrackItem> = emptyList(),
    val currentIndex: Int = -1,
) {
    val currentTrack: TrackItem? get() = queue.getOrNull(currentIndex)
    val hasPrev: Boolean get() = currentIndex > 0
    val hasNext: Boolean get() = currentIndex < queue.size - 1
    val artBytes: ByteArray? get() = currentTrack?.artBytes
}
