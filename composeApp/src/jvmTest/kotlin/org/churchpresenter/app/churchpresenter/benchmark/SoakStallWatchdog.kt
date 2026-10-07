package org.churchpresenter.app.churchpresenter.benchmark

import java.util.concurrent.atomic.AtomicLong

/**
 * Notices the soak's one frame that never finishes.
 *
 * The soak checks its clock between frames, so a frame that blocks forever runs it past its own
 * end and into the task timeout, which kills it with nothing written: the 2026-10-05 four-hour run
 * on CI ended exactly that way. This watches each [step] from another thread, and once one has run
 * longer than [limitNanos] it calls [onStall] with how long -- once -- while the stuck frame is
 * still on the stack to be dumped.
 *
 * [clock] is a parameter so a test can move time instead of waiting for it.
 */
internal class SoakStallWatchdog(
    private val limitNanos: Long,
    private val onStall: (stalledNanos: Long) -> Unit,
    private val clock: () -> Long = System::nanoTime,
) : AutoCloseable {

    private val stepStartedAt = AtomicLong(IDLE)

    @Volatile private var tripped = false

    private val thread = Thread {
        while (!tripped) {
            try {
                Thread.sleep(POLL_MS)
            } catch (_: InterruptedException) {
                return@Thread
            }
            checkOnce()
        }
    }.apply {
        isDaemon = true
        name = "soak-stall-watchdog"
    }

    fun start(): SoakStallWatchdog = apply { thread.start() }

    /** Runs one frame under watch. */
    fun <T> step(block: () -> T): T {
        stepStartedAt.set(clock())
        try {
            return block()
        } finally {
            stepStartedAt.set(IDLE)
        }
    }

    /** One watchdog tick: whether the frame now running has outlived the limit, reported if so. */
    internal fun checkOnce(): Boolean {
        val started = stepStartedAt.get()
        if (started == IDLE || tripped) return false
        val elapsed = clock() - started
        if (elapsed <= limitNanos) return false
        tripped = true
        onStall(elapsed)
        return true
    }

    override fun close() {
        thread.interrupt()
    }

    private companion object {
        const val IDLE = Long.MIN_VALUE
        const val POLL_MS = 5_000L
    }
}
