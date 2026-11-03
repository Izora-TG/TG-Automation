package com.tg.automation.engine

import android.content.BroadcastReceiver
import android.util.Log
import com.tg.automation.App
import com.tg.automation.data.LogRepository
import com.tg.automation.data.MacroRepository
import com.tg.automation.data.Prefs
import com.tg.automation.model.Component
import com.tg.automation.model.Macro
import com.tg.automation.model.Registry
import com.tg.automation.model.int
import com.tg.automation.model.p
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Herzstück: nimmt Ereignisse entgegen, sucht passende Makros, prüft Bedingungen
 * und führt die Aktionen aus. Es gibt keine Dauerschleifen – alles ist ereignisgesteuert.
 */
object Engine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun hasTrigger(type: String): Boolean =
        Prefs.masterEnabled && MacroRepository.macros.value.any { m -> m.enabled && m.triggers.any { it.type == type } }

    /** Ein Systemereignis ist eingetreten. */
    fun dispatch(type: String, data: Map<String, String> = emptyMap()): List<Job> {
        if (!Prefs.masterEnabled) return emptyList()
        val title = Registry.def(type)?.title ?: type
        return MacroRepository.macros.value
            .filter { m -> m.enabled && m.triggers.any { it.type == type && matches(it, data) } }
            .map { runMacro(it, data + ("trigger" to title), checkConstraints = true, source = title) }
    }

    /** Ein geplanter Wecker (Uhrzeit/Intervall) ist abgelaufen. */
    fun fireAlarm(macroId: String, index: Int): Job? {
        if (!Prefs.masterEnabled) return null
        val m = MacroRepository.get(macroId) ?: return null
        if (!m.enabled) return null
        val t = m.triggers.getOrNull(index) ?: return null
        val title = Registry.def(t.type)?.title ?: t.type
        return runMacro(m, mapOf("trigger" to title), checkConstraints = true, source = title)
    }

    fun runMacro(macro: Macro, data: Map<String, String>, checkConstraints: Boolean, source: String): Job =
        scope.launch {
            val ctx = App.instance
            if (checkConstraints) {
                val failed = macro.constraints.firstOrNull { c ->
                    try {
                        !Conditions.check(ctx, c)
                    } catch (e: Exception) {
                        true
                    }
                }
                if (failed != null) return@launch
            }
            val vars = baseVars().apply { putAll(data) }
            var errors = 0
            for (action in macro.actions) {
                try {
                    ActionRunner.run(ctx, action, vars)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    errors++
                    val name = Registry.def(action.type)?.title ?: action.type
                    Log.w("Engine", "Aktion $name fehlgeschlagen", e)
                    LogRepository.add(macro.name, "$name: ${e.message ?: e.javaClass.simpleName}", error = true)
                }
            }
            MacroRepository.markRun(macro.id)
            LogRepository.add(
                macro.name,
                if (errors == 0) "Ausgeführt · $source" else "Ausgeführt mit $errors Fehler(n) · $source",
                error = errors > 0,
            )
        }

    /** Lässt einen BroadcastReceiver kurz weiterleben, bis die Makros fertig sind. */
    fun finishWhenDone(pending: BroadcastReceiver.PendingResult, jobs: List<Job>) {
        if (jobs.isEmpty()) {
            pending.finish()
            return
        }
        scope.launch {
            withTimeoutOrNull(9_000) { jobs.joinAll() }
            pending.finish()
        }
    }

    private fun baseVars(): MutableMap<String, String> {
        val now = Date()
        return mutableMapOf(
            "battery" to runCatching { DeviceState.battery(App.instance).toString() }.getOrDefault("?"),
            "time" to SimpleDateFormat("HH:mm", Locale.GERMANY).format(now),
            "date" to SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(now),
        )
    }

    private fun matches(t: Component, d: Map<String, String>): Boolean = when (t.type) {
        "battery_level" -> {
            val level = d["level"]?.toIntOrNull() ?: return false
            val prev = d["prev"]?.toIntOrNull() ?: return false
            val limit = t.int("level", 20)
            if (t.p("op") == "above") prev < limit && level >= limit else prev > limit && level <= limit
        }
        "power", "screen", "headset", "airplane", "call" -> d["state"] == t.p("state")
        "bluetooth" -> d["state"] == t.p("state") &&
            (t.p("device").isBlank() || d["device"].orEmpty().contains(t.p("device").trim(), ignoreCase = true))
        "wifi" -> d["state"] == t.p("state") &&
            (t.p("ssid").isBlank() || d["ssid"].orEmpty().equals(t.p("ssid").trim(), ignoreCase = true))
        "notification" -> {
            val appOk = t.p("app").isBlank() || d["app"] == t.p("app")
            val needle = t.p("contains").trim()
            val textOk = needle.isBlank() ||
                "${d["title"].orEmpty()} ${d["text"].orEmpty()}".contains(needle, ignoreCase = true)
            appOk && textOk
        }
        "app_opened" -> d["app"] == t.p("app")
        else -> true
    }
}
