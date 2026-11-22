package com.devil.aimbot

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            textSize = 18f
            setPadding(50, 50, 50, 50)
        }
        setContentView(textView)

        try {
            val interpreter = Interpreter(loadModelFile("yolov8n.tflite"))
            textView.text = "✅ تم تحميل النموذج بنجاح!\n\nمحرك TensorFlow Lite جاهز.\nأبعاد الإدخال: ${interpreter.getInputTensor(0).shape().contentToString()}"
            interpreter.close()
        } catch (e: Exception) {
            textView.text = "❌ خطأ في تحميل النموذج:\n${e.message}"
        }
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }
}
