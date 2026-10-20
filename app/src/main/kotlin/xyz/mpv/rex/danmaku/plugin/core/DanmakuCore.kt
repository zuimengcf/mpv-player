package xyz.mpv.rex.danmaku.plugin.core

import kotlinx.serialization.json.*
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.math.BigDecimal
import java.math.RoundingMode
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

/** A portable, Android-free normalized danmaku record. */
data class DanmakuEntry(
    val timeMs: Long,
    val text: String,
    val mode: Int = 1,
    val color: Int = 0xFFFFFF,
    val size: Int = 25,
    val id: String = "",
    val source: String = "local"
)

object DanmakuCodec {
    private const val MAX_BYTES = 16 * 1024 * 1024
    private const val MAX_ENTRIES = 200_000
    private const val MAX_TEXT = 2_000
    private const val MAX_SIZE = 200
    private const val MAX_JSON_DEPTH = 32

    fun decode(content: String, format: String): List<DanmakuEntry> {
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "danmaku input exceeds 16 MiB" }
        val raw = when (format.trim().lowercase().removePrefix(".")) {
            "xml", "bilibili", "bilixml" -> decodeXml(content)
            "json", "dandan", "dandanplay" -> decodeJson(content, false)
            "anich", "anichjson" -> decodeJson(content, true)
            "ass", "ssa" -> decodeAss(content)
            else -> emptyList()
        }
        require(raw.size <= MAX_ENTRIES) { "danmaku entry count exceeds 200000" }
        return raw.mapNotNull(::normalize)
    }

    fun toXml(entries: List<DanmakuEntry>): String {
        require(entries.size <= MAX_ENTRIES) { "danmaku entry count exceeds 200000" }
        val out = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><i>")
        var byteCount = out.toString().toByteArray(Charsets.UTF_8).size + 4
        entries.asSequence().mapNotNull(::normalize).forEach { e ->
            val source = e.source.replace(',', '_')
            val id = e.id.replace(',', '_')
            val seconds = "${e.timeMs / 1000}.${(e.timeMs % 1000).toString().padStart(3, '0')}"
            val p = "$seconds,${e.mode},${e.size},${e.color},0,0,$source,$id"
            val row = "<d p=\"${xml(p)}\">${xml(e.text)}</d>"
            byteCount += row.toByteArray(Charsets.UTF_8).size
            require(byteCount <= MAX_BYTES) { "danmaku XML exceeds 16 MiB" }
            out.append(row)
        }
        return out.append("</i>").toString()
    }

    private fun decodeXml(content: String): List<DanmakuEntry> {
        if (Regex("<!\\s*(doctype|entity)", RegexOption.IGNORE_CASE).containsMatchIn(content))
            throw IllegalArgumentException("XML DTD and entities are not allowed")
        val result = ArrayList<DanmakuEntry>()
        try {
            val f = SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
                // Android SAX 不保证识别全部 feature；词法 DTD 拒绝与 resolver 是硬防线。
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            }
            val reader = f.newSAXParser().xmlReader
            reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw SecurityException("external entity rejected") }
            reader.contentHandler = object : DefaultHandler() {
                var text = StringBuilder(); var p: String? = null; var inside = false
                override fun startElement(uri: String?, local: String?, q: String, a: org.xml.sax.Attributes) {
                    if (q.equals("d", true)) { p = a.getValue("p"); text = StringBuilder(); inside = true }
                }
                override fun characters(ch: CharArray, start: Int, length: Int) { if (inside) text.append(ch, start, length) }
                override fun endElement(uri: String?, local: String?, q: String) {
                    if (q.equals("d", true) && inside) {
                        val v = p.orEmpty().split(',')
                        val t = v.getOrNull(0)?.toDoubleOrNull()
                        val ms = t?.let(::secondsToMillis)
                        if (ms != null && ms >= 0) {
                            if (result.size >= MAX_ENTRIES) throw IllegalArgumentException("danmaku entry count exceeds 200000")
                            result += DanmakuEntry(ms, text.toString(), v.getOrNull(1)?.toIntOrNull() ?: 1,
                                v.getOrNull(3)?.toLongOrNull()?.toInt() ?: 0xFFFFFF,
                                v.getOrNull(2)?.toDoubleOrNull()?.let { safeInt(it) } ?: 25,
                                v.getOrNull(7).orEmpty(), "bilibili")
                        }
                        inside = false
                    }
                }
            }
            reader.parse(InputSource(StringReader(content)))
        } catch (e: IllegalArgumentException) { throw e }
        catch (e: Exception) { throw IllegalArgumentException("malformed XML", e) }
        return result
    }

    private fun decodeJson(content: String, forcedAnich: Boolean): List<DanmakuEntry> {
        val result = ArrayList<DanmakuEntry>()
        try {
            // 在构建 JSON 树之前检查嵌套，避免恶意输入先耗尽解析器调用栈。
            var nesting = 0
            var inString = false
            var escaped = false
            for (c in content) {
                if (inString) {
                    if (escaped) escaped = false
                    else if (c == '\\') escaped = true
                    else if (c == '"') inString = false
                } else when (c) {
                    '"' -> inString = true
                    '[', '{' -> { nesting++; require(nesting <= MAX_JSON_DEPTH) { "JSON nesting exceeds depth 32" } }
                    ']', '}' -> nesting--
                }
            }
            fun visit(e: JsonElement, depth: Int) {
                require(depth <= MAX_JSON_DEPTH) { "JSON nesting exceeds depth 32" }
                when (e) {
                    is JsonArray -> e.forEach { visit(it, depth + 1) }
                    is JsonObject -> {
                        val p = e["p"]?.jsonPrimitive?.contentOrNull?.split(',')
                        val text = str(e, "text", "content", "message", "m")
                        val anich = forcedAnich || (e["color"]?.jsonPrimitive?.contentOrNull?.startsWith("#") == true) || e.containsKey("progress")
                        val rawTime = number(e, "timeMs", "timestamp") ?: number(e, "progress")?.let { it } ?: number(e, "time") ?: p?.getOrNull(0)?.toDoubleOrNull()
                        val ms = when {
                            e.containsKey("timeMs") || e.containsKey("progress") -> rawTime?.let { BigDecimal.valueOf(it).longValueExactOrNull() }
                            else -> rawTime?.let(::secondsToMillis)
                        }
                        if (text != null && ms != null && ms >= 0) {
                            if (result.size >= MAX_ENTRIES) throw IllegalArgumentException("danmaku entry count exceeds 200000")
                            val rawMode = integer(e, "mode", "type") ?: p?.getOrNull(1)?.toIntOrNull() ?: 1
                            val mode = if (anich) when (rawMode) { 2 -> 5; 3 -> 4; else -> 1 } else rawMode
                            val color = color(e["color"]?.jsonPrimitive?.contentOrNull) ?: p?.getOrNull(if (anich) 3 else 2)?.toIntOrNull() ?: 0xFFFFFF
                            val size = integer(e, "size", "fontSize") ?: 25
                            result += DanmakuEntry(ms, text, mode, color, size, str(e, "id", "cid") ?: p?.getOrNull(3).orEmpty(), if (anich) "anich" else "dandan")
                        }
                        e.values.filter { it is JsonArray || it is JsonObject }.forEach { visit(it, depth + 1) }
                    }
                    else -> Unit
                }
            }
            visit(Json.parseToJsonElement(content), 0)
        } catch (e: IllegalArgumentException) { throw e }
        catch (e: Exception) { throw IllegalArgumentException("malformed JSON", e) }
        return result
    }

    private fun decodeAss(content: String): List<DanmakuEntry> {
        val result = ArrayList<DanmakuEntry>()
        content.lineSequence().filter { it.startsWith("Dialogue:", true) }.forEach { line ->
            if (result.size >= MAX_ENTRIES) throw IllegalArgumentException("danmaku entry count exceeds 200000")
            val f = line.substringAfter(':').split(',', limit = 10)
            if (f.size < 10) return@forEach
            val start = assTime(f[1]) ?: return@forEach
            val raw = f[9]; val text = raw.replace(Regex("\\{[^}]*}"), "").replace("\\N", "\n").replace("\\n", "\n").trim()
            val mode = when { Regex("\\\\an[7-9]").containsMatchIn(raw) -> 5; Regex("\\\\an[1-3]").containsMatchIn(raw) -> 4; else -> 1 }
            result += DanmakuEntry(start, text, mode, source = "ass")
        }
        return result
    }

    private fun assTime(s: String): Long? {
        val p = s.trim().split(':'); if (p.size != 3) return null
        val h = p[0].toLongOrNull() ?: return null; val m = p[1].toLongOrNull() ?: return null; val sec = p[2].toBigDecimalOrNull() ?: return null
        if (h < 0 || m !in 0..59 || sec < BigDecimal.ZERO || sec >= BigDecimal(60) || !sec.toDouble().isFinite()) return null
        return (BigDecimal.valueOf(h).multiply(BigDecimal(3600)).add(BigDecimal.valueOf(m * 60)).add(sec).multiply(BigDecimal(1000))).setScale(0, RoundingMode.HALF_UP).longValueExactOrNull()
    }
    private fun secondsToMillis(s: Double): Long? = if (s.isFinite() && s >= 0) BigDecimal.valueOf(s).multiply(BigDecimal(1000)).setScale(0, RoundingMode.HALF_UP).longValueExactOrNull() else null
    private fun BigDecimal.longValueExactOrNull() = try { longValueExact() } catch (_: ArithmeticException) { null }
    private fun safeInt(d: Double) = if (d.isFinite() && d in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) d.toInt() else 25
    private fun normalize(e: DanmakuEntry): DanmakuEntry? { if (e.timeMs < 0 || e.text.isEmpty()) return null; val value = clean(e.text); val limit = if (value.length > MAX_TEXT && value[MAX_TEXT - 1].isHighSurrogate()) MAX_TEXT - 1 else MAX_TEXT; val t = value.take(limit); if (t.isEmpty()) return null; return e.copy(text = t, mode = e.mode.takeIf { it == 1 || it == 4 || it == 5 || it == 6 } ?: 1, color = e.color.takeIf { it in 0..0xFFFFFF } ?: 0xFFFFFF, size = e.size.coerceIn(1, MAX_SIZE)) }
    private fun clean(s: String) = s.codePoints().filter { it !in 0xD800..0xDFFF && it != 0xFFFE && it != 0xFFFF && (it == 9 || it == 10 || it == 13 || !Character.isISOControl(it)) }.toArray().let { String(it, 0, it.size) }
    private fun xml(s: String) = clean(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun str(o: JsonObject, vararg keys: String) = keys.firstNotNullOfOrNull { o[it]?.jsonPrimitive?.contentOrNull }
    private fun number(o: JsonObject, vararg keys: String) = keys.firstNotNullOfOrNull { o[it]?.jsonPrimitive?.doubleOrNull }
    private fun integer(o: JsonObject, vararg keys: String) = keys.firstNotNullOfOrNull { o[it]?.jsonPrimitive?.intOrNull }
    private fun color(s: String?) = s?.let { if (it.matches(Regex("#[0-9a-fA-F]{6}"))) it.substring(1).toInt(16) else it.toIntOrNull() }
}

object DanmakuPipeline {
    fun merge(entries: List<DanmakuEntry>, keywords: List<String> = emptyList(), mergeDuplicates: Boolean = true): List<DanmakuEntry> {
        val keys = keywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val seen = HashSet<Triple<Long, String, Int>>()
        return entries.asSequence().filter { e -> keys.none { e.text.contains(it, true) } }
            .filter { e -> !mergeDuplicates || seen.add(Triple(e.timeMs, e.text, e.mode)) }
            .sortedWith(compareBy<DanmakuEntry> { it.timeMs }.thenBy { it.id }).toList()
    }
}
