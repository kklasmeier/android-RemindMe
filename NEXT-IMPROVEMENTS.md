# Next improvements

Priority is reliability and spoken-create accuracy first, then spec gaps.

## 1. Natural-language recurrence — implemented

Local parsing currently covers daily, weekdays, weekends, weekly, monthly, yearly, plus explicit times like `tomorrow at 6:30am`. Add deterministic support for:

- [x] Named weekdays: “every Tuesday”
- [x] Multiple weekdays: “every Saturday and Sunday”
- [x] Monthly by date: “on the 5th of every month”
- [x] Related: “every other week”, “first Monday of the month”

Also:

- [x] 24-hour times (“at 18:30”) and “noon / midnight”
- [x] Show a readable label for custom RRULEs (not “Does not repeat”)
- [x] Recurrence chips on review highlight the selected rule the same way Settings does
- [x] Recurrence engine honors `BYMONTHDAY` so monthly-by-date advances correctly
- [x] Manual create UI exposes weekday and monthly-date choices, not only presets

## 2. Background delivery — implemented; device behavior check remains

- [x] Re-register location geofences after boot / app update
- [x] Request notification + exact-alarm (and optional full-screen) permission during first-run
- [ ] Confirm swipe-away delivery on a physical device. Android Force stop intentionally blocks alarms until reopen.
- [x] Restore location reminders after timezone / time-set, same as time reminders

## 3. Spec / product gaps already in the app

- [x] Recurring **location** reminders re-arm after each acknowledged trigger
- “Edit this occurrence only” for a recurring series
- [x] “Every time I get to …” creates a repeating location reminder; “next time” remains one-shot
- [x] Prompt already-installed users through Home/Work onboarding if they missed it
- Place detail: edit label, linked reminders, delete confirmation

## 4. Create-flow polish

- [x] Keep AI for the **task title**; keep time/recurrence local whenever the phrase is explicit
- If parse is incomplete, land on the missing step instead of a wrong time

## 5. Later / nice-to-have

- Signed release APK / Play-style signing
- Theme toggle (in spec Settings)
- Airplane-mode / no-network create path that still handles explicit times without Claude
- Geofence restore in `RemindMeApplication` on cold start as a belt-and-suspenders to BootReceiver
