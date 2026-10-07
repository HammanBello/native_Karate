package com.hamman.consoclaude

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class UsageWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        RefreshWorker.schedule(context)
        render(context, manager, ids)
    }

    override fun onEnabled(context: Context) {
        RefreshWorker.schedule(context)
        RefreshWorker.refreshNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            RefreshWorker.refreshNow(context)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.hamman.consoclaude.REFRESH"
        private val GREEN = Color.parseColor("#4CAF50")
        private val ORANGE = Color.parseColor("#FFA726")
        private val RED = Color.parseColor("#EF5350")

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, UsageWidget::class.java))
            render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val store = UsageStore(context)
            val usage = store.usage
            val now = System.currentTimeMillis()
            val v = RemoteViews(context.packageName, R.layout.widget_usage)

            v.setTextViewText(R.id.updated, if (store.updatedAt > 0) "maj ${hhmm(store.updatedAt)}" else "")

            // Session de 5h. Fenêtre absente ou déjà expirée = 100 % disponible.
            val five = usage?.fiveHour?.takeIf { it.resetsAt == 0L || it.resetsAt > now }
            if (usage == null) {
                v.setTextViewText(R.id.percent, "—")
                v.setTextViewText(R.id.countdown, "")
                v.setTextViewText(R.id.reset, "")
                v.setProgressBar(R.id.bar, 100, 0, false)
            } else if (five == null) {
                setPercent(v, R.id.percent, R.id.bar, 100)
                v.setTextViewText(R.id.countdown, "")
                v.setTextViewText(R.id.reset, "aucune session en cours")
            } else {
                setPercent(v, R.id.percent, R.id.bar, remaining(five))
                if (five.resetsAt > 0) {
                    v.setTextViewText(R.id.countdown, duration(five.resetsAt - now))
                    v.setTextViewText(R.id.reset, "reset à ${hhmm(five.resetsAt)}")
                } else {
                    v.setTextViewText(R.id.countdown, "")
                    v.setTextViewText(R.id.reset, "")
                }
            }

            // Limite hebdomadaire.
            val week = usage?.sevenDay?.takeIf { it.resetsAt == 0L || it.resetsAt > now }
            if (usage != null) {
                val pct = week?.let { remaining(it) } ?: 100
                v.setTextViewText(R.id.week, "Semaine : $pct % restant")
                v.setTextViewText(R.id.week_reset, week?.takeIf { it.resetsAt > 0 }?.let { "reset ${dayHhmm(it.resetsAt)}" } ?: "")
                v.setProgressBar(R.id.week_bar, 100, pct, false)
                tint(v, R.id.week_bar, colorFor(pct))
            } else {
                v.setTextViewText(R.id.week, "")
                v.setTextViewText(R.id.week_reset, "")
            }

            v.setTextViewText(R.id.status, store.error ?: "Touchez pour actualiser")

            val intent = Intent(context, UsageWidget::class.java).setAction(ACTION_REFRESH)
            val pending = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.root, pending)
            return v
        }

        private fun remaining(limit: Limit) = (100 - limit.utilization).roundToInt().coerceIn(0, 100)

        private fun setPercent(v: RemoteViews, textId: Int, barId: Int, pct: Int) {
            val color = colorFor(pct)
            v.setTextViewText(textId, "$pct %")
            v.setTextColor(textId, color)
            v.setProgressBar(barId, 100, pct, false)
            tint(v, barId, color)
        }

        private fun tint(v: RemoteViews, barId: Int, color: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                v.setColorStateList(barId, "setProgressTintList", ColorStateList.valueOf(color))
            }
        }

        private fun colorFor(pct: Int) = when {
            pct >= 50 -> GREEN
            pct >= 20 -> ORANGE
            else -> RED
        }

        private fun hhmm(ms: Long) = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ms))

        private fun dayHhmm(ms: Long): String {
            val sameDay = Calendar.getInstance().run {
                val today = get(Calendar.DAY_OF_YEAR)
                timeInMillis = ms
                get(Calendar.DAY_OF_YEAR) == today
            }
            return if (sameDay) hhmm(ms) else SimpleDateFormat("EEE HH:mm", Locale.FRANCE).format(Date(ms))
        }

        private fun duration(ms: Long): String {
            val minutes = (ms / 60_000).coerceAtLeast(0)
            return if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"
        }
    }
}
