package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.composables.screenPositionTag
import androidx.compose.ui.test.hasTextExactly
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.MediaSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.RSVP_FLASH_PHRASE
import org.churchpresenter.settings.CaptionReading
import org.churchpresenter.settings.CAPTION_STYLE_RSVP
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Live captions, Subtitles, Q&A and Dictionary: every row writes the profile's own copy of the
 * setting and leaves the document's alone.
 */
@OptIn(ExperimentalTestApi::class)
class ProfileOverlayPagesTest {

    @Test
    fun `captions choose what they show and how the words arrive`() = profilesTab(profileDocument()) { get ->
        openCustomizePane(CustomizePane.CAPTIONS)
        segment("Both").performClick()
        waitForIdle()
        segment("Side by side").performScrollTo().performClick()
        waitForIdle()
        // The second "Translation": the first is what the captions show, this one is which comes first
        segment("Translation", nth = 1).performScrollTo().performClick()
        waitForIdle()
        toggleCheckbox("Highlight the word being spoken")
        toggleCheckbox("Show words still being spoken")
        toggleCheckbox("Show translation still being spoken")
        val stt = get().profile().sttSettings
        assertEquals("both", stt.displayMode)
        assertEquals("side_by_side_inverse", stt.layout)
        assertNotEquals(STTSettings().showWordHighlighting, stt.showWordHighlighting)
        assertNotEquals(STTSettings().showInProgress, stt.showInProgress)
        assertNotEquals(STTSettings().showTranslationInProgress, stt.showTranslationInProgress)
        assertEquals(STTSettings().displayMode, get().sttSettings.displayMode, "the document is untouched")
    }

    @Test
    fun `captions type out at a speed, keep lines and space them`() = profilesTab(
        profileDocument(profile = OutputProfile(sttSettings = STTSettings(dripFeedEnabled = true))),
    ) { get ->
        openCustomizePane(CustomizePane.CAPTIONS)
        onNodeWithText("The speed is in milliseconds per letter.").assertExists()
        stepUp("Type words out as they arrive")
        stepUp("Lines")
        stepUp("Segments kept")
        stepUp("Line spacing")
        val stt = get().profile().sttSettings
        assertEquals(STTSettings().dripFeedSpeed + 10, stt.dripFeedSpeed)
        assertEquals(STTSettings().maxLines + 1, stt.maxLines)
        assertEquals(STTSettings().maxSegments + 1, stt.maxSegments)
        assertEquals(STTSettings().lineSpacing + 10, stt.lineSpacing)
        toggleCheckbox("Type words out as they arrive")
        assertFalse(get().profile().sttSettings.dripFeedEnabled)
    }

    @Test
    fun `captions show at a fixed speed or match the speaker`() = profilesTab(
        profileDocument(profile = OutputProfile(sttSettings = STTSettings(dripFeedEnabled = true))),
    ) { get ->
        openCustomizePane(CustomizePane.CAPTIONS)
        segment("Match the speaker").performScrollTo().performClick()
        waitForIdle()
        assertTrue(get().profile().sttSettings.matchSpeakerPace)
        onNodeWithText("The speed is in milliseconds per letter.").assertDoesNotExist()
        segment("Fixed").performScrollTo().performClick()
        waitForIdle()
        assertFalse(get().profile().sttSettings.matchSpeakerPace)
        onNodeWithText("The speed is in milliseconds per letter.").assertExists()
    }

    @Test
    fun `captions can flash RSVP words or phrases at a words-a-minute speed, and read bionically`() =
        profilesTab(profileDocument()) { get ->
            openCustomizePane(CustomizePane.CAPTIONS)
            segment("RSVP").performScrollTo().performClick()
            waitForIdle()
            segment("Phrase").performScrollTo().performClick()
            waitForIdle()
            stepUp("Speed")
            val reading = { get().profile().sttSettings.reading }
            assertEquals(CAPTION_STYLE_RSVP, reading().style)
            assertEquals(RSVP_FLASH_PHRASE, reading().rsvpWordsPerFlash)
            assertEquals(CaptionReading().rsvpWpm + 10, reading().rsvpWpm)
            segment("Match the speaker").performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Max speed").assertExists()
            toggleCheckbox("Bionic reading")
            assertTrue(reading().bionicReading)
            assertEquals(STTSettings().reading, get().sttSettings.reading, "the document is untouched")
        }

    @Test
    fun `captions carry the shared text, box and position rows`() = profilesTab(profileDocument()) { get ->
        openCustomizePane(CustomizePane.CAPTIONS)
        stepUp("Size")
        inRow("Style", hasClickAction(), 0).performClick()
        waitForIdle()
        onNodeWithContentDescription("Outline").performScrollTo().performClick()
        waitForIdle()
        onNodeWithContentDescription("Text backing").performScrollTo().performClick()
        waitForIdle()
        toggleCheckbox("Shadow")
        stepUp("Opacity")
        tap(screenPositionTag(Constants.TOP_LEFT))
        segment("Right").performScrollTo().performClick()
        waitForIdle()
        val stt = get().profile().sttSettings
        val d = STTSettings()
        assertEquals(d.fontSize + 2, stt.fontSize)
        assertTrue(stt.bold)
        assertNotEquals(d.outline, stt.outline)
        assertNotEquals(d.backdrop, stt.backdrop)
        assertTrue(stt.shadow)
        assertEquals(d.backgroundOpacity + 5, stt.backgroundOpacity)
        assertEquals(Constants.TOP_LEFT, stt.position)
        assertEquals(Constants.RIGHT, stt.horizontalAlignment)
        // The translation's colour is offered only while translations are shown.
        assertTrue(onAllNodes(hasTextExactly("Translation color")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `subtitles say which files they reach, and set text, box, place and lines`() =
        profilesTab(profileDocument()) { get ->
        openCustomizePane(CustomizePane.SUBTITLES)
        onNodeWithText("Applies to SRT/WebVTT subtitle files", substring = true).assertExists()
        stepUp("Size")
        stepUp("Opacity")
        tap(screenPositionTag(Constants.TOP_CENTER))
        stepUp("Lines")
        stepUp("Line spacing")
        val media = get().profile().mediaSettings
        val d = MediaSettings()
        assertEquals(d.fontSize + 2, media.fontSize)
        assertEquals(d.backgroundOpacity + 5, media.backgroundOpacity)
        assertEquals(Constants.TOP_CENTER, media.position)
        assertEquals(d.maxLines + 1, media.maxLines)
        assertEquals(d.lineSpacing + 10, media.lineSpacing)
    }

    @Test
    fun `Q&A sets the question's look and its code's colours`() = profilesTab(profileDocument()) { get ->
        openCustomizePane(CustomizePane.QA)
        stepUp("Size")
        stepDown("Opacity")
        tap(screenPositionTag(Constants.CENTER))
        segment("Left").performScrollTo().performClick()
        waitForIdle()
        recolor(QASettings().qrForegroundColor, "#112233")
        stepDown("Opacity", times = 1)
        val qa = get().profile().qaSettings
        val d = QASettings()
        assertEquals(d.fontSize + 2, qa.fontSize)
        assertTrue(qa.backgroundOpacity < d.backgroundOpacity)
        assertEquals(Constants.CENTER, qa.position)
        assertEquals(Constants.LEFT, qa.horizontalAlignment)
        assertEquals("#112233", qa.qrForegroundColor)
    }

    @Test
    fun `the dictionary shows each part or not, and styles the part picked on its strip`() =
        profilesTab(profileDocument()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY)
            toggleCheckbox("KJV usage")
            assertFalse(get().profile().dictionarySettings.showKjvUsage)
            stepUp("Size")
            inRow("Style", hasClickAction(), 0).performClick()
            waitForIdle()
            toggleCheckbox("Shadow")
            val d = DictionarySettings()
            assertEquals(d.wordFontSize + 2, get().profile().dictionarySettings.wordFontSize)
            assertEquals(!d.wordBold, get().profile().dictionarySettings.wordBold)
            tap(dictionaryPartTag(DictionaryPart.REFERENCE))
            stepUp("Size")
            assertEquals(d.referenceFontSize + 2, get().profile().dictionarySettings.referenceFontSize)
            tap(dictionaryPartTag(DictionaryPart.DEFINITION))
            onNodeWithContentDescription("Outline").performScrollTo().performClick()
            waitForIdle()
            assertNotEquals(d.definitionOutline, get().profile().dictionarySettings.definitionOutline)
            tap(dictionaryPartTag(DictionaryPart.KJV_USAGE))
            stepUp("Size")
            assertEquals(d.kjvUsageFontSize + 2, get().profile().dictionarySettings.kjvUsageFontSize)
            // The usage line has no face, style or shadow of its own.
            assertTrue(onAllNodes(hasTextExactly("Font")).fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `the dictionary card and its fades`() = profilesTab(profileDocument()) { get ->
        openCustomizePane(CustomizePane.DICTIONARY)
        stepDown("Card opacity")
        toggleCheckbox("Fade in")
        stepUp("Duration")
        val ds = get().profile().dictionarySettings
        val d = DictionarySettings()
        assertTrue(ds.cardBackgroundOpacity < d.cardBackgroundOpacity)
        assertEquals(!d.fadeIn, ds.fadeIn)
        assertTrue(ds.transitionDuration > d.transitionDuration)
    }
}
