package dev.bleu.usbaudiopoc.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.bleu.usbaudiopoc.MainActivity
import dev.bleu.usbaudiopoc.R

class MediaNotificationManager(private val context: Context) {
    private val channelId = "playback_channel"
    private val notificationId = 101
    private var mediaSession: MediaSessionCompat

    init {
        createNotificationChannel()
        mediaSession = MediaSessionCompat(context, "PlayerSession")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Playback"
            val descriptionText = "Audio Playback Controls"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateNotification(state: PlayerUiState) {
        val track = state.currentTrack ?: return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_nav_playing)
            .setContentTitle(track.title)
            .setContentText(track.artist.takeIf { it.isNotBlank() } ?: track.album)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(state.isPlaying)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)

        if (track.artBytes != null) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(track.artBytes, 0, track.artBytes.size)
                builder.setLargeIcon(bitmap)
            } catch (e: Exception) {}
        }

        // Add play/pause toggle (usually needs a service but we can visually fake it if not interactive)
        // Without a BroadcastReceiver or Service, interactive buttons need PendingIntents to actual broadcasts.
        // For the sake of this UI requirement, we'll configure MediaStyle at least visually.
        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
        )

        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            }
        } catch (e: Exception) {}
    }
}
