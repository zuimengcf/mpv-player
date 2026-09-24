package xyz.mpv.rex.ui.player.controls.components.sheets

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import xyz.mpv.rex.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.AdvancedPreferences
import xyz.mpv.rex.preferences.DanmakuPreferences
import xyz.mpv.rex.repository.dandanplay.DanDanPlayApi
import xyz.mpv.rex.repository.dandanplay.AnimeSearchInfo
import xyz.mpv.rex.repository.dandanplay.EpisodeInfo
import xyz.mpv.rex.utils.media.MediaInfoParser

/**
 * 弹弹play 在线弹幕搜索对话框：配置凭证 → 搜索动漫 → 选剧集 → 回调加载。
 * @param initialKeyword 初始搜索词（通常为当前播放文件名），打开时自动填充。
 */
@Composable
fun DanmakuSearchDialog(
    onDismiss: () -> Unit,
    onDanmakuXml: (xml: String, title: String) -> Unit,
    initialKeyword: String = "",
) {
    val context = LocalContext.current
    val api = koinInject<DanDanPlayApi>()
    val advancedPrefs = koinInject<AdvancedPreferences>()
    val danmakuPrefs = koinInject<DanmakuPreferences>()
    val scope = rememberCoroutineScope()

    var showCredentialInput by remember { mutableStateOf(!api.hasCredentials()) }
    var appIdInput by remember { mutableStateOf(advancedPrefs.dandanplayAppId.get()) }
    var appSecretInput by remember { mutableStateOf(advancedPrefs.dandanplayAppSecret.get()) }

    // 从当前播放文件名清洗出 1-3 个候选搜索词（用 MediaInfoParser 剥离发布组/分辨率/编码/集数等噪声）
    val candidates = remember(initialKeyword) {
        generateSearchCandidates(initialKeyword)
    }
    // 最接近的候选（第一个）预填
    var keyword by remember { mutableStateOf(candidates.firstOrNull().orEmpty()) }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<AnimeSearchInfo>>(emptyList()) }
    var selectedAnime by remember { mutableStateOf<AnimeSearchInfo?>(null) }
    var isLoadingDanmaku by remember { mutableStateOf(false) }

    fun saveCredentials() {
        advancedPrefs.dandanplayAppId.set(appIdInput)
        advancedPrefs.dandanplayAppSecret.set(appSecretInput)
        showCredentialInput = !api.hasCredentials()
        if (api.hasCredentials()) {
            Toast.makeText(context, "凭证已保存", Toast.LENGTH_SHORT).show()
        }
    }

    fun doSearch() {
        if (keyword.isBlank()) return
        if (!api.hasCredentials()) {
            Toast.makeText(context, "请先填写并保存 AppId/AppSecret", Toast.LENGTH_SHORT).show()
            return
        }
        isSearching = true
        selectedAnime = null
        scope.launch {
            api.searchAnime(keyword).fold(
                onSuccess = { resp ->
                    results = resp.animes ?: emptyList()
                    if (results.isEmpty()) Toast.makeText(context, "未找到相关番剧", Toast.LENGTH_SHORT).show()
                },
                onFailure = { Toast.makeText(context, "搜索失败: ${it.message}", Toast.LENGTH_SHORT).show() }
            )
            isSearching = false
        }
    }

    fun loadDanmaku(episode: EpisodeInfo) {
        isLoadingDanmaku = true
        scope.launch {
            api.getDanmaku(episode.episodeId, danmakuPrefs.chConvert.get()).fold(
                onSuccess = { resp ->
                    if (resp.comments.isEmpty()) {
                        Toast.makeText(context, "该集暂无弹幕", Toast.LENGTH_SHORT).show()
                    } else {
                        val xml = api.convertToXml(resp)
                        val title = "${selectedAnime?.animeTitle ?: ""} - ${episode.episodeTitle}"
                        onDanmakuXml(xml, title)
                    }
                },
                onFailure = { Toast.makeText(context, "加载弹幕失败: ${it.message}", Toast.LENGTH_SHORT).show() }
            )
            isLoadingDanmaku = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("在线弹幕 (弹弹play)") },
        text = {
            Column {
                if (showCredentialInput || !api.hasCredentials()) {
                    // 凭证配置区
                    Text(
                        text = "需配置弹弹play凭证（前往 dandanplay.com 申请）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    OutlinedTextField(
                        value = appIdInput,
                        onValueChange = { appIdInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.danmaku_app_id)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = appSecretInput,
                        onValueChange = { appSecretInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.danmaku_app_secret)) },
                        singleLine = true,
                    )
                    TextButton(onClick = { saveCredentials() }) { Text("保存凭证") }
                }

                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索番剧名") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { doSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                )

                // 候选分词 chips：点击即可替换搜索词
                if (candidates.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        candidates.forEach { candidate ->
                            AssistChip(
                                onClick = { keyword = candidate },
                                label = { Text(candidate, maxLines = 1) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }

                if (isSearching) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }

                when {
                    selectedAnime == null && results.isNotEmpty() -> {
                        // 番剧列表
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                            items(results, key = { it.animeId }) { anime ->
                                ListItem(
                                    modifier = Modifier.clickable { selectedAnime = anime },
                                    headlineContent = { Text(anime.animeTitle) },
                                    supportingContent = {
                                        Text("${anime.typeDescription ?: ""} · ${anime.episodes?.size ?: 0} 集")
                                    },
                                    leadingContent = {
                                        Icon(Icons.Default.Subtitles, contentDescription = null)
                                    },
                                )
                            }
                        }
                    }

                    selectedAnime != null -> {
                        // 剧集列表
                        Text(
                            text = selectedAnime!!.animeTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (isLoadingDanmaku) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) { CircularProgressIndicator() }
                        }
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                            items(selectedAnime!!.episodes ?: emptyList(), key = { it.episodeId }) { ep ->
                                ListItem(
                                    modifier = Modifier.clickable { loadDanmaku(ep) },
                                    headlineContent = { Text(ep.episodeTitle) },
                                    trailingContent = {
                                        Icon(Icons.Default.Subtitles, contentDescription = null)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/**
 * 从播放文件名清洗出多个候选搜索词（用于弹弹play 搜索）。
 * 候选策略（去重后按优先级排列）：
 * 1. MediaInfoParser 清洗出的纯番名（最接近，预填）
 * 2. 纯番名 + 年份（区分同名不同季/剧场版）
 * 3. 按常见分隔符拆分出的有意义词块（去掉发布组/分辨率/编码/集数等噪声，中英文都适用）
 * 4. 完整文件名（去扩展名，兜底）
 */
private fun generateSearchCandidates(fileName: String): List<String> {
    if (fileName.isBlank()) return emptyList()

    // 去掉扩展名，得到可分析的原始串
    val base = fileName.substringBeforeLast('.').trim().ifBlank { fileName.trim() }

    val candidates = mutableListOf<String>()

    // 1. MediaInfoParser 清洗出的纯番名
    val parsed = MediaInfoParser.parse(base)
    val title = parsed.title.trim()
    if (title.isNotBlank()) {
        candidates.add(title)
        // 2. 纯番名 + 年份
        parsed.year?.takeIf { it.isNotBlank() }?.let { year ->
            val withYear = "$title $year"
            if (withYear != title && withYear != base) candidates.add(withYear)
        }
    }

    // 3. 按分隔符拆出有意义词块（去掉噪声标记后的短语）
    candidates.addAll(splitMeaningfulTokens(base))

    // 4. 完整原始串（去扩展名），兜底
    val baseCleaned = base.trim()
    if (baseCleaned.isNotBlank() && candidates.none { it == baseCleaned }) {
        candidates.add(baseCleaned)
    }

    // 去空、去重并限制最多 5 个
    return candidates.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(5)
}

// 噪声片段：发布组标签、分辨率、编码、集数、文件大小、年份等
private val TOKEN_NOISE = listOf(
    "[", "]", "【", "】", "(", ")", "（", "）",
    "1080p", "720p", "480p", "2160p", "4k", "8k",
    "hevc", "x265", "h264", "x264", "10bit", "8bit", "av1", "h265",
    "mkv", "mp4", "web", "webrip", "bd", "bdrip", "bluray", "dvd",
    "tv", "tvrip", "hdtv", "raw", "jap", "chs", "cht", "简中", "繁中", "中文",
    "第二季", "第三季", "第四季", "season", "s1", "s2", "s3", "s4",
)

/**
 * 把文件名按常用分隔符（空格/./_/-等）拆成词块，过滤噪声后，
 * 把相邻的非噪声词块拼接成"短语"候选，并保留有意义的单词块。
 * 例："[桜都字幕组] 咒术回战 第二季 - 01 (1080p)" ->
 *   → "桜都字幕组", "咒术回战", "咒术回战 第二季"
 */
private fun splitMeaningfulTokens(raw: String): List<String> {
    // 去除括号/方括号包裹的噪声片段
    val parts = raw
        .replace(Regex("""\[[^\]]*]"""), " ")
        .replace(Regex("""【[^】]*】"""), " ")
        .replace(Regex("""\([^)]*\)"""), " ")
        .replace(Regex("""（[^）]*）"""), " ")
        .split(Regex("""[\s._\-～~#×x]+"""))
        .map { it.trim() }
        .filter { it.isNotBlank() && it.lowercase() !in TOKEN_NOISE }

    if (parts.isEmpty()) return emptyList()

    val result = mutableListOf<String>()
    // 逐个有意义的纯文字词块作为独立候选（不含数字：集数/分辨率等噪声）
    for (token in parts) {
        // 含数字的（集数、年份、S01E02、第12话、1080p 等）不单独做候选
        if (token.any { it.isDigit() }) continue
        if (token.length >= 2 && result.none { it.equals(token, ignoreCase = true) }) {
            result.add(token)
        }
    }
    // 相邻纯文字词块拼接成短语（如 "咒术回战 第二季"），更接近真实番名
    val textParts = parts.filter { !it.any { c -> c.isDigit() } }
    val joined = textParts.joinToString(" ").trim()
    if (joined.isNotBlank() && result.none { it.equals(joined, ignoreCase = true) }) {
        result.add(joined)
    }
    return result
}
