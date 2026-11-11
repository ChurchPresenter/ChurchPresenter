package org.churchpresenter.diagnostics

import java.util.concurrent.Executors

/**
 * Which UI-thread stalls [UiWatchdog] finds become Sentry events of their own, and the two numbers
 * a run reports to telemetry: how long an output was live, and how many live stalls passed
 * [GATE_MS] (`docs/SHOW_CONTROL.md`, Output isolation).
 *
 * - **The budget** ([budgetMs], read as each ping goes out) is [LIVE_BUDGET_MS] while an output
 *   shows content -- six frames at 60 Hz -- and [idleBudgetMs] otherwise.
 * - **Startup is skipped**: nothing counts until [SETTLE_MS] after the main window's first frame,
 *   since a first launch stalls for a second or more while it loads.
 * - **Only stalls of [SEND_MIN_MS] or more are sent.** A frame a little over the live budget is
 *   ordinary drawing, and a single sample of it lands on whatever happened to be running.
 * - **A stall is judged against the machine it ran on.** Every answered ping is kept ([answered],
 *   the last [PING_WINDOW]); a stall in the app's own code is sent only when it is also
 *   [SLOW_MACHINE_FACTOR] times this run's 95th-percentile ping ([machineP95Ms]), so an old laptop
 *   whose every frame is slow does not send its ordinary frames.
 * - **A stall that groups by kind rather than by app frame** (software rendering, a cold load, a
 *   paused process, no app frame, Chromium) is sent only from [KIND_SEND_MIN_MS]: shorter ones are
 *   a slow driver or machine, which no change to the app closes, and a freeze of a second is one
 *   the operator saw.
 * - **At most [MAX_EVENTS] events a run**, one per stall. The live-stall count is kept whatever
 *   was sent.
 * - **A stall groups on its screen and the app frame most of its samples were in**
 *   ([stallEvent]), so one slow screen is one issue.
 * - **Some causes group as one issue each** rather than one per page they surface on: the JVM
 *   loading code or Compose reading a string for the first time ([STALL_CAUSE_COLD_LOAD]), any
 *   stall on a machine whose main window draws in software ([STALL_CAUSE_SOFTWARE]), where every
 *   frame is slow, the whole process paused ([STALL_CAUSE_PAUSED]), and a stack with no app frame
 *   ([STALL_CAUSE_NO_APP_FRAME]), per OS and render API.
 * - **A stall whose every sample found the thread idle is counted but not sent**: the thread had
 *   already moved on, so there is nothing to group it on.
 *
 * [send] is handed each event on the thread that answered the ping -- the event thread -- so the
 * app's sender passes it to a thread of its own ([sendToSentry]).
 */
class UiStallReporter(
    private val idleBudgetMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
    private val send: (StallEvent) -> Unit = ::sendToSentry,
) {
    @Volatile private var live = false

    @Volatile private var countsFrom = Long.MAX_VALUE

    @Volatile private var renderApi = RENDER_API_UNKNOWN

    private var liveSince = NONE
    private var liveMs = 0L
    private var liveStalls = 0
    private var sent = 0

    private val pings = LongArray(PING_WINDOW)
    private var pingCount = 0

    /** The budget for the ping going out now. */
    fun budgetMs(): Long = if (live) LIVE_BUDGET_MS else idleBudgetMs

    /** The main window drew its first frame: stalls count from [SETTLE_MS] after this. */
    fun firstFrame() {
        if (countsFrom == Long.MAX_VALUE) countsFrom = clock() + SETTLE_MS
    }

    /**
     * The render API the main window actually draws with (skiko's `renderApi`, which names a
     * software renderer when it fell back to one), told once it has drawn.
     */
    fun renderApi(name: String) {
        renderApi = name
    }

    /** Whether any output is showing content, told on every change. */
    @Synchronized
    fun outputLive(isLive: Boolean) {
        val now = clock()
        if (isLive && liveSince == NONE) liveSince = now
        if (!isLive && liveSince != NONE) {
            liveMs += now - liveSince
            liveSince = NONE
        }
        live = isLive
    }

    /** A stall is over; counted, and sent while this run has events left. */
    @Synchronized
    fun stalled(stall: Stall) {
        val now = clock()
        val startedAt = now - stall.lateMs
        if (startedAt < countsFrom) return
        val wasLive = liveSince != NONE && liveSince <= startedAt
        if (wasLive && stall.lateMs > GATE_MS) liveStalls++
        if (stall.lateMs < SEND_MIN_MS || sent >= MAX_EVENTS || sampledAsItAnswered(stall)) return
        val p95 = machineP95Ms()
        val event = stallEvent(stall, wasLive, renderApi)?.takeIf { stall.lateMs >= sendFloorMs(it, p95) } ?: return
        sent++
        send(
            event.copy(
                tags = event.tags + ("machine.p95" to machineBucket(p95)),
                extras = event.extras + ("machine.p95.ms" to p95.toString()),
            ),
        )
    }

    /** How long [event]'s stall must have lasted to be sent, on a machine whose ordinary ping is [p95Ms]. */
    private fun sendFloorMs(event: StallEvent, p95Ms: Long): Long =
        if (event.cause == null) maxOf(SEND_MIN_MS, SLOW_MACHINE_FACTOR * p95Ms) else KIND_SEND_MIN_MS

    /**
     * The event thread answered a ping [lateMs] after it went out, stalled or not. Startup's pings
     * are left out, as its stalls are: they say how slow loading is, not the machine.
     */
    @Synchronized
    fun answered(lateMs: Long) {
        if (clock() < countsFrom) return
        pings[pingCount % PING_WINDOW] = lateMs
        pingCount++
    }

    /**
     * The 95th-percentile ping of the last [PING_WINDOW] -- how long this machine's event thread
     * ordinarily takes to come round -- or 0 before [MIN_PINGS] have been answered.
     */
    @Synchronized
    fun machineP95Ms(): Long {
        val count = minOf(pingCount, PING_WINDOW)
        if (count < MIN_PINGS) return 0
        val sorted = pings.copyOf(count).apply { sort() }
        return sorted[(count * P95_PERCENT / PERCENT).coerceAtMost(count - 1)]
    }

    /** Minutes an output has been live this run, an interval still open included. */
    @Synchronized
    fun liveMinutes(): Int {
        val open = if (liveSince == NONE) 0L else clock() - liveSince
        return ((liveMs + open) / MILLIS_PER_MINUTE).toInt()
    }

    /** Stalls past [GATE_MS] that began while an output was live, this run. */
    @Synchronized
    fun liveStallsPastGate(): Int = liveStalls

    companion object {
        /** The budget while an output shows content: six frames at 60 Hz. */
        const val LIVE_BUDGET_MS = 100L

        /** The budget with nothing live, in a release build. */
        const val RELEASE_IDLE_BUDGET_MS = 500L

        /** How long after the first frame stalls start to count. */
        const val SETTLE_MS = 30_000L

        /** The shortest stall sent; shorter ones are counted but not sent. */
        const val SEND_MIN_MS = 250L

        /** The shortest stall sent that groups by its kind rather than by an app frame. */
        const val KIND_SEND_MIN_MS = 1_000L

        /** How many of this machine's ordinary 95th-percentile pings a stall in the app's code must last. */
        const val SLOW_MACHINE_FACTOR = 3L

        /** How many answered pings the machine's ordinary latency is taken over. */
        const val PING_WINDOW = 1_200

        /** How many pings must be answered before the machine's latency counts at all. */
        const val MIN_PINGS = 100

        private const val P95_PERCENT = 95
        private const val PERCENT = 100

        /** The most stall events one run sends. */
        const val MAX_EVENTS = 10

        /** A live stall longer than this counts against the output-process gate. */
        const val GATE_MS = 500L

        private const val NONE = -1L
        private const val MILLIS_PER_MINUTE = 60_000L

        private val sender = Executors.newSingleThreadExecutor { task ->
            Thread(task, "ui-stall-sender").apply { isDaemon = true }
        }

        /** Sends [event] to Sentry from a thread of its own, never the event thread that answered. */
        fun sendToSentry(event: StallEvent) {
            sender.execute {
                CrashReporter.reportWarning(
                    STALL_CONTEXT,
                    throwable = UiStall(event.frames),
                    tags = event.tags,
                    extras = event.extras,
                    fingerprint = event.fingerprint,
                )
            }
        }
    }
}

/** The Sentry event's title, constant so every stall lands under one name. */
const val STALL_CONTEXT = "UI thread stalled"

/** What one stall sends: the stack it was stuck at, the tags and extras, and what it groups on. */
data class StallEvent(
    val frames: List<StackTraceElement>,
    val tags: Map<String, String>,
    val extras: Map<String, String>,
    val fingerprint: List<String>,
) {
    /** The kind it groups as (`stall.cause`), or null when it groups on an app frame. */
    val cause: String? get() = tags[STALL_CAUSE_TAG]
}

/** The tag naming the kind a stall groups as. */
internal const val STALL_CAUSE_TAG = "stall.cause"

/** `stall.cause` for a stall inside Chromium: starting it, or a message pumped to its windows. */
const val STALL_CAUSE_CHROMIUM = "chromium"

/** `stall.cause` for a stall on a main window that draws in software. */
const val STALL_CAUSE_SOFTWARE = "software-renderer"

/** `stall.cause` for a stall loading code from the jar, or a string through Compose, the first time. */
const val STALL_CAUSE_COLD_LOAD = "cold-load"

/** `stall.cause` for a stall whose first sample came long after the budget: the whole process was paused. */
const val STALL_CAUSE_PAUSED = "process-paused"

/** `stall.cause` for a stall none of whose samples reached the app's own code. */
const val STALL_CAUSE_NO_APP_FRAME = "no-app-frame"

/** The render API a stall reports before the main window has said which one it draws with. */
const val RENDER_API_UNKNOWN = "unknown"

/**
 * Whether [stall]'s stack is the event thread already running the watchdog's own ping: the stall
 * ended between two polls and its stack was taken as the thread answered, so it says nothing about
 * where the thread was stuck. Counted like any stall, but not sent -- it would group on the
 * watchdog itself.
 */
internal fun sampledAsItAnswered(stall: Stall): Boolean = sampledAsItAnswered(stall.frames)

internal fun sampledAsItAnswered(frames: List<StackTraceElement>): Boolean = frames.any { frame ->
    WATCHDOG_CLASSES.any { frame.className.startsWith(it) }
}

private val WATCHDOG_CLASSES = listOf(StallDetector::class.java.name, UiWatchdog::class.java.name)

/** A stall's length as a few searchable ranges, so a tag never holds a raw number. */
internal fun lengthBucket(lateMs: Long): String = when {
    lateMs < QUARTER_SECOND_MS -> "under 250ms"
    lateMs < HALF_SECOND_MS -> "250-500ms"
    lateMs < ONE_SECOND_MS -> "500ms-1s"
    lateMs < TWO_SECONDS_MS -> "1-2s"
    else -> "2s or more"
}

/** This machine's ordinary 95th-percentile ping, as a few searchable ranges (`machine.p95`). */
internal fun machineBucket(p95Ms: Long): String = when {
    p95Ms < FRAME_MS -> "under 16ms"
    p95Ms < FIFTY_MS -> "16-50ms"
    p95Ms < HUNDRED_MS -> "50-100ms"
    p95Ms < QUARTER_SECOND_MS -> "100-250ms"
    else -> "250ms or more"
}

private const val FRAME_MS = 16L
private const val FIFTY_MS = 50L
private const val HUNDRED_MS = 100L
private const val QUARTER_SECOND_MS = 250L
private const val HALF_SECOND_MS = 500L
private const val ONE_SECOND_MS = 1_000L
private const val TWO_SECONDS_MS = 2_000L

/** Carries a stall's stack into Sentry, which draws a throwable's frames as the issue's stack. */
class UiStall(frames: List<StackTraceElement>) : Throwable(STALL_CONTEXT, null, false, true) {
    init {
        stackTrace = frames.toTypedArray()
    }
}
