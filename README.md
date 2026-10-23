# MeoList

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

## 构建

需要 JDK 17 与 Android SDK（compileSdk 36）。

```bash
./gradlew assembleDebug          # 构建调试包
./gradlew testDebugUnitTest      # 单元测试
./gradlew :domain:test           # 领域层测试
```

最低支持 Android 8.0（API 26）。

## 技术栈

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · Coroutines/Flow

模块：`domain`（纯 Kotlin 业务逻辑）、`data`（Room / DataStore / 备份）、`core`（主题与通用组件）、
`notification`（提醒、计时通知、闹钟调度）、`feature/*`（各功能界面）、`app`（装配与导航）。

## 许可证

Copyright 2026 [sandrone-muou](https://github.com/sandrone-muou)

基于 [Apache License 2.0](LICENSE) 发布。
