
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
