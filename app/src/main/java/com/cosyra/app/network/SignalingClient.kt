package com.cosyra.app.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.min

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
        .retryOnConnectionFailure(true)
        .build()

    private val reconnectExecutor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "CosyraSignalingReconnect").apply { isDaemon = true }
    }

    @Volatile
    private var socket: WebSocket? = null
    @Volatile
    private var manuallyClosed = false
    @Volatile
    private var reconnectAttempt = 0
    private var reconnectTask: ScheduledFuture<*>? = null

    @Synchronized
    fun connect() {
        manuallyClosed = false
        openSocket()
    }

    @Synchronized
    private fun openSocket() {
        if (socket != null || manuallyClosed) return

        reconnectTask?.cancel(false)
        reconnectTask = null

        val request = Request.Builder().url(endpoint).build()
        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (socket !== webSocket || manuallyClosed) return
                reconnectAttempt = 0
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (socket !== webSocket || manuallyClosed) return
                runCatching { SignalingMessage.fromJson(text) }
                    .onSuccess(listener::onMessage)
                    .onFailure { listener.onFailure("Mesaj de semnalizare invalid") }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket !== webSocket) return
                socket = null
                if (!manuallyClosed) {
                    listener.onFailure(t.message ?: "Conexiunea de semnalizare a eșuat")
                    scheduleReconnect()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket !== webSocket) return
                socket = null
                if (manuallyClosed || code == 1000) {
                    listener.onClosed()
                } else {
                    listener.onFailure("Conexiunea s-a închis neașteptat ($code)")
                    scheduleReconnect()
                }
            }
        })
    }

    @Synchronized
    private fun scheduleReconnect() {
        if (manuallyClosed || reconnectTask?.isDone == false) return
        val delaySeconds = min(30L, 1L shl reconnectAttempt.coerceAtMost(5))
        reconnectAttempt++
        reconnectTask = reconnectExecutor.schedule({
            synchronized(this) {
                reconnectTask = null
                openSocket()
            }
        }, delaySeconds, TimeUnit.SECONDS)
    }

    fun send(message: SignalingMessage): Boolean = socket?.send(message.toJson()) == true

    @Synchronized
    fun close() {
        manuallyClosed = true
        reconnectTask?.cancel(false)
        reconnectTask = null
        socket?.close(1000, "Cosyra session closed")
        socket = null
        reconnectExecutor.shutdownNow()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}
