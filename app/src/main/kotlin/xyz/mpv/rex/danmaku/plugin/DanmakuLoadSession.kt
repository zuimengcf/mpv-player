package xyz.mpv.rex.danmaku.plugin

import java.util.concurrent.Executor

/**
 * Called from the UI thread, except for cleanup itself. A ticket identifies a prepare operation,
 * not a file path: reloading the same file must invalidate the previous callback too.
 * The serial executor must never run inline on the UI thread (DFM release joins its draw thread).
 */
internal class DanmakuLoadSession(
    private val cleanupExecutor: Executor,
    private val dispatchToUi: (() -> Unit) -> Unit,
) {
    class Ticket internal constructor()

    private var current: Ticket? = null
    // Once cleanup fails, the same view cannot safely be prepared again.
    private var cleanupFailure: Exception? = null

    fun begin(): Ticket = Ticket().also { current = it }
    fun isCurrent(ticket: Ticket): Boolean = current === ticket
    fun invalidate() { current = null }

    fun afterCleanup(
        ticket: Ticket?,
        cleanup: () -> Unit,
        ready: () -> Unit = {},
        failed: (Exception) -> Unit = {},
    ) {
        cleanupExecutor.execute {
            try {
                cleanup()
            } catch (e: Exception) {
                cleanupFailure = e
            }
            val failure = cleanupFailure
            dispatchToUi {
                if (ticket != null && isCurrent(ticket)) {
                    if (failure == null) ready() else failed(failure)
                }
            }
        }
    }
}
