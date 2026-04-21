package dev.bleu.usbaudiopoc.player

import dev.bleu.usbaudiopoc.audio.AudioRouteOverride

data class PlayerUiState(
    val statusMessage: String = "Idle",
    val selectedFileLabel: String = "none selected",
    val routeOverride: AudioRouteOverride = AudioRouteOverride.AUTO,
    val activeBackend: String? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
)
