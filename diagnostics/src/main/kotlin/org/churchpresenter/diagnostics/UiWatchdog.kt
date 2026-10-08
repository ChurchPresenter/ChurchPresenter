package org.churchpresenter.diagnostics

import java.awt.EventQueue

/**
 * Notices when the UI's event thread stops answering, and says where it was stuck.
 *
 * Every output window composes on that thread, so a stall there is a frozen screen for the
 * audience. The detector is told when a ping was posted to the thread and when it was answered; a
 * ping still unanswered past [budgetMs] reports the thread's stack **once per stall**, while it is
 * still stuck -- afterwards the stack is gone -- and, when the thread answers, how long it was out.
 * It holds no thread and no clock of its own, so a test drives it with numbers.
 *
 * [stack] is the event thread's current stack, [report] where a line goes (the app: `Log.warn`),
 * and [onAnswered] is told how long every ping took, stalled or not -- the soak test counts them.
 * The event thread answers and the watchdog thread polls, so every call takes the detector's lock.
 */
class StallDetector(
    private val budgetMs: Long,
    private val stack: () -> List<StackTraceElement>,
    private val report: (String) -> Unit,
    private val onAnswered: (lateMs: Long) -> Unit = {},
) {
    private var postedAt = NONE
    private var reported = false

    /** Whether a ping is out and not yet answered. */
    val isWaiting: Boolean get() = synchronized(this) { postedAt != NONE }

    /** A ping was handed to the event thread at [now]. */
    @Synchronized
    fun posted(now: Long) {
        postedAt = now
        reported = false
    }

    /** The event thread ran the ping at [now]. */
    @Synchronized
    fun answered(now: Long) {
        if (postedAt == NONE) return
        val late = now - postedAt
        if (reported) report("UI thread answered after ${late}ms (budget ${budgetMs}ms)")
        onAnswered(late)
        postedAt = NONE
        reported = false
    }

    /** Called often: reports the stack the first time the unanswered ping is past the budget. */
    @Synchronized
    fun poll(now: Long) {
        if (postedAt == NONE || reported || now - postedAt <= budgetMs) return
        reported = true
        val frames = stack().joinToString("\n") { "        at $it" }
        report("UI thread has not answered for ${now - postedAt}ms (budget ${budgetMs}ms); it is at:\n$frames")
    }

    private companion object {
        const val NONE = -1L
    }
}

/**
 * The live watchdog over [StallDetector] (`docs/SHOW_CONTROL.md`, Output isolation): a daemon thread
 * posts a ping to the AWT event queue and polls for the answer. It is for debug builds -- it wakes
 * a few times every budget -- and reports through [Log], so a stall also lands in the crash
 * report's breadcrumbs.
 */
object UiWatchdog {

    /** How long the event thread may leave a ping unanswered before a stall is reported. */
    const val DEFAULT_BUDGET_MS = 250L

    @Volatile private var thread: Thread? = null

    /**
     * Starts watching, if it is not already; [budgetMs] is how long a stall may last unreported, and
     * [onAnswered] hears how long each ping took to be answered.
     */
    @Synchronized
    fun start(budgetMs: Long = DEFAULT_BUDGET_MS, onAnswered: (lateMs: Long) -> Unit = {}) {
        if (thread?.isAlive == true) return
        val detector = StallDetector(
            budgetMs = budgetMs,
            stack = { ThreadDump.stackOf(EVENT_THREAD).orEmpty() },
            report = { Log.warn(TAG, it) },
            onAnswered = onAnswered,
        )
        thread = Thread({ watch(detector, budgetMs) }, "ui-watchdog").apply {
            isDaemon = true
            start()
        }
    }

    /** Stops watching. */
    @Synchronized
    fun stop() {
        thread?.interrupt()
        thread = null
    }

    private fun watch(detector: StallDetector, budgetMs: Long) {
        val tick = (budgetMs / TICKS_PER_BUDGET).coerceAtLeast(1)
        try {
            while (!Thread.currentThread().isInterrupted) {
                if (!detector.isWaiting) {
                    detector.posted(System.currentTimeMillis())
                    EventQueue.invokeLater { detector.answered(System.currentTimeMillis()) }
                }
                detector.poll(System.currentTimeMillis())
                Thread.sleep(tick)
            }
        } catch (_: InterruptedException) {
            // Stopped.
        }
    }

    private const val EVENT_THREAD = "AWT-EventQueue-0"
    private const val TAG = "UiWatchdog"
    private const val TICKS_PER_BUDGET = 5
}
