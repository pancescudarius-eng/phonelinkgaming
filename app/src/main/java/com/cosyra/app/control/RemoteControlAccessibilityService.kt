package com.cosyra.app.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class RemoteControlAccessibilityService : AccessibilityService() {

    private var activePath: Path? = null
    private var gestureStartedAt = 0L

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        activePath = null
        super.onDestroy()
    }

    @Synchronized
    fun execute(command: TouchCommand): Boolean {
        val metrics = resources.displayMetrics
        val x = command.x * metrics.widthPixels
        val y = command.y * metrics.heightPixels

        return when (command.action.lowercase()) {
            "down" -> {
                activePath = Path().apply { moveTo(x, y) }
                gestureStartedAt = SystemClock.uptimeMillis()
                true
            }
            "move" -> {
                activePath?.lineTo(x, y)
                activePath != null
            }
            "up" -> {
                val path = activePath ?: Path().apply { moveTo(x, y) }
                path.lineTo(x, y)
                val elapsed = (SystemClock.uptimeMillis() - gestureStartedAt)
                    .coerceIn(1L, 10_000L)
                activePath = null
                dispatch(path, elapsed)
            }
            "tap" -> {
                val path = Path().apply {
                    moveTo(x, y)
                    lineTo(x, y)
                }
                dispatch(path, command.durationMs.coerceIn(1L, 500L))
            }
            else -> false
        }
    }

    private fun dispatch(path: Path, durationMs: Long): Boolean {
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
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
