package com.devil.aimbot

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            text = "Devil Aimbot Engine v1.0\nBuilding...\nGitHub Actions is Ready!"
            textSize = 20f
            setPadding(50, 50, 50, 50)
        }
        setContentView(textView)
    }
}
