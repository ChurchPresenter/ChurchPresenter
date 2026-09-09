package org.churchpresenter.presentationengine

/**
 * What a slide lost when it had to be rendered the slow, forgiving way.
 *
 * [cause] is the simple class name of the exception that failed the whole-slide draw — not its
 * message, which can carry a file path. Reported once per slide; see [drawEachSkippingFailures].
 */
data class SlideRenderDegradation(
    val slideIndex: Int,
    val shapesTotal: Int,
    val shapesSkipped: Int,
    val cause: String,
)

/**
 * Draws every one of [items], carrying on past the ones that throw, and returns how many were
 * skipped.
 *
 * This is the whole of the recovery policy, kept separate from the POI calls that supply [drawOne]
 * so it can be tested without a deck, a display or a rasterizer: the interesting behaviour is that
 * a failure part-way through does not cost the items after it.
 *
 * `Throwable` rather than `Exception` on purpose — the failure this exists for,
 * `RecordFormatException`, is an `Exception`, but the same drawing path also raises
 * `OutOfMemoryError` on an absurd declared length, and one shape is not worth the slide.
 */
internal fun <T> drawEachSkippingFailures(items: List<T>, drawOne: (T) -> Unit): Int {
    var skipped = 0
    for (item in items) {
        try {
            drawOne(item)
        } catch (_: Throwable) {
            skipped++
        }
    }
    return skipped
}
