package com.cosyra.app.webrtc

import android.content.Context
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

class WebRtcSession(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onLocalDescription(payload: SessionDescriptionPayload)
        fun onLocalIceCandidate(payload: IceCandidatePayload)
        fun onRemoteVideoTrack(track: VideoTrack)
        fun onConnectionStateChanged(state: PeerConnection.PeerConnectionState)
        fun onError(message: String)
    }

    private val factory: PeerConnectionFactory
    private val peerConnection: PeerConnection
    private val controlChannel: DataChannel
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val configuration = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = requireNotNull(
            factory.createPeerConnection(configuration, observer())
        ) { "WebRTC PeerConnection could not be created" }

        controlChannel = peerConnection.createDataChannel("cosyra-control", DataChannel.Init())
    }

    fun addLocalAudioTrack() {
        if (audioTrack != null) return
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("cosyra-audio", audioSource).also { track ->
            peerConnection.addTrack(track, listOf("cosyra-stream"))
        }
    }

    fun addLocalVideoTrack(track: VideoTrack) {
        peerConnection.addTrack(track, listOf("cosyra-stream"))
    }

    fun createOffer() {
        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) = setLocalDescription(description)
            override fun onCreateFailure(error: String) {
                listener.onError("Nu s-a putut crea oferta WebRTC: $error")
            }
        }, MediaConstraints())
    }

    fun createAnswer() {
        peerConnection.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) = setLocalDescription(description)
            override fun onCreateFailure(error: String) {
                listener.onError("Nu s-a putut crea răspunsul WebRTC: $error")
            }
        }, MediaConstraints())
    }

    fun setRemoteDescription(payload: SessionDescriptionPayload, onApplied: (() -> Unit)? = null) {
        val type = when (payload.type.lowercase()) {
            "offer" -> SessionDescription.Type.OFFER
            "answer" -> SessionDescription.Type.ANSWER
            else -> {
                listener.onError("Tip SDP necunoscut: ${payload.type}")
                return
            }
        }

        peerConnection.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() { onApplied?.invoke() }
            override fun onSetFailure(error: String) {
                listener.onError("SDP-ul de la celălalt telefon nu a putut fi aplicat: $error")
            }
        }, SessionDescription(type, payload.sdp))
    }

    fun addRemoteIceCandidate(payload: IceCandidatePayload) {
        peerConnection.addIceCandidate(IceCandidate(payload.sdpMid, payload.sdpMLineIndex, payload.candidate))
    }

    fun close() {
        controlChannel.close()
        controlChannel.dispose()
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        peerConnection.close()
        peerConnection.dispose()
        factory.dispose()
    }

    private fun setLocalDescription(description: SessionDescription) {
        peerConnection.setLocalDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                listener.onLocalDescription(
                    SessionDescriptionPayload(description.type.canonicalForm(), description.description)
                )
            }

            override fun onSetFailure(error: String) {
                listener.onError("SDP-ul local nu a putut fi aplicat: $error")
            }
        }, description)
    }

    private fun observer() = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidate(candidate: IceCandidate) {
            listener.onLocalIceCandidate(
                IceCandidatePayload(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            )
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
            (receiver.track() as? VideoTrack)?.let(listener::onRemoteVideoTrack)
        }
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            listener.onConnectionStateChanged(newState)
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }
}
