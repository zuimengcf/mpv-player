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

  /** 弹幕显示区域：0=全部 1=顶部1/4 2=底部1/4 3=中间 */
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
}
