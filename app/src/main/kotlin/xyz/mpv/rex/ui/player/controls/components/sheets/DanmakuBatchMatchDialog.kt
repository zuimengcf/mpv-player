package xyz.mpv.rex.ui.player.controls.components.sheets

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.DanmakuPreferences
import xyz.mpv.rex.repository.dandanplay.DanDanPlayApi
import xyz.mpv.rex.ui.player.PlayerActivity
import java.io.File

/**
 * 批量匹配弹幕对话框：对当前播放列表的所有本地视频做哈希匹配，
 * 匹配成功后下载弹幕并缓存到视频同目录同名 .xml。
 * 文件名含集数，哈希匹配命中对应集弹幕库即完成集数分配。
 */
@Composable
fun DanmakuBatchMatchDialog(
    activity: PlayerActivity,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val api = koinInject<DanDanPlayApi>()
    val danmakuPrefs = koinInject<DanmakuPreferences>()
    val scope = rememberCoroutineScope()

    // 播放列表本地文件：仅收集 scheme=file 的本地视频
    val localVideos = remember {
        activity.viewModel.playlistManager.playlist.value.mapNotNull { uri ->
            if (uri.scheme == "file") {
                val path = uri.path
                if (path != null && File(path).exists()) {
                    Triple(uri, path, File(path).length())
                } else null
            } else null
        }
    }

    var isMatching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Pair<String, MatchResultStatus>>>(emptyList()) }

    sealed class MatchResultStatus {
        data class Failed(val message: String) : MatchResultStatus()
        object Cached : MatchResultStatus()
    }

    fun startMatch() {
        if (localVideos.isEmpty()) {
            Toast.makeText(context, "播放列表中没有本地视频", Toast.LENGTH_SHORT).show()
            return
        }
        isMatching = true
        scope.launch {
            val mutable = mutableListOf<Pair<String, MatchResultStatus>>()
            // 逐文件匹配（batchMatch 每批 ≤32，这里为简化逐文件处理）
            for ((uri, path, size) in localVideos) {
                val fileName = File(path).name
                try {
                    val hash = api.calculateFileHash(path)
                    // 文件名（含集数）+ 哈希匹配对应集弹幕库
                    val resp = api.matchDanmaku(fileName, hash, size)
                    if (resp.isMatched && !resp.matches.isNullOrEmpty()) {
                        val match = resp.matches.first()
                        // 命中即下载该集弹幕并缓存到视频同目录同名 .xml，完成集数分配
                        val danmaku = api.getDanmaku(match.episodeId, danmakuPrefs.chConvert.get())
                        if (danmaku.isSuccess) {
                            val xml = api.convertToXml(danmaku.getOrThrow())
                            activity.danmakuManager.setCurrentVideoPath(path)
                            val loaded = activity.danmakuManager.loadDanmakuFromXml(xml, match.animeTitle)
                            if (loaded) {
                                mutable.add(fileName to MatchResultStatus.Cached)
                            } else {
                                mutable.add(fileName to MatchResultStatus.Failed("弹幕写入失败"))
                            }
                        } else {
                            mutable.add(fileName to MatchResultStatus.Failed("下载弹幕失败"))
                        }
                    } else {
                        mutable.add(fileName to MatchResultStatus.Failed("未匹配到"))
                    }
                } catch (e: Exception) {
                    mutable.add(fileName to MatchResultStatus.Failed(e.message ?: "错误"))
                }
            }
            results = mutable
            isMatching = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isMatching) onDismiss() },
        title = { Text(stringResource(R.string.danmaku_batch_match_button)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 进度 / 结果
                if (isMatching) {
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        Text("正在批量匹配并缓存弹幕…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (results.isNotEmpty()) {
                    Text("匹配结果：", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(results) { (fileName, status) ->
                            ListItem(
                                headlineContent = { Text(fileName, maxLines = 1) },
                                supportingContent = {
                                    Text(
                                        when (status) {
                                            is MatchResultStatus.Cached -> "✓ 已缓存"
                                            is MatchResultStatus.Failed -> "✗ ${(status as MatchResultStatus.Failed).message}"
                                        },
                                        color = if (status is MatchResultStatus.Failed) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        if (status is MatchResultStatus.Failed) Icons.Default.Close else Icons.Default.Check,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }
                } else {
                    Text("将匹配播放列表中的 ${localVideos.size} 个本地视频，匹配成功后弹幕会缓存到视频同目录同名 .xml。", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (!isMatching) startMatch() },
                enabled = !isMatching,
            ) {
                Text(if (results.isEmpty()) "开始匹配" else "重新匹配")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isMatching) onDismiss() }) { Text("关闭") }
        },
    )
}
