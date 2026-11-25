
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
