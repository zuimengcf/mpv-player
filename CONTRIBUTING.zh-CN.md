想要报告问题/Bug 或提交功能请求？请参阅 [README](README.md) 文件。

感谢你对 mpvRex 的贡献感兴趣！

本指南概述了我们的开发实践、代码规范、Git 工作流以及翻译贡献流程。

---

## 代码贡献

我们欢迎 Bug 修复、性能优化和功能增强。

### 构建设置与验证

* **SDK/JDK 要求：** Java 17
* **构建工具：** Gradle

#### 标准构建命令
* **构建 Debug APK：**
  ```bash
  ./gradlew assembleDebug
  ```
* **构建 Release APK：**
  ```bash
  ./gradlew assembleRelease
  ```

#### 验证
项目没有自动化测试套件。贡献者必须在提交 Pull Request 之前在真实 Android 设备或模拟器上执行手动验证。播放过程中请检查 Android Logcat，确保没有抛出意外异常或错误。

---

### 核心架构与关键模式

我们遵循模块化的 **Ops/Manager 驱动架构**，以保持 UI 控制器精简、测试简单。

1. **ViewModel 作为协调器：** ViewModel（如 `PlayerViewModel`）应只负责管理 UI 状态表示，并将所有业务逻辑委托给专门的 Manager 类。
2. **Manager 负责领域逻辑：** 专门的操作属于 Manager 类（例如 `PlaybackManager` 负责跳转、`PlaylistManager` 负责随机播放、`SubtitleManager` 负责字幕下载）。
3. **统一媒体扫描：** 绝不直接扫描文件系统。请使用 `CoreMediaScanner`（通过 `FileSystemOps` 和 `MediaMetadataOps`）完成所有媒体文件发现，以确保文件夹计数和徽章状态处理正确。
4. **UI 一致性：** 任何列表或网格媒体展示都使用 `BaseMediaCard`，以保持宽高比和徽章样式的一致性。

#### Ops/Manager 模式示例：
```kotlin
// 1. 播放器视图（Jetpack Compose）向协调器触发操作
PlayPauseButton(onClick = { playerViewModel.togglePlayPause() })

// 2. 协调器（ViewModel）直接委托给 PlaybackManager
class PlayerViewModel(
    private val playbackManager: PlaybackManager
) : ViewModel() {
    fun togglePlayPause() {
        playbackManager.togglePlay()
    }
}

// 3. Manager（PlaybackManager）处理直接的领域/视频引擎逻辑
class PlaybackManager(private val mpvLib: MPVLib) {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    fun togglePlay() {
        val newState = !_isPlaying.value
        mpvLib.setPropertyBoolean("pause", !newState)
        _isPlaying.value = newState
    }
}
```

---

### 许可证与署名

如果你移植了其他媒体播放器（如 `mpv-android`、`mpvEx`、`mpvKt`、`Next Player`、`Gramophone`）的代码、模式或资源，你**必须**在 Pull Request 描述中注明。请附上源仓库链接，并确保其许可证与我们的 **Apache License 2.0** 兼容。

---

### Git 工作流

为保持仓库历史清爽、易于管理，我们遵守以下 Git 规则：

* **功能分支：** 每个功能或修复都从 `master` 创建新分支。
* **原子提交：** 将结构性重构与视觉/功能修复分开提交。
* **保持分支更新：** 使用 `rebase` 而非 merge 将你的分支与 `master` 保持同步。
* **提交信息：** 我们遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范（例如 `fix:`、`feat:`、`refactor:`、`chore:`），以保持提交历史清晰有序。你有责任保持提交干净合规；不合规的提交需要你自行改写或压缩后才能合并。

### Pull Requests

* 目标分支为 `master`。
* 保持 PR 范围聚焦于单个修复或功能 —— 将无关改动拆分为独立 PR。
* 如有相关 issue 编号请引用。
* 描述改动**内容**和**原因**；diff 已经展示了**方式**。

### 沟通

GitHub Issues 和 Pull Request 讨论是我们主要的沟通渠道。在编写大量代码之前，欢迎先开启一个草稿 issue 讨论设计方案。

---

## 翻译贡献

翻译方面，我们使用 **[Droidlate](https://github.com/estiaksoyeb/Droidlate)**（[PyPI](https://pypi.org/project/droidlate/)）—— 一个轻量、本地、基于 Web 的翻译工作区，专为 Android `strings.xml` 资源文件设计。

### 为什么选 Droidlate？
* **本地离线工作区：** 在自己的机器上翻译和编辑本地文件，无需将资源上传到第三方服务器。
* **保留 XML 格式与注释：** 精确保持 XML 样式、注释、结构和格式。
* **跟踪过期翻译：** 基础字符串变更时，高亮需要更新的目标翻译。
* **占位符验证与 QA：** 校验 Java 风格占位符，防止运行时崩溃。
* **孤立字符串清理：** 轻松清理已从主代码库删除的字符串。

---

### 分步翻译工作流

#### 1. 安装 Droidlate
你可以通过 `pipx`（推荐）或标准 `pip` 全局安装 Droidlate：

```bash
# 使用 pipx（推荐）
pipx install droidlate

# 或使用常规 pip
pip install droidlate
```

#### 2. 准备仓库
确保已克隆 mpvRex 的最新代码：
```bash
git clone https://github.com/sfsakhawat999/mpvRex.git
cd mpvRex
```

#### 3. 启动 Droidlate
在 mpvRex 目录根目录打开终端并运行：
```bash
droidlate
```
Droidlate 将启动一个本地 Web 服务器，并在默认浏览器中打开界面（通常在 `http://127.0.0.1:5000`）。

#### 4. 添加新语言
如果你的语言环境未出现在仪表盘上：
1. 在你的资源目录中创建名为 `values-<locale>` 的新目录（例如 `app/src/main/res/values-<locale>`），遵循 [Android 语言环境限定符格式](https://developer.android.com/guide/topics/resources/providing-resources#AlternativeResources)。
2. 在该目录中放置一个空的 `strings.xml` 文件。
3. 重启 Droidlate —— 它会检测到新的语言环境，用基础英文串填充，并允许你开始翻译。

#### 5. 在浏览器中翻译
在仪表盘上，选择你的语言环境即可打开编辑器。
* 翻译字符串、查看开发者注释、检查格式占位符警告。
* 使用键盘快捷键提高效率：
  * `Ctrl + S`：立即保存并转到下一条。
  * `Alt + 1` / `Alt + 2`：粘贴自动翻译建议（Google Translate 或 MyMemory）。

#### 6. 清理过期/孤立键
如果某些字符串已从英文文件中删除，请检查编辑器的 **"Orphaned"（孤立）** 标签页，安全清理它们并保持文件整洁。

#### 7. 提交文件与元数据台账
保存更改会更新：
1. 目标 XML 文件（例如 `app/src/main/res/values-<locale>/strings.xml`）
2. `.translation_metadata/` 目录内的跟踪文件（例如 `.translation_metadata/values-<locale>.json`）

**重要：** 你必须同时将更新后的 `strings.xml` 及其对应的 `.json` 台账文件提交到 Git。