package dev.bleu.usbaudiopoc.player

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.bleu.usbaudiopoc.audio.AudioRouteOverride
import dev.bleu.usbaudiopoc.usb.UsbAudioRouteManager
import dev.bleu.usbaudiopoc.usb.UsbRouteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val routeManager = UsbAudioRouteManager(application)
    private val controller = PlaybackController(
        context = application,
        usbAudioRouteManager = routeManager,
    )

    /** SharedPreferences to persist the last selected folder across app launches. */
    private val prefs = application.getSharedPreferences("sona_player_prefs", Context.MODE_PRIVATE)

    val playerState: StateFlow<PlayerUiState> = controller.uiState
    val usbState: StateFlow<UsbRouteState> = routeManager.usbState

    /** Emits true when a folder/file load just completed and the UI should navigate to Now Playing. */
    private val _navigateToNowPlaying = MutableStateFlow(false)
    val navigateToNowPlaying: StateFlow<Boolean> = _navigateToNowPlaying.asStateFlow()

    /** Name of the currently loaded folder (for display in Library tab). */
    private val _loadedFolderName = MutableStateFlow<String?>(null)
    val loadedFolderName: StateFlow<String?> = _loadedFolderName.asStateFlow()

    private val _ignoreSubfolders = MutableStateFlow(prefs.getBoolean(PREF_IGNORE_SUBFOLDERS, false))
    val ignoreSubfolders: StateFlow<Boolean> = _ignoreSubfolders.asStateFlow()

    init {
        // Restore last folder in the background without blocking the UI
        restoreLastFolder()
    }

    /**
     * Restore the previously selected folder.  Permission is verified before scanning
     * to avoid crashes when the folder is no longer accessible (e.g. SD card removed).
     * Tracks are loaded into the queue WITHOUT auto-playing so the user resumes manually.
     */
    private fun restoreLastFolder() {
        val savedUri = prefs.getString(PREF_LAST_FOLDER_URI, null) ?: return
        val savedName = prefs.getString(PREF_LAST_FOLDER_NAME, null)
        val treeUri = runCatching { Uri.parse(savedUri) }.getOrNull() ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                // Verify the persistable URI permission is still granted
                val hasPermission = ctx.contentResolver.persistedUriPermissions.any { perm ->
                    perm.uri == treeUri && perm.isReadPermission
                }
                if (!hasPermission) {
                    Log.w(TAG, "Saved folder permission revoked — clearing saved URI")
                    clearSavedFolder()
                    return@launch
                }
                val root = DocumentFile.fromTreeUri(ctx, treeUri) ?: run {
                    clearSavedFolder()
                    return@launch
                }
                val tracks = mutableListOf<TrackItem>()
                scanFolder(root, tracks, isRoot = true)
                tracks.sortWith(compareBy({ it.album.lowercase() }, { it.fileName.lowercase() }))
                withContext(Dispatchers.Main) {
                    if (tracks.isNotEmpty()) {
                        controller.setQueue(tracks, 0)
                        _loadedFolderName.value = savedName ?: treeUri.lastPathSegment
                        // ⚠ Do NOT auto-play on restore — user decides when to start
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore last folder, clearing saved URI", e)
                clearSavedFolder()
            }
        }
    }

    fun consumeNavigateToNowPlaying() { _navigateToNowPlaying.value = false }

    private var lastModeSwitchTime = 0L
    private val DEBOUNCE_MS = 400L

    fun setRouteOverrideSafe(r: AudioRouteOverride) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastModeSwitchTime < DEBOUNCE_MS) return
        if (playerState.value.routeOverride == r) return
        lastModeSwitchTime = currentTime
        controller.setRouteOverride(r)
    }
    fun play() = controller.play()
    fun pause() = controller.pause()
    fun stop() = controller.stop()
    fun skipNext() = controller.skipNext()
    fun skipPrev() = controller.skipPrev()
    fun requestUsbPermission() = routeManager.requestPermissionForCurrentDevice()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    fun setEqGain(band: Int, progress: Int) {
        val gainDb = (progress - 100) / 10f
        controller.setEqGain(band, gainDb)
    }

    fun toggleIgnoreSubfolders() {
        val newValue = !_ignoreSubfolders.value
        prefs.edit().putBoolean(PREF_IGNORE_SUBFOLDERS, newValue).apply()
        _ignoreSubfolders.value = newValue
    }

    fun rescanCurrentFolder() {
        restoreLastFolder()
    }

    fun selectQueueIndex(index: Int) {
        controller.setQueueIndex(index)
        controller.play()
    }

    fun selectFile(uri: Uri, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val track = extractMetadata(uri, label)
            withContext(Dispatchers.Main) {
                controller.setQueue(listOf(track), 0)
                controller.play()
                _navigateToNowPlaying.value = true
            }
        }
    }

    fun openFolder(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val root = DocumentFile.fromTreeUri(ctx, treeUri) ?: return@launch
            val folderName = root.name ?: treeUri.lastPathSegment ?: "Folder"
            val tracks = mutableListOf<TrackItem>()
            scanFolder(root, tracks, isRoot = true)
            tracks.sortWith(compareBy({ it.album.lowercase() }, { it.fileName.lowercase() }))
            withContext(Dispatchers.Main) {
                if (tracks.isNotEmpty()) {
                    // Persist so next launch reloads automatically
                    prefs.edit()
                        .putString(PREF_LAST_FOLDER_URI, treeUri.toString())
                        .putString(PREF_LAST_FOLDER_NAME, folderName)
                        .apply()
                    _loadedFolderName.value = folderName
                    controller.setQueue(tracks, 0)
                    controller.play()
                    _navigateToNowPlaying.value = true
                }
            }
        }
    }

    private fun clearSavedFolder() {
        prefs.edit().remove(PREF_LAST_FOLDER_URI).remove(PREF_LAST_FOLDER_NAME).apply()
    }

    private fun extractMetadata(uri: Uri, fallbackLabel: String): TrackItem {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(getApplication<Application>(), uri)
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: fallbackLabel
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val art = mmr.embeddedPicture
            TrackItem(uri, title, artist, album, dur, fallbackLabel, art)
        } catch (_: Exception) {
            TrackItem(uri, fallbackLabel, "", "", 0L, fallbackLabel)
        } finally {
            mmr.release()
        }
    }

    private fun scanFolder(dir: DocumentFile, out: MutableList<TrackItem>, isRoot: Boolean) {
        if (!isRoot && _ignoreSubfolders.value) return
        for (file in dir.listFiles()) {
            when {
                file.isDirectory -> scanFolder(file, out, false)
                file.type?.startsWith("audio/") == true || file.name?.endsWith(".wav", true) == true || file.name?.endsWith(".flac", true) == true -> {
                    val fileName = file.name ?: continue
                    out += extractMetadata(file.uri, fileName)
                }
            }
        }
    }

    override fun onCleared() {
        controller.close()
        routeManager.close()
        super.onCleared()
    }

    private companion object {
        const val TAG = "PlayerViewModel"
        const val PREF_LAST_FOLDER_URI = "last_folder_uri"
        const val PREF_LAST_FOLDER_NAME = "last_folder_name"
        const val PREF_IGNORE_SUBFOLDERS = "ignore_subfolders"
    }
}
