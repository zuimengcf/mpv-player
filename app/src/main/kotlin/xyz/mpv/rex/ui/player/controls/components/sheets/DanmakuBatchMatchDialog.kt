package xyz.mpv.rex.ui.player.controls.components.sheets

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Subtitles
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
import xyz.mpv.rex.repository.dandanplay.MatchInfo
import xyz.mpv.rex.ui.player.PlayerActivity
import java.io.File

/**
 * 批量匹配弹幕对话框：对当前播放列表的所有本地视频做哈希匹配，
 * 先列出每个文件匹配到的"番剧名 + 集数"供用户审阅，确认后再批量下载缓存。
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

    var isMatching by remember { mutableStateOf(false) }   // 阶段1：匹配预览中
    var isCaching by remember { mutableStateOf(false) }    // 阶段2：批量缓存中
    // 匹配预览结果：文件名 -> 匹配信息或失败原因
    var matches by remember { mutableStateOf<List<Pair<String, MatchInfo?>>>(emptyList()) }
    // 缓存结果：文件名 -> 状态
    var cacheResults by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }

    // 阶段1：只做哈希匹配，列出"番剧名 + 集数"供审阅，不下载
    fun startMatch() {
        if (localVideos.isEmpty()) {
            Toast.makeText(context, "播放列表中没有本地视频", Toast.LENGTH_SHORT).show()
            return
        }
        isMatching = true
        scope.launch {
            val mutable = mutableListOf<Pair<String, MatchInfo?>>()
            for ((_, path, size) in localVideos) {
                val fileName = File(path).name
                try {
                    val hash = api.calculateFileHash(path)
                    val resp = api.matchDanmaku(fileName, hash, size)
                    if (resp.isMatched && !resp.matches.isNullOrEmpty()) {
                        mutable.add(fileName to resp.matches.first())
                    } else {
                        mutable.add(fileName to null)
                    }
                } catch (e: Exception) {
                    mutable.add(fileName to null)
                }
            }
            matches = mutable
            isMatching = false
        }
    }

    // 阶段2：用户确认后，对所有匹配到的文件批量下载并缓存
    fun startCache() {
        isCaching = true
        cacheResults = emptyList()
        scope.launch {
            val mutable = mutableListOf<Pair<String, Boolean>>()
            for ((_, path, _) in localVideos) {
                val fileName = File(path).name
                val match = matches.firstOrNull { it.first == fileName }?.second
                if (match == null) {
                    mutable.add(fileName to false)
                    continue
                }
                try {
                    val danmaku = api.getDanmaku(match.episodeId, danmakuPrefs.chConvert.get())
                    if (danmaku.isSuccess) {
                        val xml = api.convertToXml(danmaku.getOrThrow())
                        activity.danmakuManager.setCurrentVideoPath(path)
                        val loaded = activity.danmakuManager.loadDanmakuFromXml(xml, match.animeTitle)
                        mutable.add(fileName to loaded)
                    } else {
                        mutable.add(fileName to false)
                    }
                } catch (e: Exception) {
                    mutable.add(fileName to false)
                }
            }
            cacheResults = mutable
            isCaching = false
        }
    }

    val matchedCount = matches.count { it.second != null }

    AlertDialog(
        onDismissRequest = { if (!isMatching && !isCaching) onDismiss() },
        title = { Text(stringResource(R.string.danmaku_batch_match_button)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    // 阶段2：缓存进度 / 结果
                    isCaching -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                            Text("正在批量下载并缓存弹幕…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    cacheResults.isNotEmpty() -> {
                        Text("缓存结果：", style = MaterialTheme.typography.labelLarge)
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                            items(cacheResults) { (fileName, ok) ->
                                ListItem(
                                    headlineContent = { Text(fileName, maxLines = 1) },
                                    supportingContent = {
                                        Text(
                                            if (ok) "✓ 已缓存" else "✗ 缓存失败",
                                            color = if (ok) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            if (ok) Icons.Default.Check else Icons.Default.Close,
                                            contentDescription = null,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    // 阶段1：匹配预览
                    isMatching -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                            Text("正在匹配番剧…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    // 匹配完成：展示"番剧名 + 集数"，等待确认
                    matches.isNotEmpty() -> {
                        Text("匹配到 $matchedCount/${matches.size} 个视频，确认后批量缓存：", style = MaterialTheme.typography.labelLarge)
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                            items(matches, key = { it.first }) { (fileName, match) ->
                                ListItem(
                                    headlineContent = { Text(fileName, maxLines = 1) },
                                    supportingContent = {
                                        if (match != null) {
                                            Text("${match.animeTitle} · ${match.episodeTitle}")
                                        } else {
                                            Text("✗ 未匹配到", color = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    leadingContent = {
                                        Icon(
                                            if (match != null) Icons.Default.Subtitles else Icons.Default.Close,
                                            contentDescription = null,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    else -> {
                        Text("将匹配播放列表中的 ${localVideos.size} 个本地视频，匹配出「番剧名 + 集数」展示给你确认后，再批量缓存弹幕。", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            when {
                // 有匹配预览时：确认按钮 = 批量缓存
                matches.isNotEmpty() && !isMatching -> {
                    TextButton(
                        onClick = { startCache() },
                        enabled = !isCaching && matchedCount > 0,
                    ) { Text("批量缓存") }
                }
                else -> {
                    TextButton(onClick = { if (!isMatching && !isCaching) onDismiss() }) { Text("关闭") }
                }
            }
        },
        dismissButton = {
            when {
                matches.isNotEmpty() && !isMatching -> {
                    // 已匹配后，dismiss 位 = 重新匹配
                    TextButton(
                        onClick = { if (!isCaching) startMatch() },
                        enabled = true,
                    ) { Text("重新匹配") }
                }
                else -> {
                    TextButton(onClick = { if (!isMatching && !isCaching) startMatch() }) { Text("开始匹配") }
                }
            }
        },
    )
}