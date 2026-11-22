package com.devil.aimbot

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import org.tensorflow.lite.Interpreter
import rikka.shizuku.Shizuku
import java.io.FileInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

class AimService : Service() {
    private lateinit var projection: MediaProjection
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var interpreter: Interpreter
    private var screenWidth = 0
    private var screenHeight = 0
    private var shProcess: Process? = null
    private var shOutput: OutputStream? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var isProcessing = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        
        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = pm.getMediaProjection(code, data)!!
        
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = wm.currentWindowMetrics.bounds
        screenWidth = metrics.width()
        screenHeight = metrics.height()

        // 1. تجهيز Shizuku Shell لحقن اللمس
        try {
            shProcess = Shizuku.newProcess(arrayOf("sh"), null, null)
            shOutput = shProcess?.outputStream
        } catch (e: Exception) { e.printStackTrace() }

        // 2. تحميل النموذج
        interpreter = Interpreter(loadModelFile("yolov8n.tflite"))

        // 3. بدء التقاط الشاشة
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection.createVirtualDisplay("Aim", screenWidth, screenHeight, resources.displayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null)

        imageReader?.setOnImageAvailableListener({ reader ->
            if (isProcessing) { reader.acquireLatestImage()?.close(); return@setOnImageAvailableListener }
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            isProcessing = true
            
            executor.execute {
                try {
                    processImage(image)
                } catch (e: Exception) { e.printStackTrace() } 
                finally {
                    image.close()
                    isProcessing = false
                }
            }
        }, Handler(Looper.getMainLooper()))

        return START_STICKY
    }

    private fun processImage(image: android.media.Image) {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        
        val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        // معالجة الصورة للنموذج (320x320)
        val resized = Bitmap.createScaledBitmap(bitmap, 320, 320, true)
        val input = ByteBuffer.allocateDirect(1 * 320 * 320 * 3 * 4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(320 * 320)
        resized.getPixels(pixels, 0, 320, 0, 0, 320, 320)
        for (p in pixels) {
            input.putFloat(((p shr 16 and 0xFF) / 255.0f))
            input.putFloat(((p shr 8 and 0xFF) / 255.0f))
            input.putFloat(((p and 0xFF) / 255.0f))
        }

        // تشغيل النموذج
        val output = Array(1) { Array(84) { FloatArray(2100) } }
        interpreter.run(input, output)

        // استخراج أفضل رأس
        var bestConf = 0f
        var bestX = 0f
        var bestY = 0f

        for (i in 0 until 2100) {
            val conf = output[0][4][i] // Class 0 (Person)
            if (conf > 0.5f && conf > bestConf) {
                val cx = output[0][0][i]
                val cy = output[0][1][i]
                val w = output[0][2][i]
                val h = output[0][3][i]
                
                // معادلة استخراج الرأس (أعلى المربع)
                val headX = cx
                val headY = cy - (h / 2f) + (h * 0.15f) // 15% أسفل أعلى نقطة
                
                bestConf = conf
                bestX = headX
                bestY = headY
            }
        }

        // إذا وجدنا رأس، قم بحقن اللمس
        if (bestConf > 0.5f) {
            val screenX = (bestX / 320f * screenWidth).toInt()
            val screenY = (bestY / 320f * screenHeight).toInt()
            
            // إرسال أمر السحب (Swipe) عبر Shizuku
            val centerX = screenWidth / 2
            val centerY = screenHeight / 2
            shOutput?.write("input swipe $centerX $centerY $screenX $screenY 10\n".toByteArray())
            shOutput?.flush()
        }
    }

    private fun loadModelFile(path: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(path)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    private fun createNotification(): Notification {
        val channelId = "aim_channel"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Aimbot", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Devil Aimbot Active")
            .setContentText("Scanning for heads...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        imageReader?.close()
        projection.stop()
        shProcess?.destroy()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
