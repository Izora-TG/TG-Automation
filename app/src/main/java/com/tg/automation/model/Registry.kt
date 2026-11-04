package com.tg.automation.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.tg.automation.App
import com.tg.automation.data.MacroRepository
import com.tg.automation.engine.Apps
import com.tg.automation.engine.ScreenPicker

/** Beschreibt ein einstellbares Feld eines Bausteins. */
sealed class Param(val key: String, val label: String, val default: String) {
    class Text(
        key: String,
        label: String,
        default: String = "",
        val hint: String = "",
        val multiline: Boolean = false,
    ) : Param(key, label, default)

    class Number(
        key: String,
        label: String,
        default: Int,
        val min: Int,
        val max: Int,
        val unit: String = "",
    ) : Param(key, label, default.toString())

    class Slider(
        key: String,
        label: String,
        default: Int,
        val min: Int,
        val max: Int,
        val unit: String = "",
    ) : Param(key, label, default.toString())

    class Choice(
        key: String,
        label: String,
        val options: List<Pair<String, String>>,
        default: String = options.first().first,
    ) : Param(key, label, default)

    class Time(key: String, label: String, default: String = "08:00") : Param(key, label, default)

    class Days(key: String = "days", label: String = "Wochentage", default: String = "1,2,3,4,5,6,7") :
        Param(key, label, default)

    class App(key: String = "app", label: String = "App", val optional: Boolean = false) :
        Param(key, label, "")

    class MacroRef(key: String = "macro", label: String = "Makro") : Param(key, label, "")

    /** Position auf dem Bildschirm ("x;y" als Anteile 0–1), per Overlay wählbar. */
    class ScreenPoint(key: String = "pos", label: String = "Position", default: String = "0.5000;0.5000") :
        Param(key, label, default)

    /** Wisch-Strecke ("x1;y1;x2;y2"), per Overlay wählbar. */
    class ScreenSwipe(key: String = "path", label: String = "Eigene Strecke", default: String = "") :
        Param(key, label, default)
}

class ComponentDef(
    val type: String,
    val kind: Kind,
    val category: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val params: List<Param> = emptyList(),
    val requirements: (Component) -> Set<Requirement> = { emptySet() },
    /** Auslöser, die den Hintergrund-Dienst brauchen (Systemereignisse). */
    val needsService: Boolean = false,
    /** Beschriftung der beiden Ausgänge bei verzweigenden Blöcken. */
    val portLabels: Pair<String, String> = "Ja" to "Nein",
    val summary: (Component) -> String = { "" },
) {
    fun create(): Component = Component(type, params.associate { it.key to it.default })

    fun choiceLabel(c: Component, key: String): String {
        val p = params.firstOrNull { it.key == key } as? Param.Choice ?: return c.p(key)
        val v = c.p(key)
        return p.options.firstOrNull { it.first == v }?.second ?: v
    }
}

/** Liefert einen Parameter oder dessen Standardwert. */
fun Component.p(key: String): String =
    params[key] ?: Registry.def(type)?.params?.firstOrNull { it.key == key }?.default ?: ""

fun Component.int(key: String, fallback: Int = 0): Int = p(key).toIntOrNull() ?: fallback

private val DAY_NAMES = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

fun formatDays(days: String): String {
    val set = days.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSortedSet()
    return when {
        set.isEmpty() || set.size == 7 -> "täglich"
        set == sortedSetOf(1, 2, 3, 4, 5) -> "Mo–Fr"
        set == sortedSetOf(6, 7) -> "Wochenende"
        else -> set.joinToString(", ") { DAY_NAMES[it - 1] }
    }
}

private fun askTimeout() = Param.Number("timeout", "Ohne Antwort abbrechen nach (0 = nie)", 120, 0, 86400, "s")

private fun appName(pkg: String): String =
    if (pkg.isBlank()) "beliebige App" else Apps.label(App.instance, pkg)

object Registry {
    private val connected = listOf("connected" to "Verbunden", "disconnected" to "Getrennt")
    private val onOff = listOf("on" to "Ein", "off" to "Aus")
    private val yesNo = listOf("yes" to "Ja", "no" to "Nein")

    val all: List<ComponentDef> = listOf(
        // ───────────── AUSLÖSER ─────────────
        ComponentDef(
            "time", Kind.TRIGGER, "Zeit", "Uhrzeit",
            "Zu einer festen Uhrzeit an ausgewählten Tagen", Icons.Default.Schedule,
            params = listOf(Param.Time("time", "Uhrzeit"), Param.Days()),
            requirements = { setOf(Requirement.EXACT_ALARM) },
            summary = { "${it.p("time")} Uhr, ${formatDays(it.p("days"))}" },
        ),
        ComponentDef(
            "interval", Kind.TRIGGER, "Zeit", "Intervall",
            "Regelmäßig wiederholen (akkuschonend, nicht minutengenau)", Icons.Default.Timer,
            params = listOf(Param.Number("minutes", "Alle … Minuten", 60, 15, 1440, "min")),
            summary = { "Alle ${it.p("minutes")} Minuten" },
        ),
        ComponentDef(
            "battery_level", Kind.TRIGGER, "Gerät", "Akkustand",
            "Wenn der Akku eine Grenze unter- oder überschreitet", Icons.Default.BatteryAlert,
            params = listOf(
                Param.Choice("op", "Wenn der Akku", listOf("below" to "fällt auf/unter", "above" to "steigt auf/über")),
                Param.Slider("level", "Grenze", 20, 1, 100, "%"),
            ),
            needsService = true,
            summary = { c -> "Akku ${Registry.def("battery_level")!!.choiceLabel(c, "op")} ${c.p("level")} %" },
        ),
        ComponentDef(
            "power", Kind.TRIGGER, "Gerät", "Ladegerät",
            "Ladekabel ein- oder ausgesteckt", Icons.Default.Power,
            params = listOf(Param.Choice("state", "Ladegerät", connected)),
            needsService = true,
            summary = { if (it.p("state") == "connected") "Ladegerät eingesteckt" else "Ladegerät ausgesteckt" },
        ),
        ComponentDef(
            "screen", Kind.TRIGGER, "Gerät", "Bildschirm",
            "Bildschirm an, aus oder entsperrt", Icons.Default.Smartphone,
            params = listOf(
                Param.Choice("state", "Bildschirm", listOf("on" to "Eingeschaltet", "off" to "Ausgeschaltet", "unlocked" to "Entsperrt")),
            ),
            needsService = true,
            summary = { "Bildschirm ${Registry.def("screen")!!.choiceLabel(it, "state").lowercase()}" },
        ),
        ComponentDef(
            "headset", Kind.TRIGGER, "Verbindungen", "Kopfhörer",
            "Kabel-, USB- oder Bluetooth-Kopfhörer verbunden/getrennt", Icons.Default.Headphones,
            params = listOf(Param.Choice("state", "Kopfhörer", connected)),
            needsService = true,
            summary = { if (it.p("state") == "connected") "Kopfhörer verbunden" else "Kopfhörer getrennt" },
        ),
        ComponentDef(
            "bluetooth", Kind.TRIGGER, "Verbindungen", "Bluetooth-Gerät",
            "Ein Bluetooth-Gerät (z. B. Auto, Uhr) verbindet oder trennt sich", Icons.Default.Bluetooth,
            params = listOf(
                Param.Choice("state", "Status", connected),
                Param.Text("device", "Gerätename (optional)", hint = "z. B. Auto oder Buds"),
            ),
            requirements = { setOf(Requirement.BLUETOOTH) },
            needsService = true,
            summary = {
                val dev = it.p("device").ifBlank { "Beliebiges Gerät" }
                "$dev ${if (it.p("state") == "connected") "verbunden" else "getrennt"}"
            },
        ),
        ComponentDef(
            "wifi", Kind.TRIGGER, "Verbindungen", "WLAN",
            "Mit einem WLAN verbunden oder getrennt", Icons.Default.Wifi,
            params = listOf(
                Param.Choice("state", "Status", connected),
                Param.Text("ssid", "WLAN-Name (optional)", hint = "leer = jedes WLAN"),
            ),
            requirements = { if (it.p("ssid").isNotBlank()) setOf(Requirement.LOCATION) else emptySet() },
            needsService = true,
            summary = {
                val n = it.p("ssid").ifBlank { "WLAN" }
                "$n ${if (it.p("state") == "connected") "verbunden" else "getrennt"}"
            },
        ),
        ComponentDef(
            "airplane", Kind.TRIGGER, "Verbindungen", "Flugmodus",
            "Flugmodus ein- oder ausgeschaltet", Icons.Default.AirplanemodeActive,
            params = listOf(Param.Choice("state", "Flugmodus", onOff)),
            needsService = true,
            summary = { "Flugmodus ${if (it.p("state") == "on") "ein" else "aus"}" },
        ),
        ComponentDef(
            "call", Kind.TRIGGER, "Verbindungen", "Anruf",
            "Eingehender Anruf, Gespräch oder Auflegen", Icons.Default.Call,
            params = listOf(
                Param.Choice("state", "Wenn", listOf("ringing" to "Es klingelt", "offhook" to "Gespräch läuft", "idle" to "Aufgelegt")),
            ),
            requirements = { setOf(Requirement.PHONE) },
            needsService = true,
            summary = { "Anruf: ${Registry.def("call")!!.choiceLabel(it, "state")}" },
        ),
        ComponentDef(
            "notification", Kind.TRIGGER, "Apps", "Benachrichtigung erhalten",
            "Eine App zeigt eine Benachrichtigung (optional mit Stichwort)", Icons.Default.Notifications,
            params = listOf(
                Param.App(optional = true),
                Param.Text("contains", "Enthält Text (optional)", hint = "z. B. Paket"),
            ),
            requirements = { setOf(Requirement.NOTIFICATION_LISTENER) },
            summary = {
                val t = it.p("contains").let { s -> if (s.isBlank()) "" else " mit „$s“" }
                "Benachrichtigung von ${appName(it.p("app"))}$t"
            },
        ),
        ComponentDef(
            "app_opened", Kind.TRIGGER, "Apps", "App geöffnet",
            "Eine bestimmte App wird geöffnet", Icons.Default.Apps,
            params = listOf(Param.App()),
            requirements = { setOf(Requirement.ACCESSIBILITY) },
            summary = { "${appName(it.p("app"))} geöffnet" },
        ),
        ComponentDef(
            "boot", Kind.TRIGGER, "Sonstiges", "Handy gestartet",
            "Nach dem Einschalten / Neustart", Icons.Default.RestartAlt,
            summary = { "Nach dem Neustart" },
        ),
        ComponentDef(
            "manual", Kind.TRIGGER, "Sonstiges", "Manuell / Verknüpfung",
            "Per ▶-Knopf in der App oder als Verknüpfung auf dem Homescreen", Icons.Default.TouchApp,
            summary = { "Per Knopfdruck" },
        ),

        // ───────────── BEDINGUNGEN ─────────────
        ComponentDef(
            "time_range", Kind.CONSTRAINT, "Zeit", "Zeitraum",
            "Nur zwischen zwei Uhrzeiten (auch über Mitternacht)", Icons.Default.AccessTime,
            params = listOf(Param.Time("from", "Von", "22:00"), Param.Time("to", "Bis", "07:00")),
            summary = { "Zwischen ${it.p("from")} und ${it.p("to")} Uhr" },
        ),
        ComponentDef(
            "days", Kind.CONSTRAINT, "Zeit", "Wochentage",
            "Nur an bestimmten Tagen", Icons.Default.DateRange,
            params = listOf(Param.Days(default = "1,2,3,4,5")),
            summary = { formatDays(it.p("days")) },
        ),
        ComponentDef(
            "battery_range", Kind.CONSTRAINT, "Gerät", "Akkustand",
            "Nur wenn der Akku in einem Bereich liegt", Icons.Default.BatteryStd,
            params = listOf(
                Param.Slider("min", "Mindestens", 0, 0, 100, "%"),
                Param.Slider("max", "Höchstens", 100, 0, 100, "%"),
            ),
            summary = { "Akku ${it.p("min")}–${it.p("max")} %" },
        ),
        ComponentDef(
            "charging", Kind.CONSTRAINT, "Gerät", "Lädt",
            "Nur wenn das Handy lädt (oder nicht)", Icons.Default.BatteryChargingFull,
            params = listOf(Param.Choice("state", "Lädt gerade", yesNo)),
            summary = { if (it.p("state") == "yes") "Handy lädt" else "Handy lädt nicht" },
        ),
        ComponentDef(
            "screen_state", Kind.CONSTRAINT, "Gerät", "Bildschirm",
            "Nur wenn der Bildschirm an bzw. aus ist", Icons.Default.PhoneAndroid,
            params = listOf(Param.Choice("state", "Bildschirm", onOff)),
            summary = { "Bildschirm ${if (it.p("state") == "on") "an" else "aus"}" },
        ),
        ComponentDef(
            "wifi_connected", Kind.CONSTRAINT, "Verbindungen", "WLAN verbunden",
            "Nur mit (oder ohne) WLAN-Verbindung", Icons.Default.Wifi,
            params = listOf(Param.Choice("state", "Mit WLAN verbunden", yesNo)),
            summary = { if (it.p("state") == "yes") "Mit WLAN verbunden" else "Ohne WLAN" },
        ),
        ComponentDef(
            "headphones", Kind.CONSTRAINT, "Verbindungen", "Kopfhörer",
            "Nur wenn Kopfhörer verbunden sind (oder nicht)", Icons.Default.Headset,
            params = listOf(Param.Choice("state", "Kopfhörer verbunden", yesNo)),
            summary = { if (it.p("state") == "yes") "Kopfhörer verbunden" else "Keine Kopfhörer" },
        ),
        ComponentDef(
            "ringer_mode", Kind.CONSTRAINT, "Gerät", "Klingelmodus",
            "Nur in einem bestimmten Klingelmodus", Icons.Default.RingVolume,
            params = listOf(
                Param.Choice("mode", "Modus", listOf("normal" to "Klingeln", "vibrate" to "Vibration", "silent" to "Lautlos")),
            ),
            summary = { "Klingelmodus: ${Registry.def("ringer_mode")!!.choiceLabel(it, "mode")}" },
        ),

        // ───────────── AKTIONEN ─────────────
        ComponentDef(
            "notify", Kind.ACTION, "Benachrichtigung & Ton", "Benachrichtigung anzeigen",
            "Zeigt eine Benachrichtigung an", Icons.Default.NotificationsActive,
            params = listOf(
                Param.Text("title", "Titel", "TG Automation"),
                Param.Text("text", "Text", "", hint = "z. B. Akku bei {battery} %", multiline = true),
            ),
            requirements = { setOf(Requirement.NOTIFICATIONS) },
            summary = { "„${it.p("title")}“" },
        ),
        ComponentDef(
            "toast", Kind.ACTION, "Benachrichtigung & Ton", "Kurze Einblendung",
            "Zeigt kurz einen Text unten am Bildschirm", Icons.AutoMirrored.Filled.Message,
            params = listOf(Param.Text("text", "Text", "Hallo!")),
            summary = { "„${it.p("text")}“" },
        ),
        ComponentDef(
            "speak", Kind.ACTION, "Benachrichtigung & Ton", "Text vorlesen",
            "Liest einen Text mit der Sprachausgabe vor", Icons.Default.RecordVoiceOver,
            params = listOf(Param.Text("text", "Text", "Hallo!", hint = "z. B. Es ist {time} Uhr", multiline = true)),
            summary = { "„${it.p("text")}“" },
        ),
        ComponentDef(
            "vibrate", Kind.ACTION, "Benachrichtigung & Ton", "Vibrieren",
            "Lässt das Handy vibrieren", Icons.Default.Vibration,
            params = listOf(Param.Choice("pattern", "Muster", listOf("short" to "Kurz", "long" to "Lang", "double" to "Doppelt"))),
            summary = { Registry.def("vibrate")!!.choiceLabel(it, "pattern") },
        ),
        ComponentDef(
            "volume", Kind.ACTION, "Benachrichtigung & Ton", "Lautstärke",
            "Setzt eine Lautstärke", Icons.AutoMirrored.Filled.VolumeUp,
            params = listOf(
                Param.Choice(
                    "stream", "Lautstärke für",
                    listOf("media" to "Medien", "ring" to "Klingelton", "notification" to "Benachrichtigungen", "alarm" to "Wecker"),
                ),
                Param.Slider("level", "Lautstärke", 50, 0, 100, "%"),
            ),
            requirements = { if (it.p("stream") == "media" || it.p("stream") == "alarm") emptySet() else setOf(Requirement.DND_ACCESS) },
            summary = { "${Registry.def("volume")!!.choiceLabel(it, "stream")} auf ${it.p("level")} %" },
        ),
        ComponentDef(
            "ringer", Kind.ACTION, "Benachrichtigung & Ton", "Klingelmodus",
            "Klingeln, Vibration oder Lautlos", Icons.Default.RingVolume,
            params = listOf(
                Param.Choice("mode", "Modus", listOf("normal" to "Klingeln", "vibrate" to "Vibration", "silent" to "Lautlos")),
            ),
            requirements = { setOf(Requirement.DND_ACCESS) },
            summary = { Registry.def("ringer")!!.choiceLabel(it, "mode") },
        ),
        ComponentDef(
            "dnd", Kind.ACTION, "Benachrichtigung & Ton", "Nicht stören",
            "„Nicht stören“ ein- oder ausschalten", Icons.Default.DoNotDisturbOn,
            params = listOf(
                Param.Choice(
                    "mode", "Modus",
                    listOf("off" to "Aus", "priority" to "Nur Wichtiges", "alarms" to "Nur Wecker", "none" to "Totale Ruhe"),
                    default = "priority",
                ),
            ),
            requirements = { setOf(Requirement.DND_ACCESS) },
            summary = { "Nicht stören: ${Registry.def("dnd")!!.choiceLabel(it, "mode")}" },
        ),
        ComponentDef(
            "torch", Kind.ACTION, "Gerät & Display", "Taschenlampe",
            "Taschenlampe an, aus oder umschalten", Icons.Default.FlashlightOn,
            params = listOf(Param.Choice("state", "Taschenlampe", listOf("toggle" to "Umschalten", "on" to "An", "off" to "Aus"))),
            summary = { "Taschenlampe: ${Registry.def("torch")!!.choiceLabel(it, "state")}" },
        ),
        ComponentDef(
            "brightness", Kind.ACTION, "Gerät & Display", "Helligkeit",
            "Bildschirmhelligkeit setzen oder auf automatisch stellen", Icons.Default.BrightnessMedium,
            params = listOf(
                Param.Choice("mode", "Modus", listOf("manual" to "Fester Wert", "auto" to "Automatisch")),
                Param.Slider("level", "Helligkeit (bei festem Wert)", 50, 1, 100, "%"),
            ),
            requirements = { setOf(Requirement.WRITE_SETTINGS) },
            summary = { if (it.p("mode") == "auto") "Automatische Helligkeit" else "Helligkeit ${it.p("level")} %" },
        ),
        ComponentDef(
            "screen_timeout", Kind.ACTION, "Gerät & Display", "Bildschirm-Timeout",
            "Nach wie vielen Sekunden sich der Bildschirm ausschaltet", Icons.Default.ScreenLockPortrait,
            params = listOf(
                Param.Choice(
                    "seconds", "Ausschalten nach",
                    listOf("15" to "15 s", "30" to "30 s", "60" to "1 min", "120" to "2 min", "300" to "5 min", "600" to "10 min", "1800" to "30 min"),
                    default = "60",
                ),
            ),
            requirements = { setOf(Requirement.WRITE_SETTINGS) },
            summary = { "Timeout: ${Registry.def("screen_timeout")!!.choiceLabel(it, "seconds")}" },
        ),
        ComponentDef(
            "auto_rotate", Kind.ACTION, "Gerät & Display", "Automatisch drehen",
            "Bildschirmdrehung ein- oder ausschalten", Icons.Default.ScreenRotation,
            params = listOf(Param.Choice("state", "Automatisch drehen", listOf("on" to "An", "off" to "Aus", "toggle" to "Umschalten"))),
            requirements = { setOf(Requirement.WRITE_SETTINGS) },
            summary = { "Drehen: ${Registry.def("auto_rotate")!!.choiceLabel(it, "state")}" },
        ),
        ComponentDef(
            "media", Kind.ACTION, "Steuerung", "Medien steuern",
            "Wiedergabe, Pause, nächster oder vorheriger Titel", Icons.Default.PlayCircle,
            params = listOf(
                Param.Choice(
                    "cmd", "Befehl",
                    listOf("play_pause" to "Play/Pause", "play" to "Play", "pause" to "Pause", "next" to "Nächster", "previous" to "Vorheriger", "stop" to "Stopp"),
                ),
            ),
            summary = { "Medien: ${Registry.def("media")!!.choiceLabel(it, "cmd")}" },
        ),
        ComponentDef(
            "global", Kind.ACTION, "Steuerung", "Handy bedienen",
            "Home, Zurück, Sperren, Screenshot, Benachrichtigungen …", Icons.Default.Gesture,
            params = listOf(
                Param.Choice(
                    "cmd", "Aktion",
                    listOf(
                        "home" to "Home", "back" to "Zurück", "recents" to "Letzte Apps",
                        "notifications" to "Benachrichtigungen", "quick_settings" to "Schnelleinstellungen",
                        "lock" to "Bildschirm sperren", "screenshot" to "Screenshot", "power_dialog" to "Ein/Aus-Menü",
                    ),
                ),
            ),
            requirements = { setOf(Requirement.ACCESSIBILITY) },
            summary = { Registry.def("global")!!.choiceLabel(it, "cmd") },
        ),
        ComponentDef(
            "launch_app", Kind.ACTION, "Apps & Web", "App öffnen",
            "Startet eine App", Icons.AutoMirrored.Filled.OpenInNew,
            params = listOf(Param.App()),
            requirements = { setOf(Requirement.ACCESSIBILITY) },
            summary = { "${appName(it.p("app"))} öffnen" },
        ),
        ComponentDef(
            "open_url", Kind.ACTION, "Apps & Web", "Webseite öffnen",
            "Öffnet einen Link im Browser oder der passenden App", Icons.Default.Link,
            params = listOf(Param.Text("url", "Adresse", "https://")),
            requirements = { setOf(Requirement.ACCESSIBILITY) },
            summary = { it.p("url") },
        ),
        ComponentDef(
            "http", Kind.ACTION, "Apps & Web", "Webhook / HTTP",
            "Sendet eine Anfrage, z. B. an Home Assistant oder IFTTT", Icons.Default.Http,
            params = listOf(
                Param.Choice("method", "Methode", listOf("GET" to "GET", "POST" to "POST")),
                Param.Text("url", "URL", "https://"),
                Param.Text("body", "Inhalt (nur POST, optional)", hint = "{\"akku\": \"{battery}\"}", multiline = true),
            ),
            summary = { "${it.p("method")} ${it.p("url")}" },
        ),
        ComponentDef(
            "settings_panel", Kind.ACTION, "Apps & Web", "Einstellung öffnen",
            "Öffnet WLAN-, Internet-, NFC-, Lautstärke- oder Bluetooth-Einstellungen", Icons.Default.Settings,
            params = listOf(
                Param.Choice(
                    "panel", "Einstellung",
                    listOf("internet" to "Internet", "wifi" to "WLAN", "bluetooth" to "Bluetooth", "nfc" to "NFC", "volume" to "Lautstärke"),
                ),
            ),
            requirements = { setOf(Requirement.ACCESSIBILITY) },
            summary = { "${Registry.def("settings_panel")!!.choiceLabel(it, "panel")} öffnen" },
        ),
        ComponentDef(
            "clipboard", Kind.ACTION, "Apps & Web", "In Zwischenablage",
            "Kopiert einen Text in die Zwischenablage", Icons.Default.ContentPaste,
            params = listOf(Param.Text("text", "Text", "")),
            summary = { "„${it.p("text")}“ kopieren" },
        ),
        ComponentDef(
            "wait", Kind.ACTION, "Logik", "Warten",
            "Wartet, bevor die nächste Aktion ausgeführt wird", Icons.Default.HourglassEmpty,
            params = listOf(Param.Number("seconds", "Sekunden", 5, 1, 3600, "s")),
            summary = { "${it.p("seconds")} Sekunden warten" },
        ),

        // ───────────── WEITERE AUSLÖSER ─────────────
        ComponentDef(
            "shake", Kind.TRIGGER, "Gerät", "Schütteln",
            "Handy schütteln (nur bei eingeschaltetem Bildschirm – spart Akku)", Icons.Default.Sensors,
            params = listOf(Param.Choice("sensitivity", "Empfindlichkeit", listOf("low" to "Kräftig", "medium" to "Normal", "high" to "Leicht"), default = "medium")),
            needsService = true,
            summary = { "Schütteln (${Registry.def("shake")!!.choiceLabel(it, "sensitivity")})" },
        ),
        ComponentDef(
            "face_down", Kind.TRIGGER, "Gerät", "Umdrehen",
            "Handy mit dem Display nach unten hinlegen oder wieder umdrehen", Icons.Default.FlipToBack,
            params = listOf(Param.Choice("state", "Wenn", listOf("down" to "Display nach unten", "up" to "Wieder nach oben"))),
            needsService = true,
            summary = { Registry.def("face_down")!!.choiceLabel(it, "state") },
        ),
        ComponentDef(
            "ringer_changed", Kind.TRIGGER, "Gerät", "Klingelmodus geändert",
            "Wenn zu Klingeln, Vibration oder Lautlos gewechselt wird", Icons.Default.RingVolume,
            params = listOf(
                Param.Choice("mode", "Neuer Modus", listOf("any" to "Egal", "normal" to "Klingeln", "vibrate" to "Vibration", "silent" to "Lautlos")),
            ),
            needsService = true,
            summary = { "Klingelmodus → ${Registry.def("ringer_changed")!!.choiceLabel(it, "mode")}" },
        ),
        ComponentDef(
            "dnd_changed", Kind.TRIGGER, "Gerät", "Nicht stören geändert",
            "„Nicht stören“ wurde ein- oder ausgeschaltet", Icons.Default.DoNotDisturbOn,
            params = listOf(Param.Choice("state", "Nicht stören", onOff)),
            needsService = true,
            summary = { "Nicht stören ${if (it.p("state") == "on") "an" else "aus"}" },
        ),
        ComponentDef(
            "power_save", Kind.TRIGGER, "Gerät", "Energiesparmodus",
            "Energiesparmodus ein- oder ausgeschaltet", Icons.Default.BatterySaver,
            params = listOf(Param.Choice("state", "Energiesparmodus", onOff)),
            needsService = true,
            summary = { "Energiesparen ${if (it.p("state") == "on") "an" else "aus"}" },
        ),
        ComponentDef(
            "mobile_data", Kind.TRIGGER, "Verbindungen", "Mobile Daten",
            "Mobilfunk-Internet verbunden oder getrennt", Icons.Default.SignalCellularAlt,
            params = listOf(Param.Choice("state", "Status", connected)),
            needsService = true,
            summary = { "Mobile Daten ${if (it.p("state") == "connected") "verbunden" else "getrennt"}" },
        ),

        // ───────────── ABFRAGEN ─────────────
        ComponentDef(
            "ask_yes_no", Kind.QUESTION, "Abfragen", "Frage: Ja oder Nein",
            "Fragt dich etwas – „Ja“ und „Nein“ führen zu verschiedenen Blöcken", Icons.AutoMirrored.Filled.HelpOutline,
            params = listOf(
                Param.Text("text", "Frage", "Soll ich das machen?", multiline = true),
                Param.Text("yes", "Text für Ja", "Ja"),
                Param.Text("no", "Text für Nein", "Nein"),
                askTimeout(),
            ),
            requirements = { setOf(Requirement.NOTIFICATIONS) },
            summary = { "„${it.p("text")}“" },
        ),
        ComponentDef(
            "ask_text", Kind.QUESTION, "Abfragen", "Frage: Text eingeben",
            "Fragt nach einem Text und speichert ihn in einer Variable", Icons.Default.Keyboard,
            params = listOf(
                Param.Text("text", "Frage", "Was soll ich mir merken?", multiline = true),
                Param.Text("var", "Antwort speichern in Variable", "antwort"),
                Param.Text("default", "Vorgabe (optional)", ""),
                askTimeout(),
            ),
            requirements = { setOf(Requirement.NOTIFICATIONS) },
            portLabels = "OK" to "Abbruch",
            summary = { "„${it.p("text")}“ → {${it.p("var")}}" },
        ),
        ComponentDef(
            "ask_number", Kind.QUESTION, "Abfragen", "Frage: Zahl eingeben",
            "Fragt nach einer Zahl und speichert sie in einer Variable", Icons.Default.Pin,
            params = listOf(
                Param.Text("text", "Frage", "Wie viele Minuten?", multiline = true),
                Param.Text("var", "Antwort speichern in Variable", "zahl"),
                Param.Text("default", "Vorgabe (optional)", ""),
                askTimeout(),
            ),
            requirements = { setOf(Requirement.NOTIFICATIONS) },
            portLabels = "OK" to "Abbruch",
            summary = { "„${it.p("text")}“ → {${it.p("var")}}" },
        ),
        ComponentDef(
            "ask_time", Kind.QUESTION, "Abfragen", "Frage: Uhrzeit wählen",
            "Lässt dich eine Uhrzeit wählen (gespeichert als HH:MM)", Icons.Default.Schedule,
            params = listOf(
                Param.Text("text", "Frage", "Wann soll der Wecker klingeln?", multiline = true),
                Param.Text("var", "Antwort speichern in Variable", "uhrzeit"),
                askTimeout(),
            ),
            requirements = { setOf(Requirement.NOTIFICATIONS) },
            portLabels = "OK" to "Abbruch",
            summary = { "„${it.p("text")}“ → {${it.p("var")}}" },
        ),
        ComponentDef(
            "ask_date", Kind.QUESTION, "Abfragen", "Frage: Datum wählen",
            "Lässt dich ein Datum wählen (gespeichert als TT.MM.JJJJ)", Icons.Default.DateRange,
            params = listOf(
                Param.Text("text", "Frage", "Welcher Tag?", multiline = true),
                Param.Text("var", "Antwort speichern in Variable", "datum"),
                askTimeout(),
            ),
            requirements = { setOf(Requirement.NOTIFICATIONS) },
            portLabels = "OK" to "Abbruch",
            summary = { "„${it.p("text")}“ → {${it.p("var")}}" },
        ),
        ComponentDef(
            "ask_choice", Kind.QUESTION, "Abfragen", "Frage: Auswahl",
            "Lässt dich aus mehreren Möglichkeiten wählen", Icons.Default.Checklist,
            params = listOf(
                Param.Text("text", "Frage", "Was gibt es zu essen?", multiline = true),
                Param.Text("options", "Möglichkeiten (mit Komma getrennt)", "Pizza, Pasta, Salat"),
                Param.Text("var", "Antwort speichern in Variable", "auswahl"),
                askTimeout(),
            ),
            requirements = { setOf(Requirement.NOTIFICATIONS) },
            portLabels = "OK" to "Abbruch",
            summary = { "${it.p("options")} → {${it.p("var")}}" },
        ),
        ComponentDef(
            "ask_slider", Kind.QUESTION, "Abfragen", "Frage: Schieberegler",
            "Lässt dich einen Wert mit einem Regler einstellen, z. B. Lautstärke", Icons.Default.Tune,
            params = listOf(
                Param.Text("text", "Frage", "Wie laut?", multiline = true),
                Param.Number("min", "Kleinster Wert", 0, -100000, 100000),
                Param.Number("max", "Größter Wert", 100, -100000, 100000),
                Param.Text("var", "Antwort speichern in Variable", "wert"),
                askTimeout(),
            ),
            requirements = { setOf(Requirement.NOTIFICATIONS) },
            portLabels = "OK" to "Abbruch",
            summary = { "${it.p("min")}–${it.p("max")} → {${it.p("var")}}" },
        ),

        // ───────────── WEITERE BEDINGUNGEN ─────────────
        ComponentDef(
            "chance", Kind.CONSTRAINT, "Logik", "Zufall",
            "„Ja“ mit einer bestimmten Wahrscheinlichkeit", Icons.Default.Casino,
            params = listOf(Param.Slider("percent", "Wahrscheinlichkeit für Ja", 50, 1, 99, "%")),
            summary = { "${it.p("percent")} % Ja" },
        ),
        ComponentDef(
            "var_compare", Kind.CONSTRAINT, "Logik", "Variable prüfen",
            "Vergleicht eine gespeicherte Variable mit einem Wert", Icons.Default.DataObject,
            params = listOf(
                Param.Text("name", "Variable", "zaehler", hint = "z. B. zaehler"),
                Param.Choice("op", "Vergleich", listOf("eq" to "=", "ne" to "≠", "gt" to ">", "lt" to "<", "contains" to "enthält")),
                Param.Text("value", "Wert", "0"),
            ),
            summary = { "${it.p("name")} ${Registry.def("var_compare")!!.choiceLabel(it, "op")} ${it.p("value")}" },
        ),
        ComponentDef(
            "current_app", Kind.CONSTRAINT, "Apps", "App im Vordergrund",
            "Nur wenn gerade eine bestimmte App offen ist", Icons.Default.Apps,
            params = listOf(Param.App()),
            requirements = { setOf(Requirement.ACCESSIBILITY) },
            summary = { "${appName(it.p("app"))} ist offen" },
        ),
        ComponentDef(
            "dnd_active", Kind.CONSTRAINT, "Gerät", "Nicht stören aktiv",
            "Prüft, ob „Nicht stören“ an ist", Icons.Default.DoNotDisturbOn,
            params = listOf(Param.Choice("state", "Nicht stören an", yesNo)),
            summary = { if (it.p("state") == "yes") "Nicht stören an" else "Nicht stören aus" },
        ),
        ComponentDef(
            "power_save_active", Kind.CONSTRAINT, "Gerät", "Energiesparmodus aktiv",
            "Prüft, ob der Energiesparmodus an ist", Icons.Default.BatterySaver,
            params = listOf(Param.Choice("state", "Energiesparen an", yesNo)),
            summary = { if (it.p("state") == "yes") "Energiesparen an" else "Energiesparen aus" },
        ),
        ComponentDef(
            "airplane_active", Kind.CONSTRAINT, "Verbindungen", "Flugmodus aktiv",
            "Prüft, ob der Flugmodus an ist", Icons.Default.AirplanemodeActive,
            params = listOf(Param.Choice("state", "Flugmodus an", yesNo)),
            summary = { if (it.p("state") == "yes") "Flugmodus an" else "Flugmodus aus" },
        ),

        // ───────────── WEITERE AKTIONEN ─────────────
        ComponentDef(
            "tap", Kind.ACTION, "Steuerung", "Auf Bildschirm tippen",
            "Tippt an eine Stelle – die Position wählst du direkt auf dem Bildschirm", Icons.Default.TouchApp,
            params = listOf(
                Param.ScreenPoint(),
                Param.Choice("press", "Art", listOf("short" to "Kurz", "double" to "Doppelt", "long" to "Lang")),
            ),
            requirements = { setOf(Requirement.ACCESSIBILITY) },
            summary = {
                val pos = it.params["pos"]
                val where = if (pos != null) ScreenPicker.describe(pos) else "${it.p("x")} % · ${it.p("y")} %"
                "${Registry.def("tap")!!.choiceLabel(it, "press")} tippen bei $where"
            },
        ),
        ComponentDef(
            "swipe", Kind.ACTION, "Steuerung", "Wischen",
            "Wischt über den Bildschirm – feste Richtung oder eigene Strecke", Icons.Default.Swipe,
            params = listOf(
                Param.Choice(
                    "dir", "Richtung",
                    listOf("up" to "Nach oben", "down" to "Nach unten", "left" to "Nach links", "right" to "Nach rechts", "custom" to "Eigene Strecke"),
                ),
                Param.ScreenSwipe(),
                Param.Choice("speed", "Tempo", listOf("fast" to "Schnell", "normal" to "Normal", "slow" to "Langsam"), default = "normal"),
            ),
            requirements = { setOf(Requirement.ACCESSIBILITY) },
            summary = {
                if (it.p("dir") == "custom") "Wischen ${ScreenPicker.describe(it.p("path"))}"
                else Registry.def("swipe")!!.choiceLabel(it, "dir") + " wischen"
            },
        ),
        ComponentDef(
            "volume_step", Kind.ACTION, "Benachrichtigung & Ton", "Lauter / Leiser",
            "Lautstärke eine Stufe ändern oder stummschalten", Icons.Default.UnfoldMore,
            params = listOf(
                Param.Choice("stream", "Lautstärke für", listOf("media" to "Medien", "ring" to "Klingelton", "alarm" to "Wecker")),
                Param.Choice("dir", "Richtung", listOf("up" to "Lauter", "down" to "Leiser", "mute" to "Stumm", "unmute" to "Ton an")),
            ),
            summary = { "${Registry.def("volume_step")!!.choiceLabel(it, "stream")}: ${Registry.def("volume_step")!!.choiceLabel(it, "dir")}" },
        ),
        ComponentDef(
            "play_sound", Kind.ACTION, "Benachrichtigung & Ton", "Ton abspielen",
            "Spielt den Benachrichtigungs-, Klingel- oder Weckton", Icons.Default.MusicNote,
            params = listOf(Param.Choice("sound", "Ton", listOf("notification" to "Benachrichtigung", "ringtone" to "Klingelton", "alarm" to "Wecker"))),
            summary = { Registry.def("play_sound")!!.choiceLabel(it, "sound") },
        ),
        ComponentDef(
            "clear_notifications", Kind.ACTION, "Benachrichtigung & Ton", "Benachrichtigungen löschen",
            "Entfernt alle wegwischbaren Benachrichtigungen", Icons.Default.ClearAll,
            requirements = { setOf(Requirement.NOTIFICATION_LISTENER) },
            summary = { "Alle löschen" },
        ),
        ComponentDef(
            "set_alarm", Kind.ACTION, "Apps & Web", "Wecker stellen",
            "Stellt einen Wecker in der Uhr-App", Icons.Default.Alarm,
            params = listOf(
                Param.Time("time", "Uhrzeit", "07:00"),
                Param.Text("from_var", "Oder Uhrzeit aus Variable (optional)", "", hint = "z. B. {uhrzeit}"),
                Param.Text("label", "Bezeichnung", "TG Automation"),
            ),
            requirements = { setOf(Requirement.ACCESSIBILITY) },
            summary = { if (it.p("from_var").isNotBlank()) "Wecker ${it.p("from_var")}" else "Wecker ${it.p("time")} Uhr" },
        ),
        ComponentDef(
            "set_timer", Kind.ACTION, "Apps & Web", "Timer stellen",
            "Startet einen Timer in der Uhr-App – Dauer auch aus Variable, z. B. {zahl}", Icons.Default.Timer,
            params = listOf(
                Param.Text("minutes", "Minuten", "5", hint = "Zahl oder {variable}"),
                Param.Text("label", "Bezeichnung", "TG Automation"),
            ),
            requirements = { setOf(Requirement.ACCESSIBILITY) },
            summary = { "Timer ${it.p("minutes")} min" },
        ),
        ComponentDef(
            "set_var", Kind.ACTION, "Logik", "Variable setzen",
            "Speichert einen Wert dauerhaft – nutzbar als {name} in Texten", Icons.Default.DataObject,
            params = listOf(
                Param.Text("name", "Variable", "zaehler", hint = "z. B. zaehler"),
                Param.Choice("op", "Aktion", listOf("set" to "Setzen", "add" to "Plus", "sub" to "Minus", "append" to "Anhängen")),
                Param.Text("value", "Wert", "1"),
            ),
            summary = {
                val v = it.p("value")
                when (it.p("op")) {
                    "add" -> "${it.p("name")} + $v"
                    "sub" -> "${it.p("name")} − $v"
                    "append" -> "${it.p("name")} += „$v“"
                    else -> "${it.p("name")} = $v"
                }
            },
        ),
        ComponentDef(
            "run_macro", Kind.ACTION, "Logik", "Makro ausführen",
            "Startet ein anderes Makro", Icons.Default.AccountTree,
            params = listOf(Param.MacroRef()),
            summary = { MacroRepository.get(it.p("macro"))?.name?.let { n -> "„$n“ starten" } ?: "Kein Makro gewählt" },
        ),
        ComponentDef(
            "log", Kind.ACTION, "Logik", "Ins Protokoll schreiben",
            "Schreibt einen Text ins Protokoll – praktisch zum Testen", Icons.Default.EditNote,
            params = listOf(Param.Text("text", "Text", "Akku: {battery} %")),
            summary = { "„${it.p("text")}“" },
        ),
    )

    private val byType = all.associateBy { it.type }

    fun def(type: String): ComponentDef? = byType[type]

    fun of(kind: Kind): List<ComponentDef> = all.filter { it.kind == kind }
}

/** Platzhalter, die in Texten benutzt werden können. */
const val VARIABLE_HINT =
    "Platzhalter: {battery} Akku · {var} eigene Variable · {time} Uhrzeit · {date} Datum · {app_name} App · {title} / {text} Benachrichtigung · {ssid} WLAN · {device} Gerät · {http_response} Antwort"
