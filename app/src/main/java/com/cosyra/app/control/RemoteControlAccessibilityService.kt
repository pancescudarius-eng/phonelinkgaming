package com.cosyra.app.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import java.util.ArrayDeque

class RemoteControlAccessibilityService : AccessibilityService() {

    private var activePath: Path? = null
    private var gestureStartedAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingFrames = ArrayDeque<MultiTouchCommand>()
    private val activeStrokes = mutableMapOf<Int, GestureDescription.StrokeDescription>()
    private val lastPositions = mutableMapOf<Int, Pair<Float, Float>>()
    private var lastFrameTimeMs = 0L
    private var frameInFlight = false

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        activePath = null
        pendingFrames.clear()
        activeStrokes.clear()
        lastPositions.clear()
        frameInFlight = false
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

    fun execute(command: MultiTouchCommand): Boolean {
        if (command.pointers.size > MultiTouchCommand.MAX_POINTERS) return false
        mainHandler.post {
            pendingFrames.addLast(command)
            processNextFrame()
        }
        return true
    }

    private fun processNextFrame() {
        if (frameInFlight) return
        val command = pendingFrames.removeFirstOrNull() ?: return
        if (command.action.equals("cancel", ignoreCase = true)) {
            resetMultitouchState()
            processNextFrame()
            return
        }

        val metrics = resources.displayMetrics
        val durationMs = if (lastFrameTimeMs == 0L) {
            1L
        } else {
            (command.eventTimeMs - lastFrameTimeMs).coerceIn(1L, 50L)
        }
        val nextStrokes = mutableMapOf<Int, GestureDescription.StrokeDescription>()
        val builder = GestureDescription.Builder()

        command.pointers.take(MultiTouchCommand.MAX_POINTERS).forEach { pointer ->
            val x = pointer.x * metrics.widthPixels
            val y = pointer.y * metrics.heightPixels
            val previousPosition = lastPositions[pointer.id] ?: (x to y)
            val path = Path().apply {
                moveTo(previousPosition.first, previousPosition.second)
                lineTo(x, y)
            }
            val endsHere = command.action.equals("up", ignoreCase = true) &&
                command.actionPointerId == pointer.id
            val stroke = activeStrokes[pointer.id]?.continueStroke(
                path,
                0L,
                durationMs,
                !endsHere
            ) ?: GestureDescription.StrokeDescription(path, 0L, durationMs, !endsHere)
            builder.addStroke(stroke)
            if (!endsHere) nextStrokes[pointer.id] = stroke
        }

        if (command.pointers.isEmpty()) {
            resetMultitouchState()
            processNextFrame()
            return
        }

        frameInFlight = true
        val accepted = dispatchGesture(
            builder.build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    activeStrokes.clear()
                    activeStrokes.putAll(nextStrokes)
                    lastPositions.clear()
                    command.pointers.forEach { pointer ->
                        if (nextStrokes.containsKey(pointer.id)) {
                            lastPositions[pointer.id] =
                                pointer.x * metrics.widthPixels to pointer.y * metrics.heightPixels
                        }
                    }
                    lastFrameTimeMs = command.eventTimeMs
                    frameInFlight = false
                    processNextFrame()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    resetMultitouchState()
                    frameInFlight = false
                    processNextFrame()
                }
            },
            mainHandler
        )
        if (!accepted) {
            resetMultitouchState()
            frameInFlight = false
            processNextFrame()
        }
    }

    private fun resetMultitouchState() {
        activeStrokes.clear()
        lastPositions.clear()
        lastFrameTimeMs = 0L
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
        fun dispatch(command: MultiTouchCommand): Boolean = instance?.execute(command) == true
        fun isEnabled(): Boolean = instance != null
    }
}
