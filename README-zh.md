<p align="center">
  <img src="docs/icon.png" width="132" height="132" alt="MeoList 应用图标">
</p>

# MeoList

[English](README.md) · 简体中文

MeoList 是一个本地优先的 Android「计时清单」应用：任务、活动计时、习惯打卡、倒数日、统计与日历，
再加上一个用专注时间浇灌作物的「计时农场」。所有数据保存在本机。

[下载最新版本](https://github.com/sandrone-muou/MeoList/releases/latest) · Android 8.0+

## 功能

- **任务清单**：清单 / 文件夹、子任务、优先级、标签、截止日期、重复任务、创建时自定义提醒、搜索、回收站
- **活动计时**：自定义活动类型，一键开始 / 停止，多个计时可同时运行，可关联任务，支持日 / 周 / 月时长目标
- **习惯打卡**：每天 / 每周指定几天 / 每周 N 次，连续天数、周进度与打卡日历
- **倒数日 / 纪念日**：倒数或正数，支持每年重复
- **统计报表**：日 / 周 / 月 / 年视图；月视图支持热力图 / 直方图切换，展示计时分布与完成任务，记录可编辑
- **日历 + 四象限**：月 / 周视图；日程区区分任务、习惯、倒数日与计时记录；四象限可按标签归类，未打标签的任务按优先级 / 截止日期自动归类
- **计时农场**：计时时长、完成任务、习惯打卡都会变成养分，让作物生长、收获并解锁成就
- **数据**：纯本地存储；JSON 备份 / 恢复与 CSV 导出；可调计时、提醒、每周起始日与月视图图表

## 下载

最新正式签名 APK 见 [Releases](https://github.com/sandrone-muou/MeoList/releases/latest) 页面。
可直接覆盖旧版本安装并保留数据；卸载会删除本机数据，换机或卸载前建议先导出 JSON 备份。

## 语言

界面默认跟随系统语言：支持简体中文与 English，其他系统语言回退为 English。也可在
「更多 → 设置 → 显示 → 语言」中手动指定，选择会立即生效并保留。
提醒与计时通知同样跟随该设置；任务、习惯等用户数据不会被翻译。
月视图图表样式可在「更多 → 设置 → 显示 → 月视图图表」中切换。

## 构建与测试

需要 JDK 17 与 Android SDK（compileSdk 36），最低支持 Android 8.0（API 26）。

```bash
./gradlew assembleDebug              # 构建调试包
./gradlew testDebugUnitTest          # 应用单元测试
./gradlew :domain:test               # 领域层测试
./gradlew lintDebug                  # Android Lint
./gradlew connectedDebugAndroidTest  # 设备测试（备份往返、冻结 schema）
```

`assembleRelease` 会从 `keystore.properties` 读取签名凭据，该文件已被 Git 忽略；
缺少该文件时，release 构建会产出未签名 APK。

## 技术栈

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · Coroutines/Flow

- `domain` — 纯 Kotlin 业务逻辑
- `data` — Room、DataStore、备份与 CSV 导出
- `core` — 主题、通用组件与语言
- `notification` — 提醒、计时通知与闹钟调度
- `feature/*` — 任务、计时、习惯、倒数日、统计、日历、农场与设置界面
- `app` — 装配与导航

## 许可证

Copyright 2026 [sandrone-muou](https://github.com/sandrone-muou)

基于 [Apache License 2.0](LICENSE) 发布。
