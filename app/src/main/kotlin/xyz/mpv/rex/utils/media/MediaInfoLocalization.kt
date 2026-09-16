package xyz.mpv.rex.utils.media

/**
 * MediaInfo 字段中英映射 — 用于把 MediaInfo 库输出的英文字段名
 * （General / Complete name / Format / Duration …）翻译成中文显示。
 *
 * 只翻译标签（label），值（value）保持原样（时长、码率等数值本身无需翻译）。
 * 未匹配的字段名原样返回。
 */
object MediaInfoLocalization {

  /** 区块名映射（General / Video / Audio / Text / Menu …） */
  private val SECTION_MAP = mapOf(
    "General" to "常规",
    "Video" to "视频",
    "Audio" to "音频",
    "Text" to "文本",
    "Menu" to "菜单",
    "Other" to "其他",
    "Image" to "图像",
  )

  /** 字段名映射（MediaInfo 文本输出里的 key） */
  private val LABEL_MAP = mapOf(
    // ── General ──
    "Complete name" to "完整文件名",
    "Format" to "格式",
    "Format profile" to "格式配置",
    "Format/Info" to "格式信息",
    "Format version" to "格式版本",
    "Format settings" to "格式设置",
    "Format settings, CABAC" to "格式设置（CABAC）",
    "Format settings, Reference frames" to "格式设置（参考帧）",
    "Codec ID" to "编码 ID",
    "Codec ID/Info" to "编码 ID/信息",
    "File size" to "文件大小",
    "Duration" to "时长",
    "Overall bit rate" to "总码率",
    "Bit rate" to "码率",
    "Bit rate mode" to "码率模式",
    "Frame rate" to "帧率",
    "Frame rate mode" to "帧率模式",
    "Width" to "宽度",
    "Height" to "高度",
    "Display aspect ratio" to "显示宽高比",
    "Color space" to "色彩空间",
    "Chroma subsampling" to "色度抽样",
    "Bit depth" to "位深",
    "Scan type" to "扫描方式",
    "Bits/(Pixel*Frame)" to "比特/（像素×帧）",
    "Stream size" to "流大小",
    "Writing application" to "写入程序",
    "Writing library" to "编码库",
    "Encoding settings" to "编码参数",
    "Codec configuration box" to "编码配置盒",
    "Title" to "标题",
    "Performer" to "表演者",
    "Album" to "专辑",
    "Encoded date" to "编码日期",
    "Tagged date" to "标记日期",
    "Language" to "语言",
    "Default" to "默认",
    "Forced" to "强制",
    "ID" to "ID",
    "Unique ID" to "唯一 ID",
    "Menu ID" to "菜单 ID",
    // ── Audio ──
    "Channel(s)" to "声道数",
    "Channel layout" to "声道布局",
    "Sampling rate" to "采样率",
    "Compression mode" to "压缩模式",
    "Audio" to "音频",
    // ── Text ──
    "Service kind" to "服务类型",
    "Muxing mode" to "封装模式",
    // ── 通用 ──
    "Source" to "来源",
    "Source duration" to "来源时长",
    "Original source medium" to "原始来源介质",
    "Minimum frame rate" to "最低帧率",
    "Maximum frame rate" to "最高帧率",
    "Nominal bit rate" to "标称码率",
    "Reel" to "卷",
    "Count of audio streams" to "音频流数量",
    "Count of video streams" to "视频流数量",
    "Count of text streams" to "文本流数量",
    "Count of menu streams" to "菜单流数量",
  )

  /**
   * 翻译区块名（General → 常规）。
   * 若带 " #n" 后缀（如 "Audio #2"）会保留后缀。
   */
  fun translateSection(name: String): String {
    // 处理 "Audio #2" / "Video #1" 这类带序号区块
    val indexMatch = Regex("""^(General|Video|Audio|Text|Menu|Other|Image)(\s+#\d+)?$""").find(name.trim())
    if (indexMatch != null) {
      val base = SECTION_MAP[indexMatch.groupValues[1]] ?: name.trim()
      val suffix = indexMatch.groupValues[2]
      return if (suffix.isNullOrEmpty()) base else "$base$suffix"
    }
    return SECTION_MAP[name.trim()] ?: name
  }

  /** 翻译字段标签（Complete name → 完整文件名）。未命中原样返回。 */
  fun translateLabel(key: String): String {
    val trimmed = key.trim()
    // 有些字段带序号后缀，如 "Format settings, RefFrames" 之类直接查表
    LABEL_MAP[trimmed]?.let { return it }
    // 尝试去掉行尾数字序号（如 "Stream size" 无需处理；"Language" 无后缀）
    return trimmed
  }
}
