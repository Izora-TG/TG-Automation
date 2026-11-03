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
import android.media.AudioManager
import android.net.Uri
import android.os.Build
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
import com.tg.automation.model.Component
import com.tg.automation.model.int
import com.tg.automation.model.p
import com.tg.automation.service.ControlService
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

    suspend fun run(ctx: Context, a: Component, vars: MutableMap<String, String>) {
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
    private val regex = Regex("\\{([a-z_]+)\\}")
    fun apply(text: String, vars: Map<String, String>): String =
        regex.replace(text) { m -> vars[m.groupValues[1]] ?: m.value }
}
