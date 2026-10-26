<p align="center">
  <img src="docs/icon.png" width="132" height="132" alt="MeoList app icon">
</p>

# MeoList

简体中文 · [English](#english)

MeoList 是一个本地优先的 Android「计时清单」应用：任务清单、活动计时、习惯打卡、倒数日、统计与日历，
再加上一个用专注时间浇灌作物的「计时农场」。

界面采用低饱和的雾蓝（Mist Blue）主题；应用图标是一只带对勾的线稿猫。

## 功能

- **任务清单**：清单 / 文件夹、子任务、优先级、标签、截止日期、重复任务、创建时设置提醒、搜索、回收站
- **活动计时**：自定义活动类型，一键开始 / 停止，多个计时可同时运行，可关联任务，支持日 / 周 / 月时长目标
- **习惯打卡**：每天 / 每周指定几天 / 每周 N 次，连续天数、周进度与打卡日历
- **倒数日 / 纪念日**：倒数或正数，支持每年重复
- **统计报表**：日 / 周 / 月 / 年视图；月视图支持热力图 / 直方图切换，展示计时分布与完成任务
- **日历 + 四象限**：月 / 周视图；日程区区分任务、习惯、倒数日与计时记录；四象限按重要 / 紧急自动分类
- **计时农场**：计时时长、完成任务、习惯打卡都会变成养分，让作物生长、收获并解锁成就
- **数据与设置**：纯本地存储；JSON 备份 / 恢复、CSV 导出；可调计时、提醒、每周起始日与月视图图表

## 语言

界面默认显示简体中文，可在「更多 → 设置 → 显示 → 语言」中切换为 English，选择会立即生效并保留。
提醒与计时通知同样跟随该设置；任务、习惯等用户数据不会被翻译。
月视图图表样式可在「更多 → 设置 → 显示 → 月视图图表」中切换。

## 构建

需要 JDK 17 与 Android SDK（compileSdk 36）。

```bash
./gradlew assembleDebug          # 构建调试包
./gradlew testDebugUnitTest      # 单元测试
./gradlew :domain:test           # 领域层测试
./gradlew lintDebug              # Android Lint
./gradlew connectedDebugAndroidTest  # 设备测试（备份往返、冻结 schema）
```

最低支持 Android 8.0（API 26）。

`assembleRelease` 目前使用 debug 签名，仅用于本地安装；正式分发前请替换为自己的签名配置。

## 技术栈

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · Coroutines/Flow

模块：`domain`（纯 Kotlin 业务逻辑）、`data`（Room / DataStore / 备份）、`core`（主题、通用组件与语言）、
`notification`（提醒、计时通知、闹钟调度）、`feature/*`（各功能界面）、`app`（装配与导航）。

## 设计

- 色板：雾蓝（Mist Blue）+ 奶油白，低饱和，浅色 / 深色跟随系统
- 图标：暖米白底 + 深蓝线稿猫（闭眼 + 对勾），包含自适应图标与单色层

## 许可证

Copyright 2026 [sandrone-muou](https://github.com/sandrone-muou)

基于 [Apache License 2.0](LICENSE) 发布。

---

## English

[简体中文](#meolist) · English

MeoList is a local-first Android "time-tracking to-do list": tasks, activity timers, habit check-ins,
countdowns, stats and a calendar, plus a timing farm that grows crops from your focus time.

The interface uses a low-saturation Mist Blue theme; the launcher icon is a line-art cat with a check.

### Features

- **Tasks**: lists / folders, subtasks, priorities, tags, due dates, recurring tasks, reminders when creating a task, search, trash
- **Activity timing**: custom activity types, one-tap start / stop, several timers at once, link timers to tasks, daily / weekly / monthly duration goals
- **Habits**: every day / selected weekdays / N times a week, streaks, weekly progress and a check-in calendar
- **Countdowns**: count down or up, optional yearly repeat
- **Statistics**: day / week / month / year views; month view switches between heatmap and bars, with tracked time and completed tasks
- **Calendar + quadrants**: month / week views; the agenda separates tasks, habits, countdowns and timer records; quadrants classify tasks by importance / urgency
- **Timing farm**: focus time, completed tasks and habit check-ins become nutrients that grow crops, unlock harvests and achievements
- **Data and settings**: everything stays on device; JSON backup / restore, CSV export; timing, reminder, week-start and month-chart preferences

### Language

The app starts in Simplified Chinese. Switch to English under **More → Settings → Appearance → Language**;
the choice applies immediately and is kept. Reminders and timer notifications follow the same setting.
Your own task, habit and note text is never translated.
The month chart style is under **More → Settings → Appearance → Month view chart**.

### Build

Requires JDK 17 and the Android SDK (compileSdk 36).

```bash
./gradlew assembleDebug          # debug build
./gradlew testDebugUnitTest      # unit tests
./gradlew :domain:test           # domain tests
./gradlew lintDebug              # Android Lint
./gradlew connectedDebugAndroidTest  # device tests (backup round trip, frozen schema)
```

Android 8.0 (API 26) and later.

`assembleRelease` is currently signed with the debug key and is meant for local installs only;
replace the signing config before distributing it.

### Tech stack

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · Coroutines/Flow

Modules: `domain` (pure Kotlin business logic), `data` (Room / DataStore / backup), `core` (theme, shared UI, language),
`notification` (reminders, timer notifications, alarm scheduling), `feature/*` (screens), `app` (wiring and navigation).

### Design

- Palette: Mist Blue + cream, low saturation, light / dark follows the system
- Icon: navy line-art cat with closed eyes and a check on warm ivory, including adaptive and monochrome layers

### License

Copyright 2026 [sandrone-muou](https://github.com/sandrone-muou)

Released under the [Apache License 2.0](LICENSE).
