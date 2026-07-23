package com.cosyra.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cosyra.app.network.SessionCode
import com.cosyra.app.network.SignalingClient
import com.cosyra.app.network.SignalingMessage
import com.cosyra.app.webrtc.IceCandidatePayload
import com.cosyra.app.webrtc.SessionDescriptionPayload
import com.cosyra.app.webrtc.WebRtcSession
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class MainActivity : AppCompatActivity(), SignalingClient.Listener, WebRtcSession.Listener {

    private val background = Color.rgb(5, 10, 18)
    private val panel = Color.rgb(10, 23, 40)
    private val blue = Color.rgb(22, 139, 255)
    private val cyan = Color.rgb(0, 194, 255)
    private val muted = Color.rgb(166, 184, 204)

    private lateinit var hostStatus: TextView
    private lateinit var sessionCodeView: TextView
    private lateinit var clientStatus: TextView
    private lateinit var startHostButton: Button
    private lateinit var stopHostButton: Button
    private lateinit var remoteRenderer: SurfaceViewRenderer

    private var signalingClient: SignalingClient? = null
    private var webRtcSession: WebRtcSession? = null
    private var activeSessionCode: String? = null
    private var pendingRole: String? = null
    private var capturePermissionData: Intent? = null

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val resultData = result.data
            if (result.resultCode == Activity.RESULT_OK && resultData != null) {
                capturePermissionData = Intent(resultData)
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, ScreenCaptureService::class.java).apply {
                        action = ScreenCaptureService.ACTION_START
                    }
                )
                beginHostSession()
            } else {
                showHostStopped("Capturarea a fost anulată.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContent())
    }

    private fun requestScreenCapture() {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        hostStatus.text = "Aștept permisiunea Android pentru capturarea ecranului…"
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun beginHostSession() {
        closePeerConnection()
        val permission = capturePermissionData ?: run {
            showHostStopped("Permisiunea de capturare lipsește.")
            return
        }

        activeSessionCode = SessionCode.generate()
        pendingRole = "host"
        sessionCodeView.text = "COD SESIUNE: $activeSessionCode"
        hostStatus.text = "Pornesc fluxul video al ecranului…"

        val metrics = resources.displayMetrics
        ensurePeerConnection().startScreenShare(
            permissionData = permission,
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            fps = 30
        )

        connectSignaling()
        showHostRunning()
    }

    private fun joinSession(code: String) {
        closePeerConnection()
        pendingRole = "client"
        activeSessionCode = code
        remoteRenderer.visibility = View.VISIBLE
        clientStatus.text = "Se conectează la sesiunea $code…"
        ensurePeerConnection()
        connectSignaling()
    }

    private fun connectSignaling() {
        signalingClient?.close()
        signalingClient = SignalingClient(BuildConfig.SIGNALING_URL, this).also { it.connect() }
    }

    private fun ensurePeerConnection(): WebRtcSession =
        webRtcSession ?: WebRtcSession(applicationContext, this).also { session ->
            webRtcSession = session
            session.initializeRenderer(remoteRenderer)
        }

    private fun stopScreenCapture() {
        startService(Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        })
        signalingClient?.close()
        signalingClient = null
        closePeerConnection()
        capturePermissionData = null
        activeSessionCode = null
        pendingRole = null
        remoteRenderer.visibility = View.GONE
        sessionCodeView.text = "COD SESIUNE: —"
        showHostStopped("Host oprit. Ecranul nu mai este transmis.")
    }

    override fun onOpen() {
        val code = activeSessionCode ?: return
        val type = if (pendingRole == "host") "host_create" else "client_join"
        signalingClient?.send(SignalingMessage(type, code))
    }

    override fun onMessage(message: SignalingMessage) = runOnUiThread {
        when (message.type) {
            "host_ready" -> hostStatus.text =
                "HOST ACTIV • flux video pregătit • cod ${message.sessionCode}"

            "client_joined" -> clientStatus.text = "Sesiune găsită • pregătesc WebRTC…"

            "peer_joined" -> {
                val session = ensurePeerConnection()
                if (pendingRole == "host") {
                    hostStatus.text = "CLIENT CONECTAT • pornesc negocierea video…"
                    session.createOffer()
                } else {
                    clientStatus.text = "HOST CONECTAT • aștept fluxul video…"
                }
            }

            "webrtc_offer" -> {
                val payload = message.payload ?: return@runOnUiThread
                val session = ensurePeerConnection()
                session.setRemoteDescription(SessionDescriptionPayload.fromJson(payload)) {
                    session.createAnswer()
                }
            }

            "webrtc_answer" -> {
                val payload = message.payload ?: return@runOnUiThread
                ensurePeerConnection().setRemoteDescription(SessionDescriptionPayload.fromJson(payload))
            }

            "webrtc_ice" -> {
                val payload = message.payload ?: return@runOnUiThread
                ensurePeerConnection().addRemoteIceCandidate(IceCandidatePayload.fromJson(payload))
            }

            "peer_left" -> {
                closePeerConnection()
                remoteRenderer.visibility = View.GONE
                if (pendingRole == "host") {
                    hostStatus.text = "Client deconectat • repornește Hostul pentru o sesiune nouă"
                } else {
                    clientStatus.text = "Hostul s-a deconectat"
                }
            }

            "error" -> showNetworkError(message.payload ?: "Eroare necunoscută")
        }
    }

    override fun onFailure(message: String) = runOnUiThread { showNetworkError(message) }

    override fun onClosed() = runOnUiThread {
        if (pendingRole == "client") clientStatus.text = "Conexiunea a fost închisă"
    }

    override fun onLocalDescription(payload: SessionDescriptionPayload) {
        val code = activeSessionCode ?: return
        val type = if (payload.type.equals("offer", true)) "webrtc_offer" else "webrtc_answer"
        signalingClient?.send(SignalingMessage(type, code, payload.toJson()))
    }

    override fun onLocalIceCandidate(payload: IceCandidatePayload) {
        val code = activeSessionCode ?: return
        signalingClient?.send(SignalingMessage("webrtc_ice", code, payload.toJson()))
    }

    override fun onRemoteVideoTrack(track: VideoTrack) = runOnUiThread {
        remoteRenderer.visibility = View.VISIBLE
        clientStatus.text = "VIDEO ACTIV • imaginea Hostului este transmisă"
    }

    override fun onConnectionStateChanged(state: PeerConnection.PeerConnectionState) = runOnUiThread {
        val text = when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> "WEBRTC CONECTAT • flux securizat activ"
            PeerConnection.PeerConnectionState.CONNECTING -> "WebRTC se conectează…"
            PeerConnection.PeerConnectionState.DISCONNECTED -> "WebRTC deconectat temporar"
            PeerConnection.PeerConnectionState.FAILED -> "WebRTC a eșuat"
            PeerConnection.PeerConnectionState.CLOSED -> "WebRTC închis"
            else -> "WebRTC: ${state.name.lowercase()}"
        }
        if (pendingRole == "host") hostStatus.text = text else clientStatus.text = text
    }

    override fun onError(message: String) = runOnUiThread { showNetworkError(message) }

    private fun closePeerConnection() {
        webRtcSession?.close()
        webRtcSession = null
    }

    private fun showNetworkError(message: String) {
        val readable = when (message) {
            "session_not_found" -> "Codul nu există sau Hostul nu este online."
            "session_full" -> "Sesiunea are deja un Client conectat."
            "session_exists" -> "Codul a fost deja folosit. Repornește Hostul."
            "peer_not_connected" -> "Celălalt telefon nu este încă pregătit."
            else -> "Conexiune eșuată: $message"
        }
        if (pendingRole == "host") hostStatus.text = readable else clientStatus.text = readable
    }

    private fun createContent(): ScrollView = ScrollView(this).apply {
        isFillViewport = true
        setBackgroundColor(background)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(24))

            addView(title("COSYRA", 38f, Color.WHITE))
            addView(title("REMOTE GAMING • ANDROID", 13f, cyan).apply {
                setPadding(0, dp(4), 0, dp(20))
            })

            addView(TextView(context).apply {
                text = "Telefonul Host rulează jocul. Telefonul Client primește imaginea prin WebRTC."
                textSize = 15f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(18), dp(12), dp(18))
                background = roundedPanel()
            }, fullWidth())

            sessionCodeView = title("COD SESIUNE: —", 22f, cyan)
            addView(sessionCodeView, fullWidth())

            hostStatus = status("Host inactiv • ecranul nu este capturat")
            addView(hostStatus, fullWidth())

            startHostButton = actionButton("PORNEȘTE CA HOST", blue) { requestScreenCapture() }
            addView(startHostButton, fullWidth(56))

            stopHostButton = actionButton("OPREȘTE HOST", Color.rgb(120, 28, 45)) {
                stopScreenCapture()
            }.apply {
                isEnabled = false
                alpha = 0.5f
            }
            addView(stopHostButton, fullWidth(52))

            addView(title("CONECTARE CLIENT", 13f, cyan).apply {
                setPadding(0, dp(12), 0, dp(6))
            }, fullWidth())

            val codeInput = EditText(context).apply {
                hint = "Cod de 6 cifre"
                textSize = 20f
                gravity = Gravity.CENTER
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(6))
                setTextColor(Color.WHITE)
                setHintTextColor(Color.rgb(100, 125, 150))
                setSingleLine(true)
                background = roundedOutline()
                setPadding(dp(16), 0, dp(16), 0)
            }
            addView(codeInput, fullWidth(56))

            addView(actionButton("CONECTEAZĂ-TE", Color.rgb(9, 75, 145)) {
                val code = codeInput.text.toString().trim()
                if (!SessionCode.isValid(code)) codeInput.error = "Introdu exact 6 cifre"
                else joinSession(code)
            }, fullWidth(56))

            clientStatus = status("Client inactiv")
            addView(clientStatus, fullWidth())

            remoteRenderer = SurfaceViewRenderer(context).apply {
                visibility = View.GONE
                setBackgroundColor(Color.BLACK)
            }
            addView(remoteRenderer, fullWidth(420))

            addView(status("Versiune 0.6.0 • capturare și redare video WebRTC").apply {
                setTextColor(Color.rgb(92, 112, 135))
            }, fullWidth())
        }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun title(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
        setTextColor(color)
        gravity = Gravity.CENTER
    }

    private fun status(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(muted)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun actionButton(value: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        textSize = 16f
        setTextColor(Color.WHITE)
        background = roundedButton(color)
        setOnClickListener { action() }
    }

    private fun showHostRunning() {
        startHostButton.isEnabled = false
        startHostButton.alpha = 0.5f
        stopHostButton.isEnabled = true
        stopHostButton.alpha = 1f
        Toast.makeText(this, "Cosyra Host a pornit.", Toast.LENGTH_SHORT).show()
    }

    private fun showHostStopped(message: String) {
        hostStatus.text = message
        hostStatus.setTextColor(muted)
        startHostButton.isEnabled = true
        startHostButton.alpha = 1f
        stopHostButton.isEnabled = false
        stopHostButton.alpha = 0.5f
    }

    override fun onDestroy() {
        signalingClient?.close()
        signalingClient = null
        closePeerConnection()
        super.onDestroy()
    }

    private fun roundedPanel() = GradientDrawable().apply {
        cornerRadius = dp(18).toFloat()
        setColor(panel)
        setStroke(dp(1), Color.rgb(18, 70, 115))
    }

    private fun roundedButton(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(color)
    }

    private fun roundedOutline() = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(Color.rgb(7, 17, 29))
        setStroke(dp(2), blue)
    }

    private fun fullWidth(heightDp: Int? = null): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            heightDp?.let(::dp) ?: ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
