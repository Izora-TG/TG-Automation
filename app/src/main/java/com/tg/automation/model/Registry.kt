package com.tg.automation.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
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
import com.tg.automation.engine.Apps

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
            "wait", Kind.ACTION, "Sonstiges", "Warten",
            "Wartet, bevor die nächste Aktion ausgeführt wird", Icons.Default.HourglassEmpty,
            params = listOf(Param.Number("seconds", "Sekunden", 5, 1, 3600, "s")),
            summary = { "${it.p("seconds")} Sekunden warten" },
        ),
    )

    private val byType = all.associateBy { it.type }

    fun def(type: String): ComponentDef? = byType[type]

    fun of(kind: Kind): List<ComponentDef> = all.filter { it.kind == kind }
}

/** Platzhalter, die in Texten benutzt werden können. */
const val VARIABLE_HINT =
    "Platzhalter: {battery} Akku · {time} Uhrzeit · {date} Datum · {app_name} App · {title} / {text} Benachrichtigung · {ssid} WLAN · {device} Gerät · {http_response} Antwort"
