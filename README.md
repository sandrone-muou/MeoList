# MeoList

简体中文 · [English](#english)

MeoList 是一个本地优先的 Android「计时清单」应用：任务清单、活动计时、习惯打卡、倒数日，
再加上一个用专注时间浇灌作物的「计时农场」。

## 功能

- **任务清单**：清单 / 文件夹、子任务、优先级、标签、截止日期、重复任务、提醒、搜索、回收站
- **活动计时**：自定义活动类型，一键开始 / 停止，多个计时可同时运行，可关联任务，支持日 / 周 / 月时长目标
- **习惯打卡**：每天 / 每周指定几天 / 每周 N 次，打卡日历与连续天数
- **倒数日 / 纪念日**：倒数或正数，支持每年重复
- **统计报表**：按日 / 周 / 月查看计时分布与任务完成情况，记录可编辑
- **日历 + 四象限**：月历汇总任务、习惯与倒数日；按重要 / 紧急自动分类任务
- **计时农场**：计时时长、完成任务、习惯打卡都会变成养分，让作物生长、收获并解锁成就
- **数据**：纯本地存储，支持备份 / 恢复与 CSV 导出

## 语言

界面默认显示简体中文，可在「更多 → 设置 → 显示 → 语言」中切换为 English，选择会立即生效并保留。
提醒与计时通知同样跟随该设置；任务、习惯等用户数据不会被翻译。

## 构建

需要 JDK 17 与 Android SDK（compileSdk 36）。

```bash
./gradlew assembleDebug          # 构建调试包
./gradlew testDebugUnitTest      # 单元测试
./gradlew :domain:test           # 领域层测试
./gradlew connectedDebugAndroidTest  # 设备测试（备份往返、冻结 schema）
```

最低支持 Android 8.0（API 26）。

## 技术栈

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · Coroutines/Flow

模块：`domain`（纯 Kotlin 业务逻辑）、`data`（Room / DataStore / 备份）、`core`（主题、通用组件与语言）、
`notification`（提醒、计时通知、闹钟调度）、`feature/*`（各功能界面）、`app`（装配与导航）。

## 许可证

Copyright 2026 [sandrone-muou](https://github.com/sandrone-muou)

基于 [Apache License 2.0](LICENSE) 发布。

---

## English

[简体中文](#meolist) · English

MeoList is a local-first Android "time-tracking to-do list": tasks, activity timers, habit check-ins
and countdowns, plus a timing farm that grows crops from your focus time.

### Features

- **Tasks**: lists / folders, subtasks, priorities, tags, due dates, recurring tasks, reminders, search, trash
- **Activity timing**: custom activity types, one-tap start / stop, several timers at once, link timers to tasks, daily / weekly / monthly duration goals
- **Habits**: every day / selected weekdays / N times a week, check-in calendar and streaks
- **Countdowns**: count down or up, optional yearly repeat
- **Statistics**: daily / weekly / monthly breakdown of tracked time and completed tasks, editable records
- **Calendar + quadrants**: monthly overview of tasks, habits and countdowns; automatic importance / urgency classification
- **Timing farm**: focus time, completed tasks and habit check-ins become nutrients that grow crops, unlock harvests and achievements
- **Data**: everything stays on device, with backup / restore and CSV export

### Language

The app starts in Simplified Chinese. Switch to English under **More → Settings → Appearance → Language**;
the choice applies immediately and is kept. Reminders and timer notifications follow the same setting.
Your own task, habit and note text is never translated.

### Build

Requires JDK 17 and the Android SDK (compileSdk 36).

```bash
./gradlew assembleDebug          # debug build
./gradlew testDebugUnitTest      # unit tests
./gradlew :domain:test           # domain tests
./gradlew connectedDebugAndroidTest  # device tests (backup round trip, frozen schema)
```

Android 8.0 (API 26) and later.

### Tech stack

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · Coroutines/Flow

Modules: `domain` (pure Kotlin business logic), `data` (Room / DataStore / backup), `core` (theme, shared UI, language),
`notification` (reminders, timer notifications, alarm scheduling), `feature/*` (screens), `app` (wiring and navigation).

### License

Copyright 2026 [sandrone-muou](https://github.com/sandrone-muou)

Released under the [Apache License 2.0](LICENSE).
