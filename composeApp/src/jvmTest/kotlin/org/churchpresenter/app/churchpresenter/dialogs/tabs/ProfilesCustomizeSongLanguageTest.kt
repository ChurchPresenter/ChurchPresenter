@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.SongTextStyle
import org.churchpresenter.settings.SongTranslationSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The second-language switch inside the Song pane.
 *
 * A song carries its lyrics and its title twice, and each half has a profile of its own, so a
 * screen showing both languages can be given a different look for the second. The switch appears
 * only where there is a second language on this profile *and* an element that has a second profile
 * for it -- everywhere else it would offer a choice with one answer.
 *
 * Ported from `ProjectionCustomizeSongLanguageTest`.
 */
class ProfilesCustomizeSongLanguageTest {

    private fun doc(
        songMode: String = Constants.SONG_LANG_BOTH,
        mode: String = Constants.DISPLAY_MODE_FULLSCREEN,
    ) = profileDocument(
        mode = mode,
        profile = OutputProfile(songMode = songMode),
        song = SongSettings(
            lyricsFontSize = 61,
            // The second language styles itself; the first draws from SongSettings directly.
            translations = listOf(
                SongTranslationSettings(overrideStyle = true),
                SongTranslationSettings(
                    overrideStyle = true,
                    lyrics = SongTextStyle(fontSize = 47),
                ),
            ),
        ),
    )


    /**
     * The pane's own language switch, by position.
     *
     * The profile header carries a song-language picker that lists the same "Language N" captions,
     * so a bare text match finds two nodes. The pane is composed after the header, so the switch is
     * the last of them.
     */
    private fun SkikoComposeUiTest.languageSwitch(label: String) =
        onAllNodesWithText(label).onLast()

    /** How many nodes carry [label] -- the header's picker contributes one of them. */
    private fun SkikoComposeUiTest.countOf(label: String) =
        onAllNodesWithText(label).fetchSemanticsNodes().size

    @Test
    fun `a bilingual profile offers the language switch on the lyrics`() {
        profilesTab(doc()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            // Two of each: the header's picker and the pane's switch.
            assertEquals(2, countOf("Language 1"))
            assertEquals(2, countOf("Language 2"))
        }
    }

    @Test
    fun `a single-language profile offers no such switch`() {
        profilesTab(doc(songMode = Constants.SONG_LANG_PRIMARY)) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            // One language, so the switch would offer a choice with one answer -- only the
            // header's picker still names the second.
            assertEquals(1, countOf("Language 2"))
        }
    }

    /** The size the panel shows for [language], read the same way the panel reads it. */
    private fun org.churchpresenter.settings.AppSettings.shownSize(
        language: SongStyleLanguage,
    ): Int = song().elementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN, language).fontSize

    @Test
    fun `the switch moves the controls onto the second language's own profile`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            languageSwitch("Language 2").performScrollTo().performClick()
            waitForIdle()

            val shown = get().shownSize(SongStyleLanguage.SECONDARY)
            retypeNumberField(shown, shown + 5)

            assertEquals(shown + 5, get().shownSize(SongStyleLanguage.SECONDARY), "the second moved")
            assertEquals(61, get().song().lyricsFontSize, "and the first was left alone")
        }
    }

    @Test
    fun `switching back returns the controls to the first language`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            languageSwitch("Language 2").performScrollTo().performClick()
            waitForIdle()
            languageSwitch("Language 1").performScrollTo().performClick()
            waitForIdle()

            val second = get().shownSize(SongStyleLanguage.SECONDARY)
            retypeNumberField(61, 66)

            assertEquals(66, get().song().lyricsFontSize)
            assertEquals(second, get().shownSize(SongStyleLanguage.SECONDARY), "the second stayed put")
        }
    }

    /**
     * Keyed on the language: one set of controls stands for two stored profiles, and without that
     * key Compose keeps the subtree across the switch and hands each control the state -- and the
     * write-back lambda -- of the profile that held its slot before.
     */
    @Test
    fun `the controls are rebuilt across a language switch rather than reused`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            languageSwitch("Language 2").performScrollTo().performClick()
            waitForIdle()

            // The field shows the second language's own size, not the first's carried over.
            assertNumberFieldShows(
                get().shownSize(SongStyleLanguage.SECONDARY),
                "the second language's own size",
            )
            assertEquals(61, get().song().lyricsFontSize, "nothing was written by the switch itself")
        }
    }

    @Test
    fun `the title also carries a second language`() {
        profilesTab(doc()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
            assertEquals(2, countOf("Language 2"), "the title has a second language too")
        }
    }

    @Test
    fun `an element with no second profile is offered no switch`() {
        profilesTab(doc()) { _ ->
            // The song number is one number whichever language is showing.
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            assertEquals(1, countOf("Language 2"), "only the header's picker names it")
        }
    }
}
