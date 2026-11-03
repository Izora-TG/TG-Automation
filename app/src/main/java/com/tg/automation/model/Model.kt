package com.tg.automation.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Ein Baustein eines Makros: Auslöser, Bedingung oder Aktion. */
@Serializable
data class Component(
    val type: String,
    val params: Map<String, String> = emptyMap(),
)

@Serializable
data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val triggers: List<Component> = emptyList(),
    val constraints: List<Component> = emptyList(),
    val actions: List<Component> = emptyList(),
    val lastRun: Long = 0L,
) {
    fun list(kind: Kind): List<Component> = when (kind) {
        Kind.TRIGGER -> triggers
        Kind.CONSTRAINT -> constraints
        Kind.ACTION -> actions
    }

    fun withList(kind: Kind, list: List<Component>): Macro = when (kind) {
        Kind.TRIGGER -> copy(triggers = list)
        Kind.CONSTRAINT -> copy(constraints = list)
        Kind.ACTION -> copy(actions = list)
    }

    val all: List<Component> get() = triggers + constraints + actions
}

@Serializable
data class LogEntry(
    val time: Long,
    val macro: String,
    val message: String,
    val error: Boolean = false,
)

enum class Kind(val title: String, val subtitle: String, val addLabel: String) {
    TRIGGER("Wenn …", "Auslöser – eines davon genügt, um das Makro zu starten", "Auslöser hinzufügen"),
    CONSTRAINT("Nur wenn …", "Bedingungen (optional) – alle müssen erfüllt sein", "Bedingung hinzufügen"),
    ACTION("Dann …", "Aktionen – werden der Reihe nach ausgeführt", "Aktion hinzufügen"),
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
        "Für den Auslöser „Benachrichtigung erhalten“.",
    ),
    ACCESSIBILITY(
        "Bedienungshilfe (Steuerung)",
        "Für „App geöffnet“, Home/Zurück/Sperren/Screenshot und um Apps im Hintergrund zu öffnen. Liest keine Bildschirminhalte.",
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
