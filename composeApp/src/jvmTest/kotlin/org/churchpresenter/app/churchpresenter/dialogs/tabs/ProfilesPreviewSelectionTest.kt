@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The preview draws what the profile shows, not the whole library.
 *
 * The Bible stage used to render `bibleSettings.translationList()` outright and the Song stage fell
 * back to the song-level language setting, so narrowing an output to one translation or one
 * language changed the screen and left the picture beside the controls showing all of them. The
 * output was right the whole time; only the preview lied, which is the worst way round -- the
 * preview is the thing an operator is looking at while deciding.
 *
 * Counted rather than matched one-for-one: every translation falls back to the same English sample
 * here, because the preview reads no `.spb`, so the number of copies of that sentence *is* the
 * number of translations being drawn.
 */
class ProfilesPreviewSelectionTest {

    private val sampleVerse = "For God so loved the world, that he gave his only begotten Son."

    /** Three translations, so a subset is distinguishable from the whole stack. */
    private fun doc(profile: OutputProfile) = profileDocument(
        profile = profile,
        bible = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = "kjv.spb"),
                BibleTranslationSettings(fileName = "niv.spb"),
                BibleTranslationSettings(fileName = "esv.spb"),
            ),
        ),
    )

    private fun SkikoComposeUiTest.versesDrawn() =
        onAllNodesWithText(sampleVerse, substring = true).fetchSemanticsNodes().size

    @Test
    fun `an untouched profile previews the whole stack`() {
        profilesTab(doc(OutputProfile())) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            // Empty selection means all of them, including any added later.
            assertEquals(3, versesDrawn())
        }
    }

    @Test
    fun `a profile narrowed to one translation previews one`() {
        profilesTab(doc(OutputProfile(bibleTranslations = listOf(1)))) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            assertEquals(1, versesDrawn())
        }
    }

    @Test
    fun `a profile narrowed to two previews two`() {
        profilesTab(doc(OutputProfile(bibleTranslations = listOf(0, 2)))) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            assertEquals(2, versesDrawn())
        }
    }

    @Test
    fun `positions past the end of the stack are ignored rather than drawn`() {
        profilesTab(doc(OutputProfile(bibleTranslations = listOf(0, 9)))) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            assertEquals(1, versesDrawn(), "a settings file can outlive the translations it names")
        }
    }

    @Test
    fun `a selection with nothing left still previews the type`() {
        profilesTab(doc(OutputProfile(bibleTranslations = listOf(7, 8)))) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            // Something has to carry the type: the pane is where it is set, and type cannot be
            // judged on a blank.
            assertEquals(1, versesDrawn())
        }
    }

    // ── Songs ───────────────────────────────────────────────────────────────────────────────────

    /**
     * The second language's line in the sample, which only a bilingual output draws.
     *
     * The Russian, not the title: "Amazing Grace" is the song's title and is drawn whatever
     * languages the output carries, so it would prove nothing either way.
     */
    private val secondLanguageLine = "О, благодать!"

    private fun SkikoComposeUiTest.secondLanguageDrawn() =
        onAllNodesWithText(secondLanguageLine, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a profile showing both languages previews both`() {
        profilesTab(doc(OutputProfile(songMode = Constants.SONG_LANG_BOTH))) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            assertEquals(true, secondLanguageDrawn())
        }
    }

    @Test
    fun `a profile narrowed to one language previews one`() {
        profilesTab(doc(OutputProfile(songMode = Constants.SONG_LANG_PRIMARY))) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            assertEquals(false, secondLanguageDrawn(), "the second language is not on this output")
        }
    }

    @Test
    fun `a profile with songs switched off offers no Songs styling`() {
        profilesTab(doc(OutputProfile(songMode = Constants.SONG_LANG_OFF))) { _ ->
            // Nothing of the songs reaches this output, so there is nothing to style: the tab goes,
            // and comes back when songs are switched on under Content or Sources.
            onNodeWithTag(railTag(CustomizePane.SONGS.name)).assertDoesNotExist()
        }
    }
}
