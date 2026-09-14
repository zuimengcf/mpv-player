package xyz.mpv.rex.repository.dandanplay

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DanDanPlay API 数据模型（参考 xyoye/DanDanPlayForAndroid 与小喵player）
 */

// 搜索动漫响应
@Serializable
data class SearchAnimeResponse(
    @SerialName("hasMore") val hasMore: Boolean,
    @SerialName("animes") val animes: List<AnimeSearchInfo>? = null,
    @SerialName("errorCode") val errorCode: Int? = null,
    @SerialName("success") val success: Boolean? = null,
    @SerialName("errorMessage") val errorMessage: String? = null,
)

// 动漫信息
@Serializable
data class AnimeSearchInfo(
    @SerialName("animeId") val animeId: Int,
    @SerialName("animeTitle") val animeTitle: String,
    @SerialName("type") val type: String? = null,
    @SerialName("typeDescription") val typeDescription: String? = null,
    @SerialName("episodes") val episodes: List<EpisodeInfo>? = null,
)

// 剧集信息
@Serializable
data class EpisodeInfo(
    @SerialName("episodeId") val episodeId: Int,
    @SerialName("episodeTitle") val episodeTitle: String,
)

// 弹幕响应
@Serializable
data class DanmakuResponse(
    @SerialName("count") val count: Int,
    @SerialName("comments") val comments: List<DanmakuComment>,
)

// 弹幕评论
@Serializable
data class DanmakuComment(
    @SerialName("cid") val cid: Long,
    @SerialName("p") val p: String,  // 格式: "时间,模式,颜色,用户ID"
    @SerialName("m") val m: String,  // 弹幕内容
)

// 文件哈希匹配请求
@Serializable
data class MatchRequest(
    @SerialName("fileName") val fileName: String,
    @SerialName("fileHash") val fileHash: String,
    @SerialName("fileSize") val fileSize: Long,
    @SerialName("videoDuration") val videoDuration: Double? = null,
    @SerialName("matchMode") val matchMode: String? = null,
)

// 文件哈希匹配响应
@Serializable
data class MatchResponse(
    @SerialName("isMatched") val isMatched: Boolean,
    @SerialName("matches") val matches: List<MatchInfo>? = null,
)

// 匹配信息
@Serializable
data class MatchInfo(
    @SerialName("episodeId") val episodeId: Int,
    @SerialName("animeId") val animeId: Int,
    @SerialName("animeTitle") val animeTitle: String,
    @SerialName("episodeTitle") val episodeTitle: String,
    @SerialName("type") val type: String? = null,
    @SerialName("typeDescription") val typeDescription: String? = null,
    @SerialName("shift") val shift: Double = 0.0,
)
