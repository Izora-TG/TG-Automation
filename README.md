# TG Automation

Eine einfache, akkuschonende Automations-App für Android 8 – 16 (z. B. Nothing Phone, Pixel, Samsung).
Kein Google-Play-Dienst nötig.

## Board-Editor

Makros werden aus **Blöcken** auf einem Board gebaut und mit Linien verbunden:

- **Auslöser** (blau/Hauptfarbe) starten den Ablauf.
- **Bedingungen** haben zwei Ausgänge: **Ja** (grün) und **Nein** (rot) – so entstehen Verzweigungen.
- **Abfragen** (gelb) stellen dir eine Frage – Ja/Nein, Text, Zahl, Uhrzeit, Datum, Auswahl oder Schieberegler.
  Die Antwort landet in einer Variable (z. B. `{zahl}`), danach geht es über **OK** oder **Abbruch** weiter.
  Die Frage erscheint als Dialog (mit aktiver Bedienungshilfe auch aus dem Hintergrund) und immer als
  Benachrichtigung mit Antwort-Knöpfen.
- **Aktionen** werden ausgeführt und geben dann an den nächsten Block weiter.

Bedienung: Block antippen → Werkzeugleiste (bearbeiten, duplizieren, Verbindungen lösen, löschen).
Mit „+ Block anhängen“ wird ein neuer Block direkt unter dem ausgewählten angehängt und verbunden.
Vom ● unter einem Block auf einen anderen ziehen verbindet sie; ins Leere ziehen hängt einen neuen Block an.
Mit zwei Fingern zoomen und verschieben, „Anordnen“ räumt das Board automatisch auf.

## Blöcke (70)

| Auslöser (20) | Bedingungen (14) | Abfragen (7) | Aktionen (29) |
|---|---|---|---|
| Uhrzeit, Intervall, Akkustand, Ladegerät, Bildschirm an/aus/entsperrt, Schütteln, Umdrehen, Kopfhörer, Bluetooth-Gerät, WLAN, Mobile Daten, Flugmodus, Anruf, Klingelmodus geändert, Nicht stören geändert, Energiesparmodus, Benachrichtigung erhalten, App geöffnet, Handy gestartet, Manuell/Verknüpfung | Zeitraum, Wochentage, Akkubereich, Lädt, Bildschirm, WLAN verbunden, Kopfhörer, Klingelmodus, Nicht stören aktiv, Energiesparmodus aktiv, Flugmodus aktiv, App im Vordergrund, Zufall, Variable prüfen | Ja/Nein, Text, Zahl, Uhrzeit, Datum, Auswahl, Schieberegler | Benachrichtigung, Einblendung, Vorlesen, Vibrieren, Ton abspielen, Lautstärke, Lauter/Leiser, Klingelmodus, Nicht stören, Benachrichtigungen löschen, Taschenlampe, Helligkeit, Bildschirm-Timeout, Auto-Drehen, Medien steuern, Handy bedienen (Home, Zurück, Sperren, Screenshot …), Tippen, Wischen, App öffnen, Webseite öffnen, Webhook/HTTP, Einstellung öffnen, Wecker stellen, Timer stellen, Zwischenablage, Variable setzen, Makro ausführen, Ins Protokoll schreiben, Warten |

**Tippen & Wischen:** Die Position wählst du direkt auf dem Bildschirm. Im Block auf „Auf dem Bildschirm wählen“
tippen, die Ziel-App öffnen, dann auf die rote Blase „📍 Position wählen“ – über der App erscheint ein Raster,
dort tippst (bzw. wischst) du an die gewünschte Stelle. Mit „Testen“ wird die Geste einmal wirklich ausgeführt.

Variablen (z. B. Zähler) bleiben gespeichert und können in Texten als `{name}` benutzt werden.

Texte können Platzhalter enthalten: `{eigene_variable}`, `{battery}`, `{time}`, `{date}`, `{app_name}`, `{title}`, `{text}`, `{ssid}`, `{device}`, `{http_response}`.

Extras: Vorlagen zum Schnellstart, Homescreen-Verknüpfungen für Makros, Schnelleinstellungs-Kachel
„Automationen“ zum Pausieren, Protokoll mit Fehlermeldungen.

## Akku

- Keine Dauerabfragen – alles reagiert auf Signale von Android.
- Uhrzeit/Intervall laufen über den System-Wecker (`AlarmManager`).
- Der Hintergrund-Dienst startet **nur**, wenn ein aktives Makro Systemereignisse braucht,
  und registriert nur die nötigen Empfänger. Kein GPS.
- Schütteln/Umdrehen nutzen den Bewegungssensor nur bei eingeschaltetem Bildschirm.

## Installieren

1. Auf GitHub rechts bei **Releases** das neueste Release öffnen (jeder Build wird automatisch veröffentlicht).
2. Die `.apk` unter *Assets* auf dem Handy herunterladen und öffnen, Installation aus unbekannten Quellen erlauben.
3. In der App unter **Einrichtung** nur die Berechtigungen erlauben, die deine Makros brauchen.

> Android 13+: Sind Bedienungshilfe oder Benachrichtigungszugriff ausgegraut:
> App-Info → ⋮ → „Eingeschränkte Einstellungen zulassen“.

## Eigener Signatur-Schlüssel (empfohlen)

Ohne eigenen Schlüssel signiert jeder CI-Build mit einem neuen Debug-Schlüssel – dann muss die App
vor einem Update deinstalliert werden. Für Updates ohne Deinstallation in GitHub unter
*Settings → Secrets and variables → Actions* anlegen:

- `SIGNING_KEYSTORE_BASE64` – deine `.jks`-Datei als Base64 (`base64 -w0 key.jks`)
- `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`

Schlüssel erzeugen: `keytool -genkeypair -keystore key.jks -alias tg -keyalg RSA -keysize 2048 -validity 10000`

## Selbst bauen

```
./gradlew assembleRelease
```

Kotlin, Jetpack Compose (Material 3, dynamische Farben), kotlinx.serialization. minSdk 26, targetSdk 35.
