package dev.bleu.usbaudiopoc.player

import android.net.Uri

data class TrackItem(
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val fileName: String,
    val artBytes: ByteArray? = null,
) {
    // Don't include artBytes in equality/hashCode — it's large
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackItem) return false
        return uri == other.uri && title == other.title && artist == other.artist &&
            album == other.album && durationMs == other.durationMs && fileName == other.fileName
    }
    override fun hashCode(): Int = uri.hashCode()
}
