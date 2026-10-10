package org.churchpresenter.presentationengine

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DeckRasterizerScaleTest {

    @Test
    fun `a normal page renders at the target width`() {
        assertEquals(2.0, DeckRasterizer.boundedRenderScale(960.0, 540.0, 1920), 1e-9)
    }

    @Test
    fun `a degenerate page or target falls back to unit scale`() {
        assertEquals(1.0, DeckRasterizer.boundedRenderScale(0.0, 540.0, 1920))
        assertEquals(1.0, DeckRasterizer.boundedRenderScale(960.0, -1.0, 1920))
        assertEquals(1.0, DeckRasterizer.boundedRenderScale(960.0, 540.0, 0))
    }

    @Test
    fun `a pathologically tall page is capped by the longest side and the pixel budget`() {
        val tall = DeckRasterizer.boundedRenderScale(100.0, 100_000.0, 1920)
        assertTrue(100_000.0 * tall <= DeckRasterizer.MAX_RENDER_DIMENSION + 1e-6)
        val huge = DeckRasterizer.boundedRenderScale(4000.0, 4000.0, 8000)
        assertTrue(4000.0 * huge * 4000.0 * huge <= DeckRasterizer.MAX_RENDER_PIXELS + 1.0)
    }

    @Test
    fun `flattening an opaque image returns it untouched and a translucent one onto the background`() {
        val opaque = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        assertSame(opaque, DeckRasterizer.flattenToRgb(opaque))

        val clear = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        val flat = DeckRasterizer.flattenToRgb(clear, Color.BLUE)
        assertEquals(BufferedImage.TYPE_INT_RGB, flat.type)
        assertEquals(Color.BLUE.rgb, flat.getRGB(2, 2))
    }
}
