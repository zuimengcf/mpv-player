package xyz.mpv.rex.danmaku.plugin.source

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Read-only client for an AniCh-compatible provider. It never authenticates or writes data. */
class AniChDanmakuSource(baseUrl: String, client: OkHttpClient = defaultClient()) {
    private val base: HttpUrl = validateBaseUrl(baseUrl)
    private val client: OkHttpClient = client.newBuilder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun load(bangumiId: Int, episode: Int): List<xyz.mpv.rex.danmaku.plugin.core.DanmakuEntry> {
        require(bangumiId >= 0) { "bangumiId must be non-negative" }
        require(episode >= 0) { "episode must be non-negative" }
        val result = ArrayList<xyz.mpv.rex.danmaku.plugin.core.DanmakuEntry>()
        val seenPages = HashSet<String>()
        var skip = 0L
        var pages = 0
        var rawEntries = 0L
        var rawBytes = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            if (++pages > MAX_PAGES) throw AniChProtocolException("pagination exceeds $MAX_PAGES pages")
            val url = base.newBuilder().addPathSegment("danmaku")
                .addQueryParameter("bangumi", bangumiId.toString())
                .addQueryParameter("episode", episode.toString())
                .addQueryParameter("skip", skip.toString()).build()
            val page = execute(Request.Builder().url(url).get().build())
            rawBytes += page.rawBytes
            if (rawBytes > MAX_TOTAL_BYTES) throw AniChProtocolException("responses exceed $MAX_TOTAL_BYTES bytes")
            if (!seenPages.add(page.sha256)) throw AniChProtocolException("duplicate page")
            rawEntries += page.rawEntryCount
            if (rawEntries > MAX_ENTRIES) throw AniChProtocolException("danmaku exceeds $MAX_ENTRIES entries")
            result += page.entries
            if (page.rawEntryCount < PAGE_SIZE) return result
            skip += PAGE_SIZE
        }
    }

    private suspend fun execute(request: Request): Page = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { readPage(it, call) }.let { page ->
                        if (continuation.isActive) continuation.resume(page)
                    }
                } catch (e: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }

    private fun readPage(response: Response, call: Call): Page {
        if (!response.isSuccessful) throw IOException("AniCh HTTP ${response.code}")
        val body = response.body ?: throw AniChProtocolException("empty response body")
        val contentType = body.contentType()?.toString()?.lowercase()
        if (contentType?.contains("text/html") == true) throw AniChProtocolException("HTML response")
        if (body.contentLength() > MAX_TOTAL_BYTES) throw AniChProtocolException("response is too large")
        val bytes = body.byteStream().use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                if (call.isCanceled()) throw IOException("request cancelled")
                val n = input.read(buffer)
                if (n < 0) break
                if (n == 0) continue
                if (out.size().toLong() > MAX_TOTAL_BYTES - n) throw AniChProtocolException("response is too large")
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
        if (bytes.dropWhile { it.toInt() <= 32 }.firstOrNull()?.toInt()?.toChar() == '<') {
            throw AniChProtocolException("HTML response")
        }
        return ProtoReader(bytes).readPage(sha256(bytes))
    }

    private data class Page(
        val entries: List<xyz.mpv.rex.danmaku.plugin.core.DanmakuEntry>,
        val rawEntryCount: Int, val sha256: String, val rawBytes: Int,
    )

    private class ProtoReader(private val bytes: ByteArray) {
        private var p = 0
        fun readPage(digest: String): Page {
            val entries = ArrayList<xyz.mpv.rex.danmaku.plugin.core.DanmakuEntry>()
            var rawCount = 0
            while (p < bytes.size) {
                val key = varint(); val field = key ushr 3; val wire = key and 7
                checkField(field)
                when (field) {
                    1L -> { if (wire != 2L) bad("data wire type"); if (++rawCount > PAGE_SIZE) bad("too many page entries"); readEntry(readBytes())?.let(entries::add) }
                    2L -> { if (wire != 0L) bad("skip wire type"); uint32() }
                    else -> skipField(wire)
                }
            }
            return Page(entries, rawCount, digest, bytes.size)
        }
        private fun readEntry(raw: ByteArray): xyz.mpv.rex.danmaku.plugin.core.DanmakuEntry? {
            val r = ProtoReader(raw)
            var id = ""; var color = 0xFFFFFF; var text = ""; var time = 0.0; var type = 0L
            while (r.p < r.bytes.size) {
                val key = r.varint(); val field = key ushr 3; val wire = key and 7
                r.checkField(field)
                when (field) {
                    1L -> { if (wire != 2L) r.bad("id wire type"); id = r.string() }
                    2L -> { if (wire != 2L) r.bad("color wire type"); color = parseColor(r.string()) }
                    3L -> { if (wire != 1L) r.bad("date wire type"); r.fixed64() }
                    4L -> { if (wire != 2L) r.bad("text wire type"); text = r.string(); if (text.length > MAX_TEXT) r.bad("text too long") }
                    5L -> { if (wire != 2L) r.bad("t wire type"); r.string() }
                    6L -> { if (wire != 1L) r.bad("time wire type"); time = r.fixed64() }
                    7L -> { if (wire != 0L) r.bad("type wire type"); type = r.uint32() }
                    8L -> { if (wire != 2L) r.bad("from wire type"); r.string() }
                    else -> r.skipField(wire)
                }
            }
            if (text.isEmpty()) return null
            if (!time.isFinite() || time < 0 || time >= Long.MAX_VALUE.toDouble() / 1000.0) r.bad("invalid time")
            val mode = when (type) { 2L -> 5; 3L -> 4; else -> 1 }
            return xyz.mpv.rex.danmaku.plugin.core.DanmakuEntry(timeMs = (time * 1000.0).toLong(), text = text, mode = mode, color = color, id = id, source = "anich")
        }
        private fun string(): String {
            val b = readBytes()
            return try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString() }
            catch (_: Exception) { bad("invalid UTF-8") }
        }
        private fun readBytes(): ByteArray { val n = varint(); if (n < 0 || n > MAX_TOTAL_BYTES || n > bytes.size - p) bad("truncated length-delimited field"); val end = p + n.toInt(); return bytes.copyOfRange(p, end).also { p = end } }
        private fun uint32(): Long { val v = varint(); if (v < 0 || v > 0xFFFFFFFFL) bad("uint32 overflow"); return v }
        private fun fixed64(): Double { if (bytes.size - p < 8) bad("truncated fixed64"); val d = ByteBuffer.wrap(bytes, p, 8).order(ByteOrder.LITTLE_ENDIAN).double; p += 8; return d }
        private fun varint(): Long { var value = 0L; for (i in 0..9) { if (p >= bytes.size) bad("truncated varint"); val b = bytes[p++].toInt() and 255; if (i == 9 && b > 1) bad("varint overflow"); value = value or ((b and 0x7f).toLong() shl (7 * i)); if (b and 0x80 == 0) { if (value < 0) bad("negative varint"); return value } }; bad("varint overflow") }
        private fun skipField(wire: Long) { when (wire) { 0L -> varint(); 1L -> { if (bytes.size - p < 8) bad("truncated fixed64"); p += 8 }; 2L -> readBytes(); 5L -> { if (bytes.size - p < 4) bad("truncated fixed32"); p += 4 }; else -> bad("unsupported wire type") } }
        private fun checkField(field: Long) { if (field !in 1L..536870911L) bad("invalid field number") }
        fun bad(message: String): Nothing = throw AniChProtocolException(message)
    }

    companion object {
        private const val PAGE_SIZE = 3000
        private const val MAX_PAGES = 67
        private const val MAX_ENTRIES = 200_000L
        private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
        private const val MAX_TEXT = 2000
        private fun defaultClient() = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
        private fun validateBaseUrl(value: String): HttpUrl {
            val u = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("invalid base URL") }
            require(u.scheme.equals("https", true)) { "base URL must use HTTPS" }; require(u.userInfo.isNullOrEmpty()) { "base URL must not contain credentials" }; require(u.query == null && u.fragment == null) { "base URL must not contain query or fragment" }; require(u.path.isNullOrEmpty() || u.path == "/") { "base URL must be an HTTPS root URL" }
            return value.toHttpUrlOrNull()?.also { require(it.username.isEmpty() && it.password.isEmpty()) { "base URL must not contain credentials" } } ?: throw IllegalArgumentException("invalid base URL")
        }
        private fun parseColor(value: String): Int = try { val s = value.removePrefix("#"); require(s.length == 6 || s.length == 8 && value.startsWith("#") || s.length == 8); s.toLong(16).toInt() and 0xFFFFFF } catch (_: Exception) { throw AniChProtocolException("invalid color") }
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

class AniChProtocolException(message: String) : IOException(message)
