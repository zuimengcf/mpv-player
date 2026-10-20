package xyz.mpv.rex.danmaku.plugin

import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuLoadSessionTest {
    private class QueuedExecutor : Executor {
        private val jobs = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            jobs.addLast(command)
        }

        fun runNext() = jobs.removeFirst().run()
        fun size() = jobs.size
    }

    private class QueuedUi {
        private val actions = ArrayDeque<() -> Unit>()

        fun dispatch(action: () -> Unit) {
            actions.addLast(action)
        }

        fun runNext() = actions.removeFirst().invoke()
        fun size() = actions.size
    }

    @Test
    fun eachBeginGetsUniqueTicketEvenWhenPathWouldBeTheSame() {
        val cleanup = QueuedExecutor()
        val session = DanmakuLoadSession(cleanup) { it() }

        val first = session.begin()
        val second = session.begin()

        assertFalse(first === second)
        assertFalse(session.isCurrent(first))
        assertTrue(session.isCurrent(second))
    }

    @Test
    fun callbackAlreadyQueuedOnUiIsRejectedAfterNewBegin() {
        val cleanup = QueuedExecutor()
        val ui = QueuedUi()
        val session = DanmakuLoadSession(cleanup, ui::dispatch)
        val accepted = mutableListOf<String>()

        val old = session.begin()
        session.afterCleanup(old, cleanup = {}, ready = { accepted += "old" })
        cleanup.runNext()
        assertEquals(1, ui.size())

        session.begin()
        ui.runNext()

        assertTrue(accepted.isEmpty())
    }

    @Test
    fun callbackAlreadyQueuedOnUiIsRejectedAfterInvalidate() {
        val cleanup = QueuedExecutor()
        val ui = QueuedUi()
        val session = DanmakuLoadSession(cleanup, ui::dispatch)
        var ready = false

        val ticket = session.begin()
        session.afterCleanup(ticket, cleanup = {}, ready = { ready = true })
        cleanup.runNext()
        session.invalidate()
        ui.runNext()

        assertFalse(ready)
        assertFalse(session.isCurrent(ticket))
    }

    @Test
    fun readyRunsOnlyAfterCleanupAndOnlyOnUiDispatcher() {
        val cleanup = QueuedExecutor()
        val ui = QueuedUi()
        val session = DanmakuLoadSession(cleanup, ui::dispatch)
        val events = mutableListOf<String>()
        val ticket = session.begin()

        session.afterCleanup(
            ticket = ticket,
            cleanup = { events += "cleanup" },
            ready = { events += "ready" },
        )

        assertEquals(listOf<String>(), events)
        assertEquals(1, cleanup.size())
        cleanup.runNext()
        assertEquals(listOf("cleanup"), events)
        assertEquals(1, ui.size())
        ui.runNext()
        assertEquals(listOf("cleanup", "ready"), events)
    }

    @Test
    fun severalPendingLoadsOnlyLatestTicketCanBecomeReady() {
        val cleanup = QueuedExecutor()
        val ui = QueuedUi()
        val session = DanmakuLoadSession(cleanup, ui::dispatch)
        val ready = mutableListOf<Int>()

        val first = session.begin()
        session.afterCleanup(first, cleanup = {}, ready = { ready += 1 })
        val second = session.begin()
        session.afterCleanup(second, cleanup = {}, ready = { ready += 2 })
        val third = session.begin()
        session.afterCleanup(third, cleanup = {}, ready = { ready += 3 })

        // All cleanups may retire in order, but only the current ticket may be accepted.
        repeat(3) { cleanup.runNext() }
        assertEquals(3, ui.size())
        repeat(3) { ui.runNext() }

        assertEquals(listOf(3), ready)
    }

    @Test
    fun cleanupFailureDoesNotRunReady() {
        val cleanup = QueuedExecutor()
        val ui = QueuedUi()
        val session = DanmakuLoadSession(cleanup, ui::dispatch)
        val failure = IllegalStateException("release failed")
        var ready = false
        var reported: Exception? = null
        val ticket = session.begin()

        session.afterCleanup(
            ticket = ticket,
            cleanup = { throw failure },
            ready = { ready = true },
            failed = { reported = it },
        )
        cleanup.runNext()
        assertFalse(ready)
        assertEquals(1, ui.size())
        ui.runNext()

        assertFalse(ready)
        assertSame(failure, reported)
    }

    @Test
    fun slowCleanupDoesNotBlockCaller() {
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val callerReturned = CountDownLatch(1)
        val asyncExecutor = Executor { command ->
            Thread {
                command.run()
            }.start()
        }
        val session = DanmakuLoadSession(asyncExecutor) { it() }
        val ticket = session.begin()

        val caller = Thread {
            session.afterCleanup(ticket, cleanup = {
                started.countDown()
                finish.await(2, TimeUnit.SECONDS)
            })
            callerReturned.countDown()
        }
        caller.start()

        assertTrue(started.await(1, TimeUnit.SECONDS))
        assertTrue(callerReturned.await(1, TimeUnit.SECONDS))
        finish.countDown()
        caller.join(1_000)
        assertFalse(caller.isAlive)
    }
}
