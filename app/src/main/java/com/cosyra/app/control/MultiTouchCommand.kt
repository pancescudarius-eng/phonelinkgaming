package com.cosyra.app.control

import org.json.JSONArray
import org.json.JSONObject

data class TouchPointer(
    val id: Int,
    val x: Float,
    val y: Float
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("x", x.coerceIn(0f, 1f))
        .put("y", y.coerceIn(0f, 1f))
}

data class MultiTouchCommand(
    val action: String,
    val actionPointerId: Int,
    val eventTimeMs: Long,
    val pointers: List<TouchPointer>
) {
    init {
        require(pointers.size <= MAX_POINTERS) { "Multitouch supports at most $MAX_POINTERS pointers" }
    }

    fun toJson(): String = JSONObject()
        .put("kind", "multitouch")
        .put("action", action)
        .put("actionPointerId", actionPointerId)
        .put("eventTimeMs", eventTimeMs.coerceAtLeast(0L))
        .put("pointers", JSONArray().apply {
            pointers.forEach { put(it.toJson()) }
        })
        .toString()

    companion object {
        const val MAX_POINTERS = 10

        fun fromJson(raw: String): MultiTouchCommand? = runCatching {
            val json = JSONObject(raw)
            if (json.optString("kind") != "multitouch") return null
            val pointerArray = json.getJSONArray("pointers")
            if (pointerArray.length() > MAX_POINTERS) return null
            val pointers = buildList(pointerArray.length()) {
                repeat(pointerArray.length()) { index ->
                    val pointer = pointerArray.getJSONObject(index)
                    add(
                        TouchPointer(
                            id = pointer.getInt("id"),
                            x = pointer.getDouble("x").toFloat().coerceIn(0f, 1f),
                            y = pointer.getDouble("y").toFloat().coerceIn(0f, 1f)
                        )
                    )
                }
            }
            if (pointers.map(TouchPointer::id).distinct().size != pointers.size) return null
            MultiTouchCommand(
                action = json.getString("action"),
                actionPointerId = json.getInt("actionPointerId"),
                eventTimeMs = json.optLong("eventTimeMs", 0L).coerceAtLeast(0L),
                pointers = pointers
            )
        }.getOrNull()
    }
}
