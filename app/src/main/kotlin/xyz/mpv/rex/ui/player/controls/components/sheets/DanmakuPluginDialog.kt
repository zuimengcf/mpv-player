package xyz.mpv.rex.ui.player.controls.components.sheets

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.danmaku.plugin.DanmakuPluginBridge
import xyz.mpv.rex.danmaku.plugin.core.DanmakuCodec
import xyz.mpv.rex.danmaku.plugin.core.DanmakuEntry
import xyz.mpv.rex.danmaku.plugin.core.DanmakuPipeline
import xyz.mpv.rex.ui.player.PlayerActivity
import java.io.ByteArrayOutputStream
import java.io.File

private const val MAX_ENTRIES = 200_000
private const val MAX_BYTES = 16 * 1024 * 1024

@Composable
fun DanmakuPluginDialog(activity: PlayerActivity, onDismiss: () -> Unit, onLoaded: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  // Capture the MPV path once; do not let recomposition change the operation's identity.
  fun currentIdentity() = runCatching { MPVLib.getPropertyString("path") }.getOrNull().orEmpty().ifBlank { activity.fileName }
  val videoIdentity = remember { currentIdentity() }
  val videoTitle = remember { activity.fileName }
  var draft by remember { mutableStateOf<List<DanmakuEntry>>(emptyList()) }
  var filtered by remember { mutableStateOf<List<DanmakuEntry>>(emptyList()) }
  var sourceNames by remember { mutableStateOf<Set<String>>(emptySet()) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var query by remember { mutableStateOf("") }
  var keywords by remember { mutableStateOf("") }
  var deduplicate by remember { mutableStateOf(true) }
  var mode by remember { mutableStateOf(0) }
  var appliedDraft by remember { mutableStateOf<List<DanmakuEntry>?>(null) }
  var appliedKeywords by remember { mutableStateOf("") }
  var appliedDeduplicate by remember { mutableStateOf(true) }
  var visible by remember { mutableStateOf<List<DanmakuEntry>>(emptyList()) }
  var exportUri by remember { mutableStateOf<Uri?>(null) }
  var job by remember { mutableStateOf<Job?>(null) }

  fun start(block: suspend () -> Unit) {
    job?.cancel()
    job = scope.launch {
      busy = true; error = null
      try { block() } catch (e: CancellationException) { throw e }
      catch (e: Throwable) { error = e.message ?: "操作失败" }
      finally { busy = false; job = null }
    }
  }

  DisposableEffect(Unit) { onDispose { job?.cancel() } }

  // Filtering, searching, sorting and de-duplication never run on the main thread.
  LaunchedEffect(draft, keywords, deduplicate) {
    val snapshot = draft
    val keywordSnapshot = keywords
    val deduplicateSnapshot = deduplicate
    val keys = keywordList(keywords)
    filtered = withContext(Dispatchers.Default) {
      DanmakuPipeline.merge(snapshot, keys, deduplicateSnapshot)
    }
    sourceNames = withContext(Dispatchers.Default) { snapshot.map { it.source }.toSet() }
    appliedDraft = snapshot
    appliedKeywords = keywordSnapshot
    appliedDeduplicate = deduplicateSnapshot
  }

  // Read an already-loaded online file in the background, without replacing a user import.
  LaunchedEffect(videoIdentity) {
    val path = activity.danmakuManager.getCurrentDanmakuPath()
    if (path.isNullOrBlank()) return@LaunchedEffect
    start {
      val loaded = withContext(Dispatchers.IO) { readEntries(File(path), context) }
      if (draft.isEmpty() && loaded.isNotEmpty()) { draft = loaded }
    }
  }

  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    start {
      val loaded = withContext(Dispatchers.IO) { readEntries(uri, context) }
      if (loaded.isEmpty()) error("文件中没有有效弹幕")
      val combined = if (mode == 0) draft + loaded else loaded
      require(combined.size <= MAX_ENTRIES) { "合并后超过 200000 条，已拒绝" }
      draft = combined
    }
  }
  val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/xml")) { exportUri = it }
  LaunchedEffect(exportUri) {
    val uri = exportUri ?: return@LaunchedEffect
    exportUri = null
    // SnapshotState is read on Main before entering IO.
    val snapshot = filtered.toList()
    start {
      require(snapshot.isNotEmpty()) { "没有可导出的弹幕" }
      val xml = withContext(Dispatchers.Default) { DanmakuCodec.toXml(snapshot) }
      withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(xml.toByteArray(Charsets.UTF_8)) } ?: error("无法写入导出文件") }
      Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
    }
  }

  val filterReady = appliedDraft === draft && appliedKeywords == keywords && appliedDeduplicate == deduplicate
  LaunchedEffect(filtered, query) {
    val snapshot = filtered
    val search = query
    visible = withContext(Dispatchers.Default) {
      snapshot.asSequence().filter { search.isBlank() || it.text.contains(search, true) }.take(200).toList()
    }
  }
  AlertDialog(
    onDismissRequest = { if (busy) job?.cancel() else onDismiss() },
    title = { Text("弹幕管理") },
    text = {
      Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("支持 XML / ASS / SSA / JSON；本地读取上限 16 MiB")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(enabled = !busy, onClick = { picker.launch(arrayOf("text/*", "application/xml", "application/json")) }) { Text("导入文件") }
          OutlinedButton(enabled = !busy && filterReady && filtered.isNotEmpty(), onClick = { savePicker.launch("danmaku.xml") }) { Text("导出 XML") }
        }
        Row {
          RadioButton(mode == 0, enabled = !busy, onClick = { mode = 0 }); Text("合并下一来源", Modifier.padding(top = 12.dp))
          RadioButton(mode == 1, enabled = !busy, onClick = { mode = 1 }); Text("替换", Modifier.padding(top = 12.dp))
        }
        OutlinedTextField(keywords, { keywords = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("关键词屏蔽（分号/；分隔）") }, singleLine = true)
        Row { Checkbox(deduplicate, enabled = !busy, onCheckedChange = { deduplicate = it }); Text("去重", Modifier.padding(top = 12.dp)) }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
      }
    },
    confirmButton = {
      Button(enabled = !busy && filterReady && filtered.isNotEmpty(), onClick = {
        val snapshot = filtered.toList()
        start {
          val ok = DanmakuPluginBridge(context).install(snapshot, videoTitle, activity.danmakuManager) {
            currentIdentity() == videoIdentity
          }
          check(ok) { "加载失败" }
          withContext(Dispatchers.Main.immediate) { activity.danmakuManager.showDanmaku(); activity.saveDanmakuBinding(); onLoaded(); onDismiss() }
        }
      }) { if (busy) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp) else Text("加载到播放器") }
    },
    dismissButton = { TextButton(onClick = { if (busy) job?.cancel() else onDismiss() }) { Text(if (busy) "取消操作" else "取消") } },
  )
}

private fun keywordList(value: String): List<String> = value.split(';', '；').map(String::trim).filter(String::isNotEmpty)

private fun readEntries(file: File, context: Context): List<DanmakuEntry> {
  require(file.isFile) { "找不到当前弹幕文件" }
  require(file.length() <= MAX_BYTES) { "文件超过 16 MiB" }
  return file.inputStream().use { decodeBytes(it.readBounded(), file.name) }
}

private fun readEntries(uri: Uri, context: Context): List<DanmakuEntry> {
  val name = uri.displayName(context)
  val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded() } ?: error("无法读取文件")
  return decodeBytes(bytes, name)
}

private fun java.io.InputStream.readBounded(): ByteArray {
  val out = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0
  while (true) { val n = read(buffer); if (n < 0) break; total += n; require(total <= MAX_BYTES) { "文件超过 16 MiB" }; out.write(buffer, 0, n) }
  return out.toByteArray()
}

private fun decodeBytes(bytes: ByteArray, name: String): List<DanmakuEntry> =
  DanmakuCodec.decodeBytes(bytes, name)

private fun Uri.displayName(context: Context): String = context.contentResolver.query(this, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: lastPathSegment.orEmpty()
