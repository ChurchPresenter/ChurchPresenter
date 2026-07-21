package org.churchpresenter.app.churchpresenter.presenter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shrinking a verse until it fits the screen.
 *
 * A long passage in a large font does not fit, and the presenter cannot ask "what scale would fit?"
 * — it can only lay text out at a given scale and ask whether the result overflowed. So it
 * bisects: eight probes between a floor and full size, keeping the largest scale that still fit.
 *
 * Two properties matter and neither is visible from the call site. The answer must always be one
 * that FITS — returning a scale that overflows puts a verse on screen with its last line cut off,
 * which is exactly what auto-fit exists to prevent — and the probe count must stay fixed, because
 * each probe is a full text layout of the whole passage and this runs on every verse change.
 *
 * The search is private to `BiblePresenter`, so it is reached by reflection on the file class.
 */
class BibleFitScaleTest {

    private val search =
        Class.forName("org.churchpresenter.app.churchpresenter.presenter.BiblePresenterKt")
            .getDeclaredMethod(
                "binarySearchFitScale",
                Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Function1::class.java,
            )
            .apply { isAccessible = true }

    /** Records every scale the search probed, in order. */
    private val probed = mutableListOf<Float>()

    /**
     * Runs the search against a text that fits at or below [fitsUpTo], the shape a real layout has:
     * every smaller scale fits and every larger one overflows.
     */
    private fun fitScale(fitsUpTo: Float, minScale: Float = 0.15f, iterations: Int = 8): Float {
        val fits = { scale: Float -> probed.add(scale); scale <= fitsUpTo }
        @Suppress("UNCHECKED_CAST")
        return search.invoke(null, minScale, iterations, fits as Function1<Float, Boolean>) as Float
    }

    // ── The answer is always usable ─────────────────────────────────────────────

    @Test
    fun `the scale it settles on fits`() {
        val fitsUpTo = 0.62f

        val scale = fitScale(fitsUpTo)

        assertTrue(
            scale <= fitsUpTo,
            "a scale that overflows cuts the last line of the verse off the bottom of the screen: $scale",
        )
    }

    @Test
    fun `text that fits at full size is not shrunk needlessly`() {
        val scale = fitScale(fitsUpTo = 1f)

        assertTrue(scale > 0.99f, "a short verse must fill the screen, not sit small in the middle of it: $scale")
    }

    @Test
    fun `text that fits nowhere still returns the floor rather than nothing`() {
        val scale = fitScale(fitsUpTo = 0f)

        assertEquals(
            0.15f,
            scale,
            "an unreadably long passage is shown small; showing nothing at all would be worse",
        )
    }

    @Test
    fun `the answer never drops below the floor it was given`() {
        listOf(0.15f, 0.3f, 0.5f).forEach { floor ->
            assertTrue(fitScale(fitsUpTo = 0f, minScale = floor) >= floor, "the floor is a legibility limit")
        }
    }

    @Test
    fun `the answer never exceeds full size`() {
        assertTrue(fitScale(fitsUpTo = 2f) <= 1f, "scaling past 1 would render the verse larger than its own style")
    }

    // ── It closes in on the right answer ────────────────────────────────────────

    @Test
    fun `the scale it settles on is close to the largest that fits`() {
        val fitsUpTo = 0.62f

        val scale = fitScale(fitsUpTo)

        assertTrue(
            fitsUpTo - scale < 0.01f,
            "eight halvings of the 0.15..1 range should land within a percent of the true limit, got $scale",
        )
    }

    @Test
    fun `more probes narrow the answer further`() {
        val coarse = fitScale(fitsUpTo = 0.62f, iterations = 3)
        probed.clear()
        val fine = fitScale(fitsUpTo = 0.62f, iterations = 12)

        assertTrue(fine >= coarse, "more work must not produce a worse fit")
        assertTrue(0.62f - fine <= 0.62f - coarse)
    }

    // ── It costs a fixed amount ─────────────────────────────────────────────────

    @Test
    fun `the number of layout probes is fixed rather than driven by the text`() {
        fitScale(fitsUpTo = 0.62f)
        val forAMediumVerse = probed.size
        probed.clear()

        fitScale(fitsUpTo = 0.01f)

        assertEquals(
            forAMediumVerse,
            probed.size,
            "each probe lays the whole passage out again — a passage-dependent count would stall on long ones",
        )
        assertEquals(8, forAMediumVerse, "eight is the budget this runs on for every verse change")
    }

    @Test
    fun `no probe falls outside the range it was given`() {
        fitScale(fitsUpTo = 0.62f, minScale = 0.2f)

        assertTrue(
            probed.all { it in 0.2f..1f },
            "laying text out at a scale outside the range wastes a probe on an answer that cannot be used: $probed",
        )
    }

    @Test
    fun `zero probes returns the floor untouched`() {
        val scale = fitScale(fitsUpTo = 1f, iterations = 0)

        assertEquals(0.15f, scale)
        assertTrue(probed.isEmpty(), "no probes means no layout work at all")
    }
}
