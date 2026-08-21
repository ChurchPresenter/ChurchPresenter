package org.churchpresenter.lottiegen.spec

import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpecLayoutContextTest {

    private fun ctx(cfg: LottieGenConfig = LottieGenConfig(), spec: StyleSpec = StyleSpec()) =
        SpecLayoutContext(spec, cfg)

    @Test
    fun `ALWAYS is visible regardless of config`() {
        assertTrue(ctx().visible(listOf(VisibilityRule.ALWAYS)))
    }

    @Test
    fun `an empty rule list is visible`() {
        assertTrue(ctx().visible(emptyList()))
    }

    @Test
    fun `BG_ENABLED follows the background toggle`() {
        assertTrue(ctx(LottieGenConfig(bgEnabled = true)).visible(listOf(VisibilityRule.BG_ENABLED)))
        assertFalse(ctx(LottieGenConfig(bgEnabled = false)).visible(listOf(VisibilityRule.BG_ENABLED)))
    }

    @Test
    fun `LOGO_ENABLED follows the logo toggle`() {
        assertTrue(ctx(LottieGenConfig(logoEnabled = true)).visible(listOf(VisibilityRule.LOGO_ENABLED)))
        assertFalse(ctx(LottieGenConfig(logoEnabled = false)).visible(listOf(VisibilityRule.LOGO_ENABLED)))
    }

    @Test
    fun `NAME_VISIBLE and INFO_VISIBLE invert their hide flags`() {
        assertFalse(ctx(LottieGenConfig(hideName = true)).visible(listOf(VisibilityRule.NAME_VISIBLE)))
        assertTrue(ctx(LottieGenConfig(hideName = false)).visible(listOf(VisibilityRule.NAME_VISIBLE)))
        assertFalse(ctx(LottieGenConfig(hideInfo = true)).visible(listOf(VisibilityRule.INFO_VISIBLE)))
        assertTrue(ctx(LottieGenConfig(hideInfo = false)).visible(listOf(VisibilityRule.INFO_VISIBLE)))
    }

    @Test
    fun `BORDER_SET needs a positive thickness`() {
        assertTrue(ctx(LottieGenConfig(borderThickness = 2f)).visible(listOf(VisibilityRule.BORDER_SET)))
        assertFalse(ctx(LottieGenConfig(borderThickness = 0f)).visible(listOf(VisibilityRule.BORDER_SET)))
    }

    @Test
    fun `all rules must hold, not just one`() {
        val cfg = LottieGenConfig(bgEnabled = true, logoEnabled = false)

        assertFalse(ctx(cfg).visible(listOf(VisibilityRule.BG_ENABLED, VisibilityRule.LOGO_ENABLED)))
    }

    @Test
    fun `a rect, ellipse and background all resolve their own size spec`() {
        val c = ctx()
        val em = SizeSpec.Em(2.0, 1.0)

        assertEquals(c.sizeOf(em), c.resolveSize(RectElement(id = "r", size = em)))
        assertEquals(c.sizeOf(em), c.resolveSize(EllipseElement(id = "e", size = em)))
        assertEquals(c.sizeOf(em), c.resolveSize(BackgroundElement(id = "b", size = em)))
    }

    @Test
    fun `a logo falls back to the configured logo size when the element sets none`() {
        val c = ctx(LottieGenConfig(logoSize = 3f))

        assertEquals(c.em(3.0) to c.em(3.0), c.resolveSize(LogoElement(id = "logo")))
    }

    @Test
    fun `a logo honours its own size when it sets one`() {
        val c = ctx(LottieGenConfig(logoSize = 3f))

        assertEquals(c.em(5.0) to c.em(5.0), c.resolveSize(LogoElement(id = "logo", sizeEm = 5.0)))
    }

    @Test
    fun `a polygon with no vertices resolves to zero rather than throwing`() {
        assertEquals(0.0 to 0.0, ctx().resolveSize(PolygonElement(id = "p", verticesEm = emptyList())))
    }

    @Test
    fun `a polygon resolves to its vertex extent`() {
        val c = ctx()
        val poly = PolygonElement(id = "p", verticesEm = listOf(listOf(0.0, 0.0), listOf(2.0, 1.0)))

        assertEquals(c.em(2.0) to c.em(1.0), c.resolveSize(poly))
    }

    @Test
    fun `a path with no vertices resolves to zero rather than throwing`() {
        assertEquals(0.0 to 0.0, ctx().resolveSize(PathElement(id = "p", verticesEm = emptyList())))
    }

    @Test
    fun `a path resolves to its vertex bounding box`() {
        val c = ctx()
        val path = PathElement(
            id = "p",
            verticesEm = listOf(CurveVertex(0.0, 0.0), CurveVertex(3.0, 2.0))
        )

        assertEquals(c.em(3.0) to c.em(2.0), c.resolveSize(path))
    }

    @Test
    fun `every width basis resolves to a positive measurement`() {
        val c = ctx()

        WidthBasis.entries.forEach {
            assertTrue(c.basisWidthPx(it) > 0.0, "$it measured ${c.basisWidthPx(it)}")
        }
    }

    @Test
    fun `the text block basis is at least as wide as either field`() {
        val c = ctx()

        assertTrue(c.basisWidthPx(WidthBasis.TEXT_BLOCK) >= c.basisWidthPx(WidthBasis.NAME))
        assertTrue(c.basisWidthPx(WidthBasis.TEXT_BLOCK) >= c.basisWidthPx(WidthBasis.INFO))
    }

    @Test
    fun `no requested fit leaves the shape unscaled`() {
        assertEquals(1.0, ctx().fitFactor(null, naturalWidthPx = 50.0))
    }

    @Test
    fun `a zero-width shape cannot be fitted and stays unscaled`() {
        assertEquals(1.0, ctx().fitFactor(WidthBasis.NAME, naturalWidthPx = 0.0))
        assertEquals(1.0, ctx().fitFactor(WidthBasis.NAME, naturalWidthPx = -4.0))
    }

    @Test
    fun `a fitted shape scales to the basis width`() {
        val c = ctx()
        val natural = 20.0

        assertEquals(c.basisWidthPx(WidthBasis.NAME) / natural, c.fitFactor(WidthBasis.NAME, natural))
    }
}
