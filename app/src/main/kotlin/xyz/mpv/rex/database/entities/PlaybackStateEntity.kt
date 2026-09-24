package xyz.mpv.rex.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PlaybackStateEntity(
  @PrimaryKey val mediaTitle: String,
  val lastPosition: Int, // in seconds
  val playbackSpeed: Double,
  val videoZoom: Float = 0f,
  val sid: Int,
  val secondarySid: Int = -1, // Secondary subtitle track ID (-1 means disabled)
  val subDelay: Int,
  val subSpeed: Double,
  val aid: Int,
  val audioDelay: Int,
  val timeRemaining: Int = 0, // in seconds (duration - lastPosition)
  val savedOrientation: Int? = null, // Persisted orientation for Smart mode
  val externalSubtitles: String = "", // Pipe-separated list of external subtitle URIs
  val externalAudioTracks: String = "", // Pipe-separated list of external audio URIs
  val hasBeenWatched: Boolean = false, // Persistent flag: true if video has ever reached the watched threshold
  val danmakuPath: String = "", // 绑定的弹幕文件路径（空=未绑定）。持久化绑定，退出不解绑，手动解除才清空
  val danmakuTitle: String = "", // 绑定的弹幕标题（如 "番剧名 - 第x集"）
  val danmakuSelected: Boolean = false, // 弹幕是否显示（绑定但可临时隐藏，切换状态持久化）
  val danmakuOffset: Long = 0L, // 弹幕时间轴偏移（毫秒，正=延后，负=提前）
  val videoAspect: String? = null, // Persisted aspect ratio mode name (e.g. Fit, Crop, Stretch)
  val customAspectRatio: Double = -1.0, // Persisted custom aspect ratio value
)
