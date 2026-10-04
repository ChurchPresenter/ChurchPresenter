package org.churchpresenter.profiles

import org.churchpresenter.presenter.SongStyleElement
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.TextBoxOverflow
import org.churchpresenter.settings.boxAt
import org.churchpresenter.settings.textBoxKey
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The Profiles controls this work added: text boxes on every page that draws text, the margins past
 * 500, the language gap and fit, a content region that leaves the background alone, and a content
 * background that remembers its own picture while it follows the profile's.
 *
 * Every control is driven and its written value read back from the document.
 */
@OptIn(ExperimentalTestApi::class)
class ProfilesTextBoxTest {

    private val kjv = BibleTranslationSettings(fileName = "kjv.spb", customAbbreviation = "KJV")

    private fun doc(profile: OutputProfile = OutputProfile()) =
        profileDocument(bible = BibleSettings(translations = listOf(kjv)), profile = profile)

    private val twoLanguages = OutputProfile(songMode = Constants.SONG_LANG_BOTH)

    // ── Songs ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `turning on an element's text box starts it near where the element sits`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
        toggleCheckbox("Text box")
        val box = get().song().layoutExtras.textBoxes.boxAt("TITLE")
        assertTrue(box.enabled)
        assertEquals(defaultSongBox(SongStyleElement.TITLE, null).copy(enabled = true), box)
    }

    @Test
    fun `a box's vertical place, overflow and rectangle are written as picked`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
        toggleCheckbox("Text box")
        chooseSegment("Bottom")
        chooseSegment("Cut off")
        typeInRow("Box position", 30, nth = 0)
        val box = get().song().layoutExtras.textBoxes.boxAt("NUMBER")
        assertEquals(Constants.BOTTOM, box.vertical)
        assertEquals(TextBoxOverflow.CUT, box.overflow)
        assertEquals(30f, box.xPercent)
    }

    @Test
    fun `fill the box is offered while text shrinks to fit`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
        toggleCheckbox("Text box")
        chooseSegment("Fill the box")
        assertTrue(get().song().layoutExtras.textBoxes.boxAt("TITLE").fill)
    }

    @Test
    fun `the page's box options are written for every box`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
        toggleCheckbox("Text box")
        chooseSegment("Inside margins")
        toggleCheckbox("Keep clear of other boxes")
        toggleCheckbox("Snap to guides")
        val options = get().song().layoutExtras.textBoxOptions
        assertTrue(options.insideMargins)
        assertTrue(options.keepClear)
        assertFalse(options.snap)
    }

    @Test
    fun `turning a box off keeps its rectangle for next time`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
        toggleCheckbox("Text box")
        typeInRow("Box position", 25, nth = 0)
        toggleCheckbox("Text box")
        val box = get().song().layoutExtras.textBoxes.boxAt("TITLE")
        assertFalse(box.enabled)
        assertEquals(25f, box.xPercent)
    }

    @Test
    fun `with two languages under All, the lyrics' box asks for a language first`() =
        profilesTab(doc(twoLanguages)) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onNodeWithText("Pick a language above", substring = true).performScrollTo()
        }

    @Test
    fun `the gap between languages and how they are fitted are written`() = profilesTab(doc(twoLanguages)) { get ->
        openCustomizePane(CustomizePane.SONGS)
        onNodeWithTag(SONG_LANGUAGE_GAP_TAG).performScrollTo().performTextReplacement("40")
        chooseSegment("Each on its own")
        val extras = get().song().layoutExtras
        assertEquals(40, extras.languageGap)
        assertTrue(extras.fitLanguagesSeparately)
    }

    @Test
    fun `a margin can go past 500 for half the screen`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.SONGS)
        onNodeWithTag(MARGIN_RIGHT_TAG).performScrollTo().performTextReplacement("960")
        assertEquals(960, get().song().marginRight)
    }

    @Test
    fun `the content region can leave the background filling the screen`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.SONGS)
        toggleCheckbox("Background follows Content Region")
        assertFalse(get().song().layoutExtras.contentRegion.movesBackground)
    }

    // ── Bible ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a translation's verse text gets a box of its own`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
        toggleCheckbox("Text box")
        val box = get().bible().textBoxes.boxAt(textBoxKey("TEXT", lowerThird = false, language = "kjv.spb"))
        assertTrue(box.enabled)
    }

    // ── Single-form pages ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the Q and A page's item row picks which item the box rows edit`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.QA)
        chooseSegment("QR code")
        toggleCheckbox("Text box")
        val boxes = get().asRendered().qaSettings.textBoxes
        assertTrue(boxes.boxAt("QR_CODE").enabled)
        assertFalse(boxes.boxAt("QUESTION").enabled)
    }

    @Test
    fun `with no box on, Adjust says there is nothing to move yet`() = profilesTab(doc()) { _ ->
        openCustomizePane(CustomizePane.CAPTIONS)
        tap(ADJUST_SWITCH_TAG)
        assertEquals(1, countTag(ADJUST_NO_BOXES_TAG))
        toggleCheckbox("Text box")
        assertEquals(0, countTag(ADJUST_NO_BOXES_TAG))
        assertEquals(1, countTag(adjustBoxTag("TRANSCRIPT")))
    }

    // ── Moving and resizing on the preview ──────────────────────────────────────────────────────

    @Test
    fun `a box is moved by its body and resized by its handles`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
        toggleCheckbox("Text box")
        toggleCheckbox("Snap to guides")
        tap(ADJUST_SWITCH_TAG)
        val start = get().song().layoutExtras.textBoxes.boxAt("TITLE")
        dragTag(ADJUST_BOX_MOVE_TAG, 0f, 20f)
        val moved = get().song().layoutExtras.textBoxes.boxAt("TITLE")
        assertTrue(moved.yPercent > start.yPercent, "moved down: ${start.yPercent} → ${moved.yPercent}")
        dragTag(adjustBoxGripTag(BoxGrip.RIGHT), -30f, 0f)
        val resized = get().song().layoutExtras.textBoxes.boxAt("TITLE")
        assertTrue(
            resized.widthPercent < moved.widthPercent,
            "narrower: ${moved.widthPercent} → ${resized.widthPercent}",
        )
    }

    @Test
    fun `Reset positions turns the page's boxes off, keeping them`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
        toggleCheckbox("Text box")
        tap(RESET_POSITIONS_TAG)
        val box = get().song().layoutExtras.textBoxes.boxAt("TITLE")
        assertFalse(box.enabled)
        assertNotEquals(TextBox(), box)
    }

    // ── A content background remembers its own ──────────────────────────────────────────────────

    @Test
    fun `Own, then Profile default, then Own again brings the own picture back`() {
        val own = BackgroundConfig(backgroundType = Constants.BACKGROUND_COLOR, backgroundColor = "#123456")
        val profile = OutputProfile(
            backgroundOverrides = setOf(BackgroundScope.SONG.name),
            backgroundSettings = BackgroundSettings(songBackground = own),
        )
        profilesTab(doc(profile)) { get ->
            openCustomizePane(CustomizePane.SONGS)
            tap(BG_PROFILE_DEFAULT_TAG)
            assertEquals(BackgroundScope.SONG.inheritType, get().backgroundFor(BackgroundScope.SONG).backgroundType)
            tap(BG_OWN_TAG)
            val back = get().backgroundFor(BackgroundScope.SONG)
            assertEquals(Constants.BACKGROUND_COLOR, back.backgroundType)
            assertEquals("#123456", back.backgroundColor)
        }
    }
}
