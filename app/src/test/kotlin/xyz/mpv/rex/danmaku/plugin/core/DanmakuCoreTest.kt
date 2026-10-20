package xyz.mpv.rex.danmaku.plugin.core

import org.junit.Assert.*
import org.junit.Test

class DanmakuCoreTest {
    @Test fun bilibiliXmlParsesMillisecondsAndSkipsBadRows() {
        val xml = """<?xml version="1.0"?><i><d p="1.234,1,30,16711680,0,0,u,id">hello &amp; world</d><d>bad</d><d p="NaN,1,25,0,0,0,,">bad</d><d p="2,99,0,99999999,0,0,,x">ok</d></i>"""
        val r = DanmakuCodec.decode(xml, "bilibili")
        assertEquals(2, r.size); assertEquals(1234, r[0].timeMs); assertEquals("hello & world", r[0].text)
        assertEquals(1, r[1].mode); assertEquals(0xFFFFFF, r[1].color); assertEquals(1, r[1].size)
    }

    @Test fun xmlRejectsDoctypeAndMalformedDocumentExplicitly() {
        val payload = "<!DOCTYPE i [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><i><d p=\"1,1,25,0\">&xxe;</d></i>"
        try { DanmakuCodec.decode(payload, "xml"); fail("expected rejection") } catch (_: IllegalArgumentException) { }
        try { DanmakuCodec.decode("<i><d p=\"1,1,25,0\">x", "xml"); fail("expected rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun dandanPUsesTimeModeColorUserAndAnichUsesProgressAndTypes() {
        val d = DanmakuCodec.decode("{\"data\":[{\"p\":\"2.5,4,16711680,user,ignored\",\"m\":\"D\"}]}", "dandan")
        assertEquals(1, d.size); assertEquals(2500, d[0].timeMs); assertEquals(4, d[0].mode)
        assertEquals(0xFF0000, d[0].color); assertEquals(25, d[0].size); assertEquals("user", d[0].id)
        val a = DanmakuCodec.decode("{\"data\":[{\"progress\":3000,\"content\":\"A\",\"type\":2,\"color\":\"#ffffff\"}]}", "json")
        assertEquals(1, a.size); assertEquals(3000, a[0].timeMs); assertEquals(5, a[0].mode); assertEquals(0xFFFFFF, a[0].color)
        val a3 = DanmakuCodec.decode("[{\"progress\":1,\"content\":\"B\",\"type\":3,\"color\":\"#000000\"}]", "anich")
        assertEquals(4, a3.single().mode)
    }

    @Test fun assDialogueRejectsNanOverflowAndMinuteSixty() {
        val ass = """Dialogue: 0,0:01:02.50,0:01:04.00,Default,,0,0,0,,rolling
Dialogue: 0:60:00.00,0:01:04.00,Default,,0,0,0,,bad
Dialogue: 0:nan:00.00,0:01:04.00,Default,,0,0,0,,bad"""
        val r = DanmakuCodec.decode(ass, "ssa")
        assertEquals(1, r.size); assertEquals(62500, r[0].timeMs)
    }

    @Test fun exportEscapesOnceAndCleansCommasAndInvalidUnicode() {
        val xml = DanmakuCodec.toXml(listOf(DanmakuEntry(1234, "<&>\"'\u0001\uFFFE", 1, 0x12, 25, "i,d", "s,c")))
        assertTrue(xml.contains("p=\"1.234,1,25,18,0,0,s_c,i_d\"")); assertTrue(xml.contains("&lt;&amp;&gt;&quot;&apos;")); assertFalse(xml.contains("\u0001"))
        assertEquals(1234, DanmakuCodec.decode(xml, "xml").single().timeMs)
    }

    @Test fun normalizeFiltersInvalidValuesAndTextLimit() {
        val long = "x".repeat(2_010)
        val r = DanmakuCodec.decode("[{\"time\":-1,\"text\":\"negative\"},{\"time\":1,\"text\":\"$long\",\"color\":-2,\"size\":1000}]", "json")
        assertEquals(1, r.size); assertEquals(2000, r.single().text.length); assertEquals(0xFFFFFF, r.single().color); assertEquals(200, r.single().size)
    }

    @Test fun pipelineKeywordsExcludeMatchesAndDeduplicates() {
        val inRows = listOf(DanmakuEntry(2, "x", 1), DanmakuEntry(1, "keep", 1), DanmakuEntry(2, "x", 1), DanmakuEntry(2, "x", 5), DanmakuEntry(3, "x", 1))
        assertEquals(listOf(1L, 2L, 2L, 3L), DanmakuPipeline.merge(inRows).map { it.timeMs })
        assertEquals(listOf(2L, 2L, 3L), DanmakuPipeline.merge(inRows, keywords = listOf("KEEP", "nope")).map { it.timeMs })
        assertEquals(5, DanmakuPipeline.merge(inRows, mergeDuplicates = false).size)
    }

    @Test fun deepJsonAndOversizedInputAreExplicitErrors() {
        var s = "1"; repeat(33) { s = "[$s]" }
        try { DanmakuCodec.decode(s, "json"); fail("expected depth error") } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("depth")) }
        try { DanmakuCodec.decode("x".repeat(16 * 1024 * 1024 + 1), "json"); fail("expected size error") } catch (_: IllegalArgumentException) { }
    }

    @Test fun unknownFormatIsSafeAndControlCharactersAreCleaned() {
        assertTrue(DanmakuCodec.decode("whatever", "wat").isEmpty())
        val r = DanmakuCodec.decode("[{\"time\":1,\"text\":\"a\\u0000b\\tb\"}]", "json")
        assertEquals("ab\tb", r.single().text)
    }
    @Test fun decodeBytesSupportsUtf16BomAndXmlDeclarationWithoutBom() {
        val xml = "<?xml version=\"1.0\"?><i><d p=\"1,1,25,0\">你好</d></i>"
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + xml.toByteArray(Charsets.UTF_16LE)
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + xml.toByteArray(Charsets.UTF_16BE)
        assertEquals("你好", DanmakuCodec.decodeBytes(le, "x.bin").single().text)
        assertEquals("你好", DanmakuCodec.decodeBytes(be, "x.bin").single().text)
        val latin = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><i><d p=\"1,1,25,0\">caf\u00e9</d></i>"
        assertEquals("café", DanmakuCodec.decodeBytes(latin.toByteArray(Charsets.ISO_8859_1), "no-extension").single().text)
    }

    @Test fun decodeBytesSupportsBothUtf16ByteOrdersWithAndWithoutBom() {
        for ((charset, bom) in listOf(
            Charsets.UTF_16LE to byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
            Charsets.UTF_16BE to byteArrayOf(0xFE.toByte(), 0xFF.toByte()),
        )) {
            for (declared in listOf("UTF-16", charset.name())) {
                val xml = "<?xml version=\"1.0\" encoding=\"$declared\"?><i><d p=\"1.25,5,25,16711680\">中文弹幕😀</d></i>"
                for (prefix in listOf(byteArrayOf(), bom)) {
                    val entry = DanmakuCodec.decodeBytes(prefix + xml.toByteArray(charset), "import.XML").single()
                    assertEquals("中文弹幕😀", entry.text)
                    assertEquals(1250L, entry.timeMs)
                    assertEquals(5, entry.mode)
                }
            }
        }
    }

    @Test fun decodeBytesSupportsUtf8BomAndSniffsWithoutExtension() {
        val xml = "<i><d p=\"2,1,25,0\">bom</d></i>"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + xml.toByteArray(Charsets.UTF_8)
        assertEquals(2000, DanmakuCodec.decodeBytes(bytes, "download").single().timeMs)
    }

    @Test fun decodeBytesRejectsEncodedDtdAndMalformedBytesClearly() {
        val dtd = "<!DOCTYPE i [<!ENTITY xxe \"x\">]><i><d p=\"1,1,25,0\">&xxe;</d></i>"
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + dtd.toByteArray(Charsets.UTF_16LE)
        try { DanmakuCodec.decodeBytes(utf16, "data") ; fail("expected DTD rejection") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("DTD")) }
        try { DanmakuCodec.decodeBytes(byteArrayOf(0xC3.toByte(), 0x28), "data.json"); fail("expected malformed bytes") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("malformed")) }
        try { DanmakuCodec.decodeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x3C), "data.xml"); fail("expected malformed UTF-16") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("malformed UTF-16")) }
    }

}
