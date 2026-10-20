package xyz.mpv.rex.danmaku.plugin

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuTimelineTest {
    @Test fun positiveDelayShowsCommentsLater() {
        assertEquals(8_000L, DanmakuTimeline.position(10_000L, 2_000L))
    }
    @Test fun negativeDelayShowsCommentsEarlier() {
        assertEquals(12_000L, DanmakuTimeline.position(10_000L, -2_000L))
    }
    @Test fun timelineCannotGoBelowZero() {
        assertEquals(0L, DanmakuTimeline.position(1_000L, 2_000L))
        assertEquals(0L, DanmakuTimeline.position(-50L, 0L))
    }
    @Test fun extremeValuesCannotWrap() {
        assertEquals(Long.MAX_VALUE, DanmakuTimeline.position(Long.MAX_VALUE, -1L))
        assertEquals(Long.MAX_VALUE, DanmakuTimeline.position(0L, Long.MIN_VALUE))
        assertEquals(0L, DanmakuTimeline.position(0L, Long.MAX_VALUE))
    }
}
