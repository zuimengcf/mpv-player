package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.media.MediaLibraryEvents
import xyz.mpv.rex.utils.storage.FileTypeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject

@Serializable
object CustomVideoExtensionsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
  @Composable
  override fun Content() {
    val browserPreferences = koinInject<BrowserPreferences>()
    val hybridMediaIndex = koinInject<HybridMediaIndexRepository>()
    val backstack = LocalBackStack.current
    val scope = rememberCoroutineScope()

    val customRaw by browserPreferences.customVideoExtensions.collectAsState()
    var input by remember { mutableStateOf("") }

    val customSet = remember(customRaw) { FileTypeUtils.parseExtensions(customRaw) }
    val builtInSet = FileTypeUtils.VIDEO_EXTENSIONS

    fun applyExtensions(raw: String) {
      browserPreferences.customVideoExtensions.set(raw)
      FileTypeUtils.setCustomVideoExtensions(raw)
      MediaLibraryEvents.notifyChanged()
      scope.launch(Dispatchers.IO) {
        runCatching { hybridMediaIndex.ensureFresh(force = true, userInitiated = true) }
      }
    }

    fun addExtension(ext: String) {
      val cleaned = ext.trim().removePrefix(".").lowercase()
      if (cleaned.isEmpty()) return
      val updated = (customSet + cleaned).sorted().joinToString(", ")
      applyExtensions(updated)
      input = ""
    }

    fun removeExtension(ext: String) {
      val updated = (customSet - ext).sorted().joinToString(", ")
      applyExtensions(updated)
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.pref_custom_video_extensions_title),
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
      val navBarHeight = LocalNavigationBarHeight.current
      ProvidePreferenceLocals {
        LazyColumn(
          state = rememberPreferenceLazyListState(),
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentPadding = PaddingValues(bottom = navBarHeight + 16.dp),
        ) {
          // 说明文字
          item {
            Text(
              text = stringResource(R.string.pref_custom_video_extensions_hint),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            )
          }

          // 自定义扩展名 Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_custom_video_extensions_custom_title))
          }

          item {
            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.FIRST,
                highlightKey = R.string.pref_custom_video_extensions_custom_title,
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.pref_custom_video_extensions_editor_title)) },
                    placeholder = { Text(stringResource(R.string.pref_custom_video_extensions_placeholder)) },
                    singleLine = true,
                  )
                  Button(
                    onClick = { addExtension(input) },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(stringResource(R.string.pref_custom_video_extensions_add))
                  }
                }
              }

              GroupedPreferenceCard(
                position = GroupPosition.LAST,
                highlightKey = R.string.pref_custom_video_extensions_custom_title,
              ) {
                if (customSet.isEmpty()) {
                  Preference(
                    title = {
                      Text(
                        text = stringResource(R.string.pref_custom_video_extensions_custom_empty),
                        color = MaterialTheme.colorScheme.outline,
                      )
                    },
                    onClick = null,
                  )
                } else {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp, vertical = 12.dp),
                  ) {
                    FlowRow(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      customSet.sorted().forEach { ext ->
                        InputChip(
                          selected = true,
                          onClick = { removeExtension(ext) },
                          label = { Text(".$ext") },
                          trailingIcon = {
                            Icon(
                              Icons.Filled.Close,
                              contentDescription = stringResource(R.string.pref_custom_video_extensions_remove, ext),
                              modifier = Modifier.padding(start = 4.dp),
                            )
                          },
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          // 内置扩展名 Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_custom_video_extensions_builtin_title))
          }

          item {
            GroupedListColumn {
              GroupedPreferenceCard(
                position = GroupPosition.ONLY,
                highlightKey = R.string.pref_custom_video_extensions_builtin_title,
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                  FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    builtInSet.sorted().forEach { ext ->
                      AssistChip(
                        onClick = {},
                        label = { Text(".$ext") },
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}