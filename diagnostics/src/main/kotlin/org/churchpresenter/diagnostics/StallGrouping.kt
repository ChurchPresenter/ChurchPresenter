package org.churchpresenter.diagnostics

/**
 * The event for [stall], grouped so that one cause is one issue however the samples fell; null when
 * none of its samples is evidence of anything, which is counted but not sent.
 *
 * Each sample (a stack [StallDetector] took while the stall lasted) is first cut at the innermost
 * nested event loop ([aboveInnermostLoop]): below it is the code that opened a modal dialog and is
 * waiting for it, not what the thread is stuck in. A sample taken as the thread answered the ping,
 * or while it sat idle waiting for an event ([isIdle]), says nothing about the stall and is left out.
 *
 * What is left names its first frame of the app's own code; the one named most often is the cause,
 * and the sample it came from is the stack Sentry shows. The outermost screen on the stack
 * (`stall.screen`, [screenOf]) is grouped on too, ahead of the cause, so one screen's slow opening
 * is one issue rather than one per frame it happened to be in.
 *
 * Some stalls group with the rest of their kind instead, and say which in `stall.cause`, taken in
 * this order: any stall on a main window drawn in software ([renderApi], [STALL_CAUSE_SOFTWARE]);
 * one most of whose samples are loading code or a string for the first time
 * ([STALL_CAUSE_COLD_LOAD]); one whose first sample came far later than the watchdog's polling
 * allows, so the whole process was paused and the stack is from after it ([STALL_CAUSE_PAUSED]);
 * and one with no app frame in any sample ([STALL_CAUSE_NO_APP_FRAME]), grouped on [osFamily] and
 * [renderApi], since a driver or the OS is all such a stack can blame.
 */
fun stallEvent(
    stall: Stall,
    live: Boolean,
    renderApi: String = RENDER_API_UNKNOWN,
    osFamily: String = CrashReporter.osFamily(System.getProperty("os.name", "")),
): StallEvent? {
    val evidence = stall.samples.filterNot { sampledAsItAnswered(it) || isIdle(it) }
    val samples = evidence.ifEmpty { listOf(stall.frames) }
    val cut = samples.map(::aboveInnermostLoop)
    val causes = cut.map { sample -> sample.getOrNull(appFrameIndex(sample))?.groupName }
    val cause = mostFrequent(causes.filterNotNull())
    val shown = cause?.let { samples[causes.indexOf(it)] } ?: samples.first()
    val screen = mostFrequent(cut.mapNotNull(::screenOf))
    val kind = when {
        isSoftwareRenderer(renderApi) -> STALL_CAUSE_SOFTWARE
        cut.count { isColdLoad(it, appFrameIndex(it)) } * 2 > cut.size -> STALL_CAUSE_COLD_LOAD
        stall.firstSampleMs > pausedPastMs(stall.budgetMs) -> STALL_CAUSE_PAUSED
        evidence.isEmpty() -> return null
        cause == null -> STALL_CAUSE_NO_APP_FRAME
        else -> null
    }
    val tags = buildMap {
        put("stall.live", live.toString())
        put("stall.length", lengthBucket(stall.lateMs))
        kind?.let { put("stall.cause", it) }
        screen?.let { put("stall.screen", it) }
        modalCaller(shown)?.let { put("stall.modal", it) }
        if (kind == STALL_CAUSE_NO_APP_FRAME) put("stall.top", shown.firstOrNull()?.groupName ?: "no-frames")
    }
    val fingerprint = when (kind) {
        null -> listOfNotNull(screen, cause)
        STALL_CAUSE_NO_APP_FRAME -> listOf(kind, osFamily, renderApi)
        else -> listOf(kind)
    }
    return StallEvent(
        frames = shown,
        tags = tags,
        extras = mapOf(
            "stall.ms" to stall.lateMs.toString(),
            "stall.budget.ms" to stall.budgetMs.toString(),
            "stall.first.sample.ms" to stall.firstSampleMs.toString(),
            "stall.samples" to evidence.size.toString(),
            "stall.samples.top" to sampleSummary(causes),
        ),
        fingerprint = listOf("ui-stall") + fingerprint,
    )
}

/** Whether skiko's [renderApi] name is one of its software renderers. */
internal fun isSoftwareRenderer(renderApi: String): Boolean = renderApi.startsWith(SOFTWARE_RENDER_API)

/**
 * How late a stall's first sample may come before the process is taken to have been paused: the
 * budget, the one poll interval past it the watchdog may sleep through, and two sample intervals of
 * slack for a coarse timer. A first sample later than that means the watchdog itself did not run.
 */
internal fun pausedPastMs(budgetMs: Long): Long =
    budgetMs + budgetMs / StallDetector.TICKS_PER_BUDGET + 2 * StallDetector.SAMPLE_INTERVAL_MS

private const val SOFTWARE_RENDER_API = "SOFTWARE"

/** The index of [frames]' first frame of the app's own code, or -1; generated resource accessors are not it. */
internal fun appFrameIndex(frames: List<StackTraceElement>): Int = frames.indexOfFirst { it.isAppFrame }

internal val StackTraceElement.isAppFrame: Boolean
    get() = className.startsWith(APP_PACKAGE) && !isGeneratedResource

private val StackTraceElement.isGeneratedResource: Boolean get() = GENERATED_RESOURCES in className

/** What a stall groups on for this frame: its class and method, never its line. */
internal val StackTraceElement.groupName: String get() = "$className.$methodName"

/** The value seen most often in [values], the first seen winning a tie; null when there are none. */
private fun mostFrequent(values: List<String>): String? =
    values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

/** The causes the samples named, most frequent first, `-` for a sample with no app frame: `12x a.B.c; 3x -`. */
internal fun sampleSummary(causes: List<String?>): String =
    causes.groupingBy { it ?: "-" }.eachCount().entries
        .sortedByDescending { it.value }
        .take(SUMMARY_CAUSES)
        .joinToString("; ") { "${it.value}x ${it.key}" }

/**
 * The screen [frames] is drawing: the composable whose name ends the way a screen's does (a page
 * of settings, then a tab or an output screen, then a dialog or window around them), the outermost
 * of the first of those kinds the stack holds. A lambda inside one counts as that one, whether
 * compiled as `BibleTab$lambda$3` or `BibleTabKt$BibleTab$1.invoke`. Null when none is on the stack.
 */
internal fun screenOf(frames: List<StackTraceElement>): String? {
    val names = frames.filter { it.isAppFrame }.mapNotNull(::composableName).asReversed()
    return SCREEN_SUFFIXES.firstNotNullOfOrNull { suffixes ->
        names.firstOrNull { name -> suffixes.any { name.endsWith(it) } }
    }
}

private fun composableName(frame: StackTraceElement): String? {
    val method = frame.methodName.substringBefore('$')
    val lambdaOf = frame.className.substringAfter("Kt$", "").substringBefore('$')
    return listOf(method, lambdaOf).firstOrNull { it.firstOrNull()?.isUpperCase() == true }
}

/** The suffixes of a screen's name, finest kind first: [screenOf] takes the first kind it finds. */
private val SCREEN_SUFFIXES = listOf(
    listOf("Page"),
    listOf("Tab", "Screen"),
    listOf("TabContent"),
    listOf("Dialog", "Window"),
)

private const val SUMMARY_CAUSES = 5

/**
 * Whether the thread was inside the class loader or Compose's resource reader, above the app's own
 * code ([appFrame], or the whole stack when there is none): a first use, paid once a run. A lazy
 * resource accessor being created counts too.
 */
internal fun isColdLoad(frames: List<StackTraceElement>, appFrame: Int): Boolean {
    val end = if (appFrame < 0) frames.size else appFrame
    return (0 until end).any { isColdLoader(frames, it) }
}

private fun isColdLoader(frames: List<StackTraceElement>, index: Int): Boolean {
    val name = frames[index].className
    if (COLD_LOADERS.any { name.startsWith(it) }) return true
    return name == LAZY_CLASS &&
        listOfNotNull(frames.getOrNull(index - 1), frames.getOrNull(index + 1)).any { it.isGeneratedResource }
}

private val COLD_LOADERS = listOf(
    "java.lang.ClassLoader",
    "jdk.internal.loader.",
    "java.lang.invoke.",
    "org.jetbrains.compose.resources.",
)

private const val LAZY_CLASS = "kotlin.SynchronizedLazyImpl"

/** The package Compose Resources generates `Res` and its accessors into, under any module's own. */
private const val GENERATED_RESOURCES = ".generated.resources."

/** The app's own code; any module's package starts with it. */
private const val APP_PACKAGE = "org.churchpresenter."
