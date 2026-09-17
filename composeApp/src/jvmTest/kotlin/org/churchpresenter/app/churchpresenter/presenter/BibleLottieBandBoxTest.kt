package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.SlotLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A screen overridden to one Bible translation or one song language leaves the paired text/
 * reference slot blank rather than absent — the slots map still carries both `Text1`/`Text2`
 * entries, one of them empty. `BandLayer` used to look each slot's box straight from the
 * template's fixed side-by-side geometry regardless, so the populated slot stayed confined to its
 * own half and the other half read as a blank gap. `effectiveBox` widens the populated slot to the
 * union of both halves when its pair has nothing to show.
 */
class BibleLottieBandBoxTest {

    private val cfg = BibleLottieGenConfig(canvasW = 1920, canvasH = 194, layout = SlotLayout.SIDE_BY_SIDE)
    private val template = assertNotNull(parseBibleLottieTemplate(LottieBandTestSupport.templateJson(cfg)))

    private fun union(a: LottieSlotBox, b: LottieSlotBox): LottieSlotBox {
        val minX = minOf(a.x, b.x)
        val minY = minOf(a.y, b.y)
        val maxX = maxOf(a.x + a.w, b.x + b.w)
        val maxY = maxOf(a.y + a.h, b.y + b.h)
        return LottieSlotBox(minX, minY, maxX - minX, maxY - minY)
    }

    @Test
    fun `a populated slot keeps its own half when the pair also has text`() {
        val texts = mapOf(
            BibleLottieTemplate.LAYER_TEXT_1 to "primary",
            BibleLottieTemplate.LAYER_TEXT_2 to "secondary",
        )
        val box = template.effectiveBox(BibleLottieTemplate.LAYER_TEXT_1, texts)
        assertEquals(template.slots.getValue(BibleLottieTemplate.LAYER_TEXT_1), box)
    }

    @Test
    fun `a populated slot expands to the union when its pair is blank`() {
        val texts = mapOf(BibleLottieTemplate.LAYER_TEXT_1 to "primary only", BibleLottieTemplate.LAYER_TEXT_2 to "")
        val box = template.effectiveBox(BibleLottieTemplate.LAYER_TEXT_1, texts)
        val text1 = template.slots.getValue(BibleLottieTemplate.LAYER_TEXT_1)
        val text2 = template.slots.getValue(BibleLottieTemplate.LAYER_TEXT_2)
        assertEquals(union(text1, text2), box)
        // The two authored halves sit side by side, so the union is strictly wider than either.
        assertTrue(box.w > text1.w && box.w > text2.w)
    }

    @Test
    fun `a populated slot expands to the union when its pair is absent from the map entirely`() {
        val texts = mapOf(BibleLottieTemplate.LAYER_TEXT_1 to "primary only")
        val box = template.effectiveBox(BibleLottieTemplate.LAYER_TEXT_1, texts)
        val text1 = template.slots.getValue(BibleLottieTemplate.LAYER_TEXT_1)
        val text2 = template.slots.getValue(BibleLottieTemplate.LAYER_TEXT_2)
        assertEquals(union(text1, text2), box)
    }

    @Test
    fun `the widened box is symmetric between the two paired slots`() {
        val texts = mapOf(BibleLottieTemplate.LAYER_TEXT_1 to "", BibleLottieTemplate.LAYER_TEXT_2 to "solo")
        val box = template.effectiveBox(BibleLottieTemplate.LAYER_TEXT_2, texts)
        val text1 = template.slots.getValue(BibleLottieTemplate.LAYER_TEXT_1)
        val text2 = template.slots.getValue(BibleLottieTemplate.LAYER_TEXT_2)
        assertEquals(union(text1, text2), box)
    }

    @Test
    fun `a reference slot expands to the union in ticker mode where references are blanked out`() {
        // bibleBandSlots/songBandSlots blank both reference slots unconditionally in ticker mode,
        // so the union branch fires there too even in normal two-language playback -- harmless,
        // since an empty string draws nothing regardless of box size, but worth pinning.
        val texts = mapOf(BibleLottieTemplate.LAYER_REFERENCE_1 to "", BibleLottieTemplate.LAYER_REFERENCE_2 to "")
        val box = template.effectiveBox(BibleLottieTemplate.LAYER_REFERENCE_1, texts)
        val ref1 = template.slots.getValue(BibleLottieTemplate.LAYER_REFERENCE_1)
        val ref2 = template.slots.getValue(BibleLottieTemplate.LAYER_REFERENCE_2)
        assertEquals(union(ref1, ref2), box)
    }

    @Test
    fun `a missing paired slot falls back to the solo box unchanged`() {
        val soloCfg = BibleLottieGenConfig(canvasW = 960, canvasH = 180, layout = SlotLayout.SINGLE)
        val soloTemplate = assertNotNull(
            parseBibleLottieTemplate(LottieBandTestSupport.templateJson(soloCfg)),
        )
        val box = soloTemplate.effectiveBox(BibleLottieTemplate.LAYER_TEXT_1, emptyMap())
        assertEquals(soloTemplate.slots.getValue(BibleLottieTemplate.LAYER_TEXT_1), box)
    }

    @Test
    fun `a layer with no pairing keeps its own box`() {
        val box = template.effectiveBox("SomeOtherLayer", emptyMap())
        assertEquals(LottieSlotBox(0f, 0f, template.width, template.height), box)
    }
}
