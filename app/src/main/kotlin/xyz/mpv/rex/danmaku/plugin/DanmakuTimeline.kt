package xyz.mpv.rex.danmaku.plugin

/** 正偏移表示弹幕延后，因此引擎读取 videoPosition - delay。 */
object DanmakuTimeline {
    fun position(videoPositionMs: Long, delayMs: Long): Long {
        val video = videoPositionMs.coerceAtLeast(0L)
        return if (delayMs >= 0L) {
            if (video <= delayMs) 0L else video - delayMs
        } else {
            try {
                Math.subtractExact(video, delayMs)
            } catch (_: ArithmeticException) {
                Long.MAX_VALUE
            }
        }
    }
}
