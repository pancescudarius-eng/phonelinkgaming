package com.cosyra.app.network

import org.json.JSONObject

data class SignalingMessage(
    val type: String,
    val sessionCode: String,
    val payload: String? = null
) {
    fun toJson(): String = JSONObject()
        .put("type", type)
        .put("sessionCode", sessionCode)
        .apply { payload?.let { put("payload", it) } }
        .toString()

    companion object {
        fun fromJson(raw: String): SignalingMessage {
            val json = JSONObject(raw)
            return SignalingMessage(
                type = json.getString("type"),
                sessionCode = json.getString("sessionCode"),
                payload = json.optString("payload").takeIf(String::isNotBlank)
            )
        }
    }
}
