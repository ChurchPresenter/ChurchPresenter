package org.churchpresenter.presentationengine

/**
 * What a slide lost when it had to be rendered the slow, forgiving way.
 *
 * [cause] is the simple class name of the exception that failed the whole-slide draw — not its
 * message, which can carry a file path. Reported once per slide; see [drawEachSkippingFailures].
 *
 * The rest says which shapes went and why, without any of the file's own content:
 * [skippedShapes] are the skipped shapes' class names (`XSLFPictureShape`), [failureOrigin] is the
 * POI class and method that refused — POI has a dozen separate size caps, and a class name alone
 * cannot say which one tripped — and [recordLimit] is the limit the refusal quoted, when it did.
 */
data class SlideRenderDegradation(
    val slideIndex: Int,
    val shapesTotal: Int,
    val shapesSkipped: Int,
    val cause: String,
    val skippedShapes: List<String> = emptyList(),
    val failureOrigin: String = "",
    val recordLimit: String = "",
)

/** One item [drawEachSkippingFailures] could not draw, and what it threw. */
internal data class SkippedDraw<T>(val item: T, val error: Throwable)

/**
 * Draws every one of [items], carrying on past the ones that throw, and returns the ones that were
 * skipped with what each threw.
 *
 * This is the whole of the recovery policy, kept separate from the POI calls that supply [drawOne]
 * so it can be tested without a deck, a display or a rasterizer: the interesting behaviour is that
 * a failure part-way through does not cost the items after it.
 *
 * `Throwable` rather than `Exception` on purpose — the failure this exists for,
 * `RecordFormatException`, is an `Exception`, but the same drawing path also raises
 * `OutOfMemoryError` on an absurd declared length, and one shape is not worth the slide.
 */
// Throwable by design -- see above; the error is kept, not swallowed, so the report can name it.
@Suppress("TooGenericExceptionCaught")
internal fun <T> drawEachSkippingFailures(items: List<T>, drawOne: (T) -> Unit): List<SkippedDraw<T>> {
    val skipped = mutableListOf<SkippedDraw<T>>()
    for (item in items) {
        try {
            drawOne(item)
        } catch (t: Throwable) {
            skipped += SkippedDraw(item, t)
        }
    }
    return skipped
}

/** Frames that only say *how* a refusal was raised, never which part of POI refused. */
private val ALLOCATOR_FRAME_PREFIXES = listOf("org.apache.poi.util.", "java.", "kotlin.", "jdk.")

/**
 * The first frame of [error] past POI's allocator and the JDK, as `Class.method`.
 *
 * `IOUtils.safelyAllocate` is where every size refusal is thrown, so the frame that matters is the
 * one that called it: `HemfFill.readBitmap` and `XSLFPictureData.getData` are different caps with
 * different fixes. Blank when the stack is empty.
 */
internal fun failureOrigin(error: Throwable): String =
    error.stackTrace.firstOrNull { frame -> ALLOCATOR_FRAME_PREFIXES.none { frame.className.startsWith(it) } }
        ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}" }
        .orEmpty()

private val RECORD_LIMIT = Regex("""maximum length for this record type is (\d+)""")

/** The limit a POI size refusal quotes in [message], or blank. Only the number is kept. */
internal fun recordLimit(message: String?): String =
    message?.let { RECORD_LIMIT.find(it)?.groupValues?.get(1) }.orEmpty()
