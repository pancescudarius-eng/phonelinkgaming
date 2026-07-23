package com.cosyra.app.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class RemoteControlAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun execute(command: TouchCommand): Boolean {
        val metrics = resources.displayMetrics
        val x = command.x * metrics.widthPixels
        val y = command.y * metrics.heightPixels
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            command.durationMs.coerceAtLeast(1L)
        )
        return dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null,
            null
        )
    }

    companion object {
        @Volatile
        private var instance: RemoteControlAccessibilityService? = null

        fun dispatch(command: TouchCommand): Boolean = instance?.execute(command) == true
        fun isEnabled(): Boolean = instance != null
    }
}
