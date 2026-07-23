package com.cosyra.app.control

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

object GamepadInputMapper {
    private const val DEAD_ZONE = 0.12f

    fun isControllerEvent(event: MotionEvent): Boolean {
        val source = event.source
        return source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
    }

    fun axes(event: MotionEvent): List<GamepadCommand> {
        if (!isControllerEvent(event)) return emptyList()
        val axes = intArrayOf(
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_HAT_Y
        )
        return axes.map { axis ->
            val raw = event.getAxisValue(axis)
            val value = if (kotlin.math.abs(raw) < DEAD_ZONE) 0f else raw.coerceIn(-1f, 1f)
            GamepadCommand("axis", axis, value)
        }
    }

    fun button(event: KeyEvent): GamepadCommand? {
        val source = event.source
        val isGamepad = source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!isGamepad || event.repeatCount > 0) return null
        val value = when (event.action) {
            KeyEvent.ACTION_DOWN -> 1f
            KeyEvent.ACTION_UP -> 0f
            else -> return null
        }
        return GamepadCommand("button", event.keyCode, value)
    }
}
