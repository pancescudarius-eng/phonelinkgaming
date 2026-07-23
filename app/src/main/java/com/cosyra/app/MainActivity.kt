package com.cosyra.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContent())
    }

    private fun createContent(): LinearLayout {
        val padding = (24 * resources.displayMetrics.density).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(16, 19, 26))

            addView(TextView(context).apply {
                text = "COSYRA"
                textSize = 34f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, fullWidth())

            addView(TextView(context).apply {
                text = "Remote gaming între două telefoane Android"
                textSize = 16f
                setTextColor(Color.rgb(190, 196, 210))
                gravity = Gravity.CENTER
                setPadding(0, padding / 2, 0, padding)
            }, fullWidth())

            addView(Button(context).apply {
                text = "Găzduiește jocul"
                isAllCaps = false
                setOnClickListener {
                    Toast.makeText(
                        context,
                        "Modul Host va porni capturarea securizată a ecranului.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }, fullWidth())

            addView(TextView(context).apply {
                text = "sau conectează-te cu un cod"
                textSize = 14f
                setTextColor(Color.rgb(190, 196, 210))
                gravity = Gravity.CENTER
                setPadding(0, padding, 0, padding / 3)
            }, fullWidth())

            val codeInput = EditText(context).apply {
                hint = "Cod de 6 cifre"
                textSize = 18f
                gravity = Gravity.CENTER
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setTextColor(Color.WHITE)
                setHintTextColor(Color.rgb(130, 138, 155))
                setSingleLine(true)
            }
            addView(codeInput, fullWidth())

            addView(Button(context).apply {
                text = "Conectează-te"
                isAllCaps = false
                setOnClickListener {
                    val code = codeInput.text.toString().trim()
                    if (code.length != 6 || code.any { !it.isDigit() }) {
                        codeInput.error = "Introdu exact 6 cifre"
                    } else {
                        Toast.makeText(
                            context,
                            "Se pregătește conectarea la sesiunea $code.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }, fullWidth())

            addView(TextView(context).apply {
                text = "Versiune de fundație 0.1.0"
                textSize = 12f
                setTextColor(Color.rgb(120, 128, 145))
                gravity = Gravity.CENTER
                setPadding(0, padding * 2, 0, 0)
            }, fullWidth())
        }
    }

    private fun fullWidth(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (12 * resources.displayMetrics.density).toInt()
        }
}
