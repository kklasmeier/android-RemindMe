# RemindMe — AI-Powered Reminder App
## Functional Specification

**Version:** 1.3
**Date:** September 12, 2026
**Platform:** Native Android (Kotlin)
**Author:** Kevin · Phoenix Team
**Changes from v1.2:** Location stack switched off Google Maps SDK and Places API. Map display uses MapLibre + OpenStreetMap tiles (no Maps API key). Place search uses Android `Geocoder` first, Nominatim fallback (submit-only; no remote autocomplete). `SavedPlace.googlePlaceId` replaced with OSM identifiers. Play Services Geofencing is unchanged. New architecture decision #9.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Tech Stack](#2-tech-stack)
3. [Architecture](#3-architecture)
4. [Feature Specification](#4-feature-specification)
5. [Data Model](#5-data-model)
6. [UI & Design System](#6-ui--design-system)
7. [Screen Reference](#7-screen-reference)
   - 7.1 [Onboarding Flow](#71-onboarding-flow)
   - 7.2 [Main App Tabs](#72-main-app-tabs)
   - 7.3 [Voice Input Flow](#73-voice-input-flow)
   - 7.4 [Location Picker Flow](#74-location-picker-flow)
   - 7.5 [Alerts & Notifications](#75-alerts--notifications)
   - 7.6 [Reminder Detail & Edit](#76-reminder-detail--edit)
8. [Build Phases](#8-build-phases)
9. [Resolved Architecture Decisions](#9-resolved-architecture-decisions)

---

## 1. Overview

**RemindMe** is a voice-first, AI-powered Android reminder app with a dark glassmorphism UI. Users create reminders by speaking naturally — Claude AI parses the intent into structured data. Reminders can be time-based or location-based (geofence triggered). Recurring is an orthogonal modifier on either base type. Alerts are configurable per reminder: full-screen takeover, heads-up notification, lock screen display, and sound.

### Core Principles

- **Voice first** — speaking is always the fastest path to creating a reminder
- **AI proposes, human confirms** — AI does the heavy lifting but the user always reviews before saving
- **Per-reminder alert control** — every reminder has its own alert style configured at creation time
- **Local only** — all data stored on-device via Room (SQLite); no cloud sync in v1
- **No dead ends** — every AI result is editable; every location is verifiable on a map; AI failures fall back to a manual form pre-filled with the raw transcript

---

## 2. Tech Stack

| Layer | Choice | Notes |
|---|---|---|
| Language | Kotlin | |
| UI Framework | Jetpack Compose | Material 3 dark theme base |
| Local Database | Room (SQLite) | Single source of truth |
| Background Work | WorkManager + AlarmManager | WorkManager for inexact; AlarmManager for exact alarms |
| Geofencing | Google Play Services Geofencing API | Triggers on enter/exit/dwell — does not require a Maps API key |
| Speech Recognition | Android SpeechRecognizer API | On-device, real-time transcript |
| AI / NLP | Claude API (`claude-sonnet-4-6`) | Parses natural language → structured reminder |
| Location / Maps | MapLibre Native + OSM tiles | Map verification, pin, live geofence ring. No Google Maps SDK. |
| Place search | Android `Geocoder` + Nominatim | One-shot search (not per-keystroke). Device location used as bias. |
| Navigation | Jetpack Navigation Component | Single-activity, composable destinations |
| Notifications | NotificationManager + NotificationChannel | Full-screen intent for takeover alerts |
| DI | Hilt | |
| Architecture | MVVM + Repository pattern | |
| API Key Storage | EncryptedSharedPreferences | Key entered via QR scan or manual entry |
| QR Scanning | ML Kit Barcode Scanning | For API key onboarding via QR code |

---

## 3. Architecture

```
UI Layer (Compose Screens)
        ↕
ViewModel (StateFlow / UiState)
        ↕
Repository (single interface per domain)
        ↕
┌───────────────────────────────────────────┐
│  Room DB   │  AI Service  │  Geocoder / OSM │
└───────────────────────────────────────────┘
        ↕
WorkManager / AlarmManager / Geofence API
        ↕
NotificationManager (alerts fire here)
```

### Key Repositories

- `ReminderRepository` — CRUD for all reminders
- `LocationRepository` — saved places, geofence registration, place search (`Geocoder` then Nominatim)
- `VoiceRepository` — wraps SpeechRecognizer, streams transcript
- `AiRepository` — calls Claude API, returns parsed `ReminderDraft`; handles offline/error fallback
- `ApiKeyRepository` — reads/writes API key from EncryptedSharedPreferences

---

## 4. Feature Specification

### 4.1 Dashboard

Voice pill always visible at top. Header shows greeting, name, date, active count, and avatar. Stats strip shows 3 stat cards. Reminder list grouped Overdue → Due Today → Upcoming. Bottom nav with 5 tabs.

### 4.2 Voice Input Flow

Four states: Listening (State 1) → Editing/Timer Paused (State 1b) → AI Confirmation (State 2) → Full Edit (State 2b). AI failure shows error state and opens State 2b pre-filled with raw transcript as title only.

### 4.3 Location Picker Flow

Three screens: Location Search → Search Results → Map Verification. Invoked when user selects Location trigger type or speaks a location name in voice input. Remote place search runs on submit (voice result or keyboard Go), not as live autocomplete. Saved places filter locally as the user types.

### 4.4 Reminder Types

`baseType: BaseType { TIME, LOCATION }` — mutually exclusive. `isRecurring: Boolean` — orthogonal modifier available for both types. Stored as RRULE string.

### 4.5 Alert & Notification System

Four independently toggleable modes per reminder: Full-Screen Takeover, Heads-Up, Lock Screen, Sound. Three NotificationChannels: `remindme_fullscreen` (MAX), `remindme_headsup` (HIGH), `remindme_silent` (DEFAULT).

### 4.6 Snooze & Dismiss

Snooze delays current instance only — next occurrence of a recurring reminder always fires on schedule. Quick options: 5 min, 30 min, 1 hour. Custom duration input. Snooze duration recorded in `reminder_history.snoozeDurationMinutes`.

### 4.7 Recurring Reminders

Patterns: Daily, Weekdays, Weekends, Weekly, Monthly, Custom. Stored as RRULE. AI parses natural language recurrence. WorkManager reschedules after each completion. Editing recurring: "This occurrence" or "All future occurrences."

### 4.8 Settings

Default alert style, default snooze duration, auto-submit delay, default geofence radius, saved places management, all permission statuses with deep links, Claude API key management, app theme.

### 4.9 API Key Onboarding

First-launch flow. Primary: QR scan via ML Kit. Secondary: manual paste. Validates `sk-ant-` prefix + minimum length. Test API ping. Success stores in `EncryptedSharedPreferences`. "Set up later" skips; AI features prompt for key when invoked.

---

## 5. Data Model

### `reminders` table

```kotlin
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val baseType: BaseType,                   // TIME | LOCATION
    val isRecurring: Boolean,                 // orthogonal modifier
    val triggerTime: Long?,                   // epoch ms (TIME type)
    val locationId: Long?,                    // FK → saved_places (LOCATION type)
    val locationTrigger: LocationTrigger?,    // ARRIVING | LEAVING | EITHER
    val locationOffsetMinutes: Int,           // 0 = immediately
    val recurrenceRule: String?,              // RRULE string
    val alertFullScreen: Boolean,
    val alertSound: Boolean,
    val alertHeadsUp: Boolean,
    val alertLockScreen: Boolean,
    val status: ReminderStatus,              // ACTIVE | SNOOZED | COMPLETED | DISMISSED
    val snoozedUntil: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

enum class BaseType { TIME, LOCATION }
enum class LocationTrigger { ARRIVING, LEAVING, EITHER }
enum class ReminderStatus { ACTIVE, SNOOZED | COMPLETED | DISMISSED }
```

### `saved_places` table

```kotlin
@Entity(tableName = "saved_places")
data class SavedPlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val displayName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val osmId: Long?,                          // OSM node/way/relation id when known
    val osmType: String?,                     // NODE | WAY | RELATION; null if geocoded without OSM id
    val geofenceRadius: Float,
    val isSystemPlace: Boolean
)
```

### `reminder_history` table

```kotlin
@Entity(tableName = "reminder_history")
data class ReminderHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val firedAt: Long,
    val action: HistoryAction,               // COMPLETED | SNOOZED | DISMISSED
    val snoozeDurationMinutes: Int?,         // populated when action = SNOOZED
    val actionAt: Long
)

enum class HistoryAction { COMPLETED, SNOOZED, DISMISSED }
```

---

## 6. UI & Design System

### Color Palette

| Token | Hex | Use |
|---|---|---|
| `bg` | `#080b14` | Screen backgrounds |
| `surface` | `rgba(255,255,255,0.05)` | Card backgrounds |
| `glass` | `rgba(255,255,255,0.07)` | Elevated glass surfaces |
| `glass-border` | `rgba(255,255,255,0.10)` | Card borders |
| `accent` | `#7b6ef6` | Time-based, primary actions, AI elements |
| `accent-glow` | `rgba(123,110,246,0.4)` | Shadows on accent elements |
| `accent2` | `#3ecfcf` | Location-based, editing state |
| `accent2-glow` | `rgba(62,207,207,0.3)` | Shadows on teal elements |
| `warn` | `#f97316` | Recurring modifier badge, snoozed state |
| `danger` | `#ef4444` | Clear/delete actions, active recording stop |
| `success` | `#22c55e` | Completion, live transcript indicator |
| `text` | `#f0f2f8` | Primary text |
| `text-mid` | `rgba(240,242,248,0.70)` | Secondary text |
| `text-dim` | `rgba(240,242,248,0.45)` | Labels, hints, placeholders |

### Typography

| Use | Font | Weight | Size |
|---|---|---|---|
| Screen titles, stat numbers | Space Grotesk | 700 | 20–28sp |
| Body text | Inter | 400–500 | 13–15sp |
| Labels / chips | Inter | 600–700 | 10–12sp |

### Reminder Type Color Coding

| Type | Left border | Icon bg | Badge |
|---|---|---|---|
| Time-based | `#7b6ef6` purple | `rgba(123,110,246,0.15)` | — |
| Location-based | `#3ecfcf` teal | `rgba(62,207,207,0.12)` | — |
| Recurring modifier | purple or teal base | same | orange chip overlay |
| Completed/dismissed | dimmed | `rgba(255,255,255,0.05)` | — |

Recurring is an orange badge overlaid on the base type card — it does not change the left border color.

### Navigation Structure

```
Bottom Nav (5 tabs):
├── Today (Dashboard)         route: screen/dashboard
├── Upcoming                  route: screen/upcoming
├── [FAB — Voice Input]       route: screen/voice-input
├── Places                    route: screen/places
└── Settings                  route: screen/settings

Modal / Full-screen routes:
├── screen/api-key-setup
├── screen/voice-input → screen/voice-confirm → screen/reminder-edit
├── screen/location-picker → screen/location-results → screen/location-map-verify
├── screen/reminder-detail/{id}
└── BottomSheet: snooze-picker
```

### Shared Component Specs

**Voice Pill** (Dashboard top): Purple-tinted pill with animated mic orb. Tapping navigates to `screen/voice-input`. Always rendered above the stats strip.

**Bottom Nav**: 5 tabs. Center tab is the FAB mic (58dp circle, purple gradient, purple glow shadow). While recording, FAB turns red stop button. While in teal edit state, FAB turns teal checkmark.

**Reminder Card**: Glassmorphism surface with 4dp colored left border. Icon in a 36×36dp rounded-rect. Title 1-line truncated. Meta chips row. Two action buttons (snooze 😴, complete ✅) stacked right. Long press opens contextual menu.

**Glass Card Pattern**: `background: rgba(255,255,255,0.07)`, `border: 1px rgba(255,255,255,0.10)`, `borderRadius: 18dp`, `backdropBlur: 20dp`.

**Primary Button**: Full width, purple gradient (`#7b6ef6` → `#6357e0`), purple glow shadow, 15sp bold, 15dp vertical padding, 18dp radius.

**Ghost Button**: Full width, glass background, glass border, text-mid color, same radius.

**Text Button**: Full width, transparent, text-dim color, no border.

---

## 7. Screen Reference

All 24 screen states are implemented in `remindme-all-screens.html`. Each screen below includes route, layout description, all UI states, all interactions, and implementation notes for the build phase. L-1–L-3 copy in the HTML may still mention Google Maps; **this spec is the source of truth** for map/search behavior.

---

### 7.1 Onboarding Flow

---

#### Screen O-1: API Key Setup — Welcome
**Route:** `screen/api-key-setup`
**Trigger:** First launch with no API key stored; also reachable from Settings → Claude API Key.

**Layout:**
- Full-screen dark bg with radial purple ambient glow centered top
- App icon (72×72dp, rounded-rect, gradient border with bell emoji) centered, with purple glow shadow
- Title: "Welcome to RemindMe" (Space Grotesk 24sp bold, centered)
- Subtitle: Explanation of Claude AI dependency (Inter 13sp, text-mid, centered, 2-line max)
- 2-column option grid (equal width):
  - **Left — Scan QR Code** (primary, purple-tinted border): camera icon, label, sub-label "Use the desktop tool to generate your QR"
  - **Right — Enter Manually** (ghost): keyboard icon, label, sub-label "Paste or type your API key"
- "Set up later · AI features will be limited" text link centered below grid

**Interactions:**
| Action | Behavior |
|---|---|
| Tap Scan QR Code | Navigate to Screen O-2 (camera open) |
| Tap Enter Manually | Navigate to Screen O-3 (manual entry) |
| Tap Set up later | Skip onboarding; navigate to Dashboard; AI features show inline prompt when invoked |

**Implementation notes:**
- Check `ApiKeyRepository.hasKey()` on app launch. If false and never skipped, show this screen before Dashboard.
- Store skip state in SharedPreferences (`onboarding_skipped: Boolean`).
- No back navigation — this is the root of the onboarding graph.

---

#### Screen O-2: API Key Setup — QR Scanner
**Route:** `screen/api-key-setup/scan`

**Layout:**
- Back arrow + screen title "Scan API Key QR"
- Camera viewfinder takes ~55% of screen height; dark bg with purple corner bracket overlays showing the scan target zone
- "Point camera at your QR code · Auto-detects" hint text below viewfinder (centered, text-dim)
- Divider row: horizontal line / "or enter manually" / horizontal line
- Manual entry text input (`sk-ant-api03-…` placeholder, monospace font)
- "Set up later" text link at bottom

**States:**
- **Scanning:** Camera live, corner brackets purple, hint text visible
- **Detected:** Brief flash animation; auto-advances to validation without user tap
- **Error:** Red banner below viewfinder: "Couldn't read QR code — try again or enter manually"

**Interactions:**
| Action | Behavior |
|---|---|
| QR detected by ML Kit | Extract string value; pass to validation flow (Screen O-3 logic) |
| Tap manual input | Keyboard opens; user can paste/type; tapping "Go" or submitting runs validation |
| Tap Set up later | Skip |

**Implementation notes:**
- Use ML Kit `BarcodeScanning` with `Barcode.FORMAT_QR_CODE`.
- The QR code encodes the raw API key string only — no envelope or JSON. Read `barcode.rawValue` directly.
- Request `CAMERA` permission before showing this screen; if denied, show explanation and offer manual entry.

---

#### Screen O-3: API Key Setup — Manual Entry / Key Entered
**Route:** `screen/api-key-setup/manual`

**Layout:**
- Back arrow + "Enter API Key"
- Field label "Claude API Key"
- Text input (monospace, `sk-ant-api03-…` placeholder, masking with `•` by default, show/hide toggle)
- Live validation hint line below input:
  - Empty: no hint
  - Typing, invalid prefix: "⚠ Claude API keys start with sk-ant- …" (orange)
  - Valid format: "✓ Key looks valid · N characters" (green)
- Security note card: "🔒 Your key is stored securely on-device in Android's encrypted storage. It is never transmitted to our servers."
- Primary button: "Verify & Connect" (disabled until format valid)
- "Set up later" text link

**States:**
- **Empty:** Button disabled
- **Typing / invalid:** Orange warning hint; button disabled
- **Valid format:** Green hint; button enabled
- **Verifying:** Button shows loading spinner; input locked
- **API error:** Inline error below button: "Couldn't connect — check your key and try again"
- **Success:** Navigate to Screen O-4

**Implementation notes:**
- Validation: `key.startsWith("sk-ant-") && key.length > 20`
- On "Verify & Connect": call `AiRepository.ping()` — a minimal Claude API request (e.g. 1-token completion). If HTTP 200, store key. If 401/403, show invalid key error. If network error, show connectivity error.
- Store with `EncryptedSharedPreferences` using `AES256_GCM` scheme.
- Use `KeyboardOptions(keyboardType = KeyboardType.Password)` with `VisualTransformation` toggle.

---

#### Screen O-4: API Key Setup — Success
**Route:** `screen/api-key-setup/success`

**Layout:**
- Large centered success icon (80×80dp circle, green border and bg, ✅ emoji, green glow shadow)
- Title: "You're all set!" (Space Grotesk 24sp bold, centered)
- Subtitle: Encouraging copy explaining what the user can do
- Example phrases card (green-tinted glass card):
  - Section label "What you can say"
  - 3 example phrases in text-mid, line-height 1.8
- Primary button: "Go to Dashboard"

**Interactions:**
| Action | Behavior |
|---|---|
| Tap Go to Dashboard | Navigate to `screen/dashboard`; clear onboarding back stack |

**Implementation notes:**
- This screen has no back navigation — clear the entire onboarding back stack on arrival.
- Mark onboarding complete in SharedPreferences (`onboarding_complete: Boolean`).

---

### 7.2 Main App Tabs

---

#### Screen T-1: Dashboard (Today Tab)
**Route:** `screen/dashboard`

**Layout (top to bottom):**
1. Status bar
2. Dynamic Island / notch
3. **Header block** (horizontal): Left = greeting ("Good morning,"), name (Space Grotesk 22sp bold), date + active count (text-dim); Right = avatar chip (40dp circle, gradient)
4. **Voice pill** (full width, purple-tinted): Animated mic orb left + "What do you need to remember?" label + "Tap to speak" hint right
5. **Stats strip** (3-column grid, equal width):
   - Due Today (purple accent number)
   - Location-based (teal accent number)
   - Snoozed (orange accent number)
6. **Reminder list** — scrollable, grouped sections:
   - **Overdue** section (danger color label ⚠): cards with red-tinted urgency
   - **Today** section: active cards
   - **Upcoming** section: dimmed cards (lighter opacity)
7. **Bottom Nav**

**Reminder Card anatomy:**
- 4dp left border (purple = TIME, teal = LOCATION)
- 36×36dp icon in rounded-rect bg
- Title: 14sp semibold, 1-line ellipsis
- Meta chips row: trigger chip (time or location, accent-colored) + recurring badge (orange, if applicable) + snoozed badge (orange, if snoozed)
- Action buttons (right, stacked): 😴 snooze, ✅ complete — each 30×30dp rounded-rect

**States:**
- **Overdue card:** Same layout but section label is danger-colored; card itself unchanged
- **Snoozed card:** Orange "😴 Snoozed" chip in meta row; snooze time shown
- **Completed card:** Full card at 45% opacity; moved to Completed section (visible on pull-down or tab)

**Interactions:**
| Action | Behavior |
|---|---|
| Tap voice pill | Navigate to `screen/voice-input` |
| Tap FAB mic | Navigate to `screen/voice-input` |
| Tap reminder card | Navigate to `screen/reminder-detail/{id}` |
| Tap ✅ | Mark reminder complete in DB; animate card to completed section |
| Tap 😴 | Open snooze picker bottom sheet for that reminder |
| Long press card | Show contextual menu: Edit, Snooze, Delete, Duplicate |
| Pull to refresh | Re-evaluate upcoming triggers and refresh list |

**Implementation notes:**
- `DashboardViewModel` exposes `StateFlow<DashboardUiState>` with grouped reminder lists.
- Group logic: `status == ACTIVE && triggerTime < now` → Overdue; `triggerTime.date == today` → Today; else Upcoming.
- Animate completed cards using `AnimatedVisibility` with fade + slide.
- Voice pill mic orb pulses via `InfiniteTransition` with `animateFloat` on scale.

---

#### Screen T-2: Upcoming Tab
**Route:** `screen/upcoming`

**Layout:**
- Screen title "Upcoming" (Space Grotesk 22sp bold)
- Section label "This Week" (then "Next Week", etc.)
- **Timeline list**: vertical timeline with dot + line column left, card right
  - Dot color: purple for TIME, teal for LOCATION
  - Line: thin vertical connector between dots (glass-border color)
  - Card: glass surface, trigger time/date (text-dim, 10sp), title (14sp semibold), sub-line (type + recurring badge if applicable)
- Recurring reminders show next single occurrence only (not all future instances)

**Interactions:**
| Action | Behavior |
|---|---|
| Tap timeline card | Navigate to `screen/reminder-detail/{id}` |
| Tap FAB mic | Navigate to `screen/voice-input` |

**Implementation notes:**
- Query: `status == ACTIVE` ordered by next trigger time ascending.
- For recurring reminders, compute next occurrence from RRULE using a library (e.g. `ical4j` or `biweekly`). Show that single date, not all instances.
- Group by calendar week.

---

#### Screen T-3: Places Tab
**Route:** `screen/places`

**Layout:**
- Screen title "Saved Places"
- Subtitle "Locations that trigger your reminders" (text-dim)
- **Places list**: each item is a glass card row:
  - 42×42dp icon in rounded-rect bg (color varies by place type)
  - Place name (14sp semibold)
  - Address (11sp, text-dim)
  - Active reminder count (accent2 color, 10sp semibold)
  - Chevron right
- **"+ Add a new place" button**: dashed teal border, full-width, below list

**Interactions:**
| Action | Behavior |
|---|---|
| Tap place row | Open place detail (edit label, view geofence, see linked reminders) |
| Long press row | Contextual menu: Edit label, Delete |
| Tap Add a new place | Navigate to `screen/location-picker` in "save place" mode |

**Implementation notes:**
- System places (Home, Work) are shown first, flagged by `isSystemPlace = true`; not deletable.
- Reminder count from `ReminderRepository.countActiveByLocationId(placeId)`.

---

#### Screen T-4: Settings
**Route:** `screen/settings`

**Layout:**
Grouped settings list (no bottom nav tab is "active" — Settings tab is active):

**AI section:**
- Claude API Key row: masked key suffix + "Connected" status; tap → `screen/api-key-setup`

**Defaults section:**
- Default Alert Style: multi-value summary; tap → bottom sheet picker
- Default Snooze Duration: single value; tap → picker
- Auto-submit Delay: single value (2/3/5 seconds); tap → picker
- Default Geofence Radius: single value; tap → picker

**Permissions section** (each row shows a status badge):
- Notifications: `Granted` (green) / `Denied` (red) — tap → system settings deep link
- Location (Precise): `Granted` / `Denied` — tap → system settings
- Background Location: `Granted` / `Denied` — tap → system settings
- Exact Alarms (Android 12+ only, hidden on < API 31): `Granted` / `Denied` — tap → `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`

**App section:**
- Theme: "Dark" (v1 only, not tappable)
- About RemindMe: version number; tap → about screen

**Permission badge colors:**
- Green (`#22c55e` bg tint): Granted
- Orange (`#f97316` bg tint): Granted but degraded (background location granted but precise denied)
- Red (`#ef4444` bg tint): Denied — reminder functionality impaired

**Implementation notes:**
- Check all permissions on every `onResume` so statuses refresh after user returns from system settings.
- Use `ActivityResultContracts.RequestPermission` for runtime requests.
- Exact alarm permission: `AlarmManager.canScheduleExactAlarms()` on API 31+. Show this row only on API ≥ 31.
- Hide Settings bottom nav "active" indicator while on nested routes (API key setup, etc.).

---

### 7.3 Voice Input Flow

---

#### Screen V-1: Voice Input — Listening (State 1)
**Route:** `screen/voice-input`

**Layout (top to bottom):**
1. Back arrow + "New Reminder" title
2. **Mic orb** (80dp circle, purple→teal gradient, purple ripple animation): centered, animates continuously while recording
3. State label: "Listening…" (purple accent)
4. Sub-label: "Speak naturally · tap text to edit" (text-dim)
5. **Waveform visualizer**: row of ~18 bars, heights animate with audio amplitude via `SpeechRecognizer` RMS callbacks; bars are purple→teal gradient
6. **Transcript textarea** (full-width):
   - Purple-tinted border and background
   - Purple outer glow shadow (`box-shadow: 0 0 0 3px rgba(123,110,246,0.10)`)
   - Updates in real-time as `SpeechRecognizer` fires `onPartialResults`
   - Editable by user at any time
   - Tapping field immediately pauses the countdown timer
   - ✕ clear button (absolute positioned top-right of textarea, red circle): clears transcript and returns to listening without closing screen
7. **Transcript hint line**: green dot (pulsing) + "Tap text to edit · ✕ to clear and re-speak"
8. **Countdown ring** (visible after speech pause):
   - 32dp circle with progress arc (purple, animates 3→0 seconds)
   - Number in center (3, 2, 1)
   - Explanatory text: "Auto-creating in 3 seconds after you stop. Tap text above to pause the timer."
   - Hidden while user is editing transcript
9. **"⏹ Done — Create Reminder Now" button**: purple-tinted ghost; skips countdown and submits immediately
10. **"Cancel" text link**: dismisses screen, nothing saved
11. **Bottom nav**: FAB is red stop button ⏹ with red glow

**States:**
- **Initial / empty transcript:** Orb pulses, waveform animates, transcript empty, countdown hidden, Done button present
- **Speech detected:** Transcript updates live via `onPartialResults`
- **Speech paused:** Countdown ring appears and counts down from 3
- **Countdown complete:** Auto-submit; navigate to State 2 loading
- **Manual submit:** User taps Done button or red FAB; navigate to State 2 loading

**Implementation notes:**
- `SpeechRecognizer.startListening()` with `EXTRA_PARTIAL_RESULTS = true`.
- Detect speech pause via `onEndOfSpeech()` callback → start 3-second coroutine timer.
- Timer cancels if `onReadyForSpeech()` fires again (user spoke again).
- Timer pauses when `TextFieldFocusChanged(focused = true)`; resumes on `focused = false`.
- Waveform: `onRmsChanged(rmsdB)` → map to bar height with `(rmsdB + 2) / 12` clamped 0..1, multiply by max bar height (42dp).
- ✕ button clears `transcriptState`, calls `recognizer.cancel()` then `recognizer.startListening()` again.
- FAB state: `LaunchedEffect(isRecording)` → toggle FAB between mic (purple) and stop (red).

---

#### Screen V-2: Voice Input — Editing / Timer Paused (State 1b)
**Route:** `screen/voice-input` (same screen, different `VoiceUiState`)

**Layout:** Same as V-1 with these changes:
- **Orb**: teal gradient (`#3ecfcf` → `#2fa8a8`), no pulse animation, shows ✏️ emoji, teal glow
- **State label**: "Editing — timer paused" (teal accent color)
- **Sub-label**: "Fix anything, then tap Done" (text-dim)
- **Waveform**: visible but dimmed to 30% opacity (inactive)
- **Transcript textarea**: teal border and teal glow shadow instead of purple; cursor active
- **Transcript hint**: teal dot (not pulsing) + "Timer paused · ✕ clears everything · tap outside to resume"
- **Countdown ring**: hidden (replaced by blank spacer of same height, 58dp, to prevent layout jump)
- **Done button**: "✅ Done Editing — Create Reminder" with teal tint
- **FAB**: teal checkmark ✅ with teal glow (not red stop)

**Transition back to State 1:**
- User taps outside transcript textarea → `FocusManager.clearFocus()` → countdown resumes from 3, colors shift back to purple, orb resumes pulse

**Implementation notes:**
- Implement as a single `VoiceScreen` composable with `uiState: VoiceUiState` sealed class (`Listening`, `Editing`, `Submitting`).
- Use `Animatable` for color transitions between purple and teal states.
- The 58dp spacer where the countdown ring would be prevents layout shift when toggling.
- Waveform bars use `alpha` animation: `1.0f` in Listening, `0.3f` in Editing, animated with `animateFloatAsState`.

---

#### Screen V-3: Voice Input — AI Parsing / Loading
**Route:** `screen/voice-confirm` (loading state)

**Layout:**
- Confirm icon: ⚡ emoji in purple glass rounded-rect (56dp), with ripple animation to indicate activity
- Title: "Parsing your reminder…" (Space Grotesk 18sp bold)
- Sub: "Claude AI is processing your request" (text-dim)
- "You said" block: transcript displayed verbatim in italics (text-mid)
- **Skeleton cards** (shimmer animation):
  - Large skeleton card (~140dp height) where the parsed card will appear
  - Smaller skeleton (~60dp) where alert chips will appear
- Two skeleton buttons below (matching primary + ghost heights)

**Implementation notes:**
- Shimmer: use `InfiniteTransition` animating a gradient sweep across skeleton shapes.
- This state shows while `AiRepository.parse(transcript)` is in flight.
- Minimum display time: 600ms (avoid flash if API returns very fast).
- On success → transition to V-4. On error → transition to V-5.

---

#### Screen V-4: Voice Input — AI Confirmation (State 2)
**Route:** `screen/voice-confirm`

**Layout:**
- Confirm icon: 🤖 emoji in purple glass rounded-rect (56dp), static
- Title: "Does this look right?" (Space Grotesk 18sp bold)
- Sub: "AI parsed your reminder — review before saving" (text-dim)
- **"You said" block**: glass surface, mic icon left, "YOU SAID" label (uppercase, text-dim, 10sp), transcript in italics below
- **Parsed card** (purple-tinted glass, purple border):
  - Header row: "⚡ AI UNDERSTOOD THIS AS" (purple, 11sp, uppercase)
  - Data rows (4 rows): Task | Trigger | Offset | Repeat
    - Each row: key label (10sp uppercase text-dim, min-width 66dp) + value (14sp, text) + "Edit" chip (teal, right-aligned)
    - Tapping "Edit" chip makes that row's value inline-editable (replaces value text with text input, teal border)
- **Alert chips row**:
  - Label "HOW IT'LL ALERT YOU" (10sp uppercase, text-dim)
  - Chips: Full-screen, Sound, Lock screen, Heads-up — each toggleable on/off
  - ON chip: teal bg tint, teal border, teal text
  - OFF chip: glass bg, glass border, text-dim
- **Three action buttons** (stacked):
  - "✅ Save Reminder" — primary purple button
  - "✏️ Edit Full Details" — ghost button → navigates to V-6
  - "🎙️ Say it again" — text button → clears transcript, returns to V-1

**Implementation notes:**
- `ReminderDraft` model: `title: String, baseType: BaseType, triggerTime: Long?, locationName: String?, locationOffsetMinutes: Int, isRecurring: Boolean, recurrenceRule: String?, alertFullScreen: Boolean, alertSound: Boolean, alertHeadsUp: Boolean, alertLockScreen: Boolean`
- Inline edit: `EditableRow` composable — `isEditing: Boolean` state per row, toggled by Edit chip tap.
- Alert chips: `LazyRow` or `FlowRow` of toggleable `FilterChip` composables.
- "Save Reminder": maps `ReminderDraft` → `Reminder`, inserts via `ReminderRepository`, schedules alarm/geofence, pops back to Dashboard.
  - If `baseType == LOCATION` and there is no confirmed `SavedPlace` yet, do **not** invent coordinates. Open the location picker with `locationName` as the search query, run one search, and require L-2/L-3 confirmation before insert + geofence.
- "Edit Full Details": passes `ReminderDraft` as navigation argument to V-6.

---

#### Screen V-5: Voice Input — AI Failure
**Route:** `screen/voice-confirm` (error state)

**Layout:**
- Confirm icon: ⚠️ in red-tinted glass rounded-rect (56dp), red glow shadow
- Title: "Couldn't reach AI" (Space Grotesk 18sp bold)
- Sub: "Network error · Edit manually to save your reminder" (text-dim)
- "You said" block: same as V-4 — shows raw transcript
- **Error banner** (red-tinted glass card):
  - 📵 icon left
  - "AI parsing failed" label (red, 13sp bold)
  - "Your transcript is saved above. Fill in the details manually and save normally." (text-mid, 11sp)
- **Three action buttons**:
  - "✏️ Edit Manually" — dimmed primary button (dark gradient, no glow) → opens V-6 with `rawTranscript` as title, all other fields empty
  - "🎙️ Try again" — ghost button → retries API call (returns to loading state)
  - "Cancel" — text button → pops back to Dashboard without saving anything

**Implementation notes:**
- AI failure does **not** persist any draft to DB. If user cancels from V-6 after this path, nothing is saved.
- "Edit Manually" passes `ReminderDraft(title = rawTranscript, all other fields = defaults)` to V-6.
- "Try again" re-calls `AiRepository.parse(transcript)` — returns to V-3 loading state.
- Distinguish error types in sub-label: `IOException` → "No network connection"; `HttpException(401)` → "API key invalid — check Settings"; timeout → "Request timed out".

---

#### Screen V-6: Full Edit Form — Time Trigger
**Route:** `screen/reminder-edit`
**Shown for:** Time-based reminder (baseType = TIME). Also shown from "Edit Full Details" in AI confirm flow.

**Layout:**
1. Back arrow + "Edit Reminder" title (or "New Reminder" if creating)
2. **"What do you need to do?" field** (full-width text input)
   - AI-populated fields have teal border + teal bg on first open
   - Teal highlight clears when user edits the field
3. **Trigger Type selector** (2-column equal grid):
   - "⏰ Time" — selected state: purple border + purple bg tint; unselected: glass
   - "📍 Location" — selected state: teal border + teal bg tint; unselected: glass
   - **Only TIME and LOCATION.** Recurring is NOT a trigger type here.
4. **Date & Time field** (shown when Time selected): text input, pre-filled from AI parse; tapping opens DateTimePicker bottom sheet
5. **Recurring toggle row** (full-width glass card):
   - Left: "🔁 Recurring" label + "Set a repeating pattern" sub-label
   - Right: toggle switch (off = gray, on = orange with knob slid right)
   - When toggled ON: row bg shifts to orange tint, border shifts to orange
6. **Recurrence pattern chips** (visible only when recurring = ON):
   - `FlowRow` of chips: Daily, Weekdays, Weekends, Weekly, Monthly, Custom…
   - Selected chip: orange bg tint, orange border, orange text
   - Unselected chip: glass bg, glass border, text-dim
   - "Custom…" opens a text input or a custom recurrence builder
7. **Alert Style grid** (2-column):
   - Full-screen, Sound, Heads-up, Lock screen — each a toggle button
   - ON: teal border, teal bg tint, teal dot indicator, full text
   - OFF: glass bg, glass border, dim dot, dim text
8. **"Save Reminder" primary button** at bottom (above bottom nav)

**Implementation notes:**
- AI-pre-filled teal highlight: track `aiFilledFields: Set<String>` in ViewModel; clear field's highlight on first user edit.
- Trigger type toggle: switching from TIME to LOCATION clears `triggerTime` and shows location picker field instead (see V-7/Location variant below).
- Recurring toggle: `isRecurring: Boolean` in `EditUiState`. Recurrence chips only composed when `isRecurring == true` (use `AnimatedVisibility`).
- "Custom…" chip: opens `RecurrenceBuilderBottomSheet` (v1 can be a free-text RRULE input with validation).
- DateTimePicker: use `MaterialDatePicker` + `MaterialTimePicker` from Material3.
- Save: validate required fields → insert/update `Reminder` → schedule alarm or geofence → navigate up.

---

#### Screen V-7: Full Edit Form — Location Trigger + Recurring ON
**Route:** `screen/reminder-edit` (same composable, different state)

**Layout:** Same as V-6 with these differences:
- **Trigger Type**: Location button selected (teal tint)
- **Date & Time field**: replaced by:
  - **Location field** (teal tint, pre-filled): shows selected place name + label ("💼 Work · Epsilon HQ"); tapping opens `screen/location-picker`
  - **Trigger On selector**: toggle button group — Arriving | Leaving | Either (single-select; selected = purple tint)
  - **Offset field**: text input, default "Immediately"; free text ("10 min after", etc.)
- **Recurring toggle**: ON (orange tint)
- **Recurrence chips**: "Weekdays" selected (orange)
- Everything else same as V-6

**Implementation notes:**
- Location field tap: launch `screen/location-picker` for result. Use `rememberLauncherForActivityResult` or Navigation backstack result to receive the selected `SavedPlace`.
- Offset text is stored as `locationOffsetMinutes: Int` — parse common phrases ("immediately" = 0, "10 min after" = 10, "half hour" = 30). For unrecognized text, prompt user with a number input.
- Recurring + Location: WorkManager re-registers the geofence after each trigger using the RRULE to determine if the next recurrence is valid.

---

### 7.4 Location Picker Flow

---

#### Screen L-1: Location Search
**Route:** `screen/location-picker`

**Layout:**
1. Back arrow + "Set Location" title
2. **Voice strip** (purple-tinted glass card, full-width):
   - Animated mic orb (28dp, purple gradient)
   - "Say a place" label (purple)
   - Sub-label example: "Kohl's on Tylersville Road" (text-dim)
   - Tapping opens inline voice capture; result passed to place search (`Geocoder` / Nominatim) then L-2
3. **Divider**: horizontal line / "or type" / horizontal line
4. **Search bar** (glass surface, magnifier icon):
   - Full-width text input, "Address, business, or landmark…" placeholder
   - Typing filters the **Saved Places** list locally (instant, no network)
   - Remote search runs on keyboard submit / search action — **not** per-keystroke (Nominatim public API forbids autocomplete)
   - After submit, results replace the Saved Places list and navigate to L-2
5. **"Saved Places" label** (10sp uppercase, text-dim)
6. **Saved Places list**: each item is a glass row:
   - 36×36dp icon in rounded-rect bg (color coded by place type)
   - Place name (14sp semibold) + address (11sp text-dim)
   - Chevron right
   - Tapping saved place → skips L-2, goes directly to L-3 with that place pre-selected

**Interactions:**
| Action | Behavior |
|---|---|
| Tap voice strip | Open inline voice capture; on result, run place search and navigate to L-2 |
| Type in search | Filter saved places locally; IME Search / Go runs remote search → L-2 |
| Tap saved place | Navigate directly to L-3 (skip search results) |
| Long press saved place | Contextual: Edit label, Delete |

**Implementation notes:**
- Place search pipeline (`LocationRepository.search(query, deviceLat, deviceLng)`):
  1. Android `Geocoder.getFromLocationName()` with a bounding box around the device (when `Geocoder.isPresent()`).
  2. If empty or thin results, Nominatim search (`q`, `format=jsonv2`, `limit=5`, `viewbox` + `bounded=1` around device, or `lat`/`lon` proximity).
  3. Deduplicate by rounded lat/lng; cache query → results in Room or memory with a long TTL.
- Nominatim: identify the app with a real User-Agent (`RemindMe/<version> (contact)`); max 1 request/second; never fire a request per keystroke.
- Voice capture on this screen uses a simplified inline recorder (no full V-1 UI) — capture, then one search call.

---

#### Screen L-2: Search Results
**Route:** `screen/location-results`

**Layout:**
1. Back arrow + "Set Location" title
2. **Search bar** (active state — purple border): shows what was said/typed; ✕ to clear and return to L-1
3. **Search hint**: "N matches · closest X.X mi away" (text-dim, 11sp)
4. **"⚡ AI ranked" badge**: purple-tinted pill (shown only when the query came from voice and Claude reordered the geocoder hits)
5. **Results list**:
   - **Top result** (best match — highlighted card): purple-tinted glass card with purple border; place name, address, distance/category; purple ✓ checkmark right
   - **Additional results** (plain rows): place icon, name, address, distance; no border; tapping any → L-3

**Interactions:**
| Action | Behavior |
|---|---|
| Tap any result | Navigate to L-3 (Map Verification) with that place |
| Tap ✕ in search bar | Return to L-1, clear query |

**Implementation notes:**
- Voice path: Claude receives the raw spoken location string plus the geocoder/Nominatim hit list (name, address, lat, lng, distance). It returns a ranked order and a short display label. Claude does **not** invent coordinates.
- Typed-submit path: show `Geocoder` / Nominatim order (distance-first); no extra Claude call unless the query was spoken.
- Distance from device via `Location.distanceBetween()`.
- Show L-2 only when results exist; if zero results, stay on L-1 with an inline error: "No places found — try a street name or city."
- If several chain stores match (e.g. multiple Krogers), keep all of them; the map on L-3 is how the user confirms the right one.

---

#### Screen L-3: Map Verification
**Route:** `screen/location-map-verify`

**Layout:**
1. **Map area** (top ~35% of screen, no padding):
   - Dark-styled MapLibre map using OpenStreetMap raster tiles (or a free dark OSM style). **© OpenStreetMap contributors** attribution visible on the map (required).
   - Dropped pin at selected place coordinates (custom purple pin with rounded top + point bottom)
   - **Dashed geofence radius ring**: circle centered on pin, dashed stroke (purple), radius updates live when radius chip changes
   - Zoom +/− controls (top-right of map, glass bg buttons)
   - Recenter button (below zoom controls)
   - **"🗺️ Open in Maps" button**: floating pill at bottom of map area (glass bg, purple border); launches the user's default maps app via `geo:` intent (Google Maps if installed, otherwise whatever handles the intent)
2. **Detail sheet** (scrollable, below map):
   - **Place detail card** (glass surface):
     - 42×42dp icon in rounded-rect
     - Place name (Space Grotesk 16sp bold)
     - Category + "AI ranked" indicator when applicable (text-dim, 11sp)
     - Address row (📍 icon + address text)
     - Hours row (🕐 icon + open/closed): **omit unless Nominatim/`opening_hours` is present** — do not show a fake open/closed state
     - Distance row (📏 icon + distance)
   - **Geofence Settings card** (purple-tinted glass):
     - "⚡ GEOFENCE SETTINGS" label (purple, 10sp uppercase)
     - **Trigger row**: "Trigger" label + toggle group: Arriving | Leaving | Either
     - **Offset row**: "Offset" label + text input (default "Immediately")
     - **Offset note** (italic, text-dim, 10sp): "Alert fires even if you leave before offset completes"
     - **Radius row**: "Radius" label (own line) + chip row below: 50m | 150m | 300m | 500m
       - Selected chip: teal bg, teal border, teal text
       - Selecting a chip updates the geofence ring on the map in real time
   - **"Wrong location?" banner** (orange-tinted glass): ⚠️ + text + underlined "Search again" link
   - **Action buttons** (stacked):
     - "✅ Use This Location" — primary purple button
     - "🔍 Search for a different place" — ghost button → returns to L-1

**Interactions:**
| Action | Behavior |
|---|---|
| Tap radius chip | Update chip selection + animate ring radius on map |
| Tap trigger toggle | Update single-select state |
| Edit offset field | Free text; parsed on save |
| Tap Open in Maps | Launch `Intent(ACTION_VIEW, geo:lat,lng?q=name)` |
| Tap Use This Location | Save place to `saved_places` if not already there; return `SavedPlace` result to edit form |
| Tap Search again / Wrong location | Return to L-1 |

**Implementation notes:**
- MapLibre `MapView` (or Compose wrapper). OSM tile usage requires attribution and a meaningful HTTP User-Agent; do not bulk-prefetch tiles.
- Map ring: update circle overlay radius reactively from selected chip value.
- Radius chip → ring update must be instant (no network call — local overlay).
- "Use This Location" checks `LocationRepository.findByOsmId(osmId, osmType)` when ids exist; else match by rounded lat/lng + label. Reuse if found; else insert `SavedPlace`. Return via Navigation result.
- Geofence radius is stored in `SavedPlace.geofenceRadius` and used when `GeofencingClient.addGeofences()` is called. Geofencing still uses Play Services, not OSM.

---

### 7.5 Alerts & Notifications

---

#### Screen A-1: Full-Screen Alert Takeover
**Trigger:** `Notification.Builder.setFullScreenIntent()` fires when AlarmManager or Geofence BroadcastReceiver triggers.
**Route:** `screen/fullscreen-alert` (launched via PendingIntent as full-screen activity)

**Layout:**
- Full-screen dark gradient bg (`#05070f` → `#0d0520`), radial purple glow from top center
- **Top section** (centered):
  - "REMINDME" app label (11sp, text-dim, uppercase, letter-spacing)
  - Current time in large display (Space Grotesk 72sp, white)
  - Current date (14sp, text-mid)
- **Middle section** (centered):
  - Icon (70×70dp rounded-rect, gradient bg, purple glow shadow)
  - "REMINDER · [TRIGGER TYPE]" label (11sp, text-dim, uppercase)
  - Reminder title (Space Grotesk 22sp bold, padded, centered, wraps if needed)
  - Trigger info: e.g. "📍 Arrived at Home · just now" or "⏰ Scheduled · 8:00 AM" (accent color, 12sp)
- **Bottom section** (actions):
  - "✅ Done" — primary purple button (full width)
  - **Snooze row** (3 equal buttons): 😴 5 min, 😴 30 min, 😴 1 hr — each glass bg with snooze icon and label + "Snooze" sub-label
  - "Dismiss" — ghost button (full width)

**Interactions:**
| Action | Behavior |
|---|---|
| Tap Done | Mark reminder complete; dismiss activity; write `COMPLETED` to `reminder_history` |
| Tap snooze option | Schedule new exact alarm for now + duration; update `status = SNOOZED, snoozedUntil`; write `SNOOZED` to `reminder_history` with `snoozeDurationMinutes`; dismiss activity |
| Tap Dismiss | Update `status = DISMISSED` (one-time) or leave ACTIVE (recurring); write `DISMISSED` to history; dismiss activity |
| Back press | Same as Dismiss |

**Implementation notes:**
- Declare as a separate `Activity` with `showWhenLocked = true`, `turnScreenOn = true`, `FLAG_KEEP_SCREEN_ON`.
- Pass `reminderId: Long` via the `PendingIntent` extras.
- Snooze schedules via `AlarmManager.setExactAndAllowWhileIdle()`.
- For recurring: Done and Dismiss both trigger WorkManager to schedule the next occurrence.

---

#### Screen A-2: Heads-Up Notification
**Trigger:** `NotificationManager` fires notification on `remindme_headsup` channel (IMPORTANCE_HIGH).
**Rendered by:** Android system (not a custom Activity). The mockup shows the expected appearance.

**Anatomy:**
- Notification card slides down from top of screen (system-rendered)
- App icon (18×18dp, rounded, gradient) + "RemindMe" app name + timestamp "now"
- Reminder icon (36×36dp, rounded-rect) left of content
- Title (14sp semibold) + sub-text (trigger info, 12sp text-dim)
- Action buttons row (system notification actions):
  - "✅ Done"
  - "😴 Snooze"
  - "Dismiss"

**Implementation notes:**
- `Notification.Builder` with:
  - `.setSmallIcon(R.drawable.ic_notification)`
  - `.setContentTitle(reminder.title)`
  - `.setContentText(triggerDescription)`
  - `.addAction(R.drawable.ic_done, "Done", donePendingIntent)`
  - `.addAction(R.drawable.ic_snooze, "Snooze", snoozePendingIntent)`
  - `.setAutoCancel(true)`
- Actions use `BroadcastReceiver` `PendingIntent` so they work without opening the app.
- "Snooze" from notification uses default snooze duration from Settings (not the picker sheet — no UI shown).

---

#### Screen A-3: Lock Screen Notifications
**Trigger:** Same notification as A-2, but device is locked.
**Rendered by:** Android system on lock screen.

**Anatomy:**
- Full-width blurred/frosted notification cards stacked on lock screen
- Each card: app icon + app name + timestamp / title / sub-text
- No action buttons on lock screen by default (swipe to expand may show them based on device settings)
- Lock screen time and date displayed above notification stack

**Implementation notes:**
- `.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)` — shows full content on lock screen without unlocking.
- Stack ordering: most recent notification on top.
- If Full-Screen alert is also enabled for this reminder, the Full-Screen activity (A-1) will override the lock screen and wake the device instead.

---

#### Screen A-4: Snooze Picker — Bottom Sheet
**Trigger:** User taps 😴 on a reminder card (Dashboard) or snooze button in notification.
**Route:** Bottom sheet modal over current screen.

**Layout:**
- **Handle bar** (40×4dp, glass, centered, top of sheet)
- **Title**: "Snooze reminder" (Space Grotesk 17sp bold)
- **Reminder identifier**: truncated title + trigger info (12sp, text-dim)
- **Quick snooze options** (3-column equal grid):
  - "5 min" / "30 min" / "1 hr" — each shows the resulting time below (11sp, text-dim)
  - Selected option: orange bg tint, orange border, orange text
- **"Custom duration" section**:
  - Label (10sp uppercase, text-dim)
  - Text input with current quick-select value pre-filled; fully editable
- **"😴 Snooze for [duration]" primary button**: label updates live as selection changes
- **"Cancel" ghost button**

**Interactions:**
| Action | Behavior |
|---|---|
| Tap quick option | Select it; update custom input; update button label |
| Edit custom input | Deselect quick options; update button label |
| Tap Snooze button | Apply snooze; dismiss sheet; update card on dashboard |
| Tap Cancel | Dismiss sheet; no change |
| Swipe down | Dismiss sheet |

**Implementation notes:**
- Implement as `ModalBottomSheet` (Material3 Compose).
- `snoozeDurationMinutes` computed from selection: 5, 30, 60, or parsed from custom input.
- On confirm: `ReminderRepository.snooze(id, durationMinutes)` → sets `status = SNOOZED`, `snoozedUntil = now + duration`; schedules new exact alarm; writes to `reminder_history`.

---

### 7.6 Reminder Detail & Edit

---

#### Screen D-1: Reminder Detail — Location + Recurring
**Route:** `screen/reminder-detail/{id}`
**Example:** A location-based, weekday-recurring reminder.

**Layout:**
1. Back arrow + "Reminder" title
2. **Type badge row**: "📍 Location" (teal pill) + "🔁 Recurring" (orange pill) side by side
3. **Title** (Space Grotesk 22sp bold, wraps)
4. **Meta rows** (each: icon + text):
   - 📍 Place name + address
   - 🚶 "Trigger: On arrival · Immediately" (or offset)
   - 🔁 "Repeats: Weekdays"
   - 📏 "Geofence: 150m radius"
5. **Alert chips row**: teal ON chips for active alert modes; dim OFF chips for inactive
6. **Action row** (3 equal buttons):
   - "✅ Complete" (green tint)
   - "✏️ Edit" (ghost)
   - "🗑️" delete icon (red tint)
7. **History section label** (10sp uppercase, text-dim) + divider
8. **History list**: each row has colored dot + action text + timestamp:
   - Green dot: Completed on arrival at [Place]
   - Orange dot: Snoozed N min
   - Dim dot: Dismissed

**Interactions:**
| Action | Behavior |
|---|---|
| Tap Complete | Mark instance complete; schedule next occurrence (recurring); animate; pop back |
| Tap Edit | Navigate to V-6/V-7 edit form pre-filled with this reminder's data |
| Tap 🗑️ | Confirmation dialog → delete reminder + cancel geofence + clear history |
| Tap alert chip | Toggle that alert mode inline (no navigation); auto-saves |

**Editing a recurring reminder:**
When user taps Edit on a recurring reminder, show a dialog:
- "Edit this occurrence only" → creates a one-time exception instance
- "Edit all future occurrences" → opens edit form; saving updates the master reminder and reschedules

**Implementation notes:**
- History list: `ReminderRepository.getHistoryForReminder(id)` ordered by `actionAt` DESC. Show last 10 entries.
- Complete on a recurring reminder: write `COMPLETED` to history + call `WorkManager` to compute and schedule next RRULE occurrence.
- Delete: `GeofencingClient.removeGeofences(listOf(reminderId.toString()))` before DB delete.

---

#### Screen D-2: Reminder Detail — Time, One-time
**Route:** `screen/reminder-detail/{id}`
**Example:** A time-based, one-time reminder that is currently snoozed.

**Layout:** Same structure as D-1 with these differences:
- **Type badge row**: "⏰ Time" (purple pill only — no recurring pill)
- **Meta rows**:
  - 📅 Date
  - ⏰ Time + "One-time" indicator
  - 😴 "Snoozed until [time]" — orange text (shown only when status = SNOOZED)
- No geofence radius row
- No recurring row
- History list is shorter (one-time reminders have less history)

**Snoozed state visual:**
- The 😴 meta row uses orange color for the snoozed-until time.
- The "✅ Complete" action button is still available and cancels the snooze + marks complete.

**Implementation notes:**
- Same `ReminderDetailViewModel` handles both D-1 and D-2 — the composable renders conditionally based on `reminder.baseType` and `reminder.isRecurring`.
- Completing a snoozed reminder: cancel the snoozed alarm (`AlarmManager.cancel()`), then mark complete.

---

## 8. Build Phases

### Phase 1 — Scaffold + Dashboard
- Android project setup (Kotlin, Compose, Hilt, Room)
- Room DB schema (`reminders`, `saved_places`, `reminder_history`) with `BaseType`/`isRecurring` model
- Repository layer + ViewModels
- Screen T-1 (Dashboard): stats strip, reminder card list, grouping logic, bottom nav
- Screen T-2 (Upcoming): timeline view
- Screen T-3 (Places): saved places list, add place stub
- Screen D-1 / D-2 (Reminder Detail): read-only view
- Basic CRUD: create/read/update/delete via Screen V-6 form (no AI, no voice yet)

### Phase 2 — Time-Based Notifications + Permissions
- AlarmManager integration for exact-time reminders
- **Android 12+ exact alarm permission check on first launch** — detect API ≥ 31; prompt with explanation screen; deep link to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`; status in Screen T-4 Settings
- NotificationChannel setup (3 channels)
- Screen A-1 (Full-Screen Alert): full-screen activity, Done/Snooze/Dismiss
- Screen A-2 (Heads-Up): notification builder with action intents
- Screen A-3 (Lock Screen): visibility flag
- Screen A-4 (Snooze Picker): bottom sheet, quick + custom options; `snoozeDurationMinutes` to history
- Screen T-4 (Settings): all permission rows with status badges
- BroadcastReceiver for boot re-registration of alarms

### Phase 3 — API Key Onboarding + Voice Input + AI
- Screen O-1 (Welcome): onboarding gate on first launch
- Screen O-2 (QR Scanner): ML Kit integration
- Screen O-3 (Manual Entry): validation + `EncryptedSharedPreferences` + API ping
- Screen O-4 (Success): clear onboarding stack
- Android SpeechRecognizer integration (real-time transcript, waveform RMS)
- Screen V-1 (Listening): orb, waveform, countdown timer, ✕ clear, state machine
- Screen V-2 (Editing): teal state, timer pause/resume on focus
- Screen V-3 (AI Loading): skeleton shimmer
- Screen V-4 (AI Confirm): parsed card, inline edit chips, alert chips, 3 action buttons
- Screen V-5 (AI Failure): error state, manual fallback path
- Screen V-6 / V-7 (Full Edit): trigger type grid (TIME/LOCATION only), recurring toggle + pattern chips, alert grid, AI pre-fill teal highlights

### Phase 4 — Location & Geofencing + Background Location Permissions
- **Background location permission flow**: two-step runtime request (`ACCESS_FINE_LOCATION` first, then `ACCESS_BACKGROUND_LOCATION`); explanation UI before each system prompt; graceful degradation if denied (location reminders disabled with explanation)
- Screen L-1 (Location Search): voice strip, submit-only remote search, local saved-place filter
- Screen L-2 (Search Results): ranked list, AI-ranked badge on voice path, highlighted best match
- Screen L-3 (Map Verify): dark MapLibre + OSM tiles, OSM attribution, pin, live-updating geofence ring, place detail card (hours only if known), geofence settings (trigger/offset/radius), wrong-place banner, "Open in Maps" geo intent
- Place search: `Geocoder` then Nominatim; cache; 1 req/s; no remote autocomplete
- Google Play Services Geofencing API integration (unchanged; no Maps API key)
- BroadcastReceiver for geofence events → trigger reminder or start offset AlarmManager timer
- Location-recurring: WorkManager re-registers geofence after each trigger per RRULE

### Phase 5 — Recurring Reminders + Polish
- RRULE parsing and WorkManager scheduling for time-recurring
- AI parsing of natural language recurrence patterns in voice input
- Recurring reminder edit: "This occurrence" vs "All future occurrences" dialog
- Completed + dismissed history in Screen D-1 / D-2 history section
- Screen T-3 (Places): full CRUD — edit label, delete, geofence radius per place
- Screen T-4 (Settings): full settings persistence, default pickers
- Notification permission onboarding prompt (Android 13+)
- Edge case hardening: airplane mode, device restart (BroadcastReceiver for `BOOT_COMPLETED`), time zone changes, Doze mode (geofences handled by Play Services — inherently Doze-safe)

---

## 9. Resolved Architecture Decisions

| # | Decision | Resolution |
|---|---|---|
| 1 | Trigger type overlap (e.g. location + recurring) | `TriggerType` replaced with `baseType: BaseType { TIME, LOCATION }` + orthogonal `isRecurring: Boolean` + `recurrenceRule`. Recurring is a modifier, not a type. Trigger type grid in UI has exactly 2 options (Time / Location). |
| 2 | Offline / Claude API failure | On failure: error toast + "Edit Manually" opens V-6/V-7 with raw transcript as title only. No draft state persisted. Cancel from V-6 after failure = nothing saved. |
| 3 | API key distribution | User-supplied key. Entry via QR scan (ML Kit, primary path, Screen O-2) or manual paste (Screen O-3). Stored in `EncryptedSharedPreferences`. Companion Python desktop tool (`qr_generator.py`) generates the QR from a pasted key locally. |
| 4 | Android 12+ exact alarm permission | Onboarding check in Phase 2. Detect API ≥ 31, show explanation screen, deep link to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. Shown in Settings (Screen T-4) with live status badge. |
| 5 | Location offset (timer vs secondary geofence) | Timer-based: AlarmManager exact alarm scheduled at geofence entry + offset minutes. Alert fires even if user leaves before timer expires. Documented in L-3 UI as an offset note. v1 intentional behavior. |
| 6 | Snooze on recurring | Snooze delays current instance only. Next occurrence always fires at its scheduled time. Confirmed in A-1 and A-4 UI — snooze options affect only the instance being displayed. |
| 7 | `reminder_history` missing snooze duration | `snoozeDurationMinutes: Int?` added to `ReminderHistory`. Populated when `action = SNOOZED`. Null for COMPLETED and DISMISSED. |
| 8 | Doze mode + background location | Geofences handled by Google Play Services — inherently Doze-safe. Phase 4 adds explicit `ACCESS_FINE_LOCATION` → `ACCESS_BACKGROUND_LOCATION` two-step permission flow with explanation UI before each system prompt. Status shown in T-4 Settings. |
| 9 | Google Maps SDK / Places API vs OSM | No Google Maps SDK or Places API in v1 (no Maps API key). **Display:** MapLibre + OSM tiles with required attribution. **Search:** Android `Geocoder` first (often high quality on Play devices, no Maps key), Nominatim fallback, submit-only. **Ranking:** Claude may reorder geocoder hits on the voice path; it must not invent lat/lng. **Geofences:** Play Services Geofencing (unchanged). **Confirm-on-map is required** before saving a location reminder. Chain-store ambiguity is resolved by the user on L-2/L-3. "Open in Maps" is a `geo:` intent, not an embedded Google map. |

---

*RemindMe Functional Spec v1.3 — Implementation Ready*
*Screen layouts still match `remindme-all-screens.html`; L-1–L-3 behavior is OSM/`Geocoder` as specified above (mock copy may still say Google Maps until the HTML is refreshed).*
