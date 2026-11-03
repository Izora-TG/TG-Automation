# TG Automation

Eine einfache, akkuschonende Automations-App für Android 8 – 16 (z. B. Nothing Phone, Pixel, Samsung).
Kein Google-Play-Dienst nötig.

Ein **Makro** besteht aus:

| Wenn … (Auslöser) | Nur wenn … (Bedingungen, optional) | Dann … (Aktionen) |
|---|---|---|
| Uhrzeit, Intervall, Akkustand, Ladegerät, Bildschirm an/aus/entsperrt, Kopfhörer, Bluetooth-Gerät, WLAN (optional bestimmtes Netz), Flugmodus, Anruf, Benachrichtigung erhalten, App geöffnet, Handy gestartet, Manuell/Homescreen-Verknüpfung | Zeitraum, Wochentage, Akkubereich, Lädt, Bildschirm an/aus, WLAN verbunden, Kopfhörer verbunden, Klingelmodus | Benachrichtigung, Einblendung, Vorlesen, Vibrieren, Lautstärke, Klingelmodus, Nicht stören, Taschenlampe, Helligkeit, Bildschirm-Timeout, Auto-Drehen, Medien steuern, Handy bedienen (Home, Zurück, Sperren, Screenshot …), App öffnen, Webseite öffnen, Webhook/HTTP, Einstellung öffnen, Zwischenablage, Warten |

Texte können Platzhalter enthalten: `{battery}`, `{time}`, `{date}`, `{app_name}`, `{title}`, `{text}`, `{ssid}`, `{device}`, `{http_response}`.

Extras: Vorlagen zum Schnellstart, Homescreen-Verknüpfungen für Makros, Schnelleinstellungs-Kachel
„Automationen“ zum Pausieren, Protokoll mit Fehlermeldungen.

## Akku

- Keine Dauerabfragen – alles reagiert auf Signale von Android.
- Uhrzeit/Intervall laufen über den System-Wecker (`AlarmManager`).
- Der Hintergrund-Dienst startet **nur**, wenn ein aktives Makro Systemereignisse braucht,
  und registriert nur die nötigen Empfänger. Kein GPS, keine Sensoren im Dauerbetrieb.

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
