package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.layout.ContentScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BandContentScaleTest {

    @Test
    fun `a box narrower than the template stretches, so it fits instead`() {
        // Portrait canvas: box aspect ~2.78 (400 wide over a 144-tall band box) against a
        // template authored wide, ~9.9 (1920x194).
        assertEquals(ContentScale.Fit, bandContentScale(boxAspectRatio = 2.78f, templateAspectRatio = 9.9f))
    }

    @Test
    fun `a box as wide as the template fills bounds unchanged`() {
        // Landscape canvas: box aspect matches the template's own -- the ordinary case.
        assertEquals(ContentScale.FillBounds, bandContentScale(boxAspectRatio = 9.9f, templateAspectRatio = 9.9f))
    }

    @Test
    fun `a box wider than the template still fills bounds`() {
        assertEquals(ContentScale.FillBounds, bandContentScale(boxAspectRatio = 12f, templateAspectRatio = 9.9f))
    }

    @Test
    fun `landscape canvas renders the configured height exactly`() {
        // 1920x1080 canvas, bandFraction 0.18: box aspect ~9.88 -- at least as wide as a template
        // authored slightly narrower, so FillBounds applies and the effective height is exactly
        // what was asked for.
        val fraction = effectiveBandFraction(
            canvasAspectRatio = 1920f / 1080f,
            bandFraction = 0.18f,
            templateAspectRatio = 9f,
        )
        assertEquals(0.18f, fraction)
    }

    @Test
    fun `portrait canvas renders shorter than the configured height`() {
        // 400x800 canvas, bandFraction 0.18: box aspect ~2.78, below the template's 9, so Fit
        // takes over and the band renders shorter than bandFraction asks for.
        val fraction = effectiveBandFraction(
            canvasAspectRatio = 400f / 800f,
            bandFraction = 0.18f,
            templateAspectRatio = 9f,
        )
        assertEquals((400f / 800f) / 9f, fraction)
        assertTrue(fraction < 0.18f)
    }
}
