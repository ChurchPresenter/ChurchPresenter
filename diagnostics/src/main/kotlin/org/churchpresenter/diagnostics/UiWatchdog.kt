package org.churchpresenter.diagnostics

import java.awt.EventQueue

/**
 * Notices when the UI's event thread stops answering, and says where it was stuck.
 *
 * Every output window composes on that thread, so a stall there is a frozen screen for the
 * audience. The detector is told when a ping was posted to the thread and when it was answered; a
 * ping still unanswered past [budgetMs] reports the thread's stack **once per stall**, while it is
 * still stuck -- afterwards the stack is gone -- and, when the thread answers, how long it was out.
 * While the stall lasts it takes the stack again every [SAMPLE_INTERVAL_MS], up to [MAX_SAMPLES]
 * in all, so what the thread spent the stall on is a count rather than one moment's guess.
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
    private val samples = mutableListOf<List<StackTraceElement>>()
    private var sampledAt = NONE

    /** Whether a ping is out and not yet answered. */
    val isWaiting: Boolean get() = synchronized(this) { postedAt != NONE }

    /** The budget the ping that is out now was posted with. */
    val currentBudgetMs: Long get() = synchronized(this) { budget }

    /** How long the watchdog may wait before its next [poll]: shorter while a stall is being sampled. */
    val pollDelayMs: Long
        get() = synchronized(this) {
            val tick = budget / TICKS_PER_BUDGET
            (if (frames != null) minOf(tick, SAMPLE_INTERVAL_MS) else tick).coerceAtLeast(1)
        }

    /** A ping was handed to the event thread at [now]. */
    @Synchronized
    fun posted(now: Long) {
        postedAt = now
        budget = budgetMs()
        frames = null
        samples.clear()
    }

    /** The event thread ran the ping at [now]. */
    @Synchronized
    fun answered(now: Long) {
        if (postedAt == NONE) return
        val late = now - postedAt
        val stuckAt = frames
        if (stuckAt != null) report("UI thread answered after ${late}ms (budget ${budget}ms)")
        onAnswered(late)
        if (stuckAt != null) {
            onStall(Stall(lateMs = late, budgetMs = budget, frames = stuckAt, samples = samples.toList()))
        }
        postedAt = NONE
        frames = null
        samples.clear()
    }

    /**
     * Called often: reports the stack the first time the unanswered ping is past the budget, and
     * samples it again every [SAMPLE_INTERVAL_MS] after that while the ping stays unanswered.
     */
    @Synchronized
    fun poll(now: Long) {
        if (postedAt == NONE || now - postedAt <= budget) return
        if (frames != null) {
            if (now - sampledAt >= SAMPLE_INTERVAL_MS && samples.size < MAX_SAMPLES) sample(now)
            return
        }
        val stuckAt = sample(now)
        frames = stuckAt
        val text = stuckAt.take(ThreadDump.STACK_DEPTH).joinToString("\n") { "        at $it" }
        report("UI thread has not answered for ${now - postedAt}ms (budget ${budget}ms); it is at:\n$text")
    }

    private fun sample(now: Long): List<StackTraceElement> {
        sampledAt = now
        return stack().also { samples += it }
    }

    companion object {
        /** How often a stalled event thread's stack is taken again while the stall lasts. */
        const val SAMPLE_INTERVAL_MS = 50L

        /** The most stacks one stall keeps: two seconds of it at [SAMPLE_INTERVAL_MS]. */
        const val MAX_SAMPLES = 40

        private const val NONE = -1L
        private const val TICKS_PER_BUDGET = 5
    }
}

/**
 * One stall, once it is over: how long the event thread was out, against which budget, where it was
 * stuck when the budget ran out ([frames]), and every stack taken while it stayed stuck ([samples],
 * [frames] first).
 */
data class Stall(
    val lateMs: Long,
    val budgetMs: Long,
    val frames: List<StackTraceElement>,
    val samples: List<List<StackTraceElement>> = listOf(frames),
)

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
            stack = ::eventThreadStack,
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
                Thread.sleep(detector.pollDelayMs)
            }
        } catch (_: InterruptedException) {
            // Stopped.
        }
    }

    /**
     * The event thread's stack, [STALL_STACK_DEPTH] frames deep: a Compose screen sits far below the
     * frame it is stuck in. The thread is found by name once and its own stack taken after that, so
     * a stall sampled every [StallDetector.SAMPLE_INTERVAL_MS] does not dump every thread each time.
     */
    private fun eventThreadStack(): List<StackTraceElement> {
        val thread = eventThread?.takeIf { it.isAlive }
            ?: Thread.getAllStackTraces().keys.firstOrNull { it.name == EVENT_THREAD }.also { eventThread = it }
        return thread?.stackTrace?.take(STALL_STACK_DEPTH).orEmpty()
    }

    @Volatile private var eventThread: Thread? = null

    /** How deep a stall's stack is taken; deeper than [ThreadDump.STACK_DEPTH], which the hung-test dump keeps. */
    const val STALL_STACK_DEPTH = 128

    private const val EVENT_THREAD = "AWT-EventQueue-0"
    private const val TAG = "UiWatchdog"
}
