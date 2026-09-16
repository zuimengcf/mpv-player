package xyz.mpv.rex.ui.player.controls.components.panels

import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preferenceTheme
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.DanmakuPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.components.PlayerSheet
import xyz.mpv.rex.presentation.components.SliderItem
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.player.controls.components.sheets.DanmakuSearchDialog
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import xyz.mpv.rex.ui.theme.spacing

/**
 * 弹幕设置面板 — 播放器内入口（竖屏底部弹出 / 横屏右侧滑出），
 * 参考 SubtitleSettingsPanel 结构。
 */
@Composable
fun DanmakuSettingsPanel(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  activity: PlayerActivity? = null,
) {
  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

  if (isPortrait) {
    PlayerSheet(onDismissRequest = onDismissRequest) {
      // 竖屏 sheet 内容需可滚动（PlayerSheet 本身不滚动，内容超高时滑不动）
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
      ) {
        DanmakuSettingsContent(modifier, activity)
      }
    }
  } else {
    DanmakuSettingsSideSheet(onDismissRequest = onDismissRequest, modifier = modifier, activity = activity)
  }
}

@Composable
private fun DanmakuSettingsSideSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  activity: PlayerActivity? = null,
) {
  val scope = rememberCoroutineScope()
  var isVisible by remember { mutableStateOf(false) }
  val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)

  val scrimAlpha by animateFloatAsState(
    targetValue = if (isVisible) 0.5f else 0f,
    animationSpec = tween(durationMillis = 280),
    label = "danmaku_side_sheet_scrim",
  )

  val dismissWithAnimation: () -> Unit = {
    scope.launch {
      isVisible = false
      delay(250)
      latestOnDismissRequest()
    }
  }

  BackHandler(enabled = isVisible, onBack = { dismissWithAnimation() })

  LaunchedEffect(Unit) {
    isVisible = true
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = scrimAlpha))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { dismissWithAnimation() },
      ),
    contentAlignment = Alignment.CenterEnd,
  ) {
    AnimatedVisibility(
      visible = isVisible,
      enter = slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
      ) + fadeIn(animationSpec = tween(durationMillis = 200)),
      exit = slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(durationMillis = 250, easing = FastOutLinearInEasing),
      ) + fadeOut(animationSpec = tween(durationMillis = 200)),
    ) {
      Surface(
        modifier = modifier
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {}, // Prevent clicking through sheet to dismiss
          )
          .widthIn(max = 380.dp)
          .fillMaxHeight()
          .windowInsetsPadding(
            WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.End),
          ),
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        ) {
          DanmakuSettingsHeader(onDismissRequest = { dismissWithAnimation() }, activity = activity)
        }
      }
    }
  }
}

@Composable
private fun DanmakuSettingsHeader(onDismissRequest: () -> Unit, activity: PlayerActivity? = null) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MaterialTheme.spacing.medium)
      .padding(top = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.small),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = stringResource(R.string.danmaku_settings_title),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
    )
    IconButton(onClick = onDismissRequest) {
      Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(24.dp))
    }
  }

  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

  Spacer(Modifier.height(MaterialTheme.spacing.small))

  DanmakuSettingsContent(activity = activity)
}

@Composable
private fun DanmakuSettingsContent(modifier: Modifier = Modifier, activity: PlayerActivity? = null) {
  val preferences = koinInject<DanmakuPreferences>()
  val context = androidx.compose.ui.platform.LocalContext.current

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = MaterialTheme.spacing.medium)
      .padding(bottom = MaterialTheme.spacing.large),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    // ── 加载弹幕 ──
    SectionTitle(stringResource(R.string.danmaku_section_show), Icons.Default.ChatBubbleOutline)

    if (activity != null) {
      DanmakuLoadSection(activity)
    }

    ProvidePreferenceLocals(theme = preferenceTheme(iconContainerMinWidth = 48.dp)) {
      val showScroll by preferences.showScrollDanmaku.collectAsState()
      SwitchPreference(
        showScroll,
        onValueChange = { preferences.showScrollDanmaku.set(it) },
        { Text(stringResource(R.string.danmaku_show_scroll_title)) },
        summary = { Text(stringResource(R.string.danmaku_show_scroll_summary)) },
      )
      val showTop by preferences.showTopDanmaku.collectAsState()
      SwitchPreference(
        showTop,
        onValueChange = { preferences.showTopDanmaku.set(it) },
        { Text(stringResource(R.string.danmaku_show_top_title)) },
        summary = { Text(stringResource(R.string.danmaku_show_top_summary)) },
      )
      val showBottom by preferences.showBottomDanmaku.collectAsState()
      SwitchPreference(
        showBottom,
        onValueChange = { preferences.showBottomDanmaku.set(it) },
        { Text(stringResource(R.string.danmaku_show_bottom_title)) },
        summary = { Text(stringResource(R.string.danmaku_show_bottom_summary)) },
      )
      val enabledByDefault by preferences.enabledByDefault.collectAsState()
      SwitchPreference(
        enabledByDefault,
        onValueChange = { preferences.enabledByDefault.set(it) },
        { Text(stringResource(R.string.danmaku_enabled_by_default_title)) },
        summary = { Text(stringResource(R.string.danmaku_enabled_by_default_summary)) },
      )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

    // ── 外观 ──
    SectionTitle(stringResource(R.string.danmaku_section_appearance), Icons.Default.Tune)

    val fontSize by preferences.fontSize.collectAsState()
    SliderItem(
      label = stringResource(R.string.danmaku_font_size_title),
      value = fontSize,
      valueText = fontSize.toString(),
      onChange = { preferences.fontSize.set(it) },
      max = 40,
      min = 12,
      icon = { Icon(Icons.Default.FormatSize, null) },
    )

    val alpha by preferences.alpha.collectAsState()
    SliderItem(
      label = stringResource(R.string.danmaku_alpha_title),
      value = alpha,
      valueText = alpha.toString(),
      onChange = { preferences.alpha.set(it) },
      max = 255,
      min = 0,
      icon = { Icon(Icons.Default.Opacity, null) },
    )

    val borderSize by preferences.borderSize.collectAsState()
    SliderItem(
      label = stringResource(R.string.danmaku_border_size_title),
      value = borderSize,
      valueText = borderSize.toString(),
      onChange = { preferences.borderSize.set(it) },
      max = 5,
      min = 0,
      icon = { Icon(Icons.Default.Tune, null) },
    )

    val shadowRadius by preferences.shadowRadius.collectAsState()
    SliderItem(
      label = stringResource(R.string.danmaku_shadow_radius_title),
      value = shadowRadius,
      valueText = shadowRadius.toString(),
      onChange = { preferences.shadowRadius.set(it) },
      max = 10,
      min = 0,
      icon = { Icon(Icons.Default.Tune, null) },
    )

    // ── 弹幕颜色覆盖 ──
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    Spacer(Modifier.height(MaterialTheme.spacing.smaller))

    ProvidePreferenceLocals(theme = preferenceTheme(iconContainerMinWidth = 48.dp)) {
      val overrideColor by preferences.overrideColor.collectAsState()
      SwitchPreference(
        overrideColor,
        onValueChange = { preferences.overrideColor.set(it) },
        { Text(stringResource(R.string.danmaku_override_color_title)) },
        summary = { Text(stringResource(R.string.danmaku_override_color_summary)) },
      )
    }

    if (preferences.overrideColor.get()) {
      DanmakuColorPicker(
        currentColor = preferences.fontColor.get(),
        onColorSelected = { preferences.fontColor.set(it) },
      )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

    // ── 行为 ──
    SectionTitle(stringResource(R.string.danmaku_section_behavior), Icons.Default.Speed)

    ProvidePreferenceLocals(theme = preferenceTheme(iconContainerMinWidth = 48.dp)) {
      val density by preferences.density.collectAsState()
      ListPreference(
        density,
        onValueChange = { preferences.density.set(it) },
        title = { Text(stringResource(R.string.danmaku_density_title)) },
        valueToText = { AnnotatedString(densityLabel(it, context)) },
        values = listOf(0, 1, 2),
        type = ListPreferenceType.DROPDOWN_MENU,
        summary = { Text(densityLabel(density, context)) },
        icon = { Icon(Icons.Default.ViewAgenda, null) },
      )

      val displayArea by preferences.displayArea.collectAsState()
      ListPreference(
        displayArea,
        onValueChange = { preferences.displayArea.set(it) },
        title = { Text(stringResource(R.string.danmaku_area_title)) },
        valueToText = { AnnotatedString(areaLabel(it, context)) },
        values = listOf(0, 1, 2, 3),
        type = ListPreferenceType.DROPDOWN_MENU,
        summary = { Text(areaLabel(displayArea, context)) },
        icon = { Icon(Icons.Default.ViewAgenda, null) },
      )
    }

    val scrollSpeed by preferences.scrollSpeed.collectAsState()
    SliderItem(
      label = stringResource(R.string.danmaku_scroll_speed_title),
      value = (scrollSpeed * 10).toInt(),
      valueText = String.format("%.1fx", scrollSpeed),
      onChange = { preferences.scrollSpeed.set(it / 10f) },
      max = 20,
      min = 5,
      icon = { Icon(Icons.Default.Speed, null) },
    )
  }
}

@Composable
private fun SectionTitle(text: String, icon: ImageVector) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = MaterialTheme.spacing.small),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
  ) {
    Icon(
      icon,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = MaterialTheme.colorScheme.primary,
    )
    Text(
      text = text,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

private fun densityLabel(value: Int, context: android.content.Context): String = when (value) {
  0 -> context.getString(R.string.danmaku_density_sparse)
  1 -> context.getString(R.string.danmaku_density_normal)
  else -> context.getString(R.string.danmaku_density_dense)
}

private fun areaLabel(value: Int, context: android.content.Context): String = when (value) {
   1 -> context.getString(R.string.danmaku_area_top)
   2 -> context.getString(R.string.danmaku_area_bottom)
   3 -> context.getString(R.string.danmaku_area_middle)
   else -> context.getString(R.string.danmaku_area_all)
 }

@Composable
private fun DanmakuColorPicker(
  currentColor: Int,
  onColorSelected: (Int) -> Unit,
) {
  val presetColors = listOf(
    0xFFFFFFFF.toInt() to "White",
    0xFFFF0000.toInt() to "Red",
    0xFFFFAA00.toInt() to "Orange",
    0xFFFFFF00.toInt() to "Yellow",
    0xFF00FF00.toInt() to "Green",
    0xFF00FFFF.toInt() to "Cyan",
    0xFF0000FF.toInt() to "Blue",
    0xFFFF00FF.toInt() to "Magenta",
    0xFF000000.toInt() to "Black",
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.smaller),
  ) {
    Text(
      text = stringResource(R.string.danmaku_font_color_title),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 8.dp),
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      presetColors.forEach { (color, _) ->
        val isSelected = currentColor == color
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color(color))
            .clickable { onColorSelected(color) }
            .then(
              if (isSelected) {
                Modifier.border(
                  2.dp,
                  MaterialTheme.colorScheme.primary,
                  androidx.compose.foundation.shape.CircleShape,
                )
              } else {
                Modifier.border(
                  1.dp,
                  MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                  androidx.compose.foundation.shape.CircleShape,
                )
              }
            ),
        )
      }
    }
  }
}

/**
 * 弹幕加载区块：在线搜索（弹弹play）+ 导入本地 XML + 显示开关。
 * 逻辑与 MoreSheet 的 DanmakuSection 一致，入口统一收进播放器弹幕面板。
 */
@Composable
private fun DanmakuLoadSection(activity: PlayerActivity) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val danmakuManager = activity.danmakuManager
  var showSearchDialog by remember { mutableStateOf(false) }
  var danmakuVisible by remember { mutableStateOf(danmakuManager.isTrackSelected()) }

  val danmakuFilePicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      val success = try {
        val input = context.contentResolver.openInputStream(uri)
        if (input == null) false else {
          // 写入持久目录（filesDir），确保绑定弹幕不被系统缓存清理
          val dir = File(context.filesDir, "danmaku")
          if (!dir.exists()) dir.mkdirs()
          val outFile = File(dir, "local_${System.currentTimeMillis()}.xml")
          FileOutputStream(outFile).use { out -> input.copyTo(out) }
          input.close()
          danmakuManager.loadDanmaku(outFile.absolutePath, "本地弹幕")
        }
      } catch (e: Exception) {
        Toast.makeText(context, "导入弹幕失败: ${e.message}", Toast.LENGTH_SHORT).show()
        false
      }
      if (success) {
        danmakuVisible = true
        danmakuManager.showDanmaku()
        activity.saveDanmakuBinding()
        Toast.makeText(context, R.string.danmaku_toast_loaded, Toast.LENGTH_SHORT).show()
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = MaterialTheme.spacing.smaller),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    // 在线搜索弹幕（弹弹play）
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier = Modifier.fillMaxWidth(),
    ) {
      ListItem(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { showSearchDialog = true },
        leadingContent = {
          Icon(
            imageVector = Icons.Default.Subtitles,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        },
        headlineContent = {
          Text(
            text = stringResource(R.string.danmaku_load_button),
            style = MaterialTheme.typography.bodyLarge,
          )
        },
        trailingContent = {
          if (danmakuManager.isDanmakuLoaded()) {
            IconButton(onClick = {
              // 手动解除：释放弹幕并清空持久化绑定
              danmakuManager.releaseDanmaku()
              danmakuVisible = false
              activity.clearDanmakuBinding()
              Toast.makeText(context, R.string.danmaku_toast_removed, Toast.LENGTH_SHORT).show()
            }) {
              Icon(imageVector = Icons.Default.Close, contentDescription = null)
            }
          }
        },
      )
    }

    // 导入本地弹幕文件（XML）
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier = Modifier.fillMaxWidth(),
    ) {
      ListItem(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { danmakuFilePicker.launch(arrayOf("application/xml", "text/xml", "text/plain", "*/*")) },
        leadingContent = {
          Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        },
        headlineContent = {
          Text(
            text = stringResource(R.string.danmaku_import_button),
            style = MaterialTheme.typography.bodyLarge,
          )
        },
      )
    }

    // 显示开关（仅弹幕已加载时可用）
    val loaded = danmakuManager.isDanmakuLoaded()
    ProvidePreferenceLocals(theme = preferenceTheme(iconContainerMinWidth = 48.dp)) {
      SwitchPreference(
        value = danmakuVisible,
        onValueChange = { on ->
          danmakuVisible = on
          if (on) danmakuManager.showDanmaku() else danmakuManager.hideDanmaku()
          // 显示开关切换也持久化（绑定但可临时隐藏）
          activity.saveDanmakuBinding()
        },
        title = { Text(stringResource(R.string.danmaku_show_switch)) },
        summary = { Text(stringResource(R.string.danmaku_show_switch_summary)) },
        enabled = loaded,
      )
    }
  }

  if (showSearchDialog) {
    DanmakuSearchDialog(
      onDismiss = { showSearchDialog = false },
      onDanmakuXml = { xml, title ->
        if (danmakuManager.loadDanmakuFromXml(xml, title)) {
          danmakuVisible = true
          activity.saveDanmakuBinding()
          Toast.makeText(context, "已加载弹幕: $title", Toast.LENGTH_SHORT).show()
        }
        showSearchDialog = false
      },
      initialKeyword = activity.fileName,
    )
  }
}