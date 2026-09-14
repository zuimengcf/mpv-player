# 播放器开发计划（基于 REX-Player 基底）

> 仓库：zuimengcf/mpv-player（com.zuimeng.player，应用名「播放器」）
> 基底：REX-Player 5.1.0（Kotlin + Compose UI）+ mpvRex-libmpv 绑定层 + libplayer.so
> 参考目标：Reex.apk（Flutter 播放器，功能从 APK 外围逆向提取）
> 更新：2026-09-13

## 一、当前基线（已达成）

- ✅ REX-Player 源码全量搬入（298 Kotlin 文件）
- ✅ mpvRex 绑定层 8 文件（MPVLib/MPVNode/BaseMPVView/MPVView/Utils/KeyMapping/FastThumbnails/FastClipper）
- ✅ 内核 so：libmpv + libplayer + FFmpeg 全家 + libmediainfo（arm64-v8a）
- ✅ 开发版配置：applicationId com.zuimeng.player、应用名「播放器(开发版)」、固定签名
- ✅ 云端 CI：push → assembleDebug → artifact（已成功出包 61MB）

## 二、Reex 功能全景（外围逆向提取）

### 播放器核心
- mpv 内核 + libplacebo 渲染 + libsmb2 网络
- 硬件解码开关（hardwareDecode）
- 视频快速解码（videoFastDecodeEnabled）
- 帧步进（frameStepEnabled）
- 快速跳转/不精确 seek（fastSeekEnabled）
- 自定义 mpv.conf（mpvConf）
- 自定义命令序列（customCommands + 逆序开关）
- TLS 验证开关（tlsVerify）

### 视频画质（视频均衡器）
- 亮度/对比度/饱和度/伽马/色调（brightness/contrast/saturation/gamma/hue）
- 镜像/旋转/缩放/PanScan（mirror/rotate/zoom/panscan）
- 纵横比：16:9、fit/fill/stretch（aspectOverride/aspectRatio）

### 音频
- 音频延迟（reAudioDelayEnabled）
- 音轨切换（reAudioTrackEnabled）
- 音频输出选择（ao）
- 音频通道（Audio Channels）
- 均衡器（BassBoost/treble/fader/surround）

### 字幕
- 自动加载（autoLoadEnabled）
- 外部字幕（externalSubtitleTrack，.srt/.ass/.ssa/.vtt）
- 样式：字体/字号/颜色/边框/粗体/斜体/描边（fontName/fontSize/fontColor/borderSize/borderColor/bold/italic）
- 忽略 ASS 内嵌样式（ignoreAssStyleEnabled）
- 字幕延迟（reSubtitleDelayEnabled）
- 双字幕（secondarySubtitleText）

### 手势（PlayerGesturePrefs）
- 双击：暂停/跳转（doubleTapEnabled + doubleTapSeekValue）
- 长按：倍速 + 震动（longPressEnabled + longPressPlaybackSpeed + vibrate）
- 左右滑动快进（horizontalDragEnabled）
- 上下滑动音量/亮度（verticalDragLeft/Right）

### 文件浏览 & 网络
- 文件夹模式/列表网格模式（folderModeEnabled/listMode）
- 自动扫描（autoScanEnabled）
- 识别 noMedia（recognizeNoMediaEnabled）
- 视频长度过滤（videoFileLengthMini）
- 网络共享 FTP/WebDAV/SMB（NetworkAccount/NetworkFileSystemPrefs）
- 网络目录收藏（NetworkDirectoryFavorite）

### 历史记录
- 播放历史（PlaybackHistory/showPlaybackHistory）
- 断点续播（reVideoPositionEnabled）
- 记忆项：倍速/音轨/字幕轨/字幕延迟/音频延迟/镜像/变换/平台音量/亮度（9 项）

### 其他
- 后台播放（backgroundModeEnabled + MediaBrowserService）
- 自定义 PiP（customPipModeEnabled + 系统 PiP）
- 睡眠定时器（sleepTimerDuration）
- 屏幕方向（screenOrientationMode）
- 覆盖层自动隐藏（autoHideMainOverlay + showMainOverlayDuration）
- 扩展显示缺口（allowWindowExtendDisplayCutout）
- 主题深/浅色（appThemeDark/Light/themeMode）
- 多语言（language）
- A-B 循环（ABLoopRange）
- 章节（Chapter）
- 播放列表（ExtendedPlaylist）
- 收藏夹 4 组位置记忆（FavoriteStore0-3/FavoriteRecall0-3）
- 搜索（BrowserSearch）
- 投屏/Cast（CAST）
- 截图（ScreenshotFormat）
- 播放速度（PlaySpeedUp/Down/Reset）
- 跳过键（MediaSkip）

## 三、REX-Player 基底已有功能（对照）

待逐项核实（检查 app/src/main/kotlin/xyz/mpv/rex/ 下的实现）：
- 文件浏览（ui/browser/MainScreen.kt）✅ 有
- 播放页（ui/player/PlayerActivity.kt）✅ 有
- 播放控制面板（controls/components/panels/VideoSettingsPanel、SubtitleSettingsPanel 等）✅ 有
- 剪辑导出（ClipExportSheet）✅ 有
- 手势（需核实）
- 历史/最近播放（domain/recentlyplayed）✅ 有
- 播放状态记忆（domain/playbackstate）✅ 有
- 网络协议（smbj/commons-net/sardine）✅ 依赖有，功能待核实
- 缩略图（domain/thumbnail + FastThumbnails）✅ 有
- 主题/多语言 ✅ 有

## 四、差异与实施优先级（P0→P2）

### 核实结论（2026-09-13）
REX-Player 基底已覆盖 Reex 绝大部分功能（甚至更强）：
- ✅ 视频均衡器（亮度/对比度/饱和度/伽马/色调/镜像/旋转/缩放）→ VideoSettingsPanel + VerticalSliders + FilterPresets
- ✅ 字幕样式（字体/字号/颜色/边框/粗斜体/描边/忽略ASS）→ SubtitleSettingsPanel + 3 Cards
- ✅ 自定义 mpv.conf / 自定义命令 / Lua 脚本 → ConfigEditorScreen + LuaScriptsPanel
- ✅ A-B 循环 + 章节 + 帧步进 → Seekbar + ChaptersSheet + FrameNavigationSheet
- ✅ 睡眠定时器 → SleepTimerSheet
- ✅ 播放速度（长按倍速 + 快捷）→ PlaybackSpeedSheet + SpeedControlSlider
- ✅ 音频延迟/音轨 → AudioDelayPanel + AudioTracksSheet
- ✅ 手势全套（双击跳转秒数/长按倍速/上下滑动）→ GestureHandler + DoubleTapSeekSecondsView
- ✅ 搜索 → SearchScreen
- ✅ 截图 → FrameNavigationSheet + MPVView
- ✅ 网络共享 SMB/FTP/WebDAV → NetworkBrowserScreen + SmbClient/FtpClient/WebDavClient
- ✅ 后台播放 + PiP → MediaPlaybackService + MPVPipHelper
- ✅ 自动扫描/noMedia/视频长度过滤 → HybridMediaDao + MediaLibraryPreferencesScreen
- ✅ 断点续播（Resume）→ ResumePlaybackPromptDialog + PlaybackStateRepository

### 真实差距（要补的）
- [x] P0-1 播放位置快速记忆（Reex FavoriteStore0-3/FavoriteRecall0-3）：播放页一键存 4 个位置标记 + 快速跳回 ✅（新增 POSITION_BOOKMARKS 按钮 + PositionBookmarksSheet，4 槽存/跳/清，与文件关联）
- [ ] P0-2 网络目录收藏（Reex NetworkDirectoryFavorite）：网络共享浏览时收藏常用目录
- [ ] P1-1 装机实测验证：确认上述 REX 既有功能在开发版全部真实可用（视频均衡器/字幕样式/mpv.conf/AB循环/睡眠定时器/手势等）
- [ ] P1-2 自定义 PiP 开关核对（customPipModeEnabled 对应项）
- [ ] P2-1 投屏/Cast（REX 有 App.kt 里 Cast 引用，需核实是否完整实现）

## 五、执行记录

- 2026-09-13：创建计划；核实 REX 基底 vs Reex 功能差距；确定真实差距为「播放位置快速记忆」+「网络目录收藏」
