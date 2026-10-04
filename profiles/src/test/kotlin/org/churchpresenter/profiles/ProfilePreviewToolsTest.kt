package org.churchpresenter.profiles

import org.churchpresenter.presenter.SongStyleElement
import org.churchpresenter.presenter.SongStyleTarget
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.presenter.referenceShiftFor
import org.churchpresenter.presenter.withReferenceShift
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The follow-ups to the redesign: Applies to offering only what a profile shows, the song strip's
 * Language 1, picking the reference from the preview, Reset positions, Checker on every page, the
 * element row on one line, and the card listing what a profile changes from the defaults.
 */
@OptIn(ExperimentalTestApi::class)
class ProfilePreviewToolsTest {

    private val three = BibleSettings(
        translations = listOf(
            BibleTranslationSettings(fileName = "kjv.spb", customAbbreviation = "KJV"),
            BibleTranslationSettings(fileName = "rst.spb", customAbbreviation = "RST"),
            BibleTranslationSettings(fileName = "niv.spb", customAbbreviation = "NIV"),
        ),
    )

    private fun AppSettings.stack() = bible().translationList()

    @Test
    fun `Applies to offers only the translations this profile shows`() =
        profilesTab(profileDocument(bible = three, profile = OutputProfile(bibleTranslations = listOf(0, 2)))) { _ ->
            openCustomizePane(CustomizePane.BIBLE)
            assertEquals(1, countTag(translationChipTag(0)))
            assertEquals(0, countTag(translationChipTag(1)), "RST is not on this screen")
            assertEquals(1, countTag(translationChipTag(2)))
        }

    @Test
    fun `the song strip names the first language beside All, and its size is its own`() =
        profilesTab(profileDocument(song = SongSettings(lyricsFontSize = 70))) { get ->
            openCustomizePane(CustomizePane.SONGS)
            assertEquals(1, countTag(SONG_ALL_LANGUAGES_TAG))
            tap(songLanguageTag(SongStyleLanguage.PRIMARY))
            typeInRow("Size", 50)
            assertEquals(50, get().song().lyricsFontSize)
            val all = get().song().allLanguagesStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN)
            assertEquals(70, all.fontSize)
        }

    @Test
    fun `the element row stays on one line`() = profilesTab(profileDocument()) { _ ->
        openCustomizePane(CustomizePane.SONGS)
        val tops = CustomizeElement.entries
            .map { elementChipTag(it.name) }
            .filter { countTag(it) > 0 }
            .map { onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(tops.size >= 4, "$tops")
        assertEquals(1, tops.distinct().size, "every element on one line: $tops")
    }

    @Test
    fun `clicking the reference points the rows at it, and Reset puts a lost one back`() =
        profilesTab(profileDocument(bible = three)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            tap(ADJUST_SWITCH_TAG)
            tap(ADJUST_REFERENCE_TAG)
            typeInRow("Size", 44)
            assertTrue(get().stack().all { it.referenceFontSize == 44 }, "the reference, under All")
            dragTag(ADJUST_REFERENCE_TAG, 0f, -20f)
            assertTrue(get().stack().first().referenceShiftFor(false).second < 0)
            tap(RESET_POSITIONS_TAG)
            assertTrue(get().stack().all { it.referenceShiftFor(false) == (0 to 0) })
        }

    @Test
    fun `Reset on the Reference rows also takes its move back`() {
        val moved = three.copy(translations = three.translations.map { it.withReferenceShift(false, 0, 250) })
        profilesTab(profileDocument(bible = moved)) { get ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_REFERENCE)
            onNodeWithTag(elementChipTag(CustomizeElement.BIBLE_REFERENCE.name)).performScrollTo().performClick()
            waitForIdle()
            // The only group away from its defaults: the Text group, by the reference's move alone.
            onAllNodes(hasTextExactly("Reset to defaults") and hasClickAction())[0].performScrollTo().performClick()
            waitForIdle()
            assertTrue(get().stack().all { it.referenceShiftFor(false) == (0 to 0) })
        }
    }

    @Test
    fun `the large preview carries Reset positions on its sample row`() =
        profilesTab(profileDocument(bible = three)) { _ ->
        openCustomizePane(CustomizePane.BIBLE)
        assertEquals(1, countTag(RESET_POSITIONS_TAG))
        tap(PREVIEW_LARGER_TAG)
        assertEquals(2, countTag(RESET_POSITIONS_TAG))
    }

    @Test
    fun `Checker is offered on the pages with no text sample too`() =
        profilesTab(profileDocument()) { _ ->
            listOf(CustomizePane.CAPTIONS, CustomizePane.BACKGROUND, CustomizePane.DICTIONARY).forEach { pane ->
                openCustomizePane(pane)
                tap(previewBackgroundTag(PreviewBackgroundMode.CHECKER))
                assertEquals(1, countTag(previewBackgroundTag(PreviewBackgroundMode.CHECKER)), "$pane")
            }
        }

    @Test
    fun `the defaults card lists a change, alike on every translation as one row, and Revert puts it back`() {
        val sized = three.copy(translations = three.translations.map { it.copy(textFontSize = 50) })
        profilesTab(profileDocument(bible = sized)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            assertEquals(1, countTag(DEFAULTS_CARD_TAG))
            assertEquals(1, onAllNodes(hasText("Text font size · All")).fetchSemanticsNodes().size)
            onAllNodes(hasTestTag(REVERT_LINK_TAG) and hasAnyAncestorTag(DEFAULTS_CARD_TAG))[0].performClick()
            waitForIdle()
            val default = BibleTranslationSettings().textFontSize
            assertTrue(get().stack().all { it.textFontSize == default })
            assertEquals(1, onAllNodes(hasText("No changes from defaults.")).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `a single change is its own row, named after its translation`() {
        val one = three.copy(
            translations = three.translations.mapIndexed { i, t -> if (i == 1) t.copy(textFontSize = 40) else t },
        )
        profilesTab(profileDocument(bible = one)) { _ ->
            openCustomizePane(CustomizePane.BIBLE)
            assertEquals(1, countTag(changeRowTag("bibleSettings.translations[rst.spb].textFontSize")))
        }
    }

    @Test
    fun `a linked profile keeps its master's card instead`() {
        val doc = profileDocument()
        val master = doc.profile()
        val follower = OutputProfile(id = "youth", name = "Youth", parentId = master.id)
        val linked = doc.copy(
            projectionSettings = doc.projectionSettings.copy(
                outputProfiles = doc.projectionSettings.outputProfiles + follower,
            ),
        )
        profilesTab(linked) { _ ->
            tap(profileRowTag("youth"))
            assertEquals(0, countTag(DEFAULTS_CARD_TAG))
            assertEquals(1, countTag(CONTEXT_CARD_TAG))
        }
    }

    @Test
    fun `a new profile starts on its own default background`() = profilesTab(profileDocument()) { get ->
        tap(NEW_PROFILE_TAG)
        val added = get().projectionSettings.outputProfiles.last()
        assertEquals(Constants.BACKGROUND_DEFAULT, added.backgroundSettings.bibleBackground.backgroundType)
        assertTrue("BIBLE" in added.backgroundOverrides)
    }
}

private fun hasAnyAncestorTag(tag: String) = hasAnyAncestor(hasTestTag(tag))
