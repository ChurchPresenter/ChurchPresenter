package org.churchpresenter.profiles

import org.churchpresenter.presenter.BibleStyleElement
import org.churchpresenter.presenter.bibleBoxKey
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BibleAdjustModelTest {

    private val on = TextBox(enabled = true, xPercent = 5f, yPercent = 5f, widthPercent = 40f, heightPercent = 20f)

    private fun bible(): BibleSettings {
        val base = BibleSettings(
            translations = listOf(BibleTranslationSettings(fileName = "kjv.spb"), BibleTranslationSettings(fileName = "rst.spb")),
        )
        return base.copy(
            textBoxes = mapOf(
                base.bibleBoxKey(BibleStyleElement.TEXT, lowerThird = false, fileName = "kjv.spb") to on,
                base.bibleBoxKey(BibleStyleElement.REFERENCE, lowerThird = false, fileName = "rst.spb") to on,
                base.bibleBoxKey(BibleStyleElement.TEXT, lowerThird = true, fileName = "kjv.spb") to on,
            ),
        )
    }

    private class Harness(lowerThird: Boolean, translationIndex: Int) {
        var draft = AppSettings()
        val translations = mutableListOf<Int>()
        val elements = mutableListOf<CustomizeElement>()
        val profile = OutputProfile(
            id = "p",
            displayMode = if (lowerThird) Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL else Constants.DISPLAY_MODE_FULLSCREEN,
            bibleMode = Constants.SONG_LANG_BOTH,
        )
        val index = translationIndex

        fun model(): AdjustModel = bibleAdjustModel(
            draft,
            profile,
            Adjustable(CustomizeElement.BIBLE_TEXT) { elements += it },
            { t -> draft = t(draft) },
            Adjustable(index) { translations += it },
        )
    }

    private fun harness(lowerThird: Boolean, translationIndex: Int = 0) =
        Harness(lowerThird, translationIndex).also { it.draft = AppSettings(bibleSettings = bible()) }

    @Test
    fun `resetting positions turns off this shape's boxes and keeps the other shape's`() {
        listOf(false, true).forEach { lowerThird ->
            val h = harness(lowerThird)
            val reset = assertNotNull(h.model().positions)
            assertTrue(reset.moved, "a box is on for this shape")
            reset.onReset()
            val boxes = h.draft.bibleSettings.textBoxes
            assertTrue(boxes.filterKeys { it.endsWith("@LT") == lowerThird }.values.none { it.enabled })
            assertTrue(boxes.filterKeys { it.endsWith("@LT") != lowerThird }.values.all { it.enabled })
            assertFalse(h.model().positions!!.moved, "nothing left moved (lowerThird=$lowerThird)")
        }
    }

    @Test
    fun `picking a box on the preview points the rows at its translation and element`() {
        val h = harness(lowerThird = false)
        val boxes = assertNotNull(h.model().boxes)
        assertEquals(2, boxes.handles.size)
        boxes.handles.forEach { it.onPick() }
        assertEquals(listOf(0, 1), h.translations)
        assertEquals(listOf(CustomizeElement.BIBLE_TEXT, CustomizeElement.BIBLE_REFERENCE), h.elements)
        val moved = on.copy(xPercent = 30f)
        boxes.handles.first().onChange(moved)
        assertEquals(moved, h.draft.bibleSettings.textBoxes[boxes.handles.first().key])
    }

    @Test
    fun `a lower third offers the one box it draws, and no boxes at all once they are off`() {
        val h = harness(lowerThird = true)
        assertEquals(1, h.model().boxes!!.handles.size)
        h.draft = h.draft.copy(bibleSettings = h.draft.bibleSettings.copy(textBoxes = emptyMap()))
        assertNull(h.model().boxes)
    }

    @Test
    fun `a picked translation's block moves on the shape being edited`() {
        listOf(false, true).forEach { lowerThird ->
            val h = harness(lowerThird, translationIndex = 1)
            val blocks = assertNotNull(h.model().blocks)
            assertEquals(listOf("kjv.spb", "rst.spb"), blocks.keys)
            assertEquals(1, blocks.selected)
            assertNotNull(blocks.reference)
            blocks.shift!!.onChange(12 to -7)
            val entry = h.draft.bibleSettings.translations[1]
            if (lowerThird) {
                assertEquals(12 to -7, entry.lowerThirdShiftX to entry.lowerThirdShiftY)
                assertEquals(0 to 0, entry.shiftX to entry.shiftY)
            } else {
                assertEquals(12 to -7, entry.shiftX to entry.shiftY)
                assertEquals(0 to 0, entry.lowerThirdShiftX to entry.lowerThirdShiftY)
            }
            blocks.onSelect(0)
            assertEquals(listOf(0), h.translations)
            assertEquals(listOf(CustomizeElement.BIBLE_TEXT), h.elements)
        }
    }

    @Test
    fun `under All nothing is picked and no block moves on its own`() {
        val blocks = harness(lowerThird = false, translationIndex = ALL_TRANSLATIONS).model().blocks!!
        assertNull(blocks.selected)
        assertNull(blocks.shift)
    }

    @Test
    fun `margins, alignment, region and band write the Bible's own settings`() {
        val full = harness(lowerThird = false)
        val model = full.model()
        assertNull(model.band)
        model.margins.onChange(Margins(1, 2, 3, 4))
        val bs = full.draft.bibleSettings
        assertEquals(listOf(1, 2, 3, 4), listOf(bs.marginTop, bs.marginBottom, bs.marginLeft, bs.marginRight))
        full.model().region!!.onChange(ContentRegion(yOffsetPercent = 20, widthPercent = 70))
        full.model().alignment.onChange(Constants.TOP)
        assertEquals(Constants.TOP, full.draft.bibleSettings.verticalAlignment)
        assertEquals(0, full.draft.bibleSettings.contentRegion.yOffsetPercent, "snapping clears the vertical offset")
        assertEquals(70, full.draft.bibleSettings.contentRegion.widthPercent)

        val band = harness(lowerThird = true)
        val bandModel = band.model()
        assertNull(bandModel.region)
        bandModel.band!!.onChange(33)
        assertEquals(33, band.draft.bibleSettings.lowerThirdHeightPercent)
    }
}
