package org.churchpresenter.diagnostics

/**
 * The event for [stall], grouped so that one cause is one issue however the samples fell.
 *
 * Each of the stall's samples (the stacks [StallDetector] took while it lasted) names its first frame
 * of the app's own code; the one named most often is the cause, and the sample it came from is the
 * stack Sentry shows. A stall wholly in the JDK or a library groups on its top frame instead. The
 * outermost screen on the stack (`stall.screen`, [screenOf]) is grouped on too, ahead of the cause,
 * so one screen's slow opening is one issue rather than one per frame it happened to be in.
 *
 * A stall on a [software]-drawn window, or one most of whose samples are loading code or a string
 * for the first time, groups with the rest of its kind and says which in `stall.cause`.
 */
fun stallEvent(stall: Stall, live: Boolean, software: Boolean = false): StallEvent {
    val samples = stall.samples.filterNot(::sampledAsItAnswered).ifEmpty { listOf(stall.frames) }
    val causes = samples.map { sample -> sample.getOrNull(appFrameIndex(sample))?.groupName }
    val cause = mostFrequent(causes.filterNotNull())
    val shown = cause?.let { samples[causes.indexOf(it)] } ?: samples.first()
    val screen = mostFrequent(samples.mapNotNull(::screenOf))
    val kind = when {
        software -> STALL_CAUSE_SOFTWARE
        samples.count { isColdLoad(it, appFrameIndex(it)) } * 2 > samples.size -> STALL_CAUSE_COLD_LOAD
        else -> null
    }
    val tags = buildMap {
        put("stall.live", live.toString())
        put("stall.length", lengthBucket(stall.lateMs))
        kind?.let { put("stall.cause", it) }
        screen?.let { put("stall.screen", it) }
    }
    val where = cause ?: shown.firstOrNull()?.groupName ?: "no-frames"
    return StallEvent(
        frames = shown,
        tags = tags,
        extras = mapOf(
            "stall.ms" to stall.lateMs.toString(),
            "stall.budget.ms" to stall.budgetMs.toString(),
            "stall.samples" to samples.size.toString(),
            "stall.samples.top" to sampleSummary(causes),
        ),
        fingerprint = listOf("ui-stall") + (kind?.let(::listOf) ?: listOfNotNull(screen, where)),
    )
}

/** The index of [frames]' first frame of the app's own code, or -1; generated resource accessors are not it. */
internal fun appFrameIndex(frames: List<StackTraceElement>): Int = frames.indexOfFirst { it.isAppFrame }

private val StackTraceElement.isAppFrame: Boolean
    get() = className.startsWith(APP_PACKAGE) && !isGeneratedResource

private val StackTraceElement.isGeneratedResource: Boolean get() = GENERATED_RESOURCES in className

/** What a stall groups on for this frame: its class and method, never its line. */
private val StackTraceElement.groupName: String get() = "$className.$methodName"

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
