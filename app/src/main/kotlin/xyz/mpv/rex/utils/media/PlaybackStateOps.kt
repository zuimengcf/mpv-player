package xyz.mpv.rex.utils.media

import android.util.Log
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import org.koin.java.KoinJavaComponent.inject
import java.io.File

/**
 * Utility for managing playback state when files are renamed or deleted.
 */
object PlaybackStateOps {
  private const val TAG = "PlaybackStateOps"
  private val repository: PlaybackStateRepository by inject(PlaybackStateRepository::class.java)

  /**
   * Called when a video file is renamed
   * Updates the playback state entry to use the new filename
   *
   * @param oldPath The original file path
   * @param newPath The new file path after renaming
   */
  suspend fun onVideoRenamed(
    oldPath: String,
    newPath: String,
  ) {
    if (oldPath.isBlank() || newPath.isBlank()) return

    try {
      val oldFileName = File(oldPath).name
      val newFileName = File(newPath).name

      // Only update if the filename actually changed
      if (oldFileName != newFileName) {
        repository.updateMediaTitle(oldFileName, newFileName)
        Log.d(TAG, "✓ Updated playback state: $oldFileName -> $newFileName")
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to update playback state: ${e.message}")
    }
  }

  /**
   * Called when a video file is deleted
   * Removes its playback state entry and deletes its associated local danmaku files.
   *
   * @param filePath The path of the deleted file
   */
  suspend fun onVideoDeleted(filePath: String) {
    if (filePath.isBlank()) return

    try {
      val file = File(filePath)
      val fileName = file.name

      // 先取该视频的播放状态（含弹幕绑定路径 danmakuPath），精确删除绑定的弹幕文件
      // （如非本地视频回退写入 filesDir/danmaku 的缓存，文件名带时间戳无法按视频名对应）
      runCatching {
        val state = repository.getVideoDataByTitle(fileName)
        val boundPath = state?.danmakuPath
        if (!boundPath.isNullOrBlank()) {
          val boundFile = File(boundPath)
          if (boundFile.exists() && boundFile.delete()) {
            Log.d(TAG, "✓ Deleted bound danmaku: $boundPath")
          }
        }
      }

      repository.deleteByTitle(fileName)
      Log.d(TAG, "✓ Deleted playback state for: $fileName")

      // 联动删除该视频关联的本地弹幕文件，避免残留：
      // 1) 视频同目录同名 .xml（"xxx.mp4" -> "xxx.xml"，随视频拷贝/移动的弹幕缓存）
      // 2) 应用私有目录 danmaku/ 下以视频文件名命名的弹幕缓存（非本地视频回退写入位置）
      runCatching {
        val parent = file.parentFile
        if (parent != null && parent.canWrite()) {
          val siblingXml = File(parent, "${file.nameWithoutExtension}.xml")
          if (siblingXml.exists() && siblingXml.delete()) {
            Log.d(TAG, "✓ Deleted sibling danmaku: ${siblingXml.absolutePath}")
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to delete playback state: ${e.message}")
    }
  }
}
