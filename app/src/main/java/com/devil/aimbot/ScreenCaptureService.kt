
package com.devil.aimbot

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.*
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var resultCode: Int = 0
    private var dataIntent: Intent? = null
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private lateinit var aimbotManager: AimbotManager

    override fun onCreate() { super.onCreate(); aimbotManager = AimbotManager(this); createNotificationChannel() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getStringExtra("action") == "STOP") { stopSelf(); return START_NOT_STICKY }
        resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        dataIntent = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("dataIntent", Intent::class.java) else intent?.getParcelableExtra("dataIntent")
        if (dataIntent != null) { startForeground(1, createNotification()); setupMediaProjection() }
        return START_STICKY
    }
    private fun setupMediaProjection() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, dataIntent!!)
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= 30) { val bounds = windowManager.currentWindowMetrics.bounds; metrics.widthPixels = bounds.width(); metrics.heightPixels = bounds.height() } else { windowManager.defaultDisplay.getMetrics(metrics) }
        metrics.densityDpi = resources.displayMetrics.densityDpi
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay("HimmiCapture", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val planes = image.planes; val buffer: ByteBuffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride; val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * metrics.widthPixels
                val bitmap = Bitmap.createBitmap(metrics.widthPixels + rowPadding / pixelStride, metrics.heightPixels, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                scope.launch { val headRect = aimbotManager.detectHead(bitmap); headRect?.let { TriggerSwipeHelper.performMagneticPull(it.centerX(), it.centerY(), metrics.widthPixels, metrics.heightPixels) } }
            } catch (e: Exception) { e.printStackTrace() } finally { image.close() }
        }, null, null)
    }
    private fun createNotificationChannel() { if (Build.VERSION.SDK_INT >= 26) { val channel = NotificationChannel("capture_channel", "Screen Capture", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager::class.java).createNotificationChannel(channel) } }
    private fun createNotification(): Notification { return if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "capture_channel").setContentTitle("Aimbot Active").setSmallIcon(android.R.drawable.ic_menu_camera).build() else Notification.Builder(this).setContentTitle("Aimbot Active").setSmallIcon(android.R.drawable.ic_menu_camera).build() }
    override fun onDestroy() { job.cancel(); virtualDisplay?.release(); imageReader?.close(); mediaProjection?.stop(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
