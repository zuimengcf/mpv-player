package xyz.mpv.rex.danmaku

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import master.flame.danmaku.controller.DanmakuFilters
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.ui.widget.DanmakuView
import java.io.File

/**
 * 弹幕关键词屏蔽过滤器：命中黑名单关键词的弹幕不显示。
 * setData 传入分号分隔的关键词列表；每次设置即刷新屏蔽规则。
 */
class BlockKeywordFilter : DanmakuFilters.BaseDanmakuFilter<List<String>>() {
    private val keywords = mutableListOf<String>()

    override fun filter(
        danmaku: BaseDanmaku,
        order: Int,
        size: Int,
        timer: DanmakuTimer,
        isR2L: Boolean,
        context: DanmakuContext,
    ): Boolean {
        if (keywords.isEmpty()) return false
        // 库可能对 null 条目调用 filter，需判空保护避免空指针闪退
        val text = danmaku?.text?.toString() ?: return false
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    override fun setData(data: List<String>) {
        keywords.clear()
        keywords.addAll(data.map { it.trim() }.filter { it.isNotBlank() })
    }

    override fun reset() {
        keywords.clear()
    }
}

/**
 * 弹幕管理器 — 精简版
 * 负责弹幕的加载、开始、暂停、seek、释放
 * 通过 PlaybackPositionProvider 与 mpv 播放进度同步
 */
class DanmakuManager(
    private val context: Context,
    private val danmakuView: DanmakuView,
    private val danmakuPreferences: xyz.mpv.rex.preferences.DanmakuPreferences? = null,
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

    /** 弹幕关键词屏蔽过滤器（registerFilter 于 init，applyPreferences 时刷新关键词） */
    private val blockFilter = BlockKeywordFilter()

    private var currentDanmakuPath: String? = null
    private var currentDanmakuTitle: String? = null
    private var danmakuLoaded = false
    private var trackSelected = false
    private var positionProvider: PlaybackPositionProvider? = null

    /** 当前播放视频的本地绝对路径（可空）。用于把弹幕缓存写到视频同目录同名 .xml。 */
    private var currentVideoPath: String? = null

    /** 弹幕时间轴偏移（毫秒，正=弹幕延后，负=弹幕提前）。用于本地动态调整。 */
    private var danmakuOffsetMs: Long = 0L

    /** 上次应用的覆盖颜色（-1=未覆盖），用于检测颜色变化是否需要重载弹幕 */
    private var lastAppliedOverrideColor = -1

    var onPreparedListener: (() -> Unit)? = null

    init {
        // 基本配置：禁止重叠
        val overlappingPair: MutableMap<Int, Boolean> = HashMap()
        overlappingPair[BaseDanmaku.TYPE_SCROLL_LR] = true
        overlappingPair[BaseDanmaku.TYPE_SCROLL_RL] = true
        overlappingPair[BaseDanmaku.TYPE_FIX_TOP] = true
        overlappingPair[BaseDanmaku.TYPE_FIX_BOTTOM] = true

        blockFilter.setData(parseBlockKeywords(danmakuPreferences?.blockKeywords?.get().orEmpty()))

        danmakuContext.apply {
            // 同一弹幕合并开关（读偏好，默认开启）
            isDuplicateMergingEnabled = danmakuPreferences?.mergeDuplicate?.get() ?: true
            preventOverlapping(overlappingPair)
            // 关键词屏蔽：命中黑名单的弹幕不显示
            registerFilter(blockFilter)
        }

        // 应用用户偏好（字号/滚动速度/描边/阴影/透明度/密度）
        applyPreferences()

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
                danmakuView.visibility = if (trackSelected && (danmakuPreferences?.displayArea?.get() ?: 0) != 3) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
                // 关键修复：prepared 后必须 start 才能真正渲染弹幕
                // 只要 trackSelected 就启动渲染循环，暂停状态由 pauseDanmaku/resumeDanmaku 同步
                if (trackSelected) {
                    danmakuView.start()
                    Log.d(TAG, "Danmaku started after prepare")
                }
                onPreparedListener?.invoke()
            }
            override fun updateTimer(timer: DanmakuTimer?) {}
        })
    }

    fun setPositionProvider(provider: PlaybackPositionProvider) {
        positionProvider = provider
    }

    /**
     * 应用弹幕偏好设置（字号/滚动速度/描边/阴影/透明度/密度/显示类型）。
     * 可在设置页变更后调用刷新；未配置 DanmakuPreferences 时保持默认。
     */
    fun applyPreferences() {
        val prefs = danmakuPreferences ?: return
        try {
            // 同一弹幕合并开关（运行时刷新）
            danmakuContext.isDuplicateMergingEnabled = prefs.mergeDuplicate.get()
            // 关键词屏蔽（运行时刷新，分号分隔）
            blockFilter.setData(parseBlockKeywords(prefs.blockKeywords.get().orEmpty()))

            val fontSize = prefs.fontSize.get().coerceIn(12, 70)
            // 基准字号 22sp → scale
            val scale = fontSize / 22f
            danmakuContext.setScaleTextSize(scale)

            val speed = prefs.scrollSpeed.get().coerceIn(0.5f, 2f)
            danmakuContext.setScrollSpeedFactor(1f / speed)

            // 描边/阴影/透明度
            val border = prefs.borderSize.get().coerceIn(0, 5)
            val shadow = prefs.shadowRadius.get().coerceIn(0, 10)
            val alpha = prefs.alpha.get().coerceIn(0, 255)
            if (border > 0) {
                danmakuContext.setDanmakuStyle(
                    master.flame.danmaku.danmaku.model.IDisplayer.DANMAKU_STYLE_STROKEN,
                    border.toFloat(), 0f
                )
            } else if (shadow > 0) {
                danmakuContext.setDanmakuStyle(
                    master.flame.danmaku.danmaku.model.IDisplayer.DANMAKU_STYLE_SHADOW,
                    shadow.toFloat(), 0f
                )
            } else {
                danmakuContext.setDanmakuStyle(
                    master.flame.danmaku.danmaku.model.IDisplayer.DANMAKU_STYLE_NONE,
                    0f, 0f
                )
            }

            // 密度（最大显示行数）：稀疏 2 行 / 正常 5 行 / 密集 10 行
            val density = prefs.density.get().coerceIn(0, 2)
            val maxLines = when (density) {
                0 -> 2
                1 -> 5
                else -> 10
            }
            val linesMap = HashMap<Int, Int>()
            linesMap[BaseDanmaku.TYPE_SCROLL_LR] = maxLines
            linesMap[BaseDanmaku.TYPE_SCROLL_RL] = maxLines
            linesMap[BaseDanmaku.TYPE_FIX_TOP] = maxLines
            linesMap[BaseDanmaku.TYPE_FIX_BOTTOM] = maxLines
            danmakuContext.setMaximumLines(linesMap)

            // 透明度：alpha 0-255 → 0.0-1.0
            danmakuContext.setDanmakuTransparency(alpha / 255f)

            // 统一颜色覆盖
            val overrideColor = prefs.overrideColor.get()
            val fontColor = prefs.fontColor.get()
            val newOverrideColor = if (overrideColor) fontColor else -1
            BiliDanmakuParser.setOverrideColor(newOverrideColor)

            // 颜色覆盖变化且已加载弹幕 → 自动重载以应用新颜色（保持播放位置与显示状态）
            if (danmakuLoaded && newOverrideColor != lastAppliedOverrideColor) {
                val currentPath = currentDanmakuPath
                val currentPos = positionProvider?.getCurrentPositionMs() ?: 0L
                val wasSelected = trackSelected
                if (currentPath != null) {
                    Log.d(TAG, "Override color changed ($lastAppliedOverrideColor -> $newOverrideColor), reloading danmaku")
                    loadDanmaku(currentPath, currentDanmakuTitle)
                    // loadDanmaku 内部 release 会重置显示状态，这里恢复并同步位置
                    if (wasSelected) {
                        showDanmaku()
                        danmakuView.seekTo(currentPos)
                    }
                }
            }
            lastAppliedOverrideColor = newOverrideColor

            // 显示类型过滤（滚动/顶部/底部）
            val showScroll = prefs.showScrollDanmaku.get()
            val showTop = prefs.showTopDanmaku.get()
            val showBottom = prefs.showBottomDanmaku.get()
            danmakuContext.setR2LDanmakuVisibility(showScroll)
            danmakuContext.setL2RDanmakuVisibility(showScroll)
            danmakuContext.setFTDanmakuVisibility(showTop)
            danmakuContext.setFBDanmakuVisibility(showBottom)

            // 显示区域（固定顶部，避免遮挡底部字幕）：
            // 0=全屏 1=半屏(50%) 2=1/4屏(25%) 3=不显示(0%)
            // 通过直接修改 DanmakuView 高度实现：顶部对齐，底部留白给字幕。
            val area = prefs.displayArea.get()
            val parent = danmakuView.parent as? ViewGroup
            val parentHeight = parent?.height ?: danmakuView.height
            // 视图未布局（高度为 0）时延后到布局完成后重试，避免误把高度设为 0
            if (parentHeight <= 0) {
                danmakuView.post {
                    try { applyPreferences() } catch (e: Exception) {
                        Log.e(TAG, "Retry applyPreferences after layout failed", e)
                    }
                }
            } else {
                applyDanmakuArea(area, parentHeight)
            }

            Log.d(TAG, "Danmaku preferences applied: scale=$scale speed=$speed border=$border shadow=$shadow alpha=$alpha density=$density area=$area scroll=$showScroll top=$showTop bottom=$showBottom")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply danmaku preferences", e)
        }
    }

    /**
     * 应用弹幕显示区域（固定顶部，底部留白给字幕）。
     * @param area 0=全屏 1=半屏(50%) 2=1/4屏(25%) 3=不显示(0%)
     * @param parentHeight 父容器高度（px）
     */
    private fun applyDanmakuArea(area: Int, parentHeight: Int) {
        val areaHeight = when (area) {
            1 -> (parentHeight / 2).coerceAtLeast(1)
            2 -> (parentHeight / 4).coerceAtLeast(1)
            3 -> 0
            else -> parentHeight
        }
        val lp = danmakuView.layoutParams
        if (lp != null) {
            lp.height = areaHeight
            danmakuView.layoutParams = lp
            // 高度为 0 时彻底隐藏，避免空白拦截点击
            danmakuView.visibility = if (areaHeight == 0 || !trackSelected) {
                View.GONE
            } else {
                View.VISIBLE
            }
            Log.d(TAG, "Danmaku display area: $area -> height=$areaHeight (parent=$parentHeight)")
        }
        // 清空旧的 margin/alignBottom 模拟，避免残留影响
        danmakuContext.setMarginTop(0)
        danmakuContext.alignBottom(false)
    }

    /**
     * 加载弹幕文件（B站 XML 格式）
     * @param title 弹幕标题（如 "番剧名 - 第x集"），用于持久化绑定展示，可为空
     */
    fun loadDanmaku(filePath: String, title: String? = null): Boolean {
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
            currentDanmakuTitle = title
            // enabledByDefault=false 时加载但不自动显示（需手动开"显示弹幕"）
            val enabledByDefault = danmakuPreferences?.enabledByDefault?.get() ?: true
            trackSelected = enabledByDefault

            val parser = BiliDanmakuParser().apply { load(dataSource) }
            danmakuView.prepare(parser, danmakuContext)
            danmakuView.visibility = if (enabledByDefault) android.view.View.VISIBLE else android.view.View.GONE

            // 重新应用显示区域高度（加载可能触发重新布局）
            val area = danmakuPreferences?.displayArea?.get() ?: 0
            val parentHeight = (danmakuView.parent as? ViewGroup)?.height ?: danmakuView.height
            if (parentHeight > 0) applyDanmakuArea(area, parentHeight) else applyPreferences()

            Log.d(TAG, "Danmaku loaded successfully (enabledByDefault=$enabledByDefault)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading danmaku", e)
            Toast.makeText(context, "弹幕加载异常: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    /**
     * 设置当前播放视频的本地绝对路径。用于把弹幕缓存写到视频同目录同名 .xml。
     * 传 null/空或非本地路径（content:// 等）时，弹幕回退写到应用私有目录。
     */
    fun setCurrentVideoPath(videoPath: String?) {
        currentVideoPath = videoPath?.takeIf { it.startsWith("/") || it.startsWith("file://") }?.let {
            if (it.startsWith("file://")) it.removePrefix("file://") else it
        }
    }

    /** 当前播放视频的本地绝对路径（清洗后，无 file:// 前缀）。未设置或非本地路径时为 null。 */
    fun getCurrentVideoPath(): String? = currentVideoPath

    /** 解析屏蔽关键词：分号（含中文；）或逗号分隔，去空白并去空项。 */
    private fun parseBlockKeywords(raw: String): List<String> =
        raw.split(';', '；', ',', '，')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    /**
     * 从 XML 字符串加载弹幕（用于 dandanplay 在线获取）。
     * 若当前视频为本地路径，弹幕写入视频同目录同名 .xml（随视频拷贝/移动，便于复用）；
     * 否则回退写入应用文件目录（filesDir/danmaku，持久存储，系统清理缓存不丢失）。
     */
    fun loadDanmakuFromXml(content: String, title: String): Boolean {
        return try {
            val videoPath = currentVideoPath
            var target: File? = null
            if (!videoPath.isNullOrBlank()) {
                val videoFile = File(videoPath)
                val parent = videoFile.parentFile
                if (parent != null && parent.canWrite()) {
                    // 视频同目录同名 .xml，如 "xxx.mp4" -> "xxx.xml"
                    val xmlFile = File(parent, "${videoFile.nameWithoutExtension}.xml")
                    target = xmlFile
                }
            }
            if (target == null) {
                val dir = File(context.filesDir, "danmaku")
                if (!dir.exists()) dir.mkdirs()
                val cleanName = title.replace(Regex("[^a-zA-Z0-9_\\u4e00-\\u9fa5]"), "_")
                target = File(dir, "${cleanName}_${System.currentTimeMillis()}.xml")
            }
            target.writeText(content)
            loadDanmaku(target.absolutePath, title)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing danmaku xml", e)
            Toast.makeText(context, "弹幕写入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            false
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
            // 若渲染循环未启动（例如加载时处于暂停状态），resume 前先 start
            if (!danmakuView.isPrepared()) {
                danmakuView.start()
                Log.d(TAG, "Danmaku started (resume fallback)")
            }
            danmakuView.resume()
            Log.d(TAG, "Danmaku resumed")
        }
    }

    fun seekTo(timeMs: Long) {
        if (danmakuLoaded) {
            val adjusted = (timeMs + danmakuOffsetMs).coerceAtLeast(0L)
            danmakuView.seekTo(adjusted)
            Log.d(TAG, "Danmaku seek to: $timeMs ms (offset=$danmakuOffsetMs, adjusted=$adjusted)")
        }
    }

    fun showDanmaku() {
        trackSelected = true
        if (danmakuLoaded) {
            if (!danmakuView.isPrepared()) {
                danmakuView.start()
                Log.d(TAG, "Danmaku started (show fallback)")
            }
            // 显示区域为「不显示」时保持隐藏（高度=0 已隐藏，这里不强行显示）
            val area = danmakuPreferences?.displayArea?.get() ?: 0
            if (area != 3) {
                danmakuView.visibility = android.view.View.VISIBLE
            }
        }
    }

    fun hideDanmaku() {
        trackSelected = false
        if (danmakuLoaded) {
            danmakuView.visibility = android.view.View.GONE
        }
    }

    fun isDanmakuLoaded(): Boolean = danmakuLoaded
    fun isTrackSelected(): Boolean = trackSelected

    /** 当前已加载弹幕的文件路径（null=未加载） */
    fun getCurrentDanmakuPath(): String? = currentDanmakuPath

    /** 当前已加载弹幕的标题（如 "番剧名 - 第x集"，null=未加载/无标题） */
    fun getCurrentDanmakuTitle(): String? = currentDanmakuTitle

    /** 获取弹幕时间轴偏移（毫秒） */
    fun getDanmakuOffset(): Long = danmakuOffsetMs

    /**
     * 设置弹幕时间轴偏移（毫秒，正=弹幕延后，负=弹幕提前）。
     * 设置后立即同步到当前播放位置。
     */
    fun setDanmakuOffset(offsetMs: Long) {
        danmakuOffsetMs = offsetMs
        if (danmakuLoaded) {
            val currentPos = positionProvider?.getCurrentPositionMs() ?: 0L
            val adjusted = (currentPos + danmakuOffsetMs).coerceAtLeast(0L)
            danmakuView.seekTo(adjusted)
            Log.d(TAG, "Danmaku offset set to $offsetMs ms, re-synced to $adjusted")
        }
    }

    fun releaseDanmaku() {
        if (danmakuLoaded) {
            danmakuView.release()
            danmakuLoaded = false
            trackSelected = false
            currentDanmakuPath = null
            currentDanmakuTitle = null
            danmakuOffsetMs = 0L
            danmakuView.visibility = android.view.View.GONE
            // 还原高度为 match_parent，避免残留区域高度影响下次加载
            val lp = danmakuView.layoutParams
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                danmakuView.layoutParams = lp
            }
            Log.d(TAG, "Danmaku released")
        }
    }

    fun release() {
        releaseDanmaku()
    }
}
