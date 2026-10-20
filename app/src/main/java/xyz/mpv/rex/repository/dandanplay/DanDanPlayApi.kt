package xyz.mpv.rex.repository.dandanplay

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import xyz.mpv.rex.BuildConfig
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * DanDanPlay API 服务（参考小喵player 的 DanDanPlayApi，改用 kotlinx.serialization）
 * API 文档: https://api.dandanplay.net/swagger/index.html
 */
class DanDanPlayApi(
    private val customBaseUrl: String? = null,
    private val appIdProvider: () -> String = { BuildConfig.DANDANPLAY_APP_ID },
    private val appSecretProvider: () -> String = { BuildConfig.DANDANPLAY_APP_SECRET },
) {
    companion object {
        private const val TAG = "DanDanPlayApi"
        const val DEFAULT_BASE_URL = "https://api.dandanplay.net"
    }

    /** 是否已配置有效凭证 */
    fun hasCredentials(): Boolean =
        appIdProvider().isNotBlank() && appSecretProvider().isNotBlank()

    private val baseUrl: String
        get() {
            val url = customBaseUrl?.takeIf { it.isNotBlank() }?.trimEnd('/')
            if (url != null && !url.startsWith("http://") && !url.startsWith("https://")) return DEFAULT_BASE_URL
            return url ?: DEFAULT_BASE_URL
        }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 生成签名（签验证模式）
     * 算法: base64(sha256(AppId + Timestamp + Path + AppSecret))
     */
    private fun generateSignature(timestamp: Long, path: String): String {
        val data = "${appIdProvider()}$timestamp$path${appSecretProvider()}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }

    private fun sign(builder: Request.Builder, timestamp: Long, path: String): Request.Builder {
        return builder
            .addHeader("X-AppId", appIdProvider())
            .addHeader("X-Timestamp", timestamp.toString())
            .addHeader("X-Signature", generateSignature(timestamp, path))
    }

    private fun decodeBody(response: okhttp3.Response): String? {
        return if (response.header("Content-Encoding") == "gzip") {
            val gzipStream = GZIPInputStream(response.body?.byteStream())
            val outputStream = ByteArrayOutputStream()
            gzipStream.copyTo(outputStream)
            outputStream.toString("UTF-8")
        } else {
            response.body?.string()
        }
    }

    /**
     * 搜索动漫
     * GET /api/v2/search/episodes
     */
    suspend fun searchAnime(keyword: String): Result<SearchAnimeResponse> = withContext(Dispatchers.IO) {
        try {
            if (!hasCredentials()) return@withContext Result.failure(Exception("未配置弹弹play AppId/AppSecret，请到设置页填写"))
            val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
            val path = "/api/v2/search/episodes"
            val url = "$baseUrl$path?anime=$encodedKeyword"
            val timestamp = System.currentTimeMillis() / 1000

            val request = sign(Request.Builder().url(url).get()
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Encoding", "gzip, deflate")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                timestamp, path).build()

            val response = client.newCall(request).execute()
            val body = decodeBody(response)
            if (response.isSuccessful && body != null) {
                Result.success(json.decodeFromString<SearchAnimeResponse>(body))
            } else {
                Result.failure(Exception("搜索失败: ${response.code} - $body"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "搜索异常", e)
            Result.failure(e)
        }
    }

    /**
     * 获取弹幕
     * GET /api/v2/comment/{episodeId}
     * @param chConvert 繁简转换：0=不转换，1=简体转换为繁体，2=繁体转换为简体
     */
    suspend fun getDanmaku(episodeId: Int, chConvert: Int = 0): Result<DanmakuResponse> = withContext(Dispatchers.IO) {
        try {
            if (!hasCredentials()) return@withContext Result.failure(Exception("未配置弹弹play AppId/AppSecret，请到设置页填写"))
            val path = "/api/v2/comment/$episodeId"
            val convertParam = if (chConvert in 1..2) "&chConvert=$chConvert" else ""
            val url = "$baseUrl$path?withRelated=true$convertParam"
            val timestamp = System.currentTimeMillis() / 1000

            val request = sign(Request.Builder().url(url).get()
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Encoding", "gzip, deflate")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                timestamp, path).build()

            val response = client.newCall(request).execute()
            val body = decodeBody(response)
            if (response.isSuccessful && body != null) {
                Result.success(json.decodeFromString<DanmakuResponse>(body))
            } else {
                Result.failure(Exception("获取弹幕失败: ${response.code} - $body"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取弹幕异常", e)
            Result.failure(e)
        }
    }

    /**
     * 将弹幕转换为 Bilibili XML 格式（供 DanmakuFlameMaster 解析）
     */
    fun convertToXml(danmakuResponse: DanmakuResponse): String {
        val entries = danmakuResponse.comments.mapNotNull { comment ->
            val fields = comment.p.split(',')
            val seconds = fields.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            if (!seconds.isFinite() || seconds < 0 || seconds >= Long.MAX_VALUE / 1000.0) {
                return@mapNotNull null
            }
            xyz.mpv.rex.danmaku.plugin.core.DanmakuEntry(
                timeMs = (seconds * 1000).toLong(),
                text = comment.m,
                mode = fields.getOrNull(1)?.toIntOrNull() ?: 1,
                color = fields.getOrNull(2)?.toIntOrNull() ?: 0xFFFFFF,
                id = comment.cid.toString(),
                source = "dandanplay",
            )
        }
        return xyz.mpv.rex.danmaku.plugin.core.DanmakuCodec.toXml(entries)
    }

    /**
     * 计算文件哈希（前16MB的MD5）
     */
    suspend fun calculateFileHash(filePath: String): String = withContext(Dispatchers.IO) {
        val file = java.io.File(filePath)
        if (!file.exists()) throw IllegalArgumentException("文件不存在: $filePath")
        val readSize = minOf(file.length(), 16 * 1024 * 1024L)
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var totalRead = 0L
            while (totalRead < readSize) {
                val bytesRead = input.read(buffer, 0, minOf(buffer.size.toLong(), readSize - totalRead).toInt())
                if (bytesRead == -1) break
                digest.update(buffer, 0, bytesRead)
                totalRead += bytesRead
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 使用文件哈希匹配弹幕
     * POST /api/v2/match
     * 文档要求 fileName 不包含文件夹名称和扩展名，此方法内部会做一次剥离保险。
     */
    suspend fun matchDanmaku(fileName: String, fileHash: String, fileSize: Long): MatchResponse = withContext(Dispatchers.IO) {
        if (!hasCredentials()) throw Exception("未配置弹弹play AppId/AppSecret，请到设置页填写")
        val timestamp = System.currentTimeMillis() / 1000
        val path = "/api/v2/match"
        val cleanName = cleanMatchFileName(fileName)
        val matchRequest = MatchRequest(fileName = cleanName, fileHash = fileHash, fileSize = fileSize)
        val requestJson = json.encodeToString(MatchRequest.serializer(), matchRequest)
        val requestBody = requestJson.toRequestBody("application/json".toMediaType())

        val request = sign(Request.Builder().url("$baseUrl$path").post(requestBody)
            .addHeader("Accept-Encoding", "gzip")
            .addHeader("Content-Type", "application/json"),
            timestamp, path).build()

        val response = client.newCall(request).execute()
        val body = decodeBody(response) ?: throw Exception("空响应")
        if (!response.isSuccessful) throw Exception("匹配失败: ${response.code} - $body")
        json.decodeFromString<MatchResponse>(body)
    }

    /**
     * 批量匹配多个文件到对应弹幕库
     * POST /api/v2/match/batch
     * 每次最多 32 个请求，不能有重复项。
     * @return 每个请求对应的匹配结果（与传入顺序一致）
     */
    suspend fun batchMatch(files: List<Triple<String, String, Long>>): BatchMatchResponse = withContext(Dispatchers.IO) {
        if (!hasCredentials()) throw Exception("未配置弹弹play AppId/AppSecret，请到设置页填写")
        require(files.size <= 32) { "批量匹配一次最多 32 个文件" }
        val timestamp = System.currentTimeMillis() / 1000
        val path = "/api/v2/match/batch"

        val requests = files.map { (fileName, fileHash, fileSize) ->
            MatchRequest(
                fileName = cleanMatchFileName(fileName),
                fileHash = fileHash,
                fileSize = fileSize,
            )
        }
        val batchRequest = BatchMatchRequest(requests = requests)
        val requestJson = json.encodeToString(BatchMatchRequest.serializer(), batchRequest)
        val requestBody = requestJson.toRequestBody("application/json".toMediaType())

        val request = sign(Request.Builder().url("$baseUrl$path").post(requestBody)
            .addHeader("Accept-Encoding", "gzip")
            .addHeader("Content-Type", "application/json"),
            timestamp, path).build()

        val response = client.newCall(request).execute()
        val body = decodeBody(response) ?: throw Exception("空响应")
        if (!response.isSuccessful) throw Exception("批量匹配失败: ${response.code} - $body")
        json.decodeFromString<BatchMatchResponse>(body)
    }

    /**
     * 清洗匹配用的文件名：去掉文件夹路径和扩展名（文档要求）。
     */
    private fun cleanMatchFileName(fileName: String): String {
        if (fileName.isBlank()) return fileName
        var name = fileName.substringAfterLast('/').substringAfterLast('\\')
        name = name.substringBeforeLast('.')
        return name.trim()
    }
}
