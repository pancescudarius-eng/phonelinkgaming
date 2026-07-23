package com.cosyra.app.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import com.cosyra.app.control.RemoteControlAccessibilityService
import com.cosyra.app.control.TouchCommand
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class WebRtcSession(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onLocalDescription(payload: SessionDescriptionPayload)
        fun onLocalIceCandidate(payload: IceCandidatePayload)
        fun onRemoteVideoTrack(track: VideoTrack)
        fun onConnectionStateChanged(state: PeerConnection.PeerConnectionState)
        fun onControlChannelStateChanged(state: DataChannel.State)
        fun onRemoteControlExecuted(success: Boolean)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val eglBase = EglBase.create()
    private val factory: PeerConnectionFactory
    private val peerConnection: PeerConnection
    private var controlChannel: DataChannel? = null

    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var screenCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var renderer: SurfaceViewRenderer? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                org.webrtc.DefaultVideoEncoderFactory(
                    eglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .setVideoDecoderFactory(
                org.webrtc.DefaultVideoDecoderFactory(eglBase.eglBaseContext)
            )
            .createPeerConnectionFactory()

        val configuration = PeerConnection.RTCConfiguration(IceServerConfig.create()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        }

        peerConnection = requireNotNull(
            factory.createPeerConnection(configuration, observer())
        ) { "WebRTC PeerConnection could not be created" }

        attachControlChannel(
            peerConnection.createDataChannel(
                "cosyra-control",
                DataChannel.Init().apply {
                    ordered = true
                }
            )
        )
    }

    fun initializeRenderer(view: SurfaceViewRenderer) {
        if (renderer === view) return
        renderer?.release()
        renderer = view.apply {
            init(eglBase.eglBaseContext, null)
            setEnableHardwareScaler(true)
            setMirror(false)
        }
        remoteVideoTrack?.addSink(view)
    }

    fun startScreenShare(permissionData: Intent, width: Int, height: Int, fps: Int = 30) {
        if (screenCapturer != null) return

        val capturer = ScreenCapturerAndroid(
            Intent(permissionData),
            object : MediaProjection.Callback() {
                override fun onStop() {
                    listener.onError("Android a oprit permisiunea de capturare a ecranului.")
                    stopScreenShare()
                }
            }
        )

        val source = factory.createVideoSource(true)
        val helper = SurfaceTextureHelper.create("CosyraScreenCapture", eglBase.eglBaseContext)

        runCatching {
            capturer.initialize(helper, appContext, source.capturerObserver)
            capturer.startCapture(width.coerceAtLeast(1), height.coerceAtLeast(1), fps.coerceIn(15, 60))
            val track = factory.createVideoTrack("cosyra-screen", source)
            peerConnection.addTrack(track, listOf("cosyra-stream"))

            screenCapturer = capturer
            surfaceTextureHelper = helper
            videoSource = source
            localVideoTrack = track
        }.onFailure { error ->
            runCatching { capturer.dispose() }
            helper.dispose()
            source.dispose()
            listener.onError("Capturarea WebRTC nu a pornit: ${error.message ?: "eroare necunoscută"}")
        }
    }

    fun stopScreenShare() {
        val capturer = screenCapturer
        screenCapturer = null
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }

        localVideoTrack?.dispose()
        localVideoTrack = null
        videoSource?.dispose()
        videoSource = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
    }

    fun sendTouch(command: TouchCommand): Boolean {
        val channel = controlChannel ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        val bytes = command.toJson().toByteArray(StandardCharsets.UTF_8)
        return channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
    }

    fun addLocalAudioTrack() {
        if (audioTrack != null) return
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("cosyra-audio", audioSource).also { track ->
            peerConnection.addTrack(track, listOf("cosyra-stream"))
        }
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
            override fun onSetSuccess() {
                onApplied?.invoke()
            }

            override fun onSetFailure(error: String) {
                listener.onError("SDP-ul de la celălalt telefon nu a putut fi aplicat: $error")
            }
        }, SessionDescription(type, payload.sdp))
    }

    fun addRemoteIceCandidate(payload: IceCandidatePayload) {
        peerConnection.addIceCandidate(
            IceCandidate(payload.sdpMid, payload.sdpMLineIndex, payload.candidate)
        )
    }

    fun close() {
        stopScreenShare()
        remoteVideoTrack?.let { track -> renderer?.let(track::removeSink) }
        remoteVideoTrack = null
        renderer?.release()
        renderer = null
        controlChannel?.close()
        controlChannel?.dispose()
        controlChannel = null
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        peerConnection.close()
        peerConnection.dispose()
        factory.dispose()
        eglBase.release()
    }

    private fun attachControlChannel(channel: DataChannel) {
        if (controlChannel !== channel) {
            controlChannel?.close()
            controlChannel?.dispose()
            controlChannel = channel
        }
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                listener.onControlChannelStateChanged(channel.state())
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) return
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                val command = TouchCommand.fromJson(String(data, StandardCharsets.UTF_8)) ?: return
                val success = RemoteControlAccessibilityService.dispatch(command)
                listener.onRemoteControlExecuted(success)
            }
        })
        listener.onControlChannelStateChanged(channel.state())
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

        override fun onDataChannel(channel: DataChannel) {
            attachControlChannel(channel)
        }

        override fun onRenegotiationNeeded() = Unit

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
            (receiver.track() as? VideoTrack)?.let { track ->
                remoteVideoTrack = track
                renderer?.let(track::addSink)
                listener.onRemoteVideoTrack(track)
            }
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
