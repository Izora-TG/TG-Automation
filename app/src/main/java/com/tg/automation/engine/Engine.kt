package com.tg.automation.engine

import android.content.BroadcastReceiver
import android.util.Log
import com.tg.automation.App
import com.tg.automation.data.LogRepository
import com.tg.automation.data.MacroRepository
import com.tg.automation.data.Prefs
import com.tg.automation.model.Component
import com.tg.automation.model.Kind
import com.tg.automation.model.Macro
import com.tg.automation.model.Node
import com.tg.automation.model.PORT_NO
import com.tg.automation.model.PORT_OUT
import com.tg.automation.model.PORT_YES
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
 * Herzstück: nimmt Ereignisse entgegen, startet beim passenden Auslöser-Block und folgt den
 * Verbindungen auf dem Board. Bedingungen verzweigen über „Ja“/„Nein“.
 * Es gibt keine Dauerschleifen – alles ist ereignisgesteuert.
 */
object Engine {
    private const val MAX_STEPS = 500
    private const val MAX_DEPTH = 5
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun hasTrigger(type: String): Boolean =
        Prefs.masterEnabled && MacroRepository.macros.value.any { m -> m.enabled && m.nodes.any { it.type == type } }

    /** Ein Systemereignis ist eingetreten. */
    fun dispatch(type: String, data: Map<String, String> = emptyMap()): List<Job> {
        if (!Prefs.masterEnabled) return emptyList()
        val title = Registry.def(type)?.title ?: type
        val jobs = mutableListOf<Job>()
        for (m in MacroRepository.macros.value) {
            if (!m.enabled) continue
            m.triggerNodes.filter { it.type == type && matches(it.component, data) }.forEach { start ->
                jobs += run(m, start.id, data + ("trigger" to title), title)
            }
        }
        return jobs
    }

    /** Ein geplanter Wecker (Uhrzeit/Intervall) ist abgelaufen. */
    fun fireAlarm(macroId: String, nodeId: String): Job? {
        if (!Prefs.masterEnabled) return null
        val m = MacroRepository.get(macroId) ?: return null
        if (!m.enabled) return null
        val n = m.node(nodeId) ?: return null
        val title = Registry.def(n.type)?.title ?: n.type
        return run(m, n.id, mapOf("trigger" to title), title)
    }

    /** Manuell starten: beim Block „Manuell“, sonst beim obersten Auslöser. */
    fun runManually(macro: Macro, source: String): Job? {
        val start = macro.triggerNodes.firstOrNull { it.type == "manual" }
            ?: macro.triggerNodes.minWithOrNull(compareBy({ it.y }, { it.x }))
            ?: return null
        return run(macro, start.id, mapOf("trigger" to source), source)
    }

    fun run(macro: Macro, startNodeId: String, data: Map<String, String>, source: String, depth: Int = 0): Job =
        scope.launch { execute(macro, startNodeId, data, source, depth) }

    private suspend fun execute(macro: Macro, startNodeId: String, data: Map<String, String>, source: String, depth: Int) {
        val ctx = App.instance
        val vars = baseVars().apply { putAll(data) }
        // Tiefensuche: Nachfolger in Board-Reihenfolge (oben → unten) abarbeiten.
        val stack = ArrayDeque<Node>()
        macro.next(startNodeId, PORT_OUT).asReversed().forEach { stack.addLast(it) }
        var steps = 0
        var actions = 0
        var errors = 0
        while (stack.isNotEmpty()) {
            if (++steps > MAX_STEPS) {
                LogRepository.add(macro.name, "Abgebrochen: mehr als $MAX_STEPS Schritte (Endlosschleife?)", error = true)
                errors++
                break
            }
            val node = stack.removeLast()
            val def = Registry.def(node.type) ?: continue
            val port: String? = when (def.kind) {
                Kind.TRIGGER -> PORT_OUT
                Kind.CONSTRAINT -> {
                    val ok = try {
                        Conditions.check(ctx, node.component)
                    } catch (e: Exception) {
                        false
                    }
                    if (ok) PORT_YES else PORT_NO
                }
                Kind.ACTION -> {
                    actions++
                    try {
                        if (node.type == "run_macro") {
                            runOther(node.component.p("macro"), depth, source)
                        } else {
                            ActionRunner.run(ctx, node.component, vars, macro.name)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        errors++
                        Log.w("Engine", "Aktion ${def.title} fehlgeschlagen", e)
                        LogRepository.add(macro.name, "${def.title}: ${e.message ?: e.javaClass.simpleName}", error = true)
                    }
                    PORT_OUT
                }
            }
            if (port != null) macro.next(node.id, port).asReversed().forEach { stack.addLast(it) }
        }
        if (actions == 0 && errors == 0) return // Bedingungen nicht erfüllt – nichts zu melden.
        MacroRepository.markRun(macro.id)
        LogRepository.add(
            macro.name,
            if (errors == 0) "Ausgeführt · $source" else "Ausgeführt mit $errors Fehler(n) · $source",
            error = errors > 0,
        )
    }

    private fun runOther(id: String, depth: Int, source: String) {
        if (depth >= MAX_DEPTH) throw ActionException("Zu viele verschachtelte Makros")
        val other = MacroRepository.get(id) ?: throw ActionException("Makro nicht gefunden")
        val start = other.triggerNodes.firstOrNull { it.type == "manual" }
            ?: other.triggerNodes.minWithOrNull(compareBy({ it.y }, { it.x }))
            ?: throw ActionException("„${other.name}“ hat keinen Auslöser")
        run(other, start.id, mapOf("trigger" to source), "von anderem Makro", depth + 1)
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
        "power", "screen", "headset", "airplane", "call", "face_down", "dnd_changed", "power_save", "mobile_data" ->
            d["state"] == t.p("state")
        "ringer_changed" -> t.p("mode") == "any" || d["mode"] == t.p("mode")
        "shake" -> {
            val g = d["g"]?.toFloatOrNull() ?: 0f
            g >= when (t.p("sensitivity")) { "high" -> 1.8f; "low" -> 2.9f; else -> 2.3f }
        }
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
