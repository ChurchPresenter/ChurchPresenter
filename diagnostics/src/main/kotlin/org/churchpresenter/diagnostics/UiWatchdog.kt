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
 * [budgetMs] is read as each ping is posted, so the budget can tighten while an output is live and
 * a ping keeps the one it started with. [stack] is the event thread's current stack, [report] where
 * a line goes (the app: `Log.warn`), [onAnswered] is told how long every ping took, stalled or not
 * -- the soak test counts them -- and [onStall] hears each stall once it is over, with the stack
 * from while it was stuck. The event thread answers and the watchdog thread polls, so every call
 * takes the detector's lock.
 */
class StallDetector(
    private val budgetMs: () -> Long,
    private val stack: () -> List<StackTraceElement>,
    private val report: (String) -> Unit,
    private val onAnswered: (lateMs: Long) -> Unit = {},
    private val onStall: (Stall) -> Unit = {},
) {
    private var postedAt = NONE
    private var budget = 0L
    private var frames: List<StackTraceElement>? = null

    /** Whether a ping is out and not yet answered. */
    val isWaiting: Boolean get() = synchronized(this) { postedAt != NONE }

    /** The budget the ping that is out now was posted with. */
    val currentBudgetMs: Long get() = synchronized(this) { budget }

    /** A ping was handed to the event thread at [now]. */
    @Synchronized
    fun posted(now: Long) {
        postedAt = now
        budget = budgetMs()
        frames = null
    }

    /** The event thread ran the ping at [now]. */
    @Synchronized
    fun answered(now: Long) {
        if (postedAt == NONE) return
        val late = now - postedAt
        val stuckAt = frames
        if (stuckAt != null) report("UI thread answered after ${late}ms (budget ${budget}ms)")
        onAnswered(late)
        if (stuckAt != null) onStall(Stall(lateMs = late, budgetMs = budget, frames = stuckAt))
        postedAt = NONE
        frames = null
    }

    /** Called often: reports the stack the first time the unanswered ping is past the budget. */
    @Synchronized
    fun poll(now: Long) {
        if (postedAt == NONE || frames != null || now - postedAt <= budget) return
        val stuckAt = stack()
        frames = stuckAt
        val text = stuckAt.joinToString("\n") { "        at $it" }
        report("UI thread has not answered for ${now - postedAt}ms (budget ${budget}ms); it is at:\n$text")
    }

    private companion object {
        const val NONE = -1L
    }
}

/** One stall, once it is over: how long the event thread was out, against which budget, and where it was stuck. */
data class Stall(val lateMs: Long, val budgetMs: Long, val frames: List<StackTraceElement>)

/**
 * The live watchdog over [StallDetector] (`docs/SHOW_CONTROL.md`, Output isolation): a daemon thread
 * posts a ping to the AWT event queue and polls for the answer a few times every budget. It runs in
 * every build, and reports each stall through [Log], so it also lands in the crash report's
 * breadcrumbs; [UiStallReporter] decides which stalls go to Sentry as events of their own.
 */
object UiWatchdog {

    /** How long the event thread may leave a ping unanswered before a stall is reported. */
    const val DEFAULT_BUDGET_MS = 250L

    @Volatile private var thread: Thread? = null

    /**
     * Starts watching, if it is not already; [budgetMs] is how long a stall may last unreported, read
     * as each ping goes out, [onAnswered] hears how long each ping took to be answered, and [onStall]
     * hears each stall once it is over.
     */
    @Synchronized
    fun start(
        budgetMs: () -> Long = { DEFAULT_BUDGET_MS },
        onAnswered: (lateMs: Long) -> Unit = {},
        onStall: (Stall) -> Unit = {},
    ) {
        if (thread?.isAlive == true) return
        val detector = StallDetector(
            budgetMs = budgetMs,
            stack = { ThreadDump.stackOf(EVENT_THREAD).orEmpty() },
            report = { Log.warn(TAG, it) },
            onAnswered = onAnswered,
            onStall = onStall,
        )
        thread = Thread({ watch(detector) }, "ui-watchdog").apply {
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

    private fun watch(detector: StallDetector) {
        try {
            while (!Thread.currentThread().isInterrupted) {
                if (!detector.isWaiting) {
                    detector.posted(System.currentTimeMillis())
                    EventQueue.invokeLater { detector.answered(System.currentTimeMillis()) }
                }
                detector.poll(System.currentTimeMillis())
                Thread.sleep((detector.currentBudgetMs / TICKS_PER_BUDGET).coerceAtLeast(1))
            }
        } catch (_: InterruptedException) {
            // Stopped.
        }
    }

    private const val EVENT_THREAD = "AWT-EventQueue-0"
    private const val TAG = "UiWatchdog"
    private const val TICKS_PER_BUDGET = 5
}
