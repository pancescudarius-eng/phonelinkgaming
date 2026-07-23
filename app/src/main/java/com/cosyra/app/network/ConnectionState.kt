package com.cosyra.app.network

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data class WaitingForPeer(val sessionCode: String) : ConnectionState
    data class Connected(val sessionCode: String) : ConnectionState
    data class Failed(val message: String) : ConnectionState
    data object Closed : ConnectionState
}
