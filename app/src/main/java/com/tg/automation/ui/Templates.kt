package com.tg.automation.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.tg.automation.model.Component
import com.tg.automation.model.Macro
import com.tg.automation.model.Registry

class Template(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val build: () -> Macro,
)

private fun c(type: String, vararg params: Pair<String, String>): Component {
    val base = Registry.def(type)!!.create()
    return base.copy(params = base.params + params)
}

object Templates {
    val all = listOf(
        Template("Nachtruhe", "Um 22:30 „Nicht stören“ an, Helligkeit runter, Vibration", Icons.Default.Bedtime) {
            Macro(
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
            Macro(
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
            Macro(
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
            Macro(
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
            Macro(
                name = "Ladegerät-Ansage",
                triggers = listOf(c("power", "state" to "connected")),
                constraints = listOf(c("time_range", "from" to "07:00", "to" to "22:00")),
                actions = listOf(c("speak", "text" to "Lädt. Akku bei {battery} Prozent.")),
            )
        },
        Template("Nachrichten vorlesen", "Liest Benachrichtigungen vor, wenn Kopfhörer verbunden sind", Icons.Default.RecordVoiceOver) {
            Macro(
                name = "Nachrichten vorlesen",
                triggers = listOf(c("notification")),
                constraints = listOf(c("headphones", "state" to "yes")),
                actions = listOf(c("speak", "text" to "{app_name}: {title}. {text}")),
            )
        },
        Template("Taschenlampe", "Per Knopf oder Homescreen-Verknüpfung umschalten", Icons.Default.FlashlightOn) {
            Macro(
                name = "Taschenlampe",
                triggers = listOf(c("manual")),
                actions = listOf(c("torch", "state" to "toggle")),
            )
        },
        Template("Bildschirm sperren", "Sperrt das Handy per Verknüpfung", Icons.Default.Lock) {
            Macro(
                name = "Bildschirm sperren",
                triggers = listOf(c("manual")),
                actions = listOf(c("global", "cmd" to "lock")),
            )
        },
    )
}
