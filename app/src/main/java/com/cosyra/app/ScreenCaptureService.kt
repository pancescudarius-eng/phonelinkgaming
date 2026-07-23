package com.cosyra.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Keeps the app in a mediaProjection foreground-service state while WebRTC owns
 * the actual MediaProjection and video capturer. A projection permission token
 * must only be consumed once on modern Android versions.
 */
class ScreenCaptureService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Cosyra Host activ")
            .setContentText("Ecranul este transmis prin conexiunea WebRTC.")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Capturare ecran Cosyra",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Afișează starea sesiunii Host."
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.cosyra.app.action.START_CAPTURE"
        const val ACTION_STOP = "com.cosyra.app.action.STOP_CAPTURE"

        private const val CHANNEL_ID = "cosyra_host_capture"
        private const val NOTIFICATION_ID = 1001
    }
}
