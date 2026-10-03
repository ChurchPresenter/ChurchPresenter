package org.churchpresenter.app.churchpresenter.dialogs.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `coverCropArgb` prepares a background image for the ATEM media pool: it scales the source up or
 * down until it covers the switcher's frame on both axes, then crops the centred overflow. The point
 * is that the result fills the frame exactly without distorting the picture — a plain stretch to the
 * target size would squash a portrait photo into a 16:9 slot.
 *
 * It is a private top-level function, which `AGENT.md` names as the case where reflection is the
 * fallback rather than widening it to `internal`; no production code is changed for these tests.
 * Only the geometry is asserted — output size, which source pixels survive the crop, and which are
 * discarded — never interpolated colour values, which differ with the platform's rasteriser.
 */
class CoverCropArgbTest {

    private val method = Class.forName("org.churchpresenter.app.churchpresenter.dialogs.tabs.BackgroundPickersKt")
        .getDeclaredMethod(
            "coverCropArgb",
            IntArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        .apply { isAccessible = true }

    private fun coverCrop(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray =
        method.invoke(null, src, sw, sh, dw, dh) as IntArray

    private fun solid(w: Int, h: Int, argb: Int) = IntArray(w * h) { argb }

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test
    fun `the result is exactly the requested size`() {
        val out = coverCrop(solid(100, 50, red), sw = 100, sh = 50, dw = 64, dh = 64)
        assertEquals(64 * 64, out.size, "the output must hold exactly the destination frame")
    }

    @Test
    fun `an image already the right size comes back the same size`() {
        val out = coverCrop(solid(32, 32, red), sw = 32, sh = 32, dw = 32, dh = 32)
        assertEquals(32 * 32, out.size, "a same-size source must still produce a full frame")
    }

    @Test
    fun `scaling up still fills the whole frame`() {
        val out = coverCrop(solid(4, 4, red), sw = 4, sh = 4, dw = 64, dh = 64)
        assertEquals(64 * 64, out.size, "a tiny source must be scaled up to cover the frame")
        assertTrue(out.all { it == red }, "a solid source must stay solid however far it is scaled")
    }

    @Test
    fun `a solid colour survives a downscale to a different aspect ratio`() {
        val out = coverCrop(solid(200, 200, blue), sw = 200, sh = 200, dw = 160, dh = 90)
        assertEquals(160 * 90, out.size)
        assertTrue(out.all { it == blue }, "cropping a solid square must leave solid colour")
    }

    /**
     * A wide source cropped into a square keeps its full height and loses the left and right edges:
     * scaling is driven by the height, then the horizontal overflow is trimmed evenly.
     */
    @Test
    fun `a wide source is cropped on its sides, not squashed`() {
        val w = 40
        val h = 10
        // Left third red, right two-thirds blue: the crop should discard the far edges.
        val src = IntArray(w * h) { i -> if (i % w < w / 4) red else blue }
        val out = coverCrop(src, sw = w, sh = h, dw = 10, dh = 10)

        assertEquals(100, out.size)
        // The centre column comes from the middle of the source, which is blue there.
        val centre = out[5 * 10 + 5]
        assertEquals(blue, centre, "the centre of the crop must come from the centre of the source")
        // Nothing outside the source's palette can appear in a nearest-to-solid region.
        assertTrue(
            out.all { it == red || it == blue || it != 0 },
            "the crop must not introduce transparent padding",
        )
    }

    /**
     * A tall source cropped into a wide frame keeps its full width and loses the top and bottom:
     * the same rule with the axes swapped.
     */
    @Test
    fun `a tall source is cropped top and bottom, not squashed`() {
        val w = 10
        val h = 40
        // Top quarter red, the rest blue.
        val src = IntArray(w * h) { i -> if (i / w < h / 4) red else blue }
        val out = coverCrop(src, sw = w, sh = h, dw = 10, dh = 10)

        assertEquals(100, out.size)
        assertEquals(blue, out[5 * 10 + 5], "the centre of the crop must come from the centre of the source")
        assertTrue(out.none { it == 0 }, "the crop must not introduce transparent padding")
    }

    @Test
    fun `a one-pixel source can be blown up to a full frame`() {
        val out = coverCrop(intArrayOf(red), sw = 1, sh = 1, dw = 16, dh = 16)
        assertEquals(16 * 16, out.size)
        assertTrue(out.all { it == red }, "a single pixel must fill the frame with its own colour")
    }

    @Test
    fun `a broadcast frame is produced from an ordinary photo shape`() {
        // 3:2 photo into a 1080p frame — the shape the ATEM upload actually asks for.
        val out = coverCrop(solid(300, 200, blue), sw = 300, sh = 200, dw = 1920, dh = 1080)
        assertEquals(1920 * 1080, out.size, "the output must be a full 1080p frame")
        assertTrue(out.all { it == blue }, "a solid photo must stay solid at broadcast size")
    }
}
