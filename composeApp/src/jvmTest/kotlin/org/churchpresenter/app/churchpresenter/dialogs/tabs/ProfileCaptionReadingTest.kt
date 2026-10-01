package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.CAPTION_BOX_BAND
import org.churchpresenter.settings.CAPTION_BREAK_SEGMENT
import org.churchpresenter.settings.CAPTION_BREAK_SENTENCE
import org.churchpresenter.settings.CAPTION_STYLE_POP_ON
import org.churchpresenter.settings.CAPTION_STYLE_TICKER
import org.churchpresenter.settings.CaptionReading
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.STTSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The captions page's reading, presentation, box, text and two-language rows: each one is pressed
 * and the profile is checked for what it wrote, and rows that do not apply are checked to be gone.
 */
@OptIn(ExperimentalTestApi::class)
class ProfileCaptionReadingTest {

    private val d = CaptionReading()

    private fun captions(stt: STTSettings = STTSettings()) =
        profileDocument(profile = OutputProfile(sttSettings = stt))

    private fun androidx.compose.ui.test.ComposeUiTest.isShown(label: String) =
        onAllNodes(hasTextExactly(label), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the reading switches turn on, and their values step`() = profilesTab(captions()) { get ->
        openCustomizePane(CustomizePane.CAPTIONS)
        toggleCheckbox("Clear after silence")
        stepUp("Clear after silence")
        stepUp("Fade-out time")
        toggleCheckbox("Reading speed limit")
        stepUp("Reading speed limit")
        toggleCheckbox("Slide lines up")
        stepUp("Slide time")
        toggleCheckbox("Dim older lines")
        stepDown("Each older line fainter by")
        stepUp("Never fainter than")
        val r = get().profile().sttSettings.reading
        assertTrue(r.clearAfterSilence && r.readingSpeedLimit && r.rollUp && r.dimOlderLines)
        assertEquals(d.clearAfterSeconds + 1, r.clearAfterSeconds)
        assertEquals(d.clearFadeMillis + 100, r.clearFadeMillis)
        assertEquals(d.readingSpeedCps + 1, r.readingSpeedCps)
        assertEquals(d.rollUpMillis + 50, r.rollUpMillis)
        assertEquals(d.dimStepPercent - 5, r.dimStepPercent)
        assertEquals(d.dimFloorPercent + 5, r.dimFloorPercent)
    }

    @Test
    fun `lines break by phrase or sentence, with a blank line between and a width`() = profilesTab(captions()) { get ->
        openCustomizePane(CustomizePane.CAPTIONS)
        assertFalse(isShown("Blank line between"), "only offered once lines break")
        segment("Phrase").performScrollTo().performClick()
        waitForIdle()
        assertEquals(CAPTION_BREAK_SEGMENT, get().profile().sttSettings.reading.lineBreaks)
        segment("Sentence").performScrollTo().performClick()
        waitForIdle()
        toggleCheckbox("Blank line between")
        stepUp("Max characters per line", times = 2)
        val r = get().profile().sttSettings.reading
        assertEquals(CAPTION_BREAK_SENTENCE, r.lineBreaks)
        assertTrue(r.blankLineBetween)
        assertEquals(2, r.maxCharsPerLine)
    }

    @Test
    fun `each presentation offers only the rows that apply to it`() = profilesTab(captions()) { get ->
        openCustomizePane(CustomizePane.CAPTIONS)
        assertTrue(isShown("Slide lines up"))
        segment("Pop-on").performScrollTo().performClick()
        waitForIdle()
        assertEquals(CAPTION_STYLE_POP_ON, get().profile().sttSettings.reading.style)
        assertFalse(isShown("Slide lines up"), "a pop-on block never slides")
        segment("Ticker").performScrollTo().performClick()
        waitForIdle()
        stepUp("Ticker speed")
        val r = get().profile().sttSettings.reading
        assertEquals(CAPTION_STYLE_TICKER, r.style)
        assertEquals(d.tickerSpeed + 10, r.tickerSpeed)
        assertFalse(isShown("Start a new line for each"), "a ticker is one line")
        assertFalse(isShown("Max characters per line"))
    }

    @Test
    fun `the box can be a band that touches the edge or floats`() = profilesTab(captions()) { get ->
        openCustomizePane(CustomizePane.CAPTIONS)
        assertFalse(isShown("Touch the screen edge"), "only offered for a band")
        segment("Full-width band").performScrollTo().performClick()
        waitForIdle()
        toggleCheckbox("Touch the screen edge")
        val stt = get().profile().sttSettings
        assertEquals(CAPTION_BOX_BAND, stt.boxShape)
        assertFalse(stt.bandTouchesEdge)
    }

    @Test
    fun `text gains capitals and spacing, and the captions get margins`() = profilesTab(captions()) { get ->
        openCustomizePane(CustomizePane.CAPTIONS)
        toggleCheckbox("All caps")
        stepUp("Letter spacing")
        stepUp("Word spacing", times = 2)
        typeInRow("Margins", 10, nth = 0)
        typeInRow("Margins", 20, nth = 1)
        typeInRow("Margins", 30, nth = 2)
        typeInRow("Margins", 40, nth = 3)
        val stt = get().profile().sttSettings
        assertTrue(stt.transcriptAllCaps)
        assertEquals(1, stt.letterSpacing)
        assertEquals(2, stt.wordSpacing)
        assertEquals(listOf(10, 20, 30, 40), listOf(stt.marginTop, stt.marginBottom, stt.marginLeft, stt.marginRight))
    }

    @Test
    fun `with both languages they can interleave, swap, and get a box each`() = profilesTab(
        captions(STTSettings(displayMode = "both")),
    ) { get ->
        openCustomizePane(CustomizePane.CAPTIONS)
        segment("Interleaved").performScrollTo().performClick()
        waitForIdle()
        assertFalse(isShown("Each language in its own box"), "an interleaved caption is one block")
        segment("Translation", nth = 1).performScrollTo().performClick()
        waitForIdle()
        assertEquals("interleaved_inverse", get().profile().sttSettings.layout)
        segment("Stacked").performScrollTo().performClick()
        waitForIdle()
        assertEquals("stacked_inverse", get().profile().sttSettings.layout, "the order is kept across arrangements")
        toggleCheckbox("Each language in its own box")
        stepUp("Translation size")
        toggleCheckbox("Translation in all caps")
        toggleCheckbox("Bold translation")
        toggleCheckbox("Italic translation")
        val stt = get().profile().sttSettings
        assertTrue(stt.separateLanguageBoxes && stt.translationAllCaps && stt.translationBold && stt.translationItalic)
        assertEquals(1, stt.translationFontSize)
    }

    @Test
    fun `translation rows are offered only while a translation is shown`() = profilesTab(captions()) { _ ->
        openCustomizePane(CustomizePane.CAPTIONS)
        assertFalse(isShown("Translation size"))
        assertFalse(isShown("Show first"))
    }
}
