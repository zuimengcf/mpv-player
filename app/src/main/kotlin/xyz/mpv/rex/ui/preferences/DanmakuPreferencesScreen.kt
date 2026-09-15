package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.DanmakuPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import xyz.mpv.rex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object DanmakuPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val preferences = koinInject<DanmakuPreferences>()

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.danmaku_settings_title),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        // 弹幕显示
        val showTop by preferences.showTopDanmaku.collectAsState()
        val showBottom by preferences.showBottomDanmaku.collectAsState()
        val showScroll by preferences.showScrollDanmaku.collectAsState()
        val enabledByDefault by preferences.enabledByDefault.collectAsState()

        // 字号/透明度
        val fontSize by preferences.fontSize.collectAsState()
        val alpha by preferences.alpha.collectAsState()

        // 显示区域/密度
        val displayArea by preferences.displayArea.collectAsState()
        val density by preferences.density.collectAsState()

        // 滚动速度
        val scrollSpeed by preferences.scrollSpeed.collectAsState()

        // 描边/阴影
        val borderSize by preferences.borderSize.collectAsState()
        val shadowRadius by preferences.shadowRadius.collectAsState()

        androidx.compose.foundation.layout.Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        ) {
          // ── 显示控制 ──
          GroupedPreferenceCard(position = GroupPosition.FIRST) {
            SwitchPreference(
              title = stringResource(R.string.danmaku_enabled_by_default_title),
              summary = stringResource(R.string.danmaku_enabled_by_default_summary),
              checked = enabledByDefault,
              onCheckedChange = { preferences.enabledByDefault.set(it) },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.MIDDLE) {
            SwitchPreference(
              title = stringResource(R.string.danmaku_show_scroll_title),
              summary = stringResource(R.string.danmaku_show_scroll_summary),
              checked = showScroll,
              onCheckedChange = { preferences.showScrollDanmaku.set(it) },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.MIDDLE) {
            SwitchPreference(
              title = stringResource(R.string.danmaku_show_top_title),
              summary = stringResource(R.string.danmaku_show_top_summary),
              checked = showTop,
              onCheckedChange = { preferences.showTopDanmaku.set(it) },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.LAST) {
            SwitchPreference(
              title = stringResource(R.string.danmaku_show_bottom_title),
              summary = stringResource(R.string.danmaku_show_bottom_summary),
              checked = showBottom,
              onCheckedChange = { preferences.showBottomDanmaku.set(it) },
            )
          }

          // ── 外观 ──
          GroupedPreferenceCard(position = GroupPosition.FIRST) {
            SliderPreference(
              value = fontSize.toFloat(),
              valueRange = 12f..40f,
              steps = 27,
              onValueChangeFinished = { },
              onValueChange = { preferences.fontSize.set(it.toInt()) },
              title = {
                Text(stringResource(R.string.danmaku_font_size_title))
              },
              summary = {
                Text("${fontSize} sp")
              },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.MIDDLE) {
            SliderPreference(
              value = alpha.toFloat(),
              valueRange = 0f..255f,
              steps = 50,
              onValueChangeFinished = { },
              onValueChange = { preferences.alpha.set(it.toInt()) },
              title = {
                Text(stringResource(R.string.danmaku_alpha_title))
              },
              summary = {
                Text("${(alpha * 100 / 255)}%")
              },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.MIDDLE) {
            SliderPreference(
              value = borderSize.toFloat(),
              valueRange = 0f..5f,
              steps = 5,
              onValueChangeFinished = { },
              onValueChange = { preferences.borderSize.set(it.toInt()) },
              title = {
                Text(stringResource(R.string.danmaku_border_size_title))
              },
              summary = {
                Text("$borderSize")
              },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.LAST) {
            SliderPreference(
              value = shadowRadius.toFloat(),
              valueRange = 0f..10f,
              steps = 10,
              onValueChangeFinished = { },
              onValueChange = { preferences.shadowRadius.set(it.toInt()) },
              title = {
                Text(stringResource(R.string.danmaku_shadow_radius_title))
              },
              summary = {
                Text("$shadowRadius")
              },
            )
          }

          // ── 行为 ──
          GroupedPreferenceCard(position = GroupPosition.FIRST) {
            ListPreference(
              value = displayArea,
              entries = listOf(
                stringResource(R.string.danmaku_area_all) to 0,
                stringResource(R.string.danmaku_area_top) to 1,
                stringResource(R.string.danmaku_area_bottom) to 2,
                stringResource(R.string.danmaku_area_middle) to 3,
              ),
              onValueChange = { preferences.displayArea.set(it) },
              title = { Text(stringResource(R.string.danmaku_area_title)) },
              summary = { Text(entriesSummary(displayArea)) },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.MIDDLE) {
            ListPreference(
              value = density,
              entries = listOf(
                stringResource(R.string.danmaku_density_sparse) to 0,
                stringResource(R.string.danmaku_density_normal) to 1,
                stringResource(R.string.danmaku_density_dense) to 2,
              ),
              onValueChange = { preferences.density.set(it) },
              title = { Text(stringResource(R.string.danmaku_density_title)) },
              summary = { Text(densitySummary(density)) },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.LAST) {
            SliderPreference(
              value = scrollSpeed,
              valueRange = 0.5f..2f,
              steps = 15,
              onValueChangeFinished = { },
              onValueChange = { preferences.scrollSpeed.set(it) },
              title = {
                Text(stringResource(R.string.danmaku_scroll_speed_title))
              },
              summary = {
                Text("${scrollSpeed}x")
              },
            )
          }
        }
      }
    }
  }

  @Composable
  private fun entriesSummary(value: Int): String {
    return when (value) {
      0 -> stringResource(R.string.danmaku_area_all)
      1 -> stringResource(R.string.danmaku_area_top)
      2 -> stringResource(R.string.danmaku_area_bottom)
      3 -> stringResource(R.string.danmaku_area_middle)
      else -> ""
    }
  }

  @Composable
  private fun densitySummary(value: Int): String {
    return when (value) {
      0 -> stringResource(R.string.danmaku_density_sparse)
      1 -> stringResource(R.string.danmaku_density_normal)
      2 -> stringResource(R.string.danmaku_density_dense)
      else -> ""
    }
  }
}