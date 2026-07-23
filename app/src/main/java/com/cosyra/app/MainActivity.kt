package com.cosyra.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val background = Color.rgb(5, 10, 18)
    private val panel = Color.rgb(10, 23, 40)
    private val blue = Color.rgb(22, 139, 255)
    private val cyan = Color.rgb(0, 194, 255)
    private val muted = Color.rgb(166, 184, 204)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContent())
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
                setPadding(0, dp(4), 0, dp(26))
            }, fullWidth())

            addView(TextView(context).apply {
                text = "Transformă telefonul care rulează jocul într-un Host și joacă de la distanță de pe al doilea telefon."
                textSize = 15f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(18), dp(12), dp(18))
                background = roundedPanel()
            }, fullWidth())

            addView(Button(context).apply {
                text = "PORNEȘTE CA HOST"
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                textSize = 16f
                setTextColor(Color.WHITE)
                background = roundedButton(blue)
                setOnClickListener {
                    Toast.makeText(
                        context,
                        "Urmează acordul Android pentru capturarea ecranului.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }, fullWidth(heightDp = 56))

            addView(TextView(context).apply {
                text = "CONECTARE CLIENT"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                setTextColor(cyan)
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, dp(6))
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
            addView(codeInput, fullWidth(heightDp = 56))

            addView(Button(context).apply {
                text = "CONECTEAZĂ-TE"
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                textSize = 16f
                setTextColor(Color.WHITE)
                background = roundedButton(Color.rgb(9, 75, 145))
                setOnClickListener {
                    val code = codeInput.text.toString().trim()
                    if (code.length != 6 || code.any { !it.isDigit() }) {
                        codeInput.error = "Introdu exact 6 cifre"
                    } else {
                        Toast.makeText(
                            context,
                            "Se pregătește conexiunea securizată pentru sesiunea $code.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }, fullWidth(heightDp = 56))

            addView(TextView(context).apply {
                text = "Fundație 0.1.1 • conexiune prin internet în dezvoltare"
                textSize = 12f
                setTextColor(Color.rgb(92, 112, 135))
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, 0)
            }, fullWidth())
        }
    }

    private fun roundedPanel() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18).toFloat()
        setColor(panel)
        setStroke(dp(1), Color.rgb(18, 70, 115))
    }

    private fun roundedButton(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(16).toFloat()
        setColor(color)
    }

    private fun roundedOutline() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(16).toFloat()
        setColor(Color.rgb(7, 17, 29))
        setStroke(dp(2), blue)
    }

    private fun fullWidth(heightDp: Int? = null): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            heightDp?.let(::dp) ?: ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
