package com.cosyra.app.control

import org.json.JSONObject

data class GamepadCommand(
    val kind: String,
    val code: Int,
    val value: Float
) {
    fun toJson(): String = JSONObject()
        .put("type", "gamepad")
        .put("kind", kind)
        .put("code", code)
        .put("value", value.coerceIn(-1f, 1f))
        .toString()

    companion object {
        fun fromJson(json: String): GamepadCommand? = runCatching {
            val objectValue = JSONObject(json)
            if (objectValue.optString("type") != "gamepad") return null
            GamepadCommand(
                kind = objectValue.getString("kind"),
                code = objectValue.getInt("code"),
                value = objectValue.getDouble("value").toFloat().coerceIn(-1f, 1f)
            )
        }.getOrNull()
    }
}
