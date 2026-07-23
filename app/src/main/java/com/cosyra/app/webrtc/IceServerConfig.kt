package com.cosyra.app.webrtc

import com.cosyra.app.BuildConfig
import org.webrtc.PeerConnection

object IceServerConfig {
    fun create(): List<PeerConnection.IceServer> = buildList {
        add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())

        val turnUrl = BuildConfig.TURN_URL.trim()
        if (turnUrl.isNotEmpty()) {
            add(
                PeerConnection.IceServer.builder(turnUrl)
                    .setUsername(BuildConfig.TURN_USERNAME)
                    .setPassword(BuildConfig.TURN_PASSWORD)
                    .createIceServer()
            )
        }
    }
}
