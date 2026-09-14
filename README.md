# RemindMe

Voice-first Android reminders for time and place. Speak something like “remind me to buy milk when I get to Kroger,” confirm it, and RemindMe alerts you with a notification or full-screen alarm.

RemindMe is a personal sideloaded app, not a Play Store listing. Claude is used only to turn messy spoken language into a reminder title (and a schedule when the local parser cannot). Explicit times such as “tomorrow at 6:30am” are parsed on the device.

**Package:** `com.phoenix.remindme`  
**Current version:** 1.0.6 (7)  
**Min / target SDK:** Android 8.0 (API 26) / API 35

## Features

- **Voice create** — speak a reminder; edit the transcript before it is submitted
- **Manual create** — type the task, then pick time or location
- **Time reminders** — one-shot or repeating (daily, weekdays, weekly, monthly, custom)
- **Location reminders** — arriving, leaving, or either, with a geofence radius
- **Saved places** — Home, Work, and other named places
- **Active alerts** — snooze, dismiss, or mark done from the alert or the Active Reminders screen
- **Full-screen alerts** — alarm-style UI over the lock screen when Android allows it
- **Local-first schedule parsing** — times and recurrence stay deterministic whenever the phrase is explicit
- **Claude (optional)** — required for messy language and title cleanup; stored only on the device

## Screens

| Area | What it does |
| --- | --- |
| Today | Greeting, voice pill, overdue and due-today cards |
| Upcoming | Future reminders |
| Places | Saved places and add/edit |
| Settings | Name, Claude key, permissions, defaults, version |
| First run | API key (or skip), Home/Work, notification and battery permissions |

A clickable HTML prototype of the screens lives in [`remindme-all-screens.html`](remindme-all-screens.html). The product spec is [`remindme-functional-spec-v1.3.md`](remindme-functional-spec-v1.3.md).

## Requirements

- Windows, macOS, or Linux with [JDK 21](https://adoptium.net/)
- [Android SDK](https://developer.android.com/studio) / command-line tools (this repo uses the Gradle wrapper)
- A phone or emulator on Android 8.0+
- USB debugging to install from a computer
- A [Claude API key](https://console.anthropic.com/) for voice/AI create (`sk-ant-…`)

Maps use [OpenStreetMap](https://www.openstreetmap.org/copyright) tiles through MapLibre. There is no Google Maps SDK.

## Privacy

- Reminder data stays in a local Room database on the phone
- The Claude key is stored in `EncryptedSharedPreferences`, not in this repository
- Backup is disabled (`android:allowBackup="false"`)
- Do not commit QR images of API keys (`tools/remindme-api-key.png` is gitignored)

## Build

From the repo root:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Sideload deploy (version bump)

`deploy-debug.ps1` increments `version.properties`, builds debug, and installs on the default `adb` device:

```powershell
.\deploy-debug.ps1
```

Install the same APK on a second phone without bumping the version:

```bash
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds are signed with the Android debug key. That is fine for personal phones. A later Play-style **release** APK needs its own keystore and cannot update a debug install without uninstalling first.

## First-run on a new phone

1. Open RemindMe and add (or skip) a Claude key
2. Set Home; set or skip Work
3. Allow notifications (required)
4. Allow exact alarms, full-screen alerts, and **Unrestricted battery** if you want reliable background alerts
5. On Samsung, Settings → Apps → RemindMe → Battery → **Unrestricted** if alerts still delay

Each phone stores its own name (Settings → Name), API key, places, and reminders.

## Architecture

| Layer | Stack |
| --- | --- |
| UI | Jetpack Compose, Material 3, Navigation |
| DI | Hilt |
| Data | Room (`ReminderDatabase`) |
| Alerts | `AlarmManager.setAlarmClock`, notifications, `FullScreenAlertActivity` |
| Location | Play services location + geofences; MapLibre + OSM / Nominatim / Geocoder |
| AI | `AiRepository` → Anthropic Messages API (`claude-sonnet-4-6`) |
| Speech | Android `SpeechRecognizer` |

Package: `com.phoenix.remindme`.

```text
app/src/main/java/com/phoenix/remindme/
  MainActivity.kt          navigation, dashboard, settings, manual create
  ai/                      Claude client, local natural-language schedule
  alerts/                  alarms, notifications, receivers, full-screen UI
  data/                    Room, recurrence
  location/                geofences, place search
  ui/                      voice flow, location flow, view models
```

Version numbers live in [`version.properties`](version.properties) and are shown at the bottom of Settings.

## Permissions

| Permission | Why |
| --- | --- |
| Notifications | Show reminder alerts |
| Exact alarms | Fire at the chosen minute |
| Full-screen intent | Alarm-style overlay (optional) |
| Fine + background location | Geofences while the app is closed |
| Ignore battery optimizations | Reduce OEM sleep delays |
| Microphone | Voice create |
| Internet | Claude, OSM tiles, geocoding |
| Boot completed | Restore alarms and geofences after reboot |

## Tests

Unit tests cover local schedule parsing and recurrence:

```bash
./gradlew testDebugUnitTest
```

```text
app/src/test/java/com/phoenix/remindme/ai/NaturalLanguageScheduleTest.kt
app/src/test/java/com/phoenix/remindme/data/RecurrenceTest.kt
```

## Claude key QR (optional)

`tools/qr_generator.py` writes a local QR of a key you paste. The PNG is gitignored. Delete it after scanning.

```bash
pip install -r tools/requirements.txt
python tools/qr_generator.py
```

## Roadmap

See [`NEXT-IMPROVEMENTS.md`](NEXT-IMPROVEMENTS.md). Notable remaining items:

- Confirm swipe-away delivery vs Android Force Stop
- “Edit this occurrence only” for a recurring series
- Signed release APK
- Theme toggle
- Offline create when the local time parser already has enough

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Please read [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) and [SECURITY.md](SECURITY.md) before opening issues about vulnerabilities.

## License

[MIT](LICENSE) © 2026 Kevin Klasmeier

Map tiles and geocoding: © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright).
