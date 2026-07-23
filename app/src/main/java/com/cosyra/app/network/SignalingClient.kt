package com.cosyra.app.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class SignalingClient(
    private val endpoint: String,
    private val listener: Listener
) {
    interface Listener {
        fun onOpen()
        fun onMessage(message: SignalingMessage)
        fun onFailure(message: String)
        fun onClosed()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null

    fun connect() {
        if (socket != null) return

        val request = Request.Builder().url(endpoint).build()
        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { SignalingMessage.fromJson(text) }
                    .onSuccess(listener::onMessage)
                    .onFailure { listener.onFailure("Mesaj de semnalizare invalid") }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                listener.onFailure(t.message ?: "Conexiunea de semnalizare a eșuat")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                listener.onClosed()
            }
        })
    }

    fun send(message: SignalingMessage): Boolean = socket?.send(message.toJson()) == true

    fun close() {
        socket?.close(1000, "Cosyra session closed")
        socket = null
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}
