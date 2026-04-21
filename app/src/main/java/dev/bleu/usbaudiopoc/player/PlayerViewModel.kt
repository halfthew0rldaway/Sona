package dev.bleu.usbaudiopoc.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import dev.bleu.usbaudiopoc.audio.AudioRouteOverride
import dev.bleu.usbaudiopoc.usb.UsbAudioRouteManager
import dev.bleu.usbaudiopoc.usb.UsbRouteState
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val routeManager = UsbAudioRouteManager(application)
    private val controller = PlaybackController(
        contentResolver = application.contentResolver,
        usbAudioRouteManager = routeManager,
    )

    val playerState: StateFlow<PlayerUiState> = controller.uiState
    val usbState: StateFlow<UsbRouteState> = routeManager.usbState

    fun selectFile(uri: Uri, label: String) {
        controller.setSelectedFile(uri, label)
    }

    fun setRouteOverride(routeOverride: AudioRouteOverride) {
        controller.setRouteOverride(routeOverride)
    }

    fun play() {
        controller.play()
    }

    fun pause() {
        controller.pause()
    }

    fun stop() {
        controller.stop()
    }

    override fun onCleared() {
        controller.close()
        routeManager.close()
        super.onCleared()
    }
}
