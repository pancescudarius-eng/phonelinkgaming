package com.cosyra.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
            ?: return stopBecauseInvalidData()
        val resultData = getResultData(intent) ?: return stopBecauseInvalidData()

        if (mediaProjection == null) {
            startCapture(resultCode, resultData)
        }

        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData).also { projection ->
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopCapture(stopProjection = false)
                    stopSelf()
                }
            }, null)
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader ->
                reader.acquireLatestImage()?.close()
            }, null)
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "CosyraHostCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    private fun stopBecauseInvalidData(): Int {
        stopCapture()
        stopSelf()
        return START_NOT_STICKY
    }

    private fun stopCapture(stopProjection: Boolean = true) {
        if (stopping) return
        stopping = true

        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null

        val projection = mediaProjection
        mediaProjection = null
        if (stopProjection) projection?.stop()

        stopping = false
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Cosyra Host activ")
            .setContentText("Ecranul este pregătit pentru streaming securizat.")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Capturare ecran Cosyra",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Afișează starea sesiunii Host."
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun getResultData(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

    companion object {
        const val ACTION_START = "com.cosyra.app.action.START_CAPTURE"
        const val ACTION_STOP = "com.cosyra.app.action.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val CHANNEL_ID = "cosyra_host_capture"
        private const val NOTIFICATION_ID = 1001
    }
}
