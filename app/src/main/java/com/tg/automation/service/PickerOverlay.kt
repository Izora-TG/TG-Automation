package com.tg.automation.service

import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.tg.automation.engine.ScreenPicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Overlay über allen Apps zum Auswählen einer Tipp-Position oder Wisch-Strecke.
 * 1. Kleine Blase „Position wählen“ – die Person öffnet in Ruhe die Ziel-App.
 * 2. Tippen auf die Blase → durchsichtige Fläche mit Raster, dort tippen bzw. wischen.
 * 3. Übernehmen / Testen / Nochmal / Abbrechen.
 */
class PickerOverlay(
    private val svc: ControlService,
    private val mode: String, // "tap" oder "swipe"
    initial: String,
    private val onDone: (String?) -> Unit,
) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var bubble: View? = null
    private var layer: FrameLayout? = null
    private var picker: PickerView? = null
    private var bar: LinearLayout? = null
    private var finished = false
    private val start: List<Float>? = ScreenPicker.parse(initial)

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, svc.resources.displayMetrics)
    private fun dpi(v: Int) = dp(v.toFloat()).roundToInt()

    private fun lp(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun pill(text: String, bg: Int, onClick: () -> Unit) = TextView(svc).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dpi(16), dpi(10), dpi(16), dpi(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(24f)
            setColor(bg)
        }
        setOnClickListener { onClick() }
    }

    // ───────────── Schritt 1: Blase ─────────────

    @SuppressLint("ClickableViewAccessibility")
    fun showBubble() {
        val row = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(6), dpi(6), dpi(6), dpi(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(32f)
                setColor(0xF0141414.toInt())
            }
            elevation = dp(8f)
        }
        val main = pill(
            if (mode == "swipe") "📍 Wisch-Strecke wählen" else "📍 Position wählen",
            0xFFD71921.toInt(),
        ) { openPicker() }
        val close = pill("✕", 0xFF3A3A3A.toInt()) { finish(null) }
        row.addView(main)
        row.addView(close, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dpi(6) })
        val hint = TextView(svc).apply {
            text = "Öffne die App, in der getippt werden soll – dann hier tippen. Blase lässt sich verschieben."
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dpi(12), dpi(6), dpi(12), dpi(6))
            maxWidth = dpi(280)
            background = GradientDrawable().apply {
                cornerRadius = dp(12f)
                setColor(0xD0141414.toInt())
            }
        }
        val box = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(row)
            addView(hint, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(6) })
        }
        val params = lp(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpi(72)
        }
        // Blase senkrecht verschiebbar machen, damit sie nichts verdeckt.
        val slop = ViewConfiguration.get(svc).scaledTouchSlop
        var downY = 0f
        var startY = 0
        var dragging = false
        val drag = View.OnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = e.rawY; startY = params.y; dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - downY
                    if (abs(dy) > slop) dragging = true
                    if (dragging) {
                        params.y = (startY + dy).roundToInt().coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(box, params) }
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging) v.performClick()
            }
            true
        }
        main.setOnTouchListener(drag)
        hint.setOnTouchListener(drag)
        runCatching { wm.addView(box, params) }
        bubble = box
    }

    // ───────────── Schritt 2: Auswahl-Fläche ─────────────

    private fun openPicker() {
        removeBubble()
        val root = FrameLayout(svc)
        val view = PickerView(svc, mode, start) { showBar() }
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        val b = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dpi(8), dpi(8), dpi(8), dpi(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(32f)
                setColor(0xF0141414.toInt())
            }
        }
        fun add(v: View) = b.addView(v, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(4); marginEnd = dpi(4) })
        add(pill("✓ Übernehmen", 0xFF2E9E4F.toInt()) { finish(view.value()) })
        add(pill("▶ Testen", 0xFF3A3A3A.toInt()) { test(view) })
        add(pill("↺", 0xFF3A3A3A.toInt()) { view.reset(); b.visibility = View.GONE })
        add(pill("✕", 0xFF3A3A3A.toInt()) { finish(null) })
        root.addView(b, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dpi(56); topMargin = dpi(56)
        })
        runCatching { wm.addView(root, lp(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)) }
        layer = root
        picker = view
        bar = b
        if (start != null) showBar()
    }

    private fun showBar() {
        val b = bar ?: return
        val v = picker ?: return
        // Leiste dort zeigen, wo sie die Auswahl nicht verdeckt.
        val lowest = v.lowestY()
        val lp = b.layoutParams as FrameLayout.LayoutParams
        lp.gravity = (if (lowest > v.height * 0.6f) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
        b.layoutParams = lp
        b.visibility = View.VISIBLE
    }

    /** Overlay kurz entfernen, Geste wirklich ausführen, dann wieder anzeigen. */
    private fun test(view: PickerView) {
        val root = layer ?: return
        val value = view.value() ?: return
        val pts = ScreenPicker.parse(value) ?: return
        runCatching { wm.removeView(root) }
        scope.launch {
            delay(250)
            val size = ScreenPicker.realSize(svc)
            val path = Path()
            val strokes = if (pts.size >= 4) {
                path.moveTo(pts[0] * size.x, pts[1] * size.y)
                path.lineTo(pts[2] * size.x, pts[3] * size.y)
                listOf(GestureDescription.StrokeDescription(path, 0, 300))
            } else {
                path.moveTo(pts[0] * size.x, pts[1] * size.y)
                listOf(GestureDescription.StrokeDescription(path, 0, 60))
            }
            svc.dispatch(strokes)
            delay(900)
            if (!finished) runCatching { wm.addView(root, lp(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)) }
        }
    }

    private fun removeBubble() {
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
    }

    fun finish(result: String?) {
        if (finished) return
        finished = true
        removeBubble()
        layer?.let { runCatching { wm.removeView(it) } }
        layer = null
        scope.cancel()
        onDone(result)
    }
}

/** Zeichnet Raster, Fadenkreuz bzw. Wisch-Pfeil und nimmt die Berührung entgegen. */
@SuppressLint("ViewConstructor")
private class PickerView(
    ctx: Context,
    private val mode: String,
    initial: List<Float>?,
    private val onPicked: () -> Unit,
) : View(ctx) {
    private val d = resources.displayMetrics.density
    private var p1: PointF? = null
    private var p2: PointF? = null
    private var pendingInitial = initial
    private val loc = IntArray(2)

    private val dim = Paint().apply { color = 0x66000000 }
    private val grid = Paint().apply { color = 0x30FFFFFF; strokeWidth = 1f * d }
    private val gridStrong = Paint().apply { color = 0x60FFFFFF; strokeWidth = 1.5f * d }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF3B47.toInt(); strokeWidth = 3f * d; style = Paint.Style.STROKE
    }
    private val accentFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3B47.toInt() }
    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAAFFFFFF.toInt(); strokeWidth = 1f * d }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 16f * d; textAlign = Paint.Align.CENTER
        setShadowLayer(4f * d, 0f, 0f, Color.BLACK)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 13f * d; textAlign = Paint.Align.CENTER
        setShadowLayer(3f * d, 0f, 0f, Color.BLACK)
    }

    private fun screen(p: PointF): PointF {
        getLocationOnScreen(loc)
        return PointF(p.x + loc[0], p.y + loc[1])
    }

    private fun fmt(p: PointF): String {
        val size = ScreenPicker.realSize(context)
        val s = screen(p)
        return String.format(Locale.GERMANY, "%.1f %% · %.1f %%", s.x / size.x * 100, s.y / size.y * 100)
    }

    fun value(): String? {
        val size = ScreenPicker.realSize(context)
        val a = p1?.let { screen(it) } ?: return null
        return if (mode == "swipe") {
            val b = p2?.let { screen(it) } ?: return null
            ScreenPicker.format(listOf(a.x / size.x, a.y / size.y, b.x / size.x, b.y / size.y))
        } else {
            ScreenPicker.format(listOf(a.x / size.x, a.y / size.y))
        }
    }

    fun reset() {
        p1 = null; p2 = null; invalidate()
    }

    fun lowestY(): Float = maxOf(p1?.y ?: 0f, p2?.y ?: 0f)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Vorherige Auswahl wieder anzeigen.
        val init = pendingInitial ?: return
        pendingInitial = null
        val size = ScreenPicker.realSize(context)
        getLocationOnScreen(loc)
        fun at(i: Int) = PointF(init[i] * size.x - loc[0], init[i + 1] * size.y - loc[1])
        if (mode == "swipe" && init.size >= 4) {
            p1 = at(0); p2 = at(2)
        } else if (mode != "swipe" && init.size >= 2) {
            p1 = at(0)
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(dim.color)
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 1..9) {
            val paint = if (i == 5) gridStrong else grid
            c.drawLine(w * i / 10, 0f, w * i / 10, h, paint)
            c.drawLine(0f, h * i / 10, w, h * i / 10, paint)
        }
        val a = p1
        val b = p2
        if (a == null) {
            c.drawText(
                if (mode == "swipe") "Wische von der Start- zur Zielstelle" else "Tippe auf die Stelle, die angetippt werden soll",
                w / 2, h * 0.45f, text,
            )
            c.drawText("Du kannst den Finger zum Feinjustieren auch verschieben", w / 2, h * 0.45f + 26 * d, label)
            return
        }
        if (mode == "swipe") {
            c.drawCircle(a.x, a.y, 14 * d, accent)
            c.drawText("Start", a.x, a.y - 22 * d, label)
            if (b != null) {
                c.drawLine(a.x, a.y, b.x, b.y, accent)
                val ang = atan2(b.y - a.y, b.x - a.x)
                val len = 22 * d
                val head = Path().apply {
                    moveTo(b.x, b.y)
                    lineTo(b.x - len * cos(ang - 0.45f), b.y - len * sin(ang - 0.45f))
                    lineTo(b.x - len * cos(ang + 0.45f), b.y - len * sin(ang + 0.45f))
                    close()
                }
                c.drawPath(head, accentFill)
                c.drawText("Ziel", b.x, b.y + 32 * d, label)
            }
        } else {
            c.drawLine(0f, a.y, w, a.y, thin)
            c.drawLine(a.x, 0f, a.x, h, thin)
            c.drawCircle(a.x, a.y, 22 * d, accent)
            c.drawCircle(a.x, a.y, 4 * d, accentFill)
            val above = a.y > 80 * d
            c.drawText(fmt(a), a.x.coerceIn(80 * d, w - 80 * d), if (above) a.y - 34 * d else a.y + 48 * d, label)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val p = PointF(e.x, e.y)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                p1 = p
                p2 = null
            }
            MotionEvent.ACTION_MOVE -> if (mode == "swipe") p2 = p else p1 = p
            MotionEvent.ACTION_UP -> {
                if (mode == "swipe") {
                    val a = p1
                    if (a == null || hypot(p.x - a.x, p.y - a.y) < 40 * d) {
                        p1 = null; p2 = null
                    } else {
                        p2 = p
                        onPicked()
                    }
                } else {
                    p1 = p
                    onPicked()
                }
            }
        }
        invalidate()
        return true
    }
}
