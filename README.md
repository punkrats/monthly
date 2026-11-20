# Monthly

An offline Android calendar with birthdays across time zones, a one-tap
cycle tracker and iCalendar import/export. No internet permission: all data
stays on the phone unless you export it.

## Features

- Month view (swipe between months), selected-day list, agenda and search
- Timed, all-day and multi-day events; colours, location, notes
- Repeat rules: daily, weekly on chosen days, monthly on a date or on the
  nth / last weekday, yearly; every n periods; until a date or n times;
  delete a single occurrence
- Time zones per event. A 10:00 New York meeting shows at 16:00 in Berlin
  and keeps its New York time across daylight-saving changes.
- Birthdays with optional birth year ("turns 34") and the friend's time
  zone. Each reminder fires on their clock or on yours, and the editor
  previews the next alert on both clocks.
- Reminders as notifications, exact to the minute, restored after reboot,
  time-zone change and app update
- Cycle tracking: the drop button in the bottom-left corner marks the
  selected day (today by default) as a period start in one tap, or clears
  it. On a day less than 15 days from a recorded start it asks whether to
  move that start or begin a new cycle.
- Forecast a year ahead from the average of the last 3 regular cycle
  lengths, or 28 days until one cycle is recorded. A recorded start shows
  a filled drop; an expected one a hollow drop and a dashed ring, with the
  days before it shaded light to strong. Lead days are adjustable.
- Regular cycles: at least 15 days and within 2/3 to 3/2 of your typical
  length (the median of your last 12 cycles). A forgotten start, which
  doubles a cycle, and a doubled tap are skipped; a late 40-day cycle
  among 28-day ones still counts.
- Import and export `.ics` (RFC 5545), including VTIMEZONE definitions.
  Cycle data is only exported when you tick the box. Opening an `.ics`
  file from a file manager or mail app offers an import.
- English and German, light and dark theme

## Install on a phone

1. Build `app/build/outputs/apk/release/app-release.apk` (see below).
2. Copy it to the phone and open it, allowing installs from that source,
   or connect via USB and run `adb install app-release.apk`.
3. Allow notifications when asked; reminders depend on them.

Release builds are signed with the key named in
`~/.gradle/gradle.properties`:

```properties
MONTHLY_KEYSTORE=/path/to/monthly-release.jks
MONTHLY_KEYSTORE_PASSWORD=...
MONTHLY_KEY_ALIAS=monthly
```

Without these properties Gradle builds `app-release-unsigned.apk`.
Android installs an update only when it carries the same signature, so
keep a backup of the keystore and its password.

## Build

Requirements: JDK 21 and the Android SDK (platform 37.2, build tools 36).

```sh
export JAVA_HOME=$(mise where java@temurin-21.0.13+9.0.LTS)
./gradlew testDebugUnitTest   # logic, iCalendar, storage, UI (Robolectric)
./gradlew lintDebug
./gradlew assembleRelease
```

`local.properties` points `sdk.dir` at the SDK (`~/Android/Sdk` here).

## How it works

- `model/` - `Event`, `Recurrence`, `Reminder`
- `logic/` - recurrence expansion, placing occurrences on device-local
  days, alert times, cycle forecast. Pure Kotlin, covered by JVM tests.
- `ics/` - iCalendar reader and writer, RRULE codec, VTIMEZONE generator
- `data/` - SQLite storage (events, cycle starts) and settings
- `alarm/` - one exact alarm armed for the next reminder; boot, clock and
  zone changes re-arm it
- `ui/` - Jetpack Compose screens

Reminder semantics:

- Timed events: minutes before the start instant.
- All-day events and birthdays: a day offset plus a time of day, on the
  event's zone ("their time") or the phone's zone ("my time"). A birthday
  in Tokyo with "on the day at 09:00, their time" fires at 01:00 in Berlin.

Cloud backup excludes the calendar database because it holds health data;
a phone-to-phone transfer keeps it. Export an `.ics` file for a backup.
