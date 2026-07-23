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
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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

    private var signalingClient: SignalingClient? = null
    private var webRtcSession: WebRtcSession? = null
    private var activeSessionCode: String? = null
    private var pendingRole: String? = null

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val resultData = result.data
            if (result.resultCode == Activity.RESULT_OK && resultData != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
                }
                ContextCompat.startForegroundService(this, serviceIntent)
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
        activeSessionCode = SessionCode.generate()
        pendingRole = "host"
        sessionCodeView.text = "COD SESIUNE: ${activeSessionCode}"
        hostStatus.text = "Se conectează la serverul Cosyra…"
        connectSignaling()
        showHostRunning()
    }

    private fun joinSession(code: String) {
        closePeerConnection()
        activeSessionCode = code
        pendingRole = "client"
        clientStatus.text = "Se conectează la sesiunea $code…"
        connectSignaling()
    }

    private fun connectSignaling() {
        signalingClient?.close()
        signalingClient = SignalingClient(BuildConfig.SIGNALING_URL, this).also { it.connect() }
    }

    private fun ensurePeerConnection(): WebRtcSession =
        webRtcSession ?: WebRtcSession(applicationContext, this).also { webRtcSession = it }

    private fun stopScreenCapture() {
        startService(Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        })
        signalingClient?.close()
        signalingClient = null
        closePeerConnection()
        activeSessionCode = null
        pendingRole = null
        sessionCodeView.text = "COD SESIUNE: —"
        showHostStopped("Host oprit. Ecranul nu mai este capturat.")
    }

    override fun onOpen() {
        val code = activeSessionCode ?: return
        val type = if (pendingRole == "host") "host_create" else "client_join"
        signalingClient?.send(SignalingMessage(type, code))
    }

    override fun onMessage(message: SignalingMessage) = runOnUiThread {
        when (message.type) {
            "host_ready" -> hostStatus.text = "HOST ACTIV • aștept clientul • cod ${message.sessionCode}"
            "client_joined" -> {
                ensurePeerConnection()
                clientStatus.text = "Sesiune găsită • pregătesc WebRTC…"
            }
            "peer_joined" -> {
                ensurePeerConnection()
                if (pendingRole == "host") {
                    hostStatus.text = "CLIENT CONECTAT • negociez fluxul WebRTC…"
                    webRtcSession?.createOffer()
                } else {
                    clientStatus.text = "HOST CONECTAT • aștept oferta video…"
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
                hostStatus.text = "Client deconectat • sesiunea rămâne deschisă"
                clientStatus.text = "Hostul s-a deconectat"
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
        clientStatus.text = "VIDEO PRIMIT • rendererul este următoarea etapă"
    }

    override fun onConnectionStateChanged(state: PeerConnection.PeerConnectionState) = runOnUiThread {
        val text = when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> "WEBRTC CONECTAT • canal securizat activ"
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
            "session_exists" -> "Codul a fost deja folosit. Încearcă din nou."
            "peer_not_connected" -> "Celălalt telefon nu este încă pregătit."
            else -> "Conexiune eșuată: $message"
        }
        if (pendingRole == "host") hostStatus.text = readable else clientStatus.text = readable
    }

    private fun createContent(): LinearLayout {
        val padding = dp(24)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(background)

            addView(TextView(context).apply {
                text = "COSYRA"
                textSize = 38f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, fullWidth())

            addView(TextView(context).apply {
                text = "REMOTE GAMING • ANDROID"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                setTextColor(cyan)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(20))
            }, fullWidth())

            addView(TextView(context).apply {
                text = "Telefonul Host rulează jocul. Telefonul Client va primi imaginea și va trimite comenzile."
                textSize = 15f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(18), dp(12), dp(18))
                background = roundedPanel()
            }, fullWidth())

            sessionCodeView = TextView(context).apply {
                text = "COD SESIUNE: —"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                setTextColor(cyan)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(4))
            }
            addView(sessionCodeView, fullWidth())

            hostStatus = TextView(context).apply {
                text = "Host inactiv • ecranul nu este capturat"
                textSize = 13f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            addView(hostStatus, fullWidth())

            startHostButton = Button(context).apply {
                text = "PORNEȘTE CA HOST"
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                textSize = 16f
                setTextColor(Color.WHITE)
                background = roundedButton(blue)
                setOnClickListener { requestScreenCapture() }
            }
            addView(startHostButton, fullWidth(56))

            stopHostButton = Button(context).apply {
                text = "OPREȘTE HOST"
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                textSize = 15f
                setTextColor(Color.WHITE)
                background = roundedButton(Color.rgb(120, 28, 45))
                isEnabled = false
                alpha = 0.5f
                setOnClickListener { stopScreenCapture() }
            }
            addView(stopHostButton, fullWidth(52))

            addView(TextView(context).apply {
                text = "CONECTARE CLIENT"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                setTextColor(cyan)
                gravity = Gravity.CENTER
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

            addView(Button(context).apply {
                text = "CONECTEAZĂ-TE"
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                textSize = 16f
                setTextColor(Color.WHITE)
                background = roundedButton(Color.rgb(9, 75, 145))
                setOnClickListener {
                    val code = codeInput.text.toString().trim()
                    if (!SessionCode.isValid(code)) codeInput.error = "Introdu exact 6 cifre"
                    else joinSession(code)
                }
            }, fullWidth(56))

            clientStatus = TextView(context).apply {
                text = "Client inactiv"
                textSize = 13f
                setTextColor(muted)
                gravity = Gravity.CENTER
            }
            addView(clientStatus, fullWidth())

            addView(TextView(context).apply {
                text = "Versiune 0.5.0 • negociere WebRTC offer/answer/ICE"
                textSize = 12f
                setTextColor(Color.rgb(92, 112, 135))
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
            }, fullWidth())
        }
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
