package org.churchpresenter.diagnostics

import java.awt.EventQueue

/**
 * A stall of [stallMs] on the UI's event thread, every [everyMs] -- what
 * `-Dchurchpresenter.injectUiStall=<ms>,<everySec>` asks a dev build for, so the cost of a stalled
 * UI to the outputs can be seen and measured (`docs/SHOW_CONTROL.md`, Output isolation).
 */
data class UiStallSpec(val stallMs: Long, val everyMs: Long) {
    companion object {
        /** `"500,2"` is a 500 ms stall every 2 s; anything else, including nothing, is no stall. */
        fun parse(value: String?): UiStallSpec? {
            val parts = value?.split(',')?.map { it.trim() } ?: return null
            if (parts.size != 2) return null
            val stall = parts[0].toLongOrNull()?.takeIf { it > 0 } ?: return null
            val everySec = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
            return UiStallSpec(stall, everySec * MILLIS_PER_SECOND)
        }

        private const val MILLIS_PER_SECOND = 1_000L
    }
}

/**
 * Blocks the event thread on a schedule, for measuring what a stalled UI costs. Dev builds and the
 * isolation benchmark only: nothing in a release reads [PROPERTY].
 */
object UiStallInjector {

    /** The system property a dev build reads its [UiStallSpec] from. */
    const val PROPERTY = "churchpresenter.injectUiStall"

    @Volatile private var thread: Thread? = null

    /**
     * Starts stalling the event thread as [spec] says, replacing any schedule already running.
     * [post] hands work to the event thread; [stall] is what runs there.
     */
    @Synchronized
    fun start(
        spec: UiStallSpec,
        post: (Runnable) -> Unit = EventQueue::invokeLater,
        stall: (Long) -> Unit = Thread::sleep,
    ) {
        stop()
        thread = Thread({
            try {
                while (!Thread.currentThread().isInterrupted) {
                    Thread.sleep(spec.everyMs)
                    post { stall(spec.stallMs) }
                }
            } catch (_: InterruptedException) {
                // Stopped.
            }
        }, "ui-stall-injector").apply {
            isDaemon = true
            start()
        }
    }

    /** Stops stalling; a stall already posted still runs. */
    @Synchronized
    fun stop() {
        thread?.interrupt()
        thread = null
    }
}
