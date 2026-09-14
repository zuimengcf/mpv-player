# 测试与质量保障路线图 — mpvRex

本文档概述了为 **mpvRex** 集成自动化测试、代码质量与 QA 关卡的分阶段路线图，遵循现代 Android 最佳实践与软件工程原则。

---

## 🎯 目标与宗旨
1. **防止回归**：确保核心播放器引擎功能、播放列表与字幕解析在应用更新中保持 100% 可靠。
2. **消除主线程 ANR**：防范并发死锁与锁竞争。
3. **自动化质量控制**：贯彻鲍勃大叔的"测试关卡"（单元测试、静态分析、覆盖率、变异测试、BDD 与 UI 基准测试）。

---

## 🛣️ 路线图阶段：从入门到进阶

```
┌─────────────────────────────────────────────────────────────────────────┐
│ PHASE 1: Unit Testing Foundation (Beginner)                             │
│ • JUnit 5, MockK, Coroutines Test, Turbine                              │
│ • Unit tests for PlayerEngineManager, M3UParser, Subtitle font fallback │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PHASE 2: Static Analysis & Code Quality Guard (Intermediate)            │
│ • Detekt, Ktlint, Android Lint                                          │
│ • Automatic main-thread I/O & coroutine deadlock detection              │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PHASE 3: Code Coverage & Mutation Testing (Upper-Intermediate)          │
│ • Kover / JaCoCo (80% Branch Coverage Target)                           │
│ • Pitest / Stryker Mutation Testing (Kill code mutants)                 │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PHASE 4: UI & Visual Regression Testing (Advanced)                      │
│ • Roborazzi / Paparazzi Compose Screenshot Testing                      │
│ • Layout verification across Phone, Tablet, and Foldable form factors   │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ PHASE 5: BDD & Macrobenchmark Gauntlet (Expert)                         │
│ • Gherkin Given-When-Then BDD Specifications                            │
│ • Android Macrobenchmark (Frame rendering & startup time tracking)      │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 📋 各阶段详细说明

### 阶段 1：单元测试基础（入门）
**重点**：快速、确定性的 JVM 单元测试，覆盖核心业务逻辑与状态管理器，无需 Android 模拟器。

* **工具**：`junit-jupiter`、`mockk`、`kotlinx-coroutines-test`、`app.cash.turbine`
* **目标组件**：
  1. `PlayerEngineManagerTest`：
     - 测试模拟线程竞争下的 `tryLock(500ms)` 锁获取。
     - 验证引擎状态流转（`IDLE` → `FOREGROUND_PLAYING` → `TEARDOWN`）。
     - 验证 `awaitTeardownSync()` 在重新初始化前能正确等待进行中的 teardown 任务。
  2. `M3UParserTest`：
     - 验证标准与扩展 M3U/M3U8 播放列表的解析（EXTINF 标签、头部、流地址）。
     - 测试损坏/空播放列表的回退处理。
  3. `SubtitleFontFallbackTest`：
     - 测试字体选择逻辑：当 `sub-font` 偏好为空时，验证会输出 `"subfont, Roboto, sans-serif"`，以防厂商可变字体空格渲染 Bug。
* **标准命令**：
  ```bash
  ./gradlew test
  ```

---

### 阶段 2：静态分析与代码质量关卡（进阶）
**重点**：在 CI/构建流水线中运行自动化静态分析工具，强制格式规范并防止代码坏味道。

* **工具**：`Detekt`、`Ktlint`、`Android Lint`
* **规则与约束**：
  - **Detekt**：强制圈复杂度上限（每个函数 < 15），检测未处理异常，捕获协程作用域中的不安全阻塞调用。
  - **Android Lint**：标记主线程磁盘 I/O、未关闭的流（`InputStream`/`OutputStream`）以及已弃用的 API 用法。
  - **Ktlint**：强制全部 Kotlin 源文件零样式偏差。
* **标准命令**：
  ```bash
  ./gradlew lintDebug detekt
  ```

---

### 阶段 3：代码覆盖率与变异测试（中高级）
**重点**：验证单元测试覆盖是否彻底，断言是否真正校验了业务契约。

* **工具**：`Kover`（Kotlin 代码覆盖率）、`Pitest` / `Stryker`
* **交付物**：
  - 为 `xyz.mpv.rex.ui.player.engine` 与 `xyz.mpv.rex.utils` 设定最低 **80% 分支覆盖率**阈值。
  - 运行变异测试：注入有意的代码变异（例如反转条件分支或修改数学运算），确保单元测试套件消灭 100% 的变异体。
* **标准命令**：
  ```bash
  ./gradlew koverHtmlReport
  ```

---

### 阶段 4：UI 与视觉回归测试（高级）
**重点**：确保 Jetpack Compose UI 组件在各种屏幕尺寸下完美渲染，无视觉回归。

* **工具**：`Roborazzi` / `Paparazzi`
* **目标组件**：
  - `PlayerControls`：顶/底栏渲染、进度条、时间标签。
  - `SpeedControlSlider`：自定义滑块格式化与倍速预设。
  - `SubtitlesPreferencesScreen`：排版卡片、下拉菜单、取色器。
* **验证**：生成基线 PNG 快照，并在 Pull Request 上运行自动化像素差异检查。

---

### 阶段 5：BDD 与宏基准测试关卡（专家）
**重点**：以用户为中心的行为规格与真机性能基准测试。

* **工具**：`Kotest BDD` / `Cucumber`、`androidx.benchmark:benchmark-macro`
* **交付物**：
  1. **行为规格（Gherkin）**：
     ```gherkin
     Feature: Background Playback Transition
       Scenario: App minimized during active playback
         Given video playback is active
         When user leaves the app via home gesture
         Then background service starts with active notification
     ```
  2. **性能基准测试**：
     - 冷启动时间：测量到首帧的时间（目标 < 100ms）。
     - 帧节奏：确保 UI 线程帧渲染预算严格低于 8.3ms（针对 120Hz 屏幕）。

---

## 🛠️ 标准执行命令

| 任务 | 标准命令 |
| :--- | :--- |
| **运行单元测试** | `./gradlew test` |
| **运行静态分析** | `./gradlew lintDebug detekt` |
| **生成覆盖率报告** | `./gradlew koverHtmlReport` |
| **完整验证** | `./gradlew check` |

> [!NOTE]
> 在本地 Termux / AndroidIDE 环境中，开发者可按需追加 `-I local-env.gradle.kts` 以注入本地环境优化。