package com.tg.automation.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.tg.automation.data.MacroRepository
import com.tg.automation.data.Prefs
import com.tg.automation.model.Component
import com.tg.automation.model.int
import com.tg.automation.model.p
import com.tg.automation.service.AlarmReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Plant Uhrzeit- und Intervall-Auslöser über den AlarmManager. Die App wird dadurch nur
 * genau dann geweckt, wenn etwas zu tun ist – kein Dauer-Timer im Hintergrund.
 */
object Scheduler {
    private const val PREFS = "scheduler"
    private const val KEY = "scheduled"
    const val ACTION = "com.tg.automation.ALARM"

    private fun isTimed(c: Component) = c.type == "time" || c.type == "interval"

    @Synchronized
    fun rescheduleAll(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getStringSet(KEY, emptySet()).orEmpty()
        val now = mutableSetOf<String>()
        if (Prefs.masterEnabled) {
            for (m in MacroRepository.macros.value) {
                if (!m.enabled) continue
                for (node in m.triggerNodes) {
                    val t = node.component
                    if (!isTimed(t)) continue
                    val sig = "${m.id}|${node.id}|${t.type}|${t.params.toSortedMap()}"
                    now += sig
                    val exists = pendingIntent(ctx, m.id, node.id, create = false) != null
                    // Uhrzeiten immer neu berechnen; Intervalle nur, wenn neu oder geändert.
                    if (t.type == "time" || sig !in old || !exists) schedule(ctx, m.id, node.id, t)
                }
            }
        }
        val activeKeys = now.map { it.split("|").take(2).joinToString("|") }.toSet()
        for (sig in old) {
            val parts = sig.split("|")
            val id = parts.getOrNull(0) ?: continue
            val idx = parts.getOrNull(1) ?: continue
            if ("$id|$idx" in activeKeys) continue
            pendingIntent(ctx, id, idx, create = false)?.let {
                ctx.getSystemService(AlarmManager::class.java).cancel(it)
                it.cancel()
            }
        }
        prefs.edit().putStringSet(KEY, now).apply()
    }

    fun schedule(ctx: Context, macroId: String, nodeId: String, t: Component) {
        val at = when (t.type) {
            "time" -> nextTime(t.p("time"), t.p("days")) ?: return
            "interval" -> System.currentTimeMillis() + t.int("minutes", 60).coerceAtLeast(15) * 60_000L
            else -> return
        }
        val pi = pendingIntent(ctx, macroId, nodeId, create = true) ?: return
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            when {
                t.type == "interval" -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                canExact(am) -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else -> am.setWindow(AlarmManager.RTC_WAKEUP, at, 5 * 60_000L, pi)
            }
        } catch (e: SecurityException) {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 5 * 60_000L, pi)
        }
    }

    private fun canExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    fun nextTime(time: String, days: String): Long? {
        val t = Conditions.parseTime(time)
        val daySet = Conditions.parseDays(days)
        val now = LocalDateTime.now()
        var dt = LocalDate.now().atTime(t)
        if (!dt.isAfter(now)) dt = dt.plusDays(1)
        repeat(8) {
            if (dt.dayOfWeek.value in daySet) return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            dt = dt.plusDays(1)
        }
        return null
    }

    private fun pendingIntent(ctx: Context, macroId: String, nodeId: String, create: Boolean): PendingIntent? {
        val intent = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION)
            .setData(Uri.parse("tgauto://alarm/$macroId/$nodeId"))
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(ctx, 0, intent, flags)
    }
}
