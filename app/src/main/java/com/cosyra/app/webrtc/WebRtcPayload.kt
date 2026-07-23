package com.cosyra.app.webrtc

import org.json.JSONObject

data class SessionDescriptionPayload(
    val type: String,
    val sdp: String
) {
    fun toJson(): String = JSONObject()
        .put("type", type)
        .put("sdp", sdp)
        .toString()

    companion object {
        fun fromJson(raw: String): SessionDescriptionPayload {
            val json = JSONObject(raw)
            return SessionDescriptionPayload(
                type = json.getString("type"),
                sdp = json.getString("sdp")
            )
        }
    }
}

data class IceCandidatePayload(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val candidate: String
) {
    fun toJson(): String = JSONObject()
        .put("sdpMid", sdpMid)
        .put("sdpMLineIndex", sdpMLineIndex)
        .put("candidate", candidate)
        .toString()

    companion object {
        fun fromJson(raw: String): IceCandidatePayload {
            val json = JSONObject(raw)
            return IceCandidatePayload(
                sdpMid = json.optString("sdpMid").takeIf(String::isNotBlank),
                sdpMLineIndex = json.getInt("sdpMLineIndex"),
                candidate = json.getString("candidate")
            )
        }
    }
}
