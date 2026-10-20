package xyz.mpv.rex.danmaku.plugin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import xyz.mpv.rex.danmaku.DanmakuManager
import xyz.mpv.rex.danmaku.plugin.core.DanmakuCodec
import xyz.mpv.rex.danmaku.plugin.core.DanmakuEntry
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** 将来源无关的弹幕快照安装到 Re Player；不修改视频旁的用户文件。 */
class DanmakuPluginBridge(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "danmaku/plugin")

    suspend fun install(
        entries: List<DanmakuEntry>,
        title: String,
        manager: DanmakuManager,
        stillCurrent: () -> Boolean = { true },
    ): Boolean {
        require(entries.isNotEmpty()) { "没有可加载的弹幕" }
        val target = withContext(Dispatchers.IO) {
            coroutineContext.ensureActive()
            val xml = DanmakuCodec.toXml(entries)
            val bytes = xml.toByteArray(Charsets.UTF_8)
            require(bytes.size <= 32 * 1024 * 1024) { "生成的弹幕文件超过 32 MiB" }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            check(directory.isDirectory || directory.mkdirs()) { "无法创建弹幕目录" }
            val destination = File(directory, "$digest.xml")
            if (!destination.exists()) {
                val temporary = File.createTempFile(".install-", ".xml", directory)
                try {
                    FileOutputStream(temporary).use { output ->
                        output.write(bytes)
                        output.fd.sync()
                    }
                    coroutineContext.ensureActive()
                    check(temporary.renameTo(destination) || destination.exists()) { "无法保存弹幕" }
                } finally {
                    temporary.delete()
                }
            }
            destination
        }
        return withContext(Dispatchers.Main.immediate) {
            coroutineContext.ensureActive()
            check(stillCurrent()) { "视频已切换，请在当前视频重新加载弹幕" }
            manager.loadDanmaku(target.absolutePath, title)
        }
    }
}
