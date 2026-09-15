package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.DanmakuPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import xyz.mpv.rex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
object DanmakuPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = androidx.compose.ui.platform.LocalContext.current
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

        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        ) {
          // ── 显示控制 ──
          GroupedPreferenceCard(position = GroupPosition.FIRST) {
            SwitchPreference(
              value = enabledByDefault,
              onValueChange = { preferences.enabledByDefault.set(it) },
              title = { Text(stringResource(R.string.danmaku_enabled_by_default_title)) },
              summary = { Text(stringResource(R.string.danmaku_enabled_by_default_summary)) },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.MIDDLE) {
            SwitchPreference(
              value = showScroll,
              onValueChange = { preferences.showScrollDanmaku.set(it) },
              title = { Text(stringResource(R.string.danmaku_show_scroll_title)) },
              summary = { Text(stringResource(R.string.danmaku_show_scroll_summary)) },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.MIDDLE) {
            SwitchPreference(
              value = showTop,
              onValueChange = { preferences.showTopDanmaku.set(it) },
              title = { Text(stringResource(R.string.danmaku_show_top_title)) },
              summary = { Text(stringResource(R.string.danmaku_show_top_summary)) },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.LAST) {
            SwitchPreference(
              value = showBottom,
              onValueChange = { preferences.showBottomDanmaku.set(it) },
              title = { Text(stringResource(R.string.danmaku_show_bottom_title)) },
              summary = { Text(stringResource(R.string.danmaku_show_bottom_summary)) },
            )
          }

          // ── 外观 ──
          GroupedPreferenceCard(position = GroupPosition.FIRST) {
            SliderPreference(
              value = fontSize.toFloat(),
              onValueChange = { preferences.fontSize.set(it.toInt()) },
              sliderValue = fontSize.toFloat(),
              onSliderValueChange = { preferences.fontSize.set(it.toInt()) },
              title = { Text(stringResource(R.string.danmaku_font_size_title)) },
              valueRange = 12f..40f,
              summary = { Text("${fontSize} sp") },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.MIDDLE) {
            SliderPreference(
              value = alpha.toFloat(),
              onValueChange = { preferences.alpha.set(it.toInt()) },
              sliderValue = alpha.toFloat(),
              onSliderValueChange = { preferences.alpha.set(it.toInt()) },
              title = { Text(stringResource(R.string.danmaku_alpha_title)) },
              valueRange = 0f..255f,
              summary = { Text("${(alpha * 100 / 255)}%") },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.MIDDLE) {
            SliderPreference(
              value = borderSize.toFloat(),
              onValueChange = { preferences.borderSize.set(it.toInt()) },
              sliderValue = borderSize.toFloat(),
              onSliderValueChange = { preferences.borderSize.set(it.toInt()) },
              title = { Text(stringResource(R.string.danmaku_border_size_title)) },
              valueRange = 0f..5f,
              summary = { Text("$borderSize") },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.LAST) {
            SliderPreference(
              value = shadowRadius.toFloat(),
              onValueChange = { preferences.shadowRadius.set(it.toInt()) },
              sliderValue = shadowRadius.toFloat(),
              onSliderValueChange = { preferences.shadowRadius.set(it.toInt()) },
              title = { Text(stringResource(R.string.danmaku_shadow_radius_title)) },
              valueRange = 0f..10f,
              summary = { Text("$shadowRadius") },
            )
          }

          // ── 行为 ──
          GroupedPreferenceCard(position = GroupPosition.FIRST) {
            ListPreference(
              value = displayArea,
              onValueChange = { preferences.displayArea.set(it) },
              values = listOf(0, 1, 2, 3),
              valueToText = { AnnotatedString(areaLabel(it, context)) },
              title = { Text(stringResource(R.string.danmaku_area_title)) },
              summary = { Text(areaLabel(displayArea, context)) },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.MIDDLE) {
            ListPreference(
              value = density,
              onValueChange = { preferences.density.set(it) },
              values = listOf(0, 1, 2),
              valueToText = { AnnotatedString(densityLabel(it, context)) },
              title = { Text(stringResource(R.string.danmaku_density_title)) },
              summary = { Text(densityLabel(density, context)) },
            )
          }
          GroupedPreferenceCard(position = GroupPosition.LAST) {
            SliderPreference(
              value = scrollSpeed,
              onValueChange = { preferences.scrollSpeed.set(it) },
              sliderValue = scrollSpeed,
              onSliderValueChange = { preferences.scrollSpeed.set(it) },
              title = { Text(stringResource(R.string.danmaku_scroll_speed_title)) },
              valueRange = 0.5f..2f,
              summary = { Text(String.format("%.1fx", scrollSpeed)) },
            )
          }
        }
      }
    }
  }

  private fun areaLabel(value: Int, context: android.content.Context): String = context.getString(
    when (value) {
      1 -> R.string.danmaku_area_top
      2 -> R.string.danmaku_area_bottom
      3 -> R.string.danmaku_area_middle
      else -> R.string.danmaku_area_all
    }
  )

  private fun densityLabel(value: Int, context: android.content.Context): String = context.getString(
    when (value) {
      0 -> R.string.danmaku_density_sparse
      2 -> R.string.danmaku_density_dense
      else -> R.string.danmaku_density_normal
    }
  )
}