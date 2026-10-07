package com.hamman.consoclaude

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime

/** Une limite : pourcentage utilisé (0-100) et heure de reset (epoch ms, 0 si inconnue). */
data class Limit(val utilization: Double, val resetsAt: Long)

/** null = aucune fenêtre ouverte en ce moment. */
data class Usage(val fiveHour: Limit?, val sevenDay: Limit?)

class AuthException(message: String) : Exception(message)

/**
 * Lit la consommation via l'API de claude.ai (la même que la page
 * Paramètres → Utilisation), avec le cookie de session du compte.
 * API non documentée : elle peut changer.
 */
object UsageApi {
    private const val BASE = "https://claude.ai/api"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

    fun fetch(cookie: String, knownOrgId: String?): Pair<String, Usage> {
        val orgId = knownOrgId ?: findOrgId(cookie)
        val json = try {
            JSONObject(get("$BASE/organizations/$orgId/usage", cookie))
        } catch (e: NotFoundException) {
            // Organisation changée : on la recherche à nouveau.
            if (knownOrgId == null) throw e
            return fetch(cookie, null)
        }
        return orgId to Usage(parseLimit(json, "five_hour"), parseLimit(json, "seven_day"))
    }

    private fun findOrgId(cookie: String): String {
        val orgs = JSONArray(get("$BASE/organizations", cookie))
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

    private class NotFoundException : Exception()

    private fun get(url: String, cookie: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Cookie", cookie)
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("anthropic-client-platform", "web_claude_ai")
            val code = conn.responseCode
            when {
                code == 401 || code == 403 ->
                    throw AuthException("Session expirée ou refusée (HTTP $code)")
                code == 404 -> throw NotFoundException()
                code !in 200..299 -> throw Exception("HTTP $code")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
