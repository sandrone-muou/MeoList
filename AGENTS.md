# Agent guide

MeoList is a Compose task/habit/timer app split into Gradle modules. UI code lives in
`feature:*`; business rules live in `domain` (pure Kotlin, JVM-testable); storage and backup
live in `data`; theme tokens, charts and shared components live in `core`; `app` owns routing,
the bottom navigation and Hilt setup.

## Commands

```bash
./gradlew assembleDebug              # debug APK (app/build/outputs/apk/debug/)
./gradlew test                       # all JVM unit tests
./gradlew :domain:test               # domain only
./gradlew lintDebug
./gradlew connectedDebugAndroidTest  # needs a running emulator
```

Debug application id is `com.meolist.app.debug`, entry activity `com.meolist.app.MainActivity`.

## UI rules

- Keep the neutral Mist Blue palette: low-saturation blue, white cards in light theme, no large
  saturated blocks, no green theme.
- Modules and charts fill the card width. No centred or underfilled grids.
- Top-level screens use a single-row title with right-side actions. Do not add a header strip.
- Tracker cards must have identical height while idle and running: no `animateContentSize`, no
  padding or spacing that depends on the running state, no removing the reserved duration block.
- Never clip or truncate text at 405dp width with 1.3x system font. Let layouts wrap or stack
  instead of adding `TextOverflow.Ellipsis`.
- Sizes come from `Meo.spacing` (8dp rhythm, dialogs and sheets) or `Meo.layout`
  (`core/theme/MeoLayout.kt`, card/chart metrics). `MeoTheme` provides `MeoLayout.Compact` when the
  window is narrower than 600dp; tablets and landscape keep the regular values.
- Font scale only drives reflow. Do not scale padding, tile sizes or touch targets with
  `fontScale`; a larger font must not shrink controls.
- Any new fixed height or minimum size belongs in `MeoLayout`, with a compact value, not as a
  literal `dp` inside a composable.

## Layout verification

Target device is a small phone: 1216x2640 at density 480 (about 405dp wide).

```bash
adb shell wm size 1216x2640 && adb shell wm density 480
adb shell settings put system font_scale 1.3   # relaunch the app after changing it
adb shell settings put system font_scale 1.0
adb shell wm size reset && adb shell wm density reset
```

- Screenshots: `D:\Code\meolist\ui.sh shot <name>` (also `dump` / `tap`); save checks under
  `MeoList/build/screens/small`.
- Never derive tap coordinates from the preview image. Read the real bounds from
  `uiautomator dump`; prefix `/sdcard/...` commands with `MSYS_NO_PATHCONV=1` in Git Bash.
- Verify light and dark themes, 405dp at 1.0x and 1.3x, and the tracker card bounds before and
  after starting a timer (they must match).
- Prefer pure decision functions (`summaryColumns`, `moreCardHeight`, `preferReminderTimeInput`,
  `donutDiameterForCenter`) plus JVM tests over screenshot assertions.

## Data

- Preserve user data across upgrades. Normalise at read time instead of rewriting stored rows,
  and keep old backups importable.
- No destructive migration for a display or calculation fix; add a test with a legacy value.
- State upgrade behaviour in the release notes when stored semantics change.

## Release

- Bump `versionCode` and `versionName` in `app/build.gradle.kts`, then update
  `settings_app_version` in `feature/settings/src/main/res/values/strings.xml` and
  `values-zh/strings.xml`. The in-app version is a string, not `BuildConfig`.
- README stays English-first with a Chinese mirror (`README-zh.md`). Release notes go to GitHub
  Releases: English first, Chinese below.
- Builds are manual; there is no CI. `assembleRelease` reads `keystore.properties`, which is
  gitignored. Never commit a keystore or print its passwords.
