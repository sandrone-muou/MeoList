# MeoList

A local-first Android app for planning tasks, repeating habits, timing work and turning that work
into a small farm. Everything lives on the device: no account, no sync, no network calls. Backups
are a JSON file and tracked time can be exported as CSV.

- Platform: Android 8.0+ (minSdk 26, compile/target SDK 36), JDK 17.
- Stack: Kotlin 2.1, Jetpack Compose + Material 3, Hilt, Room, DataStore, Coroutines/Flow,
  kotlinx.serialization. Versions live in `gradle/libs.versions.toml`.
- Five top-level tabs: Tasks, Timer, Habits, Farm, More. More opens Calendar, Quadrant, Countdowns,
  Stats, Records, Achievements and Settings.
- Application id `com.meolist.app`; debug builds append `.debug` and can be installed side by side.

## Architecture

```
app ──────────► feature:* ──► core ──► domain
 ├──► data ──────────────────────────────┘  (implements domain interfaces)
 └──► notification ──► core, domain
```

- `domain` is plain Kotlin (no Android, no Compose). Interactors own the business rules, repositories
  are interfaces, `Clock`, `TransactionRunner` and `ApplicationScope` are injected so tests can
  substitute them.
- `data` implements the domain repositories on Room and DataStore and declares every Hilt binding in
  `data/di/DataModule.kt`.
- `core` holds the theme, design tokens, shared composables, charts, routes and locale helpers, and
  re-exports Compose through `api` dependencies.
- `feature:*` modules only depend on `core` (plus Hilt). They never depend on `data` or on another
  feature: they inject domain interfaces and navigate through `core/navigation/Routes.kt`.
- `notification` owns channels, alarm scheduling adapters, notifications and deep links.
- `app` wires everything: `MeoApplication`, `MainActivity`, `MeoAppRoot` (NavHost + bottom bar) and
  the More screen.

Screens call interactors, never repositories, so side effects (repeat instances, reminders,
farm nutrients, notification refresh) run from a single entry point. Multi-step writes go through
`TransactionRunner`; time always comes from `Clock`.

## Modules

| Module | Responsibility | Start here |
|---|---|---|
| `domain/task` | Tasks, lists/folders, tags, subtasks, repeat rules, quadrants, completion hooks | `TaskInteractor.kt`, `TaskModels.kt`, `RecurrenceCalculator.kt`, `TaskGrouping.kt` |
| `domain/tracker` | Activity types, running timers, time records, goals | `TimerInteractor.kt`, `TimerSync.kt`, `TrackerTime.kt` |
| `domain/habit` | Habit definitions, schedules, check-ins | `HabitInteractor.kt`, `HabitRules.kt` |
| `domain/countdown` | One-off and yearly countdowns | `CountdownInteractor.kt`, `CountdownMath.kt` |
| `domain/reminder` | Reminder computation and the single alarm chain | `ReminderScheduler.kt`, `ReminderModels.kt` |
| `domain/stats` | Period ranges and aggregations | `TimeStats.kt` (`TimeStatsCalculator`) |
| `domain/calendar` | Month/week grids and day contents | `CalendarRules.kt` |
| `domain/farm` | Nutrient ledger, crops, growth stages, achievements | `FarmInteractor.kt`, `FarmRules.kt`, `AchievementCatalog.kt` |
| `domain/backup` | Backup contract | `BackupService.kt` |
| `data/db` | 18 Room entities, DAOs, schema | `MeoDatabase.kt` |
| `data/repo` | Room-backed repositories, `RoomTransactionRunner` | `repo/TaskRepositories.kt`, … |
| `data/prefs` | `AppSettings` on DataStore (`meolist_prefs`) | `DataStoreAppPreferences.kt` |
| `data/backup` | JSON backup, restore and CSV export | `BackupStore.kt`, `BackupCodec.kt`, `RecordCsv.kt` |
| `core/theme` | Colour scheme, typography, `MeoSpacing`, `MeoShapes`, `MeoLayout` | `Theme.kt`, `Tokens.kt`, `MeoLayout.kt` |
| `core/ui` | Scaffold, cards, charts, pickers, icons, date/duration helpers | `components/MeoComponents.kt`, `charts/Charts.kt` |
| `core/locale` | In-app language and `localized()` context | `AppLanguage.kt` |
| `notification` | Channels (`reminders`, `timers`, `goals`), timer live-update notification, reminder alarms, `meolist://` deep links | `NotificationChannels.kt`, `AndroidTimerNotifier.kt`, `DeepLinks.kt` |
| `feature:tasks` | Task list, quick add/schedule, detail, search, quadrant, list management | `TasksScreen.kt`, `detail/TaskDetailScreen.kt` |
| `feature:tracker` | Timer grid, records, activity type and record editors | `TrackerScreen.kt`, `RecordsScreen.kt` |
| `feature:habits` | Habit list, detail, editor | `HabitsScreen.kt` |
| `feature:countdown` | Countdown list and editor | `CountdownsScreen.kt` |
| `feature:stats` | Day/week/month/year statistics, heatmap, bar chart | `StatsScreen.kt` |
| `feature:calendar` | Month grid, week timeline, day agenda | `CalendarScreen.kt` |
| `feature:farm` | Farm scene, plots, planting, achievements | `FarmScreen.kt`, `AchievementsScreen.kt`, `FarmArt.kt` |
| `feature:settings` | Preferences, backup/restore, CSV export | `SettingsScreen.kt` |
| `app` | Shell, routes, deep-link dispatch, More screen | `ui/MeoAppRoot.kt`, `ui/MoreScreen.kt` |

Each feature exposes a `<name>Graph(navController)` that `MeoAppRoot` mounts; routes and argument
helpers are declared only in `core/navigation/Routes.kt`.

## Data and storage

- Room database `meolist.db`, **version 1, frozen**. `exportSchema = true`; the schema is committed at
  `data/schemas/1.json` and guarded by `data/src/androidTest/.../FrozenSchemaTest.kt`. Changing an
  entity requires a version bump, a `Migration`, a new schema file and a migration test.
- Settings live in DataStore (`meolist_prefs`) behind `AppPreferences`. The in-app language is the
  exception: it is kept in SharedPreferences `meolist_ui` so `attachBaseContext` can read it
  synchronously before Hilt or DataStore exist.
- Backup: `BackupFile` JSON with `format = 1`, every field defaulted, unknown keys ignored, and the
  `format` field required so an arbitrary JSON file cannot wipe data. `BackupStore.restore` clears
  the tables, then re-inserts the file in one transaction, preserving ids and foreign links (parents
  before children).
- CSV export of time records uses a UTF-8 BOM for Excel.
- Compatibility rule: do not destroy user history. Prefer normalising on read — for example
  `HarvestRecord.absorbedNutrients` is `minOf(nutrients, crop.need)` computed in `FarmModels.kt`, so
  older rows keep their stored value and only the display is capped.

## Behaviour worth knowing

- **Timers**: a running timer stores only its start; stopping converts it into a `TimeRecord` in one
  transaction. Records shorter than `ignoreShortRecordSec` are dropped. `multipleTimers` decides
  whether starting a new timer stops the others; a list's `defaultActivityTypeId` or the type last
  used for a task picks the type without asking. Every mutation ends with `TimerSync`, which refreshes
  notifications and goal alarms.
- **Reminders**: one alarm chain only. `ReminderScheduler` recomputes a task's fire times, arms the
  earliest pending reminder and re-arms on fire; it also purges reminders of deleted owners.
- **Repeating tasks**: completing one keeps a done copy (`repeatOriginId`) and advances the task to
  its next date; `RepeatAdvance` holds the state needed for undo.
- **Farm**: records, task completions, habit check-ins and reached goals feed a nutrient ledger
  (`FarmInteractor` / `FarmHook`). A focus stretch caps at 240 points, manual records count half,
  plot limit grows 3 → 4 → 5 with harvests, growth has six art stages. `TaskCompletionHook` stops
  linked timers; `FarmHook` recomputes nutrients.
- **Notifications**: three channels, timers at low importance so ongoing updates stay silent, a
  deep link (`meolist://task/{id}`, `meolist://tracker`) on tap, and `NotificationStartup` resyncs on
  app start and on resume so granting permission later picks up timers already running.
- **Language**: the app ships `en` and `zh-CN`. An explicit choice overrides the system language,
  other system languages fall back to English. Strings live per module in `src/main/res/values` and
  `values-zh`; non-Compose code (channels, receivers) uses `Context.localized()`.

## UI system

- Colours: neutral "Mist Blue" scheme in `core/theme/Theme.kt`, white cards in light theme, dark
  theme follows the system, dynamic colour exists but stays off. Priority, medal and farm colours
  come from `MeoExtendedColors` (`Meo.colors`).
- Type scale: Material defaults with SemiBold headings (`Typography.kt`); durations use
  `TextStyle.tabular()`.
- Spacing: `Meo.spacing` is the 4/8/12/16/24 rhythm used by screens, dialogs and sheets.
  `Meo.layout` (`MeoLayout.kt`) holds card and chart metrics and switches to `Compact` when the
  window is narrower than 600dp. Any new fixed height or minimum size belongs there, with a compact
  value, not as a literal dp.
- Shell: `MeoScaffold` is the only scaffold — one pinned `TopAppBar`, `large` only swaps the title
  style. The bottom bar is shown only on the five top-level routes and carries the farm "ready"
  badge.
- Shared pieces: `SectionHeader`, `EmptyState`, `StatChip`, `StatTile`, `MeoProgressBar`,
  `ProgressRing`, `IconBadge`, `MeoListGroup`, `MeoListDivider`, and `DonutChart`,
  `StackedBarChart`, `DayTimeline` in `core/ui/charts/Charts.kt`.
- Design invariants:
  - Modules and charts fill the card width; no centred or underfilled grids.
  - Top-level pages keep a single-row title with right-side actions.
  - Tracker cards must have identical height while idle and running: no `animateContentSize`, no
    state-dependent padding, keep the measured duration block.
  - Never clip or ellipsize text at 405dp width with 1.3x system font; let layouts wrap or stack.
  - Font scale drives reflow only. Never scale padding, cells or touch targets by `fontScale`.

## Verification

Target device is a 6.3-inch phone: 1216x2640 at density 480 (about 405dp wide).

```bash
adb shell wm size 1216x2640 && adb shell wm density 480
adb shell settings put system font_scale 1.3    # relaunch the app after changing it
adb shell settings put system font_scale 1.0
adb shell wm size reset && adb shell wm density reset
```

- `D:\Code\meolist\ui.sh shot <name>` / `dump` / `tap` drives the emulator; save checks under
  `MeoList/build/screens/small`.
- Never derive tap coordinates from a preview image. Read bounds from `uiautomator dump`; prefix
  `/sdcard/...` commands with `MSYS_NO_PATHCONV=1` in Git Bash.
- Check light and dark themes and both 1.0x and 1.3x fonts. Compare tracker card bounds before and
  after starting a timer.

```bash
./gradlew assembleDebug              # debug APK (app/build/outputs/apk/debug/)
./gradlew test                       # all JVM unit tests
./gradlew lintDebug
./gradlew connectedDebugAndroidTest  # needs a running emulator
```

Test strategy: business rules are covered by JVM tests in `domain` with fakes and `FakeClock` under
`domain/src/test/.../testing/`. Layout decisions are extracted as pure functions and unit-tested
(`summaryColumns`, `moreCardHeight`, `preferReminderTimeInput`, `donutDiameterForCenter`) instead of
being asserted from screenshots. `data` and `notification` have instrumented tests for the frozen
schema, backup round trip and timer notification.

## Conventions

- Conventional commits (`feat:`, `fix:`, `docs:`, `chore(release):`). Code, comments and identifiers
  are English; user-facing text goes to resource files, always in both `values` and `values-zh`.
- No new dependency without editing `gradle/libs.versions.toml`; check the existing catalog first.
- ViewModels are Hilt-provided (`hiltViewModel()`), expose `StateFlow`, and screens collect with
  `collectAsStateWithLifecycle()`.
- Keep comments for non-obvious intent only; the codebase is otherwise comment-light.

## Release

- Bump `versionCode` and `versionName` in `app/build.gradle.kts`, then update `settings_app_version`
  in `feature/settings/src/main/res/values/strings.xml` and `values-zh/strings.xml`. The in-app
  version is a string, not `BuildConfig`.
- `assembleRelease` signs with `keystore.properties` (gitignored, key under the user profile); never
  commit a keystore or print its passwords. Verify with `aapt2 dump badging` and
  `apksigner verify --print-certs` before uploading.
- Copy the APK to `../MeoList-<version>.apk` and publish with
  `gh release create vX.Y.Z --target main --title "..." --notes-file - MeoList-X.Y.Z.apk`.
- Release notes: English first, `---`, then Chinese. Mention compatibility (migrations, backup
  format) in every release. README stays English-first with `README-zh.md` as the mirror.
- There is no CI. `main` is protected; a normal `git push` works, and if the GitHub IP is blocked
  fall back to
  `git -c http.sslBackend=schannel -c http.curloptResolve=github.com:443:140.82.112.3 push origin main`.
