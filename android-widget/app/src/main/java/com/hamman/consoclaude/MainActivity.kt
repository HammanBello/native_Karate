package com.hamman.consoclaude

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.CookieManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var status: TextView
    private val store by lazy { UsageStore(this) }
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(Color.parseColor("#1E1E1C"))
        }

        fun text(s: String, size: Float = 14f, color: String = "#DDFFFFFF") = TextView(this).apply {
            text = s
            textSize = size
            setTextColor(Color.parseColor(color))
            setPadding(0, pad / 2, 0, pad / 2)
        }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

        layout.addView(text("Conso Claude", 22f, "#D97757"))
        status = text("", 15f)
        layout.addView(status)

        layout.addView(button("Actualiser maintenant") { refresh() })

        layout.addView(text("Connexion", 18f, "#D97757"))
        layout.addView(text(
            "Option 1 : se connecter à claude.ai ici (par e-mail ; la connexion Google " +
                "est souvent bloquée dans un navigateur intégré)."
        ))
        layout.addView(button("Se connecter à claude.ai") {
            startActivityForResult(Intent(this, LoginActivity::class.java), 1)
        })

        layout.addView(text(
            "Option 2 : sur PC, ouvrir claude.ai → F12 → Application → Cookies → " +
                "https://claude.ai → copier la valeur de « sessionKey » (sk-ant-sid…) et la coller ici."
        ))
        val keyInput = EditText(this).apply {
            hint = "sk-ant-sid…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        layout.addView(keyInput)
        layout.addView(button("Enregistrer la sessionKey") {
            val key = keyInput.text.toString().trim().removePrefix("sessionKey=")
            if (key.isEmpty()) return@button
            store.cookie = "sessionKey=$key"
            keyInput.setText("")
            refresh()
        })

        layout.addView(button("Se déconnecter") {
            store.clear()
            CookieManager.getInstance().removeAllCookies(null)
            UsageWidget.updateAll(this)
            updateStatus()
        })

        layout.addView(text(
            "Ensuite : appui long sur l'écran d'accueil → Widgets → Conso Claude. " +
                "Le widget se met à jour toutes les 15 min environ, ou quand vous le touchez.",
            13f, "#88FFFFFF"
        ))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1E1E1C"))
            addView(layout, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })

        RefreshWorker.schedule(this)
        updateStatus()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) refresh()
    }

    private fun refresh() {
        status.text = "Actualisation…"
        scope.launch {
            RefreshWorker.refresh(this@MainActivity)
            updateStatus()
            store.error?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
        }
    }

    private fun updateStatus() {
        val usage = store.usage
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
        status.text = buildString {
            append(if (store.cookie == null) "Non connecté\n" else "Connecté\n")
            if (usage != null) {
                val five = usage.fiveHour
                append("Session 5 h : ")
                append(if (five == null) "aucune session en cours" else
                    "${(100 - five.utilization).toInt()} % restant, reset ${fmt.format(Date(five.resetsAt))}")
                append("\n")
                usage.sevenDay?.let {
                    append("Semaine : ${(100 - it.utilization).toInt()} % restant, reset ${fmt.format(Date(it.resetsAt))}\n")
                }
                append("Mis à jour : ${fmt.format(Date(store.updatedAt))}\n")
            }
            store.error?.let { append("⚠ $it") }
        }
    }
}
