package org.churchpresenter.lottiegen.spec

import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpecLayoutEdgeTest {

    private val centered = StyleSpec(layout = LayoutSpec(centerSingleLine = true))
    private val hiddenLines =
        LottieGenConfig(hideName = true, hideInfo = true, hideDetail = false, detailText = "Church")

    @Test
    fun `a detail line shown on its own is centered like a lone name or info line`() {
        val alone = SpecLayoutContext(centered, hiddenLines)
        val stacked = SpecLayoutContext(StyleSpec(), hiddenLines)
        assertTrue(alone.detailLineY < stacked.detailLineY)
        assertTrue(SpecLayoutContext(centered, hiddenLines.copy(hideInfo = false)).detailLineY > alone.detailLineY)
    }

    @Test
    fun `a text-wrapped size follows the detail line it wraps`() {
        val ctx = SpecLayoutContext(StyleSpec(), hiddenLines)
        val (w, h) = ctx.sizeOf(SizeSpec.TextWrap(TextFieldRef.DETAIL, padXEm = 0.0, padYEm = 0.0))
        assertEquals(ctx.detailMeasured.width.toDouble(), w)
        assertEquals(ctx.detailSizePx, h)
    }

    @Test
    fun `a collapsed slot anchors at the block center and an unknown one says so`() {
        val spec = StyleSpec(
            layout = LayoutSpec(
                slots = listOf(
                    SlotSpec("gone", SlotKind.FIXED, widthEm = 1.0, visibleWhen = listOf(VisibilityRule.LOGO_ENABLED)),
                ),
            ),
        )
        val ctx = SpecLayoutContext(spec, LottieGenConfig())
        val collapsed = ctx.resolve(Placement(slot = "gone"))
        val unknown = ctx.resolve(Placement(slot = "nowhere"))
        assertEquals(collapsed, unknown)
        assertEquals(listOf("Unknown slot 'nowhere' — using block center"), ctx.warnings)
    }
}
