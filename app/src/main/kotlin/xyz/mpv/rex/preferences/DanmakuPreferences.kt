package xyz.mpv.rex.preferences

import xyz.mpv.rex.preferences.preference.PreferenceStore

/**
 * 弹幕（Danmaku）偏好设置 — 参考字幕功能结构。
 * 所有值存 PreferenceStore，供 DanmakuManager / 设置页 / 播放器面板使用。
 */
class DanmakuPreferences(
  preferenceStore: PreferenceStore,
) {
  /** 弹幕字号（sp），默认 22 */
  val fontSize = preferenceStore.getInt("danmaku_font_size", 22)

  /** 弹幕透明度（0-255），默认 255 不透明 */
  val alpha = preferenceStore.getInt("danmaku_alpha", 255)

  /** 弹幕显示区域：0=全屏 1=半屏 2=1/4屏 3=不显示（固定顶部，底部留给字幕） */
  val displayArea = preferenceStore.getInt("danmaku_display_area", 0)

  /** 弹幕密度：0=稀疏 1=正常 2=密集 */
  val density = preferenceStore.getInt("danmaku_density", 1)

  /** 弹幕滚动速度倍率（0.5-2.0），默认 1.0 */
  val scrollSpeed = preferenceStore.getFloat("danmaku_scroll_speed", 1f)

  /** 是否显示顶部弹幕 */
  val showTopDanmaku = preferenceStore.getBoolean("danmaku_show_top", true)

  /** 是否显示底部弹幕 */
  val showBottomDanmaku = preferenceStore.getBoolean("danmaku_show_bottom", true)

  /** 是否显示滚动弹幕 */
  val showScrollDanmaku = preferenceStore.getBoolean("danmaku_show_scroll", true)

  /** 弹幕默认开启 */
  val enabledByDefault = preferenceStore.getBoolean("danmaku_enabled_by_default", true)

  /** 弹幕描边宽度（0-5），默认 1 */
  val borderSize = preferenceStore.getInt("danmaku_border_size", 1)

  /** 弹幕阴影半径（0-10），默认 3 */
  val shadowRadius = preferenceStore.getInt("danmaku_shadow_radius", 3)

  /** 是否覆盖弹幕颜色（统一颜色），默认 false */
  val overrideColor = preferenceStore.getBoolean("danmaku_override_color", false)

  /** 弹幕统一颜色 ARGB，默认白色 0xFFFFFFFF */
  val fontColor = preferenceStore.getInt("danmaku_font_color", 0xFFFFFFFF.toInt())

  /** 弹幕繁简转换：0=不转换，1=简体转繁体，2=繁体转简体 */
  val chConvert = preferenceStore.getInt("danmaku_ch_convert", 0)

  /** 同一弹幕合并（去重合并同内容刷屏弹幕），默认开启 */
  val mergeDuplicate = preferenceStore.getBoolean("danmaku_merge_duplicate", true)

  /** 弹幕屏蔽关键词（以分号分隔），命中则不显示该条弹幕。空串表示不屏蔽 */
  val blockKeywords = preferenceStore.getString("danmaku_block_keywords", "")
}
