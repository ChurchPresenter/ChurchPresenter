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
 * - **At most [MAX_EVENTS] events a run**, one per stall. The live-stall count is kept whatever
 *   was sent.
 * - **Two causes group as one issue each** rather than one per page they surface on: the JVM
 *   loading code or Compose reading a string for the first time ([STALL_CAUSE_COLD_LOAD]), and any
 *   stall on a machine whose main window draws in software ([STALL_CAUSE_SOFTWARE]), where every
 *   frame is slow.
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

    @Volatile private var software = false

    private var liveSince = NONE
    private var liveMs = 0L
    private var liveStalls = 0
    private var sent = 0

    /** The budget for the ping going out now. */
    fun budgetMs(): Long = if (live) LIVE_BUDGET_MS else idleBudgetMs

    /** The main window drew its first frame: stalls count from [SETTLE_MS] after this. */
    fun firstFrame() {
        if (countsFrom == Long.MAX_VALUE) countsFrom = clock() + SETTLE_MS
    }

    /** Whether the main window ended up drawing in software, told once it has drawn. */
    fun softwareRendering(isSoftware: Boolean) {
        software = isSoftware
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
        sent++
        send(stallEvent(stall, wasLive, software))
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
)

/**
 * The event for [stall]. It groups on the first frame of the app's own code, so one cause is one
 * issue; a stall stuck wholly in the JDK or a library groups on its top frame instead. A stall on a
 * [software]-drawn window, or one stuck loading code or a string for the first time, groups with
 * the rest of its kind, and says which in `stall.cause`.
 */
fun stallEvent(stall: Stall, live: Boolean, software: Boolean = false): StallEvent {
    val appFrame = stall.frames.indexOfFirst { it.className.startsWith(APP_PACKAGE) }
    val cause = stall.frames.getOrNull(appFrame) ?: stall.frames.firstOrNull()
    val kind = when {
        software -> STALL_CAUSE_SOFTWARE
        isColdLoad(stall.frames, appFrame) -> STALL_CAUSE_COLD_LOAD
        else -> null
    }
    val tags = mapOf("stall.live" to live.toString(), "stall.length" to lengthBucket(stall.lateMs))
    return StallEvent(
        frames = stall.frames,
        tags = kind?.let { tags + ("stall.cause" to it) } ?: tags,
        extras = mapOf("stall.ms" to stall.lateMs.toString(), "stall.budget.ms" to stall.budgetMs.toString()),
        fingerprint = listOf("ui-stall", kind ?: cause?.let { "${it.className}.${it.methodName}" } ?: "no-frames"),
    )
}

/** `stall.cause` for a stall on a main window that draws in software. */
const val STALL_CAUSE_SOFTWARE = "software-renderer"

/** `stall.cause` for a stall loading code from the jar, or a string through Compose, the first time. */
const val STALL_CAUSE_COLD_LOAD = "cold-load"

/**
 * Whether the thread was inside the class loader or Compose's resource reader, above the app's own
 * code ([appFrame], or the whole stack when there is none): a first use, paid once a run.
 */
internal fun isColdLoad(frames: List<StackTraceElement>, appFrame: Int): Boolean {
    val above = if (appFrame < 0) frames else frames.subList(0, appFrame)
    return above.any { frame -> COLD_LOADERS.any { frame.className.startsWith(it) } }
}

private val COLD_LOADERS = listOf(
    "java.lang.ClassLoader",
    "jdk.internal.loader.",
    "java.lang.invoke.InnerClassLambdaMetafactory",
    "org.jetbrains.compose.resources.",
)

/**
 * Whether [stall]'s stack is the event thread already running the watchdog's own ping: the stall
 * ended between two polls and its stack was taken as the thread answered, so it says nothing about
 * where the thread was stuck. Counted like any stall, but not sent -- it would group on the
 * watchdog itself.
 */
internal fun sampledAsItAnswered(stall: Stall): Boolean = stall.frames.any { frame ->
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

private const val APP_PACKAGE = "org.churchpresenter."
