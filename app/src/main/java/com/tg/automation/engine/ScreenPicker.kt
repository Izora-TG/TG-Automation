package com.tg.automation.engine

import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import com.tg.automation.service.ControlService
import com.tg.automation.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Startet das Auswahl-Overlay und liefert das Ergebnis an den Editor zurück. */
object ScreenPicker {
    private val ids = AtomicInteger(1)
    private val _result = MutableStateFlow<Pair<Int, String>?>(null)
    val result: StateFlow<Pair<Int, String>?> = _result

    /** Gibt die Anfrage-ID zurück oder null, wenn die Bedienungshilfe nicht aktiv ist. */
    fun start(mode: String, initial: String): Int? {
        val svc = ControlService.instance ?: return null
        val id = ids.incrementAndGet()
        svc.startPicker(mode, initial) { value ->
            if (value != null) _result.value = id to value
            // Zurück in die App, der Editor ist noch offen.
            runCatching {
                svc.startActivity(Intent(svc, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        return id
    }

    /** Echte Bildschirmgröße inkl. Statusleiste und Navigationsleiste. */
    @Suppress("DEPRECATION")
    fun realSize(ctx: Context): Point {
        val p = Point()
        val display = ctx.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        display?.getRealSize(p)
        if (p.x <= 0 || p.y <= 0) {
            val dm = ctx.resources.displayMetrics
            p.set(dm.widthPixels, dm.heightPixels)
        }
        return p
    }

    /** "0.5;0.25" → [0.5, 0.25] (Anteile der Bildschirmbreite/-höhe). */
    fun parse(value: String?): List<Float>? {
        if (value.isNullOrBlank()) return null
        val parts = value.split(";").mapNotNull { it.trim().toFloatOrNull()?.coerceIn(0f, 1f) }
        return if (parts.size == 2 || parts.size == 4) parts else null
    }

    fun format(values: List<Float>): String =
        values.joinToString(";") { String.format(Locale.ROOT, "%.4f", it.coerceIn(0f, 1f)) }

    fun describe(value: String?): String {
        val v = parse(value) ?: return "nicht gewählt"
        fun pct(f: Float) = String.format(Locale.GERMANY, "%.1f %%", f * 100)
        return if (v.size == 4) "${pct(v[0])}/${pct(v[1])} → ${pct(v[2])}/${pct(v[3])}" else "${pct(v[0])} · ${pct(v[1])}"
    }
}
