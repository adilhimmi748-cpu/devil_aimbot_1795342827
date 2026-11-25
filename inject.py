import os, re

aimbot_code = """
package com.devil.aimbot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class AimbotManager(context: Context) {
    private var interpreter: Interpreter? = null
    private val inputSize = 300

    init {
        try {
            val fileDescriptor = context.assets.openFd("ssd_mobilenet.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val modelBuffer = inputStream.channel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
            interpreter = Interpreter(modelBuffer, Interpreter.Options().apply { setNumThreads(4) })
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun detectHead(bitmap: Bitmap): RectF? {
        val currentInterpreter = interpreter ?: return null
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply { order(ByteOrder.nativeOrder()) }
        val intValues = IntArray(inputSize * inputSize)
        resized.getPixels(intValues, 0, resized.width, 0, 0, resized.width, resized.height)
        var pixel = 0
        for (i in 0 until inputSize) { for (j in 0 until inputSize) {
            val value = intValues[pixel++]
            inputBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)
            inputBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)
            inputBuffer.putFloat((value and 0xFF) / 255.0f)
        }}
        val outputLocations = Array(1) { Array(10) { FloatArray(4) } }
        val outputClasses = Array(1) { FloatArray(10) }
        val outputScores = Array(1) { FloatArray(10) }
        val numDetections = FloatArray(1)
        val outputs = mapOf(0 to outputLocations, 1 to outputClasses, 2 to outputScores, 3 to numDetections)
        try { currentInterpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs) } catch (e: Exception) { return null }
        var bestConf = 0.0f; var bestRect: RectF? = null
        val count = numDetections[0].toInt()
        for (i in 0 until count) {
            val score = outputScores[0][i]
            if (score > 0.5f) {
                val top = outputLocations[0][i][0] * bitmap.height
                val left = outputLocations[0][i][1] * bitmap.width
                val bottom = outputLocations[0][i][2] * bitmap.height
                val right = outputLocations[0][i][3] * bitmap.width
                if (score > bestConf) {
                    bestConf = score
                    val headHeight = (bottom - top) * 0.3f
                    bestRect = RectF(left, top, right, top + headHeight)
                }
            }
        }
        return bestRect
    }
}
"""
os.makedirs('app/src/main/java/com/devil/aimbot', exist_ok=True)
with open('app/src/main/java/com/devil/aimbot/AimbotManager.kt', 'w') as f: f.write(aimbot_code)

swipe_code = """
package com.devil.aimbot

import android.view.MotionEvent
import rikka.shizuku.Shizuku

object TriggerSwipeHelper {
    fun performMagneticPull(targetX: Float, targetY: Float, screenWidth: Int, screenHeight: Int) {
        if (!Shizuku.pingBinder()) return
        val centerX = screenWidth / 2f; val centerY = screenHeight / 2f
        val moveX = (targetX - centerX) * 0.85f; val moveY = (targetY - centerY) * 0.85f
        try {
            val downTime = android.os.SystemClock.uptimeMillis()
            val eDown = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, centerX, centerY, 0)
            val eMove = MotionEvent.obtain(downTime, downTime + 15, MotionEvent.ACTION_MOVE, centerX + moveX, centerY + moveY, 0)
            val eUp = MotionEvent.obtain(downTime, downTime + 30, MotionEvent.ACTION_UP, centerX + moveX, centerY + moveY, 0)
            Shizuku.injectInputEvent(eDown); Shizuku.injectInputEvent(eMove); Shizuku.injectInputEvent(eUp)
        } catch (e: Exception) { e.printStackTrace() }
    }
}
"""
with open('app/src/main/java/com/devil/aimbot/TriggerSwipeHelper.kt', 'w') as f: f.write(swipe_code)

service_code = """
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
"""
with open('app/src/main/java/com/devil/aimbot/ScreenCaptureService.kt', 'w') as f: f.write(service_code)

manifest_path = 'app/src/main/AndroidManifest.xml'
with open(manifest_path, 'r', encoding='utf-8') as f: manifest = f.read()
if '<service android:name=".ScreenCaptureService"' not in manifest: manifest = manifest.replace('</application>', '    <service android:name=".ScreenCaptureService" android:foregroundServiceType="mediaProjection" android:exported="false" />\n</application>')
if 'FOREGROUND_SERVICE_MEDIA_PROJECTION' not in manifest: manifest = manifest.replace('<application', '<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />\n<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n<application')
with open(manifest_path, 'w', encoding='utf-8') as f: f.write(manifest)

gradle_path = 'app/build.gradle'
with open(gradle_path, 'r', encoding='utf-8') as f: gradle = f.read()
if 'tensorflow-lite' not in gradle: 
    with open(gradle_path, 'a', encoding='utf-8') as f: f.write('\ndependencies {\n    implementation "org.tensorflow:tensorflow-lite:2.14.0"\n    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"\n}\n')
