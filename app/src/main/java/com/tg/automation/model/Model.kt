package com.tg.automation.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Typ + Einstellungen eines Bausteins (wird von Registry/Engine benutzt). */
@Serializable
data class Component(
    val type: String,
    val params: Map<String, String> = emptyMap(),
)

/** Ein Block auf dem Board. Position in dp. */
@Serializable
data class Node(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val params: Map<String, String> = emptyMap(),
    val x: Float = 0f,
    val y: Float = 0f,
) {
    val component: Component get() = Component(type, params)
    val kind: Kind? get() = Registry.def(type)?.kind
}

/** Verbindung vom Ausgang [port] eines Blocks zum Eingang eines anderen. */
@Serializable
data class Link(
    val from: String,
    val port: String = PORT_OUT,
    val to: String,
)

const val PORT_OUT = "out"
const val PORT_YES = "yes"
const val PORT_NO = "no"

@Serializable
data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val nodes: List<Node> = emptyList(),
    val links: List<Link> = emptyList(),
    val lastRun: Long = 0L,
    // Altes Listen-Format (vor dem Board) – wird beim Laden umgewandelt.
    val triggers: List<Component> = emptyList(),
    val constraints: List<Component> = emptyList(),
    val actions: List<Component> = emptyList(),
) {
    val triggerNodes: List<Node> get() = nodes.filter { it.kind == Kind.TRIGGER }

    fun node(id: String): Node? = nodes.firstOrNull { it.id == id }

    /** Nachfolger eines Ausgangs – von oben nach unten, links nach rechts sortiert. */
    fun next(nodeId: String, port: String): List<Node> =
        links.filter { it.from == nodeId && it.port == port }
            .mapNotNull { l -> node(l.to) }
            .sortedWith(compareBy({ it.y }, { it.x }))

    /** Kopie mit neuen Block-IDs (damit Wecker usw. nicht kollidieren). */
    fun withNewIds(): Macro {
        val map = nodes.associate { it.id to UUID.randomUUID().toString() }
        return copy(
            nodes = nodes.map { it.copy(id = map.getValue(it.id)) },
            links = links.mapNotNull { l ->
                val f = map[l.from] ?: return@mapNotNull null
                val t = map[l.to] ?: return@mapNotNull null
                l.copy(from = f, to = t)
            },
        )
    }

    fun needsMigration(): Boolean = nodes.isEmpty() && (triggers.isNotEmpty() || actions.isNotEmpty())

    /** Wandelt das alte Format (Auslöser → Bedingungen → Aktionen) in Blöcke um. */
    fun migrated(): Macro = linear(name, triggers, constraints, actions).copy(
        id = id, enabled = enabled, lastRun = lastRun,
    )

    companion object {
        /** Baut einen einfachen Ablauf: alle Auslöser → Bedingungen (Ja) → Aktionen nacheinander. */
        fun linear(
            name: String,
            triggers: List<Component>,
            constraints: List<Component> = emptyList(),
            actions: List<Component> = emptyList(),
        ): Macro {
            val nodes = mutableListOf<Node>()
            val links = mutableListOf<Link>()
            val startX = 40f
            val gapX = 200f
            val rowY = 110f
            val triggerNodes = triggers.mapIndexed { i, c ->
                Node(type = c.type, params = c.params, x = startX + i * gapX, y = 40f)
            }
            nodes += triggerNodes
            val centerX = startX + (triggerNodes.size.coerceAtLeast(1) - 1) * gapX / 2
            var y = 40f + rowY
            var previous: List<Pair<Node, String>> = triggerNodes.map { it to PORT_OUT }
            for (c in constraints + actions) {
                val n = Node(type = c.type, params = c.params, x = centerX, y = y)
                nodes += n
                previous.forEach { (p, port) -> links += Link(p.id, port, n.id) }
                previous = listOf(n to if (n.kind?.branches == true) PORT_YES else PORT_OUT)
                y += rowY
            }
            return Macro(name = name, nodes = nodes, links = links)
        }
    }
}

@Serializable
data class LogEntry(
    val time: Long,
    val macro: String,
    val message: String,
    val error: Boolean = false,
)

enum class Kind(val title: String, val plural: String, val branches: Boolean) {
    TRIGGER("Auslöser", "Auslöser", false),
    CONSTRAINT("Bedingung", "Bedingungen", true),
    QUESTION("Abfrage", "Abfragen", true),
    ACTION("Aktion", "Aktionen", false),
}

enum class Requirement(val title: String, val description: String) {
    NOTIFICATIONS(
        "Benachrichtigungen",
        "Damit Makros Benachrichtigungen anzeigen können.",
    ),
    EXACT_ALARM(
        "Wecker & Erinnerungen",
        "Für minutengenaue Uhrzeit-Auslöser. Ohne diese Erlaubnis kann es ein paar Minuten Verzögerung geben.",
    ),
    NOTIFICATION_LISTENER(
        "Benachrichtigungszugriff",
        "Für „Benachrichtigung erhalten“ und „Benachrichtigungen löschen“.",
    ),
    ACCESSIBILITY(
        "Bedienungshilfe (Steuerung)",
        "Für „App geöffnet“, Tippen/Wischen, Home/Zurück/Sperren/Screenshot und um Apps im Hintergrund zu öffnen. Liest keine Bildschirminhalte.",
    ),
    WRITE_SETTINGS(
        "Systemeinstellungen ändern",
        "Für Helligkeit, Bildschirm-Timeout und automatisches Drehen.",
    ),
    DND_ACCESS(
        "„Nicht stören“-Zugriff",
        "Für „Nicht stören“, Lautlos-Modus und Klingellautstärke.",
    ),
    BLUETOOTH(
        "Bluetooth-Geräte",
        "Um den Namen verbundener Bluetooth-Geräte zu erkennen.",
    ),
    PHONE(
        "Telefonstatus",
        "Für den Auslöser „Anruf“.",
    ),
    LOCATION(
        "Standort (immer)",
        "Android gibt den WLAN-Namen (SSID) nur mit Standort-Erlaubnis heraus. Nur nötig, wenn du ein bestimmtes WLAN auswählst.",
    ),
    BATTERY_OPTIMIZATION(
        "Akku-Optimierung ausnehmen (empfohlen)",
        "Verhindert, dass das System die App beendet. Die App selbst verbraucht dadurch nicht mehr Akku – sie wartet nur auf Ereignisse.",
    ),
}
