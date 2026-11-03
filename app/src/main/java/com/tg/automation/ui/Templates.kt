package com.tg.automation.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.tg.automation.model.Component
import com.tg.automation.model.Link
import com.tg.automation.model.Macro
import com.tg.automation.model.Node
import com.tg.automation.model.PORT_NO
import com.tg.automation.model.PORT_OUT
import com.tg.automation.model.PORT_YES
import com.tg.automation.model.Registry

class Template(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val build: () -> Macro,
)

private fun node(type: String, x: Float, y: Float, vararg params: Pair<String, String>): Node {
    val base = Registry.def(type)!!.create()
    return Node(type = type, params = base.params + params, x = x, y = y)
}

private fun c(type: String, vararg params: Pair<String, String>): Component {
    val base = Registry.def(type)!!.create()
    return base.copy(params = base.params + params)
}

object Templates {
    val all = listOf(

        Template("Schütteln = Taschenlampe", "Handy schütteln schaltet die Taschenlampe um", Icons.Default.Sensors) {
            Macro.linear(
                name = "Schütteln = Taschenlampe",
                triggers = listOf(c("shake", "sensitivity" to "medium")),
                actions = listOf(c("torch", "state" to "toggle"), c("vibrate", "pattern" to "short")),
            )
        },
        Template("Umdrehen = Ruhe", "Display nach unten → Nicht stören an, wieder hoch → aus (mit Verzweigung)", Icons.Default.FlipToBack) {
            val down = node("face_down", 40f, 40f, "state" to "down")
            val up = node("face_down", 260f, 40f, "state" to "up")
            val dndOn = node("dnd", 40f, 150f, "mode" to "priority")
            val vib = node("vibrate", 40f, 260f, "pattern" to "short")
            val dndOff = node("dnd", 260f, 150f, "mode" to "off")
            Macro(
                name = "Umdrehen = Ruhe",
                nodes = listOf(down, up, dndOn, vib, dndOff),
                links = listOf(
                    Link(down.id, PORT_OUT, dndOn.id),
                    Link(dndOn.id, PORT_OUT, vib.id),
                    Link(up.id, PORT_OUT, dndOff.id),
                ),
            )
        },
        Template("Akku-Ampel", "Beim Entsperren: unter 30 % Warnung, sonst kurze Info (Ja/Nein-Verzweigung)", Icons.Default.Traffic) {
            val trigger = node("screen", 150f, 40f, "state" to "unlocked")
            val check = node("battery_range", 150f, 150f, "min" to "0", "max" to "29")
            val warn = node("notify", 40f, 280f, "title" to "Akku niedrig", "text" to "Nur noch {battery} % – bitte laden.")
            val ok = node("toast", 260f, 280f, "text" to "Akku: {battery} %")
            Macro(
                name = "Akku-Ampel",
                nodes = listOf(trigger, check, warn, ok),
                links = listOf(
                    Link(trigger.id, PORT_OUT, check.id),
                    Link(check.id, PORT_YES, warn.id),
                    Link(check.id, PORT_NO, ok.id),
                ),
            )
        },
        Template("Nachtruhe", "Um 22:30 „Nicht stören“ an, Helligkeit runter, Vibration", Icons.Default.Bedtime) {
            Macro.linear(
                name = "Nachtruhe",
                triggers = listOf(c("time", "time" to "22:30", "days" to "1,2,3,4,5,6,7")),
                actions = listOf(
                    c("dnd", "mode" to "priority"),
                    c("brightness", "mode" to "manual", "level" to "15"),
                    c("ringer", "mode" to "vibrate"),
                ),
            )
        },
        Template("Guten Morgen", "Mo–Fr um 7:00 „Nicht stören“ aus und Begrüßung vorlesen", Icons.Default.WbSunny) {
            Macro.linear(
                name = "Guten Morgen",
                triggers = listOf(c("time", "time" to "07:00", "days" to "1,2,3,4,5")),
                actions = listOf(
                    c("dnd", "mode" to "off"),
                    c("ringer", "mode" to "normal"),
                    c("speak", "text" to "Guten Morgen! Es ist {time} Uhr. Dein Akku hat {battery} Prozent."),
                ),
            )
        },
        Template("Akku-Sparer", "Bei 20 % Akku Helligkeit senken und Hinweis zeigen", Icons.Default.BatterySaver) {
            Macro.linear(
                name = "Akku-Sparer",
                triggers = listOf(c("battery_level", "op" to "below", "level" to "20")),
                constraints = listOf(c("charging", "state" to "no")),
                actions = listOf(
                    c("brightness", "mode" to "manual", "level" to "25"),
                    c("notify", "title" to "Akku niedrig", "text" to "Nur noch {battery} % – Helligkeit wurde reduziert."),
                ),
            )
        },
        Template("Musik mit Kopfhörern", "Kopfhörer rein → Lautstärke 40 % und Musik abspielen", Icons.Default.Headphones) {
            Macro.linear(
                name = "Musik mit Kopfhörern",
                triggers = listOf(c("headset", "state" to "connected")),
                actions = listOf(
                    c("volume", "stream" to "media", "level" to "40"),
                    c("wait", "seconds" to "1"),
                    c("media", "cmd" to "play"),
                ),
            )
        },
        Template("Ladegerät-Ansage", "Sagt den Akkustand an, wenn du das Ladekabel einsteckst", Icons.Default.BatteryChargingFull) {
            Macro.linear(
                name = "Ladegerät-Ansage",
                triggers = listOf(c("power", "state" to "connected")),
                constraints = listOf(c("time_range", "from" to "07:00", "to" to "22:00")),
                actions = listOf(c("speak", "text" to "Lädt. Akku bei {battery} Prozent.")),
            )
        },
        Template("Nachrichten vorlesen", "Liest Benachrichtigungen vor, wenn Kopfhörer verbunden sind", Icons.Default.RecordVoiceOver) {
            Macro.linear(
                name = "Nachrichten vorlesen",
                triggers = listOf(c("notification")),
                constraints = listOf(c("headphones", "state" to "yes")),
                actions = listOf(c("speak", "text" to "{app_name}: {title}. {text}")),
            )
        },
        Template("Taschenlampe", "Per Knopf oder Homescreen-Verknüpfung umschalten", Icons.Default.FlashlightOn) {
            Macro.linear(
                name = "Taschenlampe",
                triggers = listOf(c("manual")),
                actions = listOf(c("torch", "state" to "toggle")),
            )
        },
        Template("Bildschirm sperren", "Sperrt das Handy per Verknüpfung", Icons.Default.Lock) {
            Macro.linear(
                name = "Bildschirm sperren",
                triggers = listOf(c("manual")),
                actions = listOf(c("global", "cmd" to "lock")),
            )
        },
    )
}
