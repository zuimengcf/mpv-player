package xyz.mpv.rex.danmaku.plugin.source

import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AniChDanmakuSourceTest {
    @Test fun `decodes chinese text colors time and position mapping`() = runTest {
        val result = source(page(entry(id = "x", color = "#12ABEF", text = "你好", time = 1.25, type = 2))).load(7, 3)
        assertEquals(1, result.size); assertEquals("你好", result[0].text); assertEquals(1250L, result[0].timeMs)
        assertEquals(5, result[0].mode); assertEquals(0x12ABEF, result[0].color); assertEquals("x", result[0].id); assertEquals("anich", result[0].source)
    }

    @Test fun `uses fixed page offsets even when response skip is zero`() = runTest {
        val first = page((1..3000).map { entry(text = "a") }, skip = 0); val second = page(entry(text = "最后"))
        val skips = mutableListOf<String?>(); var calls = 0
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            skips += chain.request().url.queryParameter("skip"); calls++
            val body = if (calls == 1) first else second
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody("application/octet-stream".toMediaType())).build()
        }).build()
        val result = AniChDanmakuSource("https://provider.example", client).load(1, 1)
        assertEquals(3001, result.size); assertEquals(listOf("0", "3000"), skips)
    }

    @Test fun `rejects redirect duplicate pages malformed protobuf and unsafe roots`() = runTest {
        expectFailure<java.io.IOException> { AniChDanmakuSource("https://provider.example", OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("Found").header("Location", "https://elsewhere").body("".toResponseBody()).build() }.build()).load(1, 1) }
        val full = page((1..3000).map { entry(text = "x") }); var calls = 0
        val duplicateClient = OkHttpClient.Builder().addInterceptor { chain -> calls++; Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(full.toResponseBody()).build() }.build()
        expectFailure<AniChProtocolException> { AniChDanmakuSource("https://provider.example", duplicateClient).load(1, 1) }
        expectFailure<AniChProtocolException> { source(byteArrayOf(0x08, 0x80.toByte())).load(1, 1) }
        expectFailure<IllegalArgumentException> { AniChDanmakuSource("http://provider.example") }
        expectFailure<IllegalArgumentException> { AniChDanmakuSource("https://u:p@provider.example") }
        expectFailure<IllegalArgumentException> { AniChDanmakuSource("https://provider.example/api") }
    }

    @Test fun `maps types one two three and skips empty text`() = runTest {
        val result = source(page(entry(text = "one", type = 1), entry(text = "two", type = 2), entry(text = "three", type = 3), entry(text = "", type = 1))).load(1, 1)
        assertEquals(listOf(1, 5, 4), result.map { it.mode }); assertTrue(result.none { it.text.isEmpty() })
    }

    @Test fun `cancellation cancels in-flight call`() = runTest {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain -> started.countDown(); release.await(5, TimeUnit.SECONDS); Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(page(entry(text = "x")).toResponseBody()).build() }.build()
        val job = async(start = CoroutineStart.UNDISPATCHED) { AniChDanmakuSource("https://provider.example", client).load(1, 1) }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            job.cancel(); job.join(); assertTrue(job.isCancelled)
        } finally { release.countDown() }
    }

    private suspend inline fun <reified T : Throwable> expectFailure(crossinline block: suspend () -> Unit) {
        try { block() } catch (error: Throwable) { if (error is T) return else throw error }
        throw AssertionError("expected ${T::class.java.simpleName}")
    }
    private fun source(bytes: ByteArray): AniChDanmakuSource = AniChDanmakuSource("https://provider.example", OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(bytes.toResponseBody("application/octet-stream".toMediaType())).build() }.build())
    private fun page(vararg entries: ByteArray, skip: Int = 0): ByteArray = page(entries.toList(), skip)
    private fun page(entries: List<ByteArray>, skip: Int = 0): ByteArray {
        val out = mutableListOf<Byte>(); entries.forEach { out += tag(1, 2); out += varint(it.size.toLong()); out += it.toList() }; if (skip != 0) { out += tag(2, 0); out += varint(skip.toLong()) }; return out.toByteArray()
    }
    private fun entry(id: String = "", color: String = "FFFFFF", text: String = "", time: Double = 0.0, type: Int = 1): ByteArray {
        val out = mutableListOf<Byte>(); if (id.isNotEmpty()) { out += tag(1, 2); out += str(id) }; if (color.isNotEmpty()) { out += tag(2, 2); out += str(color) }; out += tag(4, 2); out += str(text); out += tag(6, 1); out += ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(time).array().toList(); out += tag(7, 0); out += varint(type.toLong()); return out.toByteArray()
    }
    private fun str(value: String): List<Byte> = varint(value.toByteArray(Charsets.UTF_8).size.toLong()) + value.toByteArray(Charsets.UTF_8).toList()
    private fun tag(field: Int, wire: Int): List<Byte> = varint(((field shl 3) or wire).toLong())
    private fun varint(value: Long): List<Byte> { var v = value; val out = mutableListOf<Byte>(); do { var b = (v and 127).toInt(); v = v ushr 7; if (v != 0L) b = b or 128; out += b.toByte() } while (v != 0L); return out }
}
