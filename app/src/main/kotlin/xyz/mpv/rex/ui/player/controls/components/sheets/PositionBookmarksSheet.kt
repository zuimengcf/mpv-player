package xyz.mpv.rex.ui.player.controls.components.sheets

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.presentation.components.PlayerSheet
import xyz.mpv.rex.ui.theme.spacing

/**
 * 播放位置书签（4 槽）：
 * - 每个槽可保存当前播放位置、跳回、清除
 * - 槽位与当前文件关联（存了文件名标识），换文件后自动失效
 */
@Composable
fun PositionBookmarksSheet(
  currentPositionSeconds: Float,
  currentFileKey: String,
  bookmarks: List<Pair<Float, String>>, // (positionSeconds, fileKey)
  onSave: (Int) -> Unit,
  onSeek: (Int) -> Unit,
  onClear: (Int) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  PlayerSheet(onDismissRequest = onDismissRequest) {
    Column(
      modifier = modifier
        .verticalScroll(rememberScrollState())
        .padding(vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      // 标题
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MaterialTheme.spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = stringResource(R.string.position_bookmarks_title),
          style = MaterialTheme.typography.bodyMedium
        )
        Text(
          text = stringResource(R.string.position_bookmarks_current_pos, formatTime(currentPositionSeconds)),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // 4 个槽位
      repeat(4) { slot ->
        val (savedPos, savedFile) = bookmarks.getOrElse(slot) { -1f to "" }
        val isValid = savedPos >= 0f && savedFile == currentFileKey
        BookmarkSlotRow(
          slot = slot,
          savedPosition = if (isValid) savedPos else null,
          onSave = { onSave(slot) },
          onSeek = { onSeek(slot) },
          onClear = { onClear(slot) },
        )
      }
    }
  }
}

@Composable
private fun BookmarkSlotRow(
  slot: Int,
  savedPosition: Float?,
  onSave: () -> Unit,
  onSeek: () -> Unit,
  onClear: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MaterialTheme.spacing.medium, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = stringResource(R.string.position_bookmarks_slot, slot + 1) +
        (savedPosition?.let { " · " + formatTime(it) } ?: " · " + stringResource(R.string.position_bookmarks_empty)),
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = if (savedPosition != null) FontWeight.Medium else FontWeight.Normal,
      modifier = Modifier.weight(1f),
    )
    if (savedPosition != null) {
      // 跳回
      OutlinedButton(onClick = onSeek, modifier = Modifier.size(width = 96.dp, height = 36.dp)) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Text(stringResource(R.string.position_bookmarks_go), style = MaterialTheme.typography.labelMedium)
      }
      // 清除
      IconButton(onClick = onClear) {
        Icon(
          imageVector = Icons.Default.Clear,
          contentDescription = stringResource(R.string.position_bookmarks_clear),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      // 保存当前
      IconButton(onClick = onSave) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = stringResource(R.string.position_bookmarks_save),
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

private fun formatTime(seconds: Float): String =
  DateUtils.formatElapsedTime(seconds.toLong().coerceAtLeast(0L))
