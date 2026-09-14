# Contributing to RemindMe

Thanks for taking an interest. This is a small personal Android app; keep changes focused and easy to sideload-test.

## Development setup

1. Clone the repo and open it in Android Studio or Cursor.
2. Use JDK 21 (the Gradle wrapper is already in the repo).
3. Do not commit `local.properties`, `build/`, `.idea/`, or API keys.

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

On Windows: `.\gradlew.bat testDebugUnitTest assembleDebug lintDebug`.

## How to work

- Match the existing Kotlin / Compose style. Prefer small, obvious changes over new layers.
- Time and recurrence that the user said explicitly should stay in the **local parser**, not Claude.
- Location search should stay OSM / Nominatim / Geocoder — not Google Maps.
- Bump `version.properties` only when you intend to ship a new install (`deploy-debug.ps1` does this).

## Pull requests

1. Branch from `main`.
2. Include tests when you change parsing or recurrence.
3. Describe what you changed and how you verified it (Gradle tasks, device, screens).
4. Do not include secrets, keystores, or QR images of API keys.

Use the pull request template. Link related issues if there are any.

## Issues

Use the GitHub issue templates for bugs and feature ideas. Include:

- App version from Settings (for example `1.0.6 (7)`)
- Device and Android version
- Whether the reminder was time-based or location-based
- Steps to reproduce

## Code of conduct

Participation is covered by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
