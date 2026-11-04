package com.tg.automation.engine

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.graphics.Path
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tg.automation.App
import com.tg.automation.R
import com.tg.automation.data.LogRepository
import com.tg.automation.data.VarStore
import com.tg.automation.model.Component
import com.tg.automation.model.int
import com.tg.automation.model.p
import com.tg.automation.service.ControlService
import com.tg.automation.service.NotificationWatcher
import com.tg.automation.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.pow
import kotlin.math.roundToInt

class ActionException(message: String) : Exception(message)

object ActionRunner {

    suspend fun run(ctx: Context, a: Component, vars: MutableMap<String, String>, macroName: String = "") {
        fun t(key: String) = Vars.apply(a.p(key), vars)
        when (a.type) {
            "notify" -> notify(ctx, t("title"), t("text"))
            "toast" -> withContext(Dispatchers.Main) { Toast.makeText(ctx, t("text"), Toast.LENGTH_LONG).show() }
            "speak" -> speak(ctx, t("text"))
            "vibrate" -> vibrate(ctx, a.p("pattern"))
            "volume" -> volume(ctx, a.p("stream"), a.int("level", 50))
            "ringer" -> ringer(ctx, a.p("mode"))
            "dnd" -> dnd(ctx, a.p("mode"))
            "torch" -> Torch.set(ctx, a.p("state"))
            "brightness" -> brightness(ctx, a.p("mode"), a.int("level", 50))
            "screen_timeout" -> {
                requireWriteSettings(ctx)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, a.int("seconds", 60) * 1000)
            }
            "auto_rotate" -> {
                requireWriteSettings(ctx)
                val cr = ctx.contentResolver
                val current = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                val on = when (a.p("state")) { "on" -> true; "off" -> false; else -> !current }
                Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
            }
            "media" -> media(ctx, a.p("cmd"))
            "global" -> global(a.p("cmd"))
            "launch_app" -> {
                val pkg = a.p("app")
                val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                    ?: throw ActionException("App nicht gefunden: $pkg")
                startActivity(ctx, intent)
            }
            "open_url" -> {
                val url = t("url").trim()
                if (url.isBlank() || url == "https://") throw ActionException("Keine Adresse angegeben")
                startActivity(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            "settings_panel" -> startActivity(ctx, settingsPanel(a.p("panel")))
            "http" -> http(a.p("method"), t("url"), t("body"), vars)
            "clipboard" -> withContext(Dispatchers.Main) {
                ctx.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("TG Automation", t("text")))
            }
            "wait" -> delay(a.int("seconds", 1).coerceIn(1, 3600) * 1000L)
            "tap" -> gesture(ctx, a.int("x", 50), a.int("y", 50), a.p("press") == "long")
            "swipe" -> swipe(ctx, a.p("dir"))
            "volume_step" -> volumeStep(ctx, a.p("stream"), a.p("dir"))
            "play_sound" -> playSound(ctx, a.p("sound"))
            "clear_notifications" -> {
                val svc = NotificationWatcher.instance
                    ?: throw ActionException("Benachrichtigungszugriff ist nicht aktiv")
                svc.cancelAllNotifications()
            }
            "set_alarm" -> {
                val fromVar = t("from_var").trim()
                if (fromVar.isNotEmpty() && !Regex("\\d{1,2}:\\d{2}").matches(fromVar)) {
                    throw ActionException("„$fromVar“ ist keine Uhrzeit (HH:MM)")
                }
                val time = Conditions.parseTime(fromVar.ifEmpty { a.p("time") })
                startActivity(
                    ctx,
                    Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, time.hour)
                        .putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, t("label"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                )
            }
            "set_timer" -> {
                val raw = t("minutes").trim().replace(',', '.')
                val minutes = raw.toDoubleOrNull() ?: throw ActionException("„$raw“ ist keine Zahl")
                val seconds = (minutes * 60).roundToInt()
                if (seconds !in 1..86_400) throw ActionException("Timer muss zwischen 1 Sekunde und 24 Stunden liegen")
                startActivity(
                    ctx,
                    Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, t("label"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                )
            }
            "set_var" -> setVar(a.p("name"), a.p("op"), t("value"))
            "log" -> LogRepository.add(macroName.ifBlank { "Makro" }, t("text"))
            else -> throw ActionException("Unbekannte Aktion: ${a.type}")
        }
    }

    // ───────────── Benachrichtigung & Ton ─────────────

    private fun notify(ctx: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) throw ActionException("Benachrichtigungen sind nicht erlaubt")
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, App.CHANNEL_MACROS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), n)
    }

    private suspend fun speak(ctx: Context, text: String) {
        if (text.isBlank()) return
        withTimeout(120_000) {
            val holder = arrayOfNulls<TextToSpeech>(1)
            val tts = suspendCancellableCoroutine<TextToSpeech> { cont ->
                withMain {
                    holder[0] = TextToSpeech(ctx.applicationContext) { status ->
                        val engine = holder[0]
                        if (status == TextToSpeech.SUCCESS && engine != null) {
                            if (cont.isActive) cont.resume(engine)
                        } else {
                            engine?.shutdown()
                            if (cont.isActive) cont.resumeWithException(ActionException("Sprachausgabe nicht verfügbar"))
                        }
                    }
                }
                cont.invokeOnCancellation { holder[0]?.shutdown() }
            }
            try {
                tts.language = Locale.getDefault()
                suspendCancellableCoroutine<Unit> { cont ->
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (cont.isActive) cont.resume(Unit)
                        }
                    })
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tg-${System.nanoTime()}")
                }
            } finally {
                tts.shutdown()
            }
        }
    }

    private fun vibrate(ctx: Context, pattern: String) {
        val vib: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val effect = when (pattern) {
            "long" -> VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE)
            "double" -> VibrationEffect.createWaveform(longArrayOf(0, 150, 120, 150), -1)
            else -> VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vib.vibrate(effect)
    }

    private fun volume(ctx: Context, streamName: String, percent: Int) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val stream = when (streamName) {
            "ring" -> AudioManager.STREAM_RING
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "alarm" -> AudioManager.STREAM_ALARM
            else -> AudioManager.STREAM_MUSIC
        }
        val max = am.getStreamMaxVolume(stream)
        val min = if (Build.VERSION.SDK_INT >= 28) am.getStreamMinVolume(stream) else 0
        val index = (min + (max - min) * percent.coerceIn(0, 100) / 100.0).roundToInt()
        try {
            am.setStreamVolume(stream, index, 0)
        } catch (e: SecurityException) {
            throw ActionException("Zugriff auf „Nicht stören“ nötig")
        }
    }

    private fun ringer(ctx: Context, mode: String) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val target = when (mode) {
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            "silent" -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        try {
            am.ringerMode = target
        } catch (e: SecurityException) {
            throw ActionException("Zugriff auf „Nicht stören“ nötig")
        }
    }

    private fun dnd(ctx: Context, mode: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) throw ActionException("Zugriff auf „Nicht stören“ nötig")
        nm.setInterruptionFilter(
            when (mode) {
                "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                "none" -> NotificationManager.INTERRUPTION_FILTER_NONE
                else -> NotificationManager.INTERRUPTION_FILTER_ALL
            },
        )
    }

    // ───────────── Gerät & Display ─────────────

    private fun requireWriteSettings(ctx: Context) {
        if (!Settings.System.canWrite(ctx)) throw ActionException("„Systemeinstellungen ändern“ ist nicht erlaubt")
    }

    private fun brightness(ctx: Context, mode: String, percent: Int) {
        requireWriteSettings(ctx)
        val cr = ctx.contentResolver
        if (mode == "auto") {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        } else {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            // Der Helligkeitsregler von Android ist nicht linear – grob an das Empfinden anpassen.
            val value = (255 * (percent.coerceIn(1, 100) / 100.0).pow(2.2)).roundToInt().coerceIn(1, 255)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, value)
        }
    }

    // ───────────── Steuerung ─────────────

    private fun media(ctx: Context, cmd: String) {
        val code = when (cmd) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = ctx.getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private suspend fun global(cmd: String) {
        val svc = ControlService.instance
            ?: throw ActionException("Bedienungshilfe „TG Automation – Steuerung“ ist nicht aktiv")
        val action = when (cmd) {
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "power_dialog" -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            "lock" -> if (Build.VERSION.SDK_INT >= 28) AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            else throw ActionException("Sperren braucht Android 9 oder neuer")
            "screenshot" -> if (Build.VERSION.SDK_INT >= 28) AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            else throw ActionException("Screenshot braucht Android 9 oder neuer")
            else -> throw ActionException("Unbekannte Aktion: $cmd")
        }
        val ok = withContext(Dispatchers.Main) { svc.performGlobalAction(action) }
        if (!ok) throw ActionException("Das System hat die Aktion abgelehnt")
    }

    private fun settingsPanel(panel: String): Intent {
        val q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val action = when (panel) {
            "wifi" -> if (q) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
            "nfc" -> if (q) Settings.Panel.ACTION_NFC else Settings.ACTION_NFC_SETTINGS
            "volume" -> if (q) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            else -> if (q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS
        }
        return Intent(action)
    }

    /**
     * Startet eine Activity. Android erlaubt das aus dem Hintergrund nur, wenn die
     * Bedienungshilfe aktiv ist – dann wird sie darüber gestartet.
     */
    private suspend fun startActivity(ctx: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.Main) {
            try {
                (ControlService.instance ?: ctx).startActivity(intent)
            } catch (e: Exception) {
                throw ActionException("Konnte nicht geöffnet werden: ${e.message}")
            }
        }
    }

    // ───────────── Web ─────────────

    private suspend fun http(method: String, url: String, body: String, vars: MutableMap<String, String>) {
        if (url.isBlank() || url == "https://") throw ActionException("Keine URL angegeben")
        withContext(Dispatchers.IO) {
            val conn = URL(url.trim()).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = if (method == "POST") "POST" else "GET"
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("User-Agent", "TG-Automation")
                if (method == "POST") {
                    conn.doOutput = true
                    val type = if (body.trimStart().startsWith("{") || body.trimStart().startsWith("[")) {
                        "application/json; charset=utf-8"
                    } else {
                        "text/plain; charset=utf-8"
                    }
                    conn.setRequestProperty("Content-Type", type)
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code < 400) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }?.take(4000) ?: ""
                vars["http_code"] = code.toString()
                vars["http_response"] = response
                if (code >= 400) throw ActionException("Server antwortete mit HTTP $code")
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun setVar(rawName: String, op: String, value: String) {
        val name = rawName.trim()
        if (name.isEmpty()) throw ActionException("Kein Variablenname angegeben")
        val current = VarStore.get(name).orEmpty()
        fun num(s: String) = s.replace(',', '.').toDoubleOrNull()
        fun fmt(d: Double) = if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
        val result = when (op) {
            "add" -> fmt((num(current) ?: 0.0) + (num(value) ?: throw ActionException("„$value“ ist keine Zahl")))
            "sub" -> fmt((num(current) ?: 0.0) - (num(value) ?: throw ActionException("„$value“ ist keine Zahl")))
            "append" -> current + value
            else -> value
        }
        VarStore.set(name, result)
    }

    private suspend fun gesture(ctx: Context, xPct: Int, yPct: Int, long: Boolean) {
        val svc = ControlService.instance
            ?: throw ActionException("Bedienungshilfe „TG Automation – Steuerung“ ist nicht aktiv")
        val dm = ctx.resources.displayMetrics
        val x = dm.widthPixels * xPct.coerceIn(0, 100) / 100f
        val y = dm.heightPixels * yPct.coerceIn(0, 100) / 100f
        val path = Path().apply { moveTo(x.coerceAtLeast(1f), y.coerceAtLeast(1f)) }
        if (!svc.dispatch(path, if (long) 800L else 60L)) throw ActionException("Das System hat die Geste abgelehnt")
    }

    private suspend fun swipe(ctx: Context, dir: String) {
        val svc = ControlService.instance
            ?: throw ActionException("Bedienungshilfe „TG Automation – Steuerung“ ist nicht aktiv")
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val (from, to) = when (dir) {
            "down" -> (w / 2 to h * 0.3f) to (w / 2 to h * 0.7f)
            "left" -> (w * 0.8f to h / 2) to (w * 0.2f to h / 2)
            "right" -> (w * 0.2f to h / 2) to (w * 0.8f to h / 2)
            else -> (w / 2 to h * 0.7f) to (w / 2 to h * 0.3f)
        }
        val path = Path().apply {
            moveTo(from.first, from.second)
            lineTo(to.first, to.second)
        }
        if (!svc.dispatch(path, 300L)) throw ActionException("Das System hat die Geste abgelehnt")
    }

    private fun volumeStep(ctx: Context, streamName: String, dir: String) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val stream = when (streamName) {
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            else -> AudioManager.STREAM_MUSIC
        }
        val direction = when (dir) {
            "down" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_MUTE
            "unmute" -> AudioManager.ADJUST_UNMUTE
            else -> AudioManager.ADJUST_RAISE
        }
        try {
            am.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)
        } catch (e: SecurityException) {
            throw ActionException("Zugriff auf „Nicht stören“ nötig")
        }
    }

    private suspend fun playSound(ctx: Context, sound: String) {
        val type = when (sound) {
            "ringtone" -> RingtoneManager.TYPE_RINGTONE
            "alarm" -> RingtoneManager.TYPE_ALARM
            else -> RingtoneManager.TYPE_NOTIFICATION
        }
        val uri = RingtoneManager.getDefaultUri(type) ?: throw ActionException("Kein Ton eingestellt")
        val ringtone = RingtoneManager.getRingtone(ctx, uri) ?: throw ActionException("Ton nicht verfügbar")
        ringtone.play()
        // Klingel-/Wecktöne laufen endlos – nach ein paar Sekunden stoppen.
        if (type != RingtoneManager.TYPE_NOTIFICATION) {
            delay(5_000)
            ringtone.stop()
        }
    }

    private fun withMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
    }
}

/** Taschenlampe mit Zustandsverfolgung für „Umschalten“. */
object Torch {
    @Volatile private var enabled = false
    private var callbackRegistered = false
    private var torchId: String? = null

    @Synchronized
    fun set(ctx: Context, mode: String) {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = torchId ?: cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }?.also { torchId = it } ?: throw ActionException("Keine Taschenlampe gefunden")
        if (!callbackRegistered) {
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, on: Boolean) {
                    if (cameraId == torchId) enabled = on
                }
            }, Handler(Looper.getMainLooper()))
            callbackRegistered = true
        }
        val on = when (mode) { "on" -> true; "off" -> false; else -> !enabled }
        try {
            cm.setTorchMode(id, on)
            enabled = on
        } catch (e: Exception) {
            throw ActionException("Taschenlampe nicht verfügbar (Kamera in Benutzung?)")
        }
    }
}

/** Ersetzt {platzhalter} in Texten. */
object Vars {
    private val regex = Regex("\\{([A-Za-z0-9_]+)\\}")
    fun apply(text: String, vars: Map<String, String>): String =
        regex.replace(text) { m ->
            val key = m.groupValues[1]
            vars[key] ?: VarStore.get(key) ?: m.value
        }
}
