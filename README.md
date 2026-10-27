<p align="center">
  <img src="docs/icon.png" width="132" height="132" alt="MeoList app icon">
</p>

# MeoList

English · [简体中文](README-zh.md)

MeoList is a local-first Android time-tracking to-do list. Tasks, activity timers, habits,
countdowns, statistics and a calendar live alongside a small farm that grows crops from your
focus time. Everything stays on the device.

[Download the latest release](https://github.com/sandrone-muou/MeoList/releases/latest) · Android 8.0+

## Features

- **Tasks**: lists / folders, subtasks, priorities, tags, due dates, recurring tasks, custom reminders when creating a task, search, trash
- **Activity timing**: custom activity types, one-tap start / stop, several timers at once, task linking, daily / weekly / monthly duration goals
- **Habits**: every day / selected weekdays / N times a week, streaks, weekly progress and a check-in calendar
- **Countdowns**: count down or up, optional yearly repeat
- **Statistics**: day / week / month / year views; month view switches between heatmap and bars; tracked time and completed tasks, with editable records
- **Calendar + quadrants**: month / week views; the agenda separates tasks, habits, countdowns and timer records; tag-bound quadrants place tasks by tag, with priority / due-date classification for untagged tasks
- **Timing farm**: focus time, completed tasks and habit check-ins become nutrients that grow crops, unlock harvests and achievements
- **Data**: local-only storage; JSON backup / restore and CSV export; timing, reminder, week-start and month-chart preferences

## Download

The latest signed APK is on the [Releases](https://github.com/sandrone-muou/MeoList/releases/latest) page.
Install it over an older version to keep your data. Uninstalling removes local data, so export a
JSON backup before switching devices or uninstalling.

## Language

The app follows your system language: Simplified Chinese and English are supported, and any other
system language falls back to English. You can override the choice under
**More → Settings → Appearance → Language**; the change applies immediately and is kept. Reminders
and timer notifications follow the same setting. Your task, habit and note text is never translated.
The month chart style is under **More → Settings → Appearance → Month view chart**.

## Build and test

Requires JDK 17 and the Android SDK (compileSdk 36). Android 8.0 (API 26) and later.

```bash
./gradlew assembleDebug              # debug APK
./gradlew testDebugUnitTest          # app unit tests
./gradlew :domain:test               # domain tests
./gradlew lintDebug                  # Android Lint
./gradlew connectedDebugAndroidTest  # device tests (backup round trip, frozen schema)
```

`assembleRelease` reads signing credentials from `keystore.properties`, which is ignored by Git.
Without it, the release build produces an unsigned APK.

## Tech stack

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · Coroutines/Flow

- `domain` — pure Kotlin business logic
- `data` — Room, DataStore, backup and CSV export
- `core` — theme, shared components and language
- `notification` — reminders, timer notifications and alarm scheduling
- `feature/*` — screens for tasks, timers, habits, countdowns, statistics, calendar, farm and settings
- `app` — wiring and navigation

## License

Copyright 2026 [sandrone-muou](https://github.com/sandrone-muou)

Released under the [Apache License 2.0](LICENSE).
