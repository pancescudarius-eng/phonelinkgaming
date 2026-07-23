package com.cosyra.app.control

import org.json.JSONObject

data class TouchCommand(
    val action: String,
    val x: Float,
    val y: Float,
    val durationMs: Long = 1L
) {
    fun toJson(): String = JSONObject()
        .put("kind", "touch")
        .put("action", action)
        .put("x", x.coerceIn(0f, 1f))
        .put("y", y.coerceIn(0f, 1f))
        .put("durationMs", durationMs.coerceIn(1L, 10_000L))
        .toString()

    companion object {
        fun fromJson(raw: String): TouchCommand? = runCatching {
            val json = JSONObject(raw)
            if (json.optString("kind") != "touch") return null
            TouchCommand(
                action = json.getString("action"),
                x = json.getDouble("x").toFloat().coerceIn(0f, 1f),
                y = json.getDouble("y").toFloat().coerceIn(0f, 1f),
                durationMs = json.optLong("durationMs", 1L).coerceIn(1L, 10_000L)
            )
        }.getOrNull()
    }
}
