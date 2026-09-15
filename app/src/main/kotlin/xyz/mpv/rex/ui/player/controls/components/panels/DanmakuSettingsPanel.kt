package xyz.mpv.rex.ui.player.controls.components.panels

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.DanmakuPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.components.PlayerSheet
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import xyz.mpv.rex.ui.theme.spacing

/**
 * 播放器内弹幕设置面板 — 参考字幕 SubtitleSettingsPanel 结构。
 * 点击"弹幕"按钮弹出；设置即时生效并持久化。
 */
@Composable
fun DanmakuSettingsPanel(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

  if (isPortrait) {
    PlayerSheet(onDismissRequest = onDismissRequest) {
      DanmakuSettingsContent(modifier)
    }
  } else {
    // 横屏：右侧滑出面板（与字幕设置面板一致）
    PlayerSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
      DanmakuSettingsContent(modifier)
    }
  }
}

@Composable
private fun DanmakuSettingsContent(modifier: Modifier = Modifier) {
  val preferences = koinInject<DanmakuPreferences>()

  val showTop by preferences.showTopDanmaku.collectAsState()
  val showBottom by preferences.showBottomDanmaku.collectAsState()
  val showScroll by preferences.showScrollDanmaku.collectAsState()
  val fontSize by preferences.fontSize.collectAsState()
  val alpha by preferences.alpha.collectAsState()
  val density by preferences.density.collectAsState()
  val scrollSpeed by preferences.scrollSpeed.collectAsState()
  val borderSize by preferences.borderSize.collectAsState()
  val shadowRadius by preferences.shadowRadius.collectAsState()

  ProvidePreferenceLocals {
    Column(
      modifier = modifier
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    ) {
      // 标题行
      Row(
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.Default.ChatBubbleOutline,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(end = 8.dp),
        )
        Text(
          text = stringResource(R.string.danmaku_settings_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismissRequest) {
          Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
        }
      }

      Spacer(Modifier.height(spacing.medium))

      // 显示控制
      Text(
        text = stringResource(R.string.danmaku_section_show),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(spacing.small))
      SwitchPreference(
        title = stringResource(R.string.danmaku_show_scroll_title),
        summary = stringResource(R.string.danmaku_show_scroll_summary),
        checked = showScroll,
        onCheckedChange = { preferences.showScrollDanmaku.set(it) },
      )
      SwitchPreference(
        title = stringResource(R.string.danmaku_show_top_title),
        summary = stringResource(R.string.danmaku_show_top_summary),
        checked = showTop,
        onCheckedChange = { preferences.showTopDanmaku.set(it) },
      )
      SwitchPreference(
        title = stringResource(R.string.danmaku_show_bottom_title),
        summary = stringResource(R.string.danmaku_show_bottom_summary),
        checked = showBottom,
        onCheckedChange = { preferences.showBottomDanmaku.set(it) },
      )

      Spacer(Modifier.height(spacing.medium))

      // 外观
      Text(
        text = stringResource(R.string.danmaku_section_appearance),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(spacing.small))
      SliderPreference(
        value = fontSize.toFloat(),
        valueRange = 12f..40f,
        steps = 27,
        onValueChangeFinished = { },
        onValueChange = { preferences.fontSize.set(it.toInt()) },
        title = { Text(stringResource(R.string.danmaku_font_size_title)) },
        summary = { Text("${fontSize} sp") },
      )
      SliderPreference(
        value = alpha.toFloat(),
        valueRange = 0f..255f,
        steps = 50,
        onValueChangeFinished = { },
        onValueChange = { preferences.alpha.set(it.toInt()) },
        title = { Text(stringResource(R.string.danmaku_alpha_title)) },
        summary = { Text("${alpha * 100 / 255}%") },
      )
      SliderPreference(
        value = borderSize.toFloat(),
        valueRange = 0f..5f,
        steps = 5,
        onValueChangeFinished = { },
        onValueChange = { preferences.borderSize.set(it.toInt()) },
        title = { Text(stringResource(R.string.danmaku_border_size_title)) },
        summary = { Text("$borderSize") },
      )
      SliderPreference(
        value = shadowRadius.toFloat(),
        valueRange = 0f..10f,
        steps = 10,
        onValueChangeFinished = { },
        onValueChange = { preferences.shadowRadius.set(it.toInt()) },
        title = { Text(stringResource(R.string.danmaku_shadow_radius_title)) },
        summary = { Text("$shadowRadius") },
      )

      Spacer(Modifier.height(spacing.medium))

      // 行为
      Text(
        text = stringResource(R.string.danmaku_section_behavior),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(spacing.small))
      ListPreference(
        value = density,
        entries = listOf(
          stringResource(R.string.danmaku_density_sparse) to 0,
          stringResource(R.string.danmaku_density_normal) to 1,
          stringResource(R.string.danmaku_density_dense) to 2,
        ),
        onValueChange = { preferences.density.set(it) },
        title = { Text(stringResource(R.string.danmaku_density_title)) },
        summary = { Text(densityLabel(density)) },
      )
      SliderPreference(
        value = scrollSpeed,
        valueRange = 0.5f..2f,
        steps = 15,
        onValueChangeFinished = { },
        onValueChange = { preferences.scrollSpeed.set(it) },
        title = { Text(stringResource(R.string.danmaku_scroll_speed_title)) },
        summary = { Text("${scrollSpeed}x") },
      )
    }
  }
}

@Composable
private fun densityLabel(value: Int): String = when (value) {
  0 -> stringResource(R.string.danmaku_density_sparse)
  1 -> stringResource(R.string.danmaku_density_normal)
  2 -> stringResource(R.string.danmaku_density_dense)
  else -> ""
}