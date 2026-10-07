package com.hamman.consoclaude

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.OffsetDateTime
import kotlin.coroutines.resume

/** Une limite : pourcentage utilisé (0-100) et heure de reset (epoch ms, 0 si inconnue). */
data class Limit(val utilization: Double, val resetsAt: Long)

/** null = aucune fenêtre ouverte en ce moment. */
data class Usage(val fiveHour: Limit?, val sevenDay: Limit?)

class AuthException(message: String) : Exception(message)

/**
 * Lit la consommation via l'API de claude.ai (la même que la page
 * Paramètres → Utilisation), avec le cookie de session du compte.
 * API non documentée : elle peut changer.
 *
 * Les requêtes passent par une WebView (vrai moteur Chrome) : claude.ai
 * refuse (HTTP 403) les requêtes HTTP « brutes » d'une app.
 */
object UsageApi {
    private const val BASE = "https://claude.ai/api"

    suspend fun fetch(context: Context, cookie: String, knownOrgId: String?): Pair<String, Usage> {
        installCookie(cookie)
        val orgId = knownOrgId ?: findOrgId(context)
        val json = getJson(context, "$BASE/organizations/$orgId/usage") as? JSONObject
            ?: throw Exception("Réponse inattendue")
        val error = json.optJSONObject("error")
        if (error != null) {
            // Organisation changée : on la recherche à nouveau.
            if (knownOrgId != null && error.optString("type") == "not_found_error") {
                return fetch(context, cookie, null)
            }
            throw AuthException(error.optString("message", "Accès refusé"))
        }
        return orgId to Usage(parseLimit(json, "five_hour"), parseLimit(json, "seven_day"))
    }

    /** Clé collée à la main : on la place dans le navigateur intégré. */
    private fun installCookie(cookie: String) {
        val key = Regex("sessionKey=([^;\\s]+)").find(cookie)?.groupValues?.get(1) ?: return
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        manager.setCookie("https://claude.ai", "sessionKey=$key; Domain=.claude.ai; Path=/; Secure")
        manager.flush()
    }

    private suspend fun findOrgId(context: Context): String {
        val result = getJson(context, "$BASE/organizations")
        if (result is JSONObject) {
            val message = result.optJSONObject("error")?.optString("message")
            throw AuthException(message ?: "Session invalide")
        }
        val orgs = result as JSONArray
        if (orgs.length() == 0) throw AuthException("Aucune organisation sur ce compte")
        var fallback: String? = null
        for (i in 0 until orgs.length()) {
            val org = orgs.getJSONObject(i)
            val caps = org.optJSONArray("capabilities")?.toString() ?: ""
            if ("claude_max" in caps || "claude_pro" in caps) return org.getString("uuid")
            if (fallback == null && "chat" in caps) fallback = org.getString("uuid")
        }
        return fallback ?: orgs.getJSONObject(0).getString("uuid")
    }

    private fun parseLimit(json: JSONObject, key: String): Limit? {
        val obj = json.optJSONObject(key) ?: return null
        if (obj.isNull("utilization")) return null
        val resets = obj.optString("resets_at", "")
        val resetsAt = if (resets.isEmpty() || resets == "null") 0L
        else runCatching { OffsetDateTime.parse(resets).toInstant().toEpochMilli() }.getOrDefault(0L)
        return Limit(obj.getDouble("utilization"), resetsAt)
    }

    /**
     * Ouvre l'URL dans une WebView invisible et renvoie le JSON affiché.
     * Une éventuelle page de vérification anti-robots est franchie
     * automatiquement : on attend qu'une page contenant du JSON s'affiche.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun getJson(context: Context, url: String): Any = withContext(Dispatchers.Main) {
        var lastTitle = ""
        try {
            withTimeout(40_000) {
                suspendCancellableCoroutine { cont ->
                    val main = Handler(Looper.getMainLooper())
                    val webView = WebView(context.applicationContext)
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, pageUrl: String) {
                            lastTitle = view.title ?: ""
                            view.evaluateJavascript("document.body ? document.body.innerText : ''") { raw ->
                                val text = (runCatching { JSONTokener(raw).nextValue() }.getOrNull() as? String)
                                    ?.trim() ?: return@evaluateJavascript
                                if (!text.startsWith("{") && !text.startsWith("[")) return@evaluateJavascript
                                val value = runCatching { JSONTokener(text).nextValue() }.getOrNull()
                                    ?: return@evaluateJavascript
                                if (cont.isActive) {
                                    cont.resume(value)
                                    main.post { view.destroy() }
                                }
                            }
                        }
                    }
                    cont.invokeOnCancellation { main.post { webView.destroy() } }
                    webView.loadUrl(url)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw Exception("Pas de réponse de claude.ai (page : « $lastTitle »)")
        }
    }
}
