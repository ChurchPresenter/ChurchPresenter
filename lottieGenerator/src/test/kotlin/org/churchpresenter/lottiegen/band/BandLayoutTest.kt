package org.churchpresenter.lottiegen.band

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BandLayoutTest {

    private val cfg = BibleLottieGenConfig(canvasW = 1000, canvasH = 200, paddingPx = 10, insetPx = 0)

    @Test
    fun `a single layout gives the verse the width and puts the reference below it`() {
        val slots = computeSlots(cfg.copy(layout = SlotLayout.SINGLE, referenceHeightFraction = 0.25f))
        assertEquals(SlotBox(0.0, 0.0, 1000.0, 200.0), slots.band)
        assertEquals(SlotBox(10.0, 10.0, 980.0, 135.0), slots.text1)
        assertEquals(SlotBox(10.0, 145.0, 980.0, 45.0), slots.reference1)
        assertNull(slots.text2)
        assertNull(slots.reference2)
    }

    @Test
    fun `reference above puts it on top of the verse`() {
        val slots =
            computeSlots(cfg.copy(referencePlacement = ReferencePlacement.ABOVE, referenceHeightFraction = 0.25f))
        assertEquals(10.0, slots.reference1.y)
        assertEquals(55.0, slots.text1.y)
    }

    @Test
    fun `side by side splits the width with a gap and stacked splits the height`() {
        val side = computeSlots(cfg.copy(layout = SlotLayout.SIDE_BY_SIDE))
        val text2 = assertNotNull(side.text2)
        assertEquals(side.text1.w, text2.w)
        assertEquals(side.text1.right + 10.0, text2.x, "one padding between the columns")
        assertEquals(side.text1.y, text2.y)

        val stacked = computeSlots(cfg.copy(layout = SlotLayout.STACKED))
        val lower = assertNotNull(stacked.text2)
        assertEquals(stacked.text1.x, lower.x)
        assertTrue(lower.y > stacked.reference1.bottom, "the second row starts under the first row's reference")
    }

    @Test
    fun `the inset shrinks the band and the padding shrinks the text inside it`() {
        val slots = computeSlots(cfg.copy(insetPx = 20))
        assertEquals(SlotBox(20.0, 20.0, 960.0, 160.0), slots.band)
        assertEquals(30.0, slots.text1.x)
    }

    @Test
    fun `the reference height fraction is clamped to a sane range`() {
        val tiny = computeSlots(cfg.copy(referenceHeightFraction = 0.01f))
        assertEquals(180.0 * 0.1, tiny.reference1.h, 0.001)
        val huge = computeSlots(cfg.copy(referenceHeightFraction = 0.9f))
        assertEquals(180.0 * 0.5, huge.reference1.h, 0.001)
    }

    @Test
    fun `styles with colour blocks keep the text off them`() {
        for (style in BandStyle.entries) {
            val insets = style.textInsets()
            val slots = computeSlots(cfg.copy(bandStyle = style))
            val band = slots.band
            assertTrue(slots.text1.x >= band.x + band.w * insets.left - 0.001, "$style keeps left")
            assertTrue(slots.text1.y >= band.y + band.h * insets.top - 0.001, "$style keeps top")
            assertTrue(slots.text1.w > 0 && slots.text1.h > 0, "$style leaves room for text")
        }
        assertTrue(BandStyle.ANGLED_BLADE.textInsets().left > 0.0)
        assertTrue(BandStyle.ARCH_DECK.textInsets().bottom > 0.0)
        assertEquals(StyleInsets(), BandStyle.GRADIENT_TRIO.textInsets(), "a gradient is a backdrop, not a block")
    }

    @Test
    fun `the text-area margins take room off, give it back when negative, and stop at the band's edge`() {
        val base = computeSlots(cfg)
        val narrowed = computeSlots(
            cfg.copy(textAreaLeftPx = 100, textAreaRightPx = 200, textAreaTopPx = 5, textAreaBottomPx = 10),
        )
        assertEquals(base.text1.x + 100, narrowed.text1.x)
        assertEquals(base.text1.w - 300, narrowed.text1.w)
        assertEquals(base.text1.y + 5, narrowed.text1.y)
        val chevron = cfg.copy(bandStyle = BandStyle.CHEVRON_TAG, insetPx = 10)
        val full = computeSlots(chevron.copy(textAreaLeftPx = -5000, textAreaRightPx = -5000))
        assertEquals(computeSlots(chevron).band.x, full.text1.x, "a negative margin reaches the band's edge")
        assertEquals(computeSlots(chevron).band.w, full.text1.w)
        val squeezed = computeSlots(cfg.copy(textAreaLeftPx = 5000, textAreaRightPx = 5000))
        assertTrue(squeezed.text1.w >= 1.0, "the slot never vanishes")
    }

    @Test
    fun `the second score of styles reserves room beside its shapes`() {
        assertTrue(BandStyle.LEFT_BLOCK.textInsets().left > BandGeometry.BLOCK_W)
        assertTrue(BandStyle.TOP_TAB.textInsets().top > BandGeometry.TOP_TAB_H)
        assertEquals(BandGeometry.BOTTOM_BAND_H, BandStyle.BOTTOM_BAND.textInsets().bottom)
        assertEquals(BandGeometry.ZIGZAG_H + BandGeometry.ZIGZAG_VALLEY, BandStyle.ZIGZAG_EDGE.textInsets().bottom)
        assertTrue(BandStyle.DIAGONAL_STRIPES.textInsets().right > 0.0)
        assertEquals(StyleInsets(), BandStyle.CORNER_BRACKETS.textInsets(), "brackets live in the padding")
        assertEquals(StyleInsets(), BandStyle.SPLIT_VERTICAL.textInsets(), "one language on each half")
    }

    @Test
    fun `slot box helpers`() {
        val box = SlotBox(10.0, 20.0, 100.0, 50.0)
        assertEquals(110.0, box.right)
        assertEquals(70.0, box.bottom)
        assertEquals(60.0, box.centerX)
        assertEquals(45.0, box.centerY)
        assertEquals(SlotBox(15.0, 25.0, 90.0, 40.0), box.inset(5.0))
    }
}
