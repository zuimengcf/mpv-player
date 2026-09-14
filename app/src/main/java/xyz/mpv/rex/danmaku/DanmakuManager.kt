package xyz.mpv.rex.danmaku

import android.content.Context
import android.util.Log
import android.widget.Toast
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.ui.widget.DanmakuView
import java.io.File

/**
 * 弹幕管理器 — 精简版
 * 负责弹幕的加载、开始、暂停、seek、释放
 * 通过 PlaybackPositionProvider 与 mpv 播放进度同步
 */
class DanmakuManager(
    private val context: Context,
    private val danmakuView: DanmakuView,
) {
    companion object {
        private const val TAG = "DanmakuManager"
    }

    interface PlaybackPositionProvider {
        fun getCurrentPositionMs(): Long
        fun isPlaying(): Boolean
    }

    private val danmakuContext = DanmakuContext.create()
    private val danmakuLoader = BiliDanmakuLoader.instance()

    private var currentDanmakuPath: String? = null
    private var danmakuLoaded = false
    private var trackSelected = false
    private var positionProvider: PlaybackPositionProvider? = null

    var onPreparedListener: (() -> Unit)? = null

    init {
        // 基本配置：禁止重叠
        val overlappingPair: MutableMap<Int, Boolean> = HashMap()
        overlappingPair[BaseDanmaku.TYPE_SCROLL_LR] = true
        overlappingPair[BaseDanmaku.TYPE_SCROLL_RL] = true
        overlappingPair[BaseDanmaku.TYPE_FIX_TOP] = true
        overlappingPair[BaseDanmaku.TYPE_FIX_BOTTOM] = true

        danmakuContext.apply {
            isDuplicateMergingEnabled = true
            enableDanmakuDrawingCache(true)
            preventOverlapping(overlappingPair)
        }

        danmakuView.setCallback(object : DrawHandler.Callback {
            override fun drawingFinished() {}
            override fun danmakuShown(danmaku: BaseDanmaku?) {}
            override fun prepared() {
                danmakuLoaded = true
                Log.d(TAG, "Danmaku prepared")
                // 同步到当前播放位置
                positionProvider?.let { provider ->
                    if (trackSelected) {
                        val pos = provider.getCurrentPositionMs()
                        danmakuView.seekTo(pos)
                        Log.d(TAG, "Synced to position: $pos ms")
                    }
                }
                danmakuView.setDanmuVisible(trackSelected)
                onPreparedListener?.invoke()
            }
            override fun updateTimer(timer: DanmakuTimer?) {}
        })
    }

    fun setPositionProvider(provider: PlaybackPositionProvider) {
        positionProvider = provider
    }

    /**
     * 加载弹幕文件（B站 XML 格式）
     */
    fun loadDanmaku(filePath: String): Boolean {
        try {
            Log.d(TAG, "Loading danmaku: $filePath")
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "Danmaku file not found: $filePath")
                Toast.makeText(context, "弹幕文件不存在", Toast.LENGTH_SHORT).show()
                return false
            }

            releaseDanmaku()

            danmakuLoader.load(filePath)
            val dataSource = danmakuLoader.dataSource
            if (dataSource == null) {
                Toast.makeText(context, "弹幕加载失败", Toast.LENGTH_SHORT).show()
                return false
            }

            currentDanmakuPath = filePath
            trackSelected = true

            val parser = BiliDanmakuParser().apply { load(dataSource) }
            danmakuView.prepare(parser, danmakuContext)
            danmakuView.visibility = android.view.View.VISIBLE

            Log.d(TAG, "Danmaku loaded successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading danmaku", e)
            Toast.makeText(context, "弹幕加载异常: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    fun startDanmaku() {
        if (danmakuLoaded && trackSelected) {
            danmakuView.start()
            Log.d(TAG, "Danmaku started")
        }
    }

    fun pauseDanmaku() {
        if (danmakuLoaded) {
            danmakuView.pause()
            Log.d(TAG, "Danmaku paused")
        }
    }

    fun resumeDanmaku() {
        if (danmakuLoaded && trackSelected) {
            danmakuView.resume()
            Log.d(TAG, "Danmaku resumed")
        }
    }

    fun seekTo(timeMs: Long) {
        if (danmakuLoaded) {
            danmakuView.seekTo(timeMs)
            Log.d(TAG, "Danmaku seek to: $timeMs ms")
        }
    }

    fun showDanmaku() {
        trackSelected = true
        if (danmakuLoaded) {
            danmakuView.setDanmuVisible(true)
        }
    }

    fun hideDanmaku() {
        trackSelected = false
        if (danmakuLoaded) {
            danmakuView.setDanmuVisible(false)
        }
    }

    fun isDanmakuLoaded(): Boolean = danmakuLoaded
    fun isTrackSelected(): Boolean = trackSelected

    fun releaseDanmaku() {
        if (danmakuLoaded) {
            danmakuView.release()
            danmakuLoaded = false
            trackSelected = false
            currentDanmakuPath = null
            danmakuView.visibility = android.view.View.GONE
            Log.d(TAG, "Danmaku released")
        }
    }

    fun release() {
        releaseDanmaku()
        danmakuView.onDetachedFromWindow()
    }
}
