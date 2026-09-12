package org.churchpresenter.app.churchpresenter.presenter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleLottieTextFitTest {

    /** Every character ten wide, so widths are easy to reason about. */
    private val tenWide: (Char) -> Float = { 10f }

    @Test
    fun `wraps at the last word boundary once the line reaches the box`() {
        val text = "For God so loved the world"
        assertEquals(listOf("For God so", "loved the", "world"), wrapLikeLottie(text, 105f, 0f, tenWide))
        assertEquals(listOf(text), wrapLikeLottie(text, 0f, 0f, tenWide), "no box, no wrap")
        assertEquals(emptyList(), wrapLikeLottie("", 100f, 0f, tenWide))
    }

    @Test
    fun `a single word wider than the box breaks inside the word`() {
        assertEquals(listOf("abcd", "efgh", "ij"), wrapLikeLottie("abcdefghij", 45f, 0f, tenWide))
    }

    @Test
    fun `tracking counts towards the line width`() {
        val tight = wrapLikeLottie("aa bb cc", 60f, 0f, tenWide)
        val tracked = wrapLikeLottie("aa bb cc", 60f, 10f, tenWide)
        assertEquals(listOf("aa bb", "cc"), tight)
        assertEquals(listOf("aa", "bb", "cc"), tracked)
    }

    @Test
    fun `the fit keeps the base size when the lines fit and shrinks until they do`() {
        val box = LottieSlotBox(0f, 0f, 200f, 60f)
        val fits = fitLottieSlot(SlotFitRequest("short", box, 20f, 0f, singleLine = false), tenWide)
        assertEquals(20f, fits.fontSize)
        assertEquals(listOf("short"), fits.lines)
        assertEquals(50f, fits.lineWidthPx)

        val long = "one two three four five six seven eight nine ten eleven twelve"
        val shrunk = fitLottieSlot(SlotFitRequest(long, box, 20f, 0f, singleLine = false), tenWide)
        assertTrue(shrunk.fontSize < 20f, "too many lines at full size")
        assertTrue(shrunk.lines.size * shrunk.fontSize * LINE_HEIGHT_FACTOR <= box.h + 0.01f, "and the result fits")
    }

    @Test
    fun `a single-line slot fits its width and keeps one line`() {
        val box = LottieSlotBox(0f, 0f, 100f, 40f)
        val fitted = fitLottieSlot(SlotFitRequest("John 3:16 (KJV)", box, 20f, 0f, singleLine = true), tenWide)
        assertEquals(1, fitted.lines.size)
        assertTrue(fitted.lineWidthPx <= 100f)
        assertTrue(fitted.fontSize < 20f)
    }

    @Test
    fun `written line breaks are kept and each written line wraps on its own`() {
        val box = LottieSlotBox(0f, 0f, 60f, 500f)
        val fitted = fitLottieSlot(SlotFitRequest("aa bb\ncc dd ee", box, 10f, 0f, singleLine = false), tenWide)
        assertEquals(listOf("aa bb", "cc dd", "ee"), fitted.lines)
        assertEquals(10f, fitted.fontSize)
    }

    @Test
    fun `empty text is a fitted nothing`() {
        val request = SlotFitRequest("", LottieSlotBox(0f, 0f, 10f, 10f), 20f, 0f, singleLine = false)
        val fitted = fitLottieSlot(request, tenWide)
        assertEquals(emptyList(), fitted.lines)
        assertEquals(20f, fitted.fontSize)
    }
}
