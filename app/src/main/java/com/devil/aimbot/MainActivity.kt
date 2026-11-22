package com.devil.aimbot

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private val REQ_SCREEN = 1001
    private val REQ_SHIZUKU = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }
        val tv = TextView(this).apply { text = "Devil Aimbot Engine v1.0\nWaiting..." }
        layout.addView(tv)

        val btnShizuku = Button(this).apply { text = "1. Grant Shizuku" }
        btnShizuku.setOnClickListener {
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQ_SHIZUKU)
            } else {
                Toast.makeText(this, "Shizuku Granted!", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(btnShizuku)

        val btnStart = Button(this).apply { text = "2. Start Aimbot" }
        btnStart.setOnClickListener {
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Please grant Shizuku first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(pm.createScreenCaptureIntent(), REQ_SCREEN)
        }
        layout.addView(btnStart)
        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SCREEN && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, AimService::class.java).apply {
                putExtra("code", resultCode)
                putExtra("data", data)
            }
            startForegroundService(intent)
            Toast.makeText(this, "Aimbot Running in Background!", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
