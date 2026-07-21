package org.churchpresenter.app.churchpresenter.presenter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The key signal sent alongside the fill on an SDI output.
 *
 * A hardware keyer is handed two frames: the fill (what to show) and the key (where to show it,
 * as a greyscale matte). This converts one into the other in place, taking the brightest channel
 * of each pixel as its key value — content on black reads as white where the content is.
 *
 * It runs per pixel on every frame of a live broadcast, and it is the one place where getting the
 * alpha or the channel packing wrong is not visible on the operator's screen at all: the fill looks
 * perfect locally while the broadcast shows the graphic cut out wrongly, or not at all. The
 * conversion is destructive — the same array is reused for the key — so it also has to be exactly
 * self-consistent rather than approximately right.
 */
class DeckLinkKeySignalTest {

    private val convert =
        Class.forName("org.churchpresenter.app.churchpresenter.presenter.DeckLinkComposeOutputKt")
            .getDeclaredMethod("convertToKeySignal", IntArray::class.java)
            .apply { isAccessible = true }

    /** Runs the conversion over [pixels] (in place, as the renderer does) and hands the array back. */
    private fun keyOf(vararg pixels: Int): IntArray = pixels.also { convert.invoke(null, it) }

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun alpha(pixel: Int) = (pixel shr 24) and 0xFF
    private fun red(pixel: Int) = (pixel shr 16) and 0xFF
    private fun green(pixel: Int) = (pixel shr 8) and 0xFF
    private fun blue(pixel: Int) = pixel and 0xFF

    // ── The two ends of the scale ───────────────────────────────────────────────

    @Test
    fun `black stays black, so nothing is keyed in`() {
        val key = keyOf(argb(255, 0, 0, 0)).single()

        assertEquals(0, red(key))
        assertEquals(0, green(key))
        assertEquals(0, blue(key))
        assertEquals(255, alpha(key), "a transparent key frame is not a matte at all")
    }

    @Test
    fun `white stays white, so the fill is keyed in fully`() {
        val key = keyOf(argb(255, 255, 255, 255)).single()

        assertEquals(argb(255, 255, 255, 255), key)
    }

    // ── Which channel decides ───────────────────────────────────────────────────

    @Test
    fun `the brightest channel decides the key value`() {
        val key = keyOf(argb(255, 10, 200, 30)).single()

        assertEquals(200, red(key), "an average would fade a saturated green graphic almost out of the broadcast")
        assertEquals(200, green(key))
        assertEquals(200, blue(key))
    }

    @Test
    fun `a pure colour of any channel keys in fully`() {
        // Red, green and blue text on black are all fully opaque graphics — none may key in dimmer
        // than the others just because of its hue.
        listOf(argb(255, 255, 0, 0), argb(255, 0, 255, 0), argb(255, 0, 0, 255)).forEach { pixel ->
            assertEquals(
                argb(255, 255, 255, 255),
                keyOf(pixel).single(),
                "a saturated primary must key in solid, not as a grey",
            )
        }
    }

    @Test
    fun `the key is always a neutral grey`() {
        val key = keyOf(argb(255, 30, 90, 200)).single()

        assertEquals(red(key), green(key), "a tinted key confuses the keyer's own channel handling")
        assertEquals(green(key), blue(key))
    }

    // ── The frame as a whole ────────────────────────────────────────────────────

    @Test
    fun `every pixel of the frame is converted`() {
        val frame = keyOf(argb(255, 255, 0, 0), argb(255, 0, 0, 0), argb(255, 0, 128, 0))

        assertEquals(listOf(255, 0, 128), frame.map { red(it) }, "one missed pixel is a hole in the matte")
        assertTrue(frame.all { alpha(it) == 255 })
    }

    @Test
    fun `an existing alpha channel does not survive into the key`() {
        // The fill may arrive premultiplied or half-transparent; the key is a matte, and a matte
        // with its own transparency is meaningless to the hardware.
        val key = keyOf(argb(0, 255, 255, 255)).single()

        assertEquals(255, alpha(key))
        assertEquals(255, red(key), "the colour channels still decide the value; alpha is ignored")
    }

    @Test
    fun `converting an already-converted frame changes nothing further`() {
        // The renderer reuses one array, so the operation has to settle rather than drift darker.
        val once = keyOf(argb(255, 10, 200, 30)).single()
        val twice = keyOf(once).single()

        assertEquals(once, twice, "a second pass must not shift the matte")
    }

    @Test
    fun `an empty frame is handled rather than throwing`() {
        assertTrue(keyOf().isEmpty(), "a zero-size capture mid-resize must not take the output down")
    }

    @Test
    fun `every input brightness maps to itself`() {
        val frame = keyOf(*(0..255).map { argb(255, it, 0, 0) }.toIntArray())

        assertEquals(
            (0..255).toList(),
            frame.map { red(it) },
            "the matte has to be linear in the fill's brightness, or gradients band on air",
        )
    }
}
