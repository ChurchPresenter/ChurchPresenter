package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants

/**
 * Adjust on preview: handles over the picture that write the page's own settings, converted from
 * pointer pixels to output pixels at the preview's scale.
 *
 * The preview is 400dp wide for a 1920-pixel screen, so a pixel of pointer is 4.8 output pixels; the
 * tests assert the direction and rough size of each change rather than exact numbers.
 */
@OptIn(ExperimentalTestApi::class)
class ProfilesAdjustTest {

    private val twoTranslations = BibleSettings(
        translations = listOf(
            BibleTranslationSettings(fileName = "kjv.spb", customAbbreviation = "KJV"),
            BibleTranslationSettings(fileName = "rst.spb", customAbbreviation = "RST"),
        ),
    )

    private fun doc(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = profileDocument(mode = mode,
            bible = twoTranslations)

    private fun androidx.compose.ui.test.SkikoComposeUiTest.bibleAdjusting() {
        openCustomizePane(CustomizePane.BIBLE)
        tap(ADJUST_SWITCH_TAG)
    }

    @Test
    fun `the handles show only while Adjust is on, with a guide to them`() = profilesTab(doc()) { _ ->
        openCustomizePane(CustomizePane.BIBLE)
        assertEquals(0, countTag(ADJUST_OVERLAY_TAG))
        tap(ADJUST_SWITCH_TAG)
        assertEquals(1, countTag(ADJUST_OVERLAY_TAG))
        onNodeWithText("Drag the blue bars", substring = true).assertExists()
        onNodeWithText("Click a block of text", substring = true).assertExists()
        tap(ADJUST_SWITCH_TAG)
        assertEquals(0, countTag(ADJUST_OVERLAY_TAG))
    }

    @Test
    fun `each margin bar moves its own margin inward`() = profilesTab(doc()) { get ->
        bibleAdjusting()
        val before = get().bible()
        dragTag(adjustMarginTag("top"), 0f, 10f)
        dragTag(adjustMarginTag("bottom"), 0f, -10f)
        dragTag(adjustMarginTag("left"), 10f, 0f)
        dragTag(adjustMarginTag("right"), -10f, 0f)
        val after = get().bible()
        assertTrue(after.marginTop > before.marginTop)
        assertTrue(after.marginBottom > before.marginBottom)
        assertTrue(after.marginLeft > before.marginLeft)
        assertTrue(after.marginRight > before.marginRight)
    }

    @Test
    fun `the move circle snaps to a guide, or sets the block's own offset between them`() = profilesTab(doc()) { get ->
        bibleAdjusting()
        assertEquals(Constants.BOTTOM, get().bible().verticalAlignment)
        // From the bottom guide up to the middle one.
        dragTag(ADJUST_MOVE_TAG, 0f, -76f)
        assertEquals(Constants.MIDDLE, get().bible().verticalAlignment)
        assertEquals(0, get().bible().contentRegion.yOffsetPercent)
        // Sideways and between guides -- a fifth of the way down from the middle: an offset of its
        // own, and no snap.
        dragTag(ADJUST_MOVE_TAG, 30f, 40f)
        val region = get().bible().contentRegion
        assertTrue(region.xOffsetPercent > 0)
        assertTrue(region.yOffsetPercent > 0)
        assertEquals(Constants.MIDDLE, get().bible().verticalAlignment)
    }

    @Test
    fun `the width dots narrow the content box`() = profilesTab(doc()) { get ->
        bibleAdjusting()
        dragTag(adjustWidthTag(true), -20f, 0f)
        val centred = get().bible().contentRegion.widthPercent
        assertTrue(centred < 100)
        // Off centre only the side dragged moves, so the same drag narrows it less.
        dragTag(ADJUST_MOVE_TAG, 40f, 0f)
        dragTag(adjustWidthTag(false), 20f, 0f)
        val offCentre = get().bible().contentRegion.widthPercent
        assertTrue(offCentre < centred)
        assertTrue(centred - offCentre < 100 - centred)
    }

    @Test
    fun `the size corner sizes every translation under All`() = profilesTab(doc()) { get ->
        bibleAdjusting()
        val before = get().bible().translationList().map { it.textFontSize }
        dragTag(ADJUST_SIZE_TAG, 20f, 20f)
        val after = get().bible().translationList().map { it.textFontSize }
        assertTrue(after.zip(before).all { (a, b) -> a > b }, "$before → $after")
        onNodeWithText("${after.first()} pt").assertExists()
    }

    @Test
    fun `on a lower third the band bar sets the band's height`() =
        profilesTab(doc(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { get ->
            bibleAdjusting()
            val before = get().bible().lowerThirdHeightPercent
            onNodeWithText("Drag the orange bar", substring = true).assertExists()
            dragTag(ADJUST_BAND_TAG, 0f, -20f)
            assertTrue(get().bible().lowerThirdHeightPercent > before)
            // No content box on a band: its width is the band.
            assertEquals(0, countTag(adjustWidthTag(true)))
        }

    @Test
    fun `a block picked on the preview is what the rows edit, and moves on its own`() = profilesTab(doc()) { get ->
        bibleAdjusting()
        assertEquals(0, countTag(ADJUST_BLOCK_MOVE_TAG), "nothing is picked under All")
        tapAt(adjustBlockTag(1), Offset(160f, 4f))
        assertEquals(1, countTag(ADJUST_BLOCK_MOVE_TAG))
        dragTag(ADJUST_BLOCK_MOVE_TAG, 10f, 10f)
        val (kjv, rst) = get().bible().translationList()
        assertTrue(rst.shiftX > 0 && rst.shiftY > 0,
                "rst ${rst.shiftX},${rst.shiftY} kjv ${kjv.shiftX},${kjv.shiftY}")
        assertEquals(0, kjv.shiftX)
        // The size corner now sizes RST alone.
        dragTag(ADJUST_SIZE_TAG, 20f, 20f)
        val (kjv2, rst2) = get().bible().translationList()
        assertEquals(kjv.textFontSize, kjv2.textFontSize)
        assertTrue(rst2.textFontSize > rst.textFontSize)
    }

    @Test
    fun `the reference is dragged anywhere on its own`() = profilesTab(doc()) { get ->
        bibleAdjusting()
        val position = get().bible().translationList()[0].referencePosition
        // Across the full width of its cell already, it has no room sideways -- only up.
        dragTag(ADJUST_REFERENCE_TAG, 20f, -30f)
        val moved = get().bible().translationList()[0]
        assertEquals(0, moved.referenceShiftX, "kept inside its cell")
        assertTrue(moved.referenceShiftY < 0, "y ${moved.referenceShiftY}")
        assertEquals(position, moved.referencePosition, "moving it is not putting it above or after")
    }

    @Test
    fun `Larger opens the preview across the window with the same handles, and Done or Esc closes it`() =
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            tap(PREVIEW_LARGER_TAG)
            assertEquals(1, countTag(LARGE_PREVIEW_TAG))
            // The large preview's own switch, the last drawn: the column's is behind the scrim.
            onAllNodes(hasTestTag(ADJUST_SWITCH_TAG))[countTag(ADJUST_SWITCH_TAG) - 1].performClick()
            waitForIdle()
            val before = get().bible().marginTop
            // The large preview's handles are the last set drawn: any the column draws are behind it.
            dragTag(adjustMarginTag("top"), 0f, 10f, nth = countTag(adjustMarginTag("top")) - 1)
            val step = get().bible().marginTop - before
            assertTrue(step in 1 until 48, "finer than the small preview's 48 for the same drag, was $step")
            tap(LARGE_PREVIEW_DONE_TAG)
            assertEquals(0, countTag(LARGE_PREVIEW_TAG))
            tap(PREVIEW_LARGER_TAG)
            onAllNodes(hasTestTag(LARGE_PREVIEW_TAG),
                    useUnmergedTree = true)[0].performKeyInput { pressKey(Key.Escape) }
            waitForIdle()
            assertEquals(0, countTag(LARGE_PREVIEW_TAG))
        }

    @Test
    fun `the Songs page adjusts its own margins and the picked language`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.SONGS)
        tap(ADJUST_SWITCH_TAG)
        val before = get().song()
        dragTag(adjustMarginTag("top"), 0f, 10f)
        dragTag(ADJUST_MOVE_TAG, 0f, -76f)
        dragTag(ADJUST_SIZE_TAG, 20f, 20f)
        val after = get().song()
        assertTrue(after.marginTop > before.marginTop)
        assertFalse(after.lyricsAlignment == before.lyricsAlignment,
                "${before.lyricsAlignment} → ${after.lyricsAlignment}")
        assertTrue(after.lyricsFontSize > before.lyricsFontSize)
        // Blocks run number, title, then each language's lyrics: the second language's is the fourth.
        tapAt(adjustBlockTag(3), Offset(160f, 4f))
        dragTag(ADJUST_BLOCK_MOVE_TAG, 0f, 10f)
        val key = songShiftKey(SongStyleElement.LYRICS, lowerThird = false, language = 1)
        assertTrue(get().song().shiftAt(key).second > 0)
    }

    @Test
    fun `the stage layout has no handles and no Larger`() =
        profilesTab(doc(Constants.DISPLAY_MODE_STAGE_MONITOR)) { _ ->
            openCustomizePane(CustomizePane.STAGE_MONITOR)
            assertEquals(0, countTag(PREVIEW_LARGER_TAG))
            assertEquals(0, countTag(ADJUST_SWITCH_TAG))
        }
}
