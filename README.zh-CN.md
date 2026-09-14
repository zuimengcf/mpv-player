# mpvRex

<p align="center">
  <a href="README.md"><b>English</b></a> &nbsp;|&nbsp; <a href="README.zh-CN.md"><b>简体中文</b></a>
</p>

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" />
</p>

<p align="center">
  <b>基于 libmpv 的功能丰富的 Android 视频播放器。</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen.svg" />
  <img src="https://img.shields.io/badge/License-Apache--2.0-blue.svg" />
  <img src="https://img.shields.io/badge/Kotlin-2.3.10-purple.svg" />
  <a href="https://github.com/sfsakhawat999/mpvRex/releases"><img src="https://img.shields.io/github/downloads/sfsakhawat999/mpvRex/total?logo=Github"/></a>
  <img src="https://img.shields.io/github/stars/sfsakhawat999/mpvRex?style=flat&logo=github" />
</p>

mpvRex 是一款先进、高度可定制的 Android 视频播放器。它将 libmpv 的强大能力与现代 Jetpack Compose 界面以及独特的以用户为中心的功能相结合。

---

## 展示

<div class="image-row" align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/glassplayer.png" width="92%">
  <p><i>播放器界面 — 玻璃拟态控件</i></p>
</div>

<div class="image-row" align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/videoscreen.png" width="31%">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/pip.png" width="31%">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/about.png" width="31%">
</div>
<p align="center"><i>视频浏览器 · 画中画 · 关于页面</i></p>

<div class="image-row" align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/playlistwindow.png" width="48%">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/moresheet.png" width="48%">
</div>
<p align="center"><i>播放列表窗口 · 文件操作面板</i></p>

---

## 功能特性

### 🎬 播放与手势

- **环形双击跳转** — 完全可自定义的环形跳转浮层，带平滑过渡动画
- **跳转取消** — 在拖动手势中向后拖动即可取消跳转，带交互式指针缩放反馈动画
- **字幕拖拽重定位** — 点击并垂直拖拽字幕，可将其放置在屏幕任意位置
- **字幕滑动跳转** — 水平滑动可在字幕行之间精确跳转
- **顶部胶囊式跳转 OSD** — 药丸形浮层显示双击跳转反馈，不遮挡视频
- **动态 A-B 循环与逐帧导航** — 可设置带垂直偏移调节的循环点；用浮动、不碰撞的逐帧面板微调
- **高级缩放与平移** — 独立视频缩放、去黑边，以及宽高比面板中的交互式缩放/平移滑杆；每部视频独立保存设置
- **精细的点击与锁定逻辑** — 自定义排除区域、可选进度条点击防护、单击控制锁定

### 🎨 界面与美学

- **玻璃主题播放器界面** — 控件、进度条、标题栏、倍速指示器和短视频播放器全部采用流畅的玻璃拟态设计
- **动态标签管理器** — 隐藏、显示和重排仪表盘标签，完全自定义底部导航
- **Material You** — 播放器控件动态匹配 Android 系统强调色或应用主题
- **主题过渡动画** — 浅色/深色主题切换时的精致圆形揭示动画
- **内嵌封面缩略图** — 自动提取内嵌封面及同目录封面图作为本地视频缩略图

### 🗂️ 文件浏览器与媒体库

- **统一浏览器引擎** — 确保每种浏览模式（本地存储、网络共享、播放列表）的观感与行为完全一致
- **M3U 播放列表支持** — 加载带自定义流标题的 M3U 播放列表，支持拖拽排序
- **多选范围** — 长按第一个文件再点击最后一个，即可轻松选择一整个范围的条目
- **分栏网格/列表布局** — 树状子目录内可独立自定义
- **文件夹元数据** — 递归文件计数、已看/未看变暗、响应式"NEW"徽章
- **面包屑导航** — 树状视图中可切换的路径面包屑
- **高级排序** — 按名称、日期、大小和时长排序
- **网络流代理** — 面向 WebDAV、SMB 和 FTP 流的高性能代理，带图片预览缓存
- **标记为系统状态** — 将视频标记为已看、跳过、新片或标记；相应筛选媒体库
- **媒体库视图** — 在文件树之外浏览完整视频收藏
- **短视频模式** — 全面翻新的竖屏视频播放器，带目录源过滤、会话自由模式、简洁界面模式和响应式 MPV 观察器

### ⚙️ 引擎与自定义

- **HDR 转 SDR 色调映射** — 通过 `hdr-toys` 着色器管线实现高质量色调映射
- **智能方向** — 每个视频强制横屏/竖屏，并保存为偏好设置
- **音频支持** — 直接扫描、展示和播放独立音频文件（文件浏览器和播放器内均可）

### ⚡ 性能

- **电池优化播放** — 优化播放引擎，在长时间观看时最大化电池续航
- **响应式 StateFlow 观察器** — 每个实例独立的 MPV 属性观察器，后台恢复后依然存活，杜绝 UI 冻结
- **手势 JNI 消除** — 平移/缩放手势期间移除逐事件 JNI 读取，交互更流畅
- **智能后台服务** — 仅在实际退到后台时才启动后台播放服务

---

## 安装

<div align="center">
  <a href="https://github.com/sfsakhawat999/mpvRex/releases">
    <img src="https://img.shields.io/badge/Download-Stable_Release-blue?style=for-the-badge&logo=github" alt="Stable Release">
  </a>
  <a href="https://sfsakhawat999.github.io/mpvRex">
    <img src="https://img.shields.io/badge/Download-Preview_Build-orange?style=for-the-badge&logo=github" alt="Preview Release">
  </a>
</div>

<p align="center"><i>预览版可能不稳定，仅供测试使用。</i></p>

---

## 翻译

翻译可使用 **[Droidlate](https://github.com/estiaksoyeb/Droidlate)**（[PyPI](https://pypi.org/project/droidlate/)）管理 —— 一个本地、基于 Web 的界面，专为编辑 Android `strings.xml` 翻译文件而设计。

如果你想为 mpvRex 翻译贡献你的语言，请参阅[翻译贡献指南](CONTRIBUTING.md#translation-contributions)，了解在本地运行 Droidlate 的分步说明。

---

## 致谢

mpvRex 的根基是 **[mpvEx](https://github.com/marlboro-advance/mpvEx)**，而 mpvEx 又构建于 **[mpv-android](https://github.com/mpv-android/mpv-android)** 之上。我们感谢他们打下的基础。

其他灵感与参考：
[mpvKt](https://github.com/abdallahmehiz/mpvKt) · [Next Player](https://github.com/anilbeesetti/nextplayer) · [Gramophone](https://github.com/FoedusProgramme/Gramophone)

---

## 许可证

基于 **Apache License 2.0** 分发。详见 `LICENSE`。