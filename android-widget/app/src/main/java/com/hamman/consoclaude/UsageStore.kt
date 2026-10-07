package com.hamman.consoclaude

import android.content.Context

/** Stockage privé de l'app (cookie de session et dernière mesure). */
class UsageStore(context: Context) {
    private val prefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE)

    var cookie: String?
        get() = prefs.getString("cookie", null)
        set(v) = prefs.edit().putString("cookie", v).remove("org_id").apply()

    var orgId: String?
        get() = prefs.getString("org_id", null)
        set(v) = prefs.edit().putString("org_id", v).apply()

    var error: String?
        get() = prefs.getString("error", null)
        set(v) = prefs.edit().putString("error", v).apply()

    val updatedAt: Long get() = prefs.getLong("updated_at", 0)

    val usage: Usage?
        get() {
            if (updatedAt == 0L) return null
            return Usage(readLimit("five"), readLimit("week"))
        }

    fun saveUsage(usage: Usage) {
        prefs.edit().apply {
            writeLimit(this, "five", usage.fiveHour)
            writeLimit(this, "week", usage.sevenDay)
            putLong("updated_at", System.currentTimeMillis())
            remove("error")
        }.apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private fun readLimit(key: String): Limit? {
        if (!prefs.contains("${key}_util")) return null
        return Limit(prefs.getFloat("${key}_util", 0f).toDouble(), prefs.getLong("${key}_reset", 0))
    }

    private fun writeLimit(editor: android.content.SharedPreferences.Editor, key: String, limit: Limit?) {
        if (limit == null) {
            editor.remove("${key}_util").remove("${key}_reset")
        } else {
            editor.putFloat("${key}_util", limit.utilization.toFloat()).putLong("${key}_reset", limit.resetsAt)
        }
    }
}
