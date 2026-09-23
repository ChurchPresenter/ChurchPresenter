@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Bible-translation picker in the profile header.
 *
 * Which translations a profile shows is stored as [OutputProfile.bibleTranslations], a list of
 * positions in the configured stack, where **empty means all of them** -- so a translation added
 * later appears on every profile rather than having to be ticked on each one. Most of what is
 * tested here follows from that one normalisation:
 *
 *  * it makes "none selected" unrepresentable as a selection, so showing none of them has to be
 *    stored as `bibleMode = SONG_LANG_OFF` instead, which is the same statement about the profile.
 *    Before that, unticking the last box wrote an empty list that read straight back as *all* and
 *    every box silently re-ticked;
 *  * and a **position** is only meaningful against the stack it was stored for. One past the end of
 *    the current stack is ignored rather than counted, and a selection left with none of its
 *    positions surviving shows nothing rather than reading as the empty "all of them".
 *
 * Ported from `ProjectionSettingsTabTranslationPickerTest`, which reached the same widget through
 * the Projection tab's Content Outputs dialog. It sits in the profile header now, always on screen,
 * so there is no dialog to open first.
 *
 * The controls are addressed by test tag, not caption: see [TranslationPickerTags]. The cleared
 * trigger reads "None", which is also what an unassigned dropdown reads, so text was never a safe
 * way to find it.
 */
class ProfileTranslationPickerTest {

    private fun threeTranslations() = profileDocument(
        bible = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = "kjv.spb"),
                BibleTranslationSettings(fileName = "niv.spb"),
                BibleTranslationSettings(fileName = "esv.spb"),
            ),
        ),
    )

    private fun SkikoComposeUiTest.openPicker() {
        // No performScrollTo: the picker lives in the profile header, which is fixed above the
        // scrolling panes, so there is no scrollable ancestor to ask.
        onNodeWithTag(TranslationPickerTags.BIBLE.trigger).performClick()
        waitForIdle()
    }

    private fun SkikoComposeUiTest.toggleTranslation(index: Int) {
        onNodeWithTag(TranslationPickerTags.BIBLE.row(index)).performClick()
        waitForIdle()
    }

    private fun SkikoComposeUiTest.master() = onNodeWithTag(TranslationPickerTags.BIBLE.master)

    private fun AppSettings.picked(): List<Int> = profile().bibleTranslations

    @Test
    fun `an untouched profile shows all of them`() {
        profilesTab(threeTranslations()) { get ->
            openPicker()
            assertEquals(emptyList(), get().picked(), "empty means all of them")
            master().assertIsOn()
        }
    }

    @Test
    fun `unticking narrows the selection to what is left`() {
        profilesTab(threeTranslations()) { get ->
            openPicker()
            toggleTranslation(0)

            assertEquals(listOf(1, 2), get().picked(), "the unticked one is gone from the selection")
        }
    }

    @Test
    fun `unticking the last translation switches scripture off`() {
        profilesTab(threeTranslations()) { get ->
            openPicker()
            toggleTranslation(0)
            toggleTranslation(1)
            assertEquals(listOf(2), get().picked())

            toggleTranslation(2)

            // Showing none of them is the same statement as switching scripture off. Storing it as
            // an empty selection instead is what used to read back as "all" and re-tick every box.
            assertEquals(Constants.SONG_LANG_OFF, get().profile().bibleMode)
            master().assertIsOff()
            onNodeWithText("0 of 3 translations enabled").assertExists()
        }
    }

    @Test
    fun `ticking one back on switches scripture on with just that one`() {
        profilesTab(threeTranslations()) { get ->
            openPicker()
            toggleTranslation(0)
            toggleTranslation(1)
            toggleTranslation(2)
            assertEquals(Constants.SONG_LANG_OFF, get().profile().bibleMode)

            toggleTranslation(1)

            assertEquals(Constants.SONG_LANG_BOTH, get().profile().bibleMode, "scripture comes back on")
            assertEquals(listOf(1), get().picked(), "with only the one that was ticked")
        }
    }

    @Test
    fun `the master row switches the whole profile off and back on`() {
        profilesTab(threeTranslations()) { get ->
            openPicker()
            master().performClick()
            waitForIdle()
            assertEquals(Constants.SONG_LANG_OFF, get().profile().bibleMode)

            master().performClick()
            waitForIdle()
            assertEquals(Constants.SONG_LANG_BOTH, get().profile().bibleMode)
        }
    }

    @Test
    fun `the menu stays open across a toggle`() {
        profilesTab(threeTranslations()) { _ ->
            openPicker()
            toggleTranslation(0)

            onNodeWithTag(TranslationPickerTags.BIBLE.row(0)).assertExists("the menu must still be open")
        }
    }

    @Test
    fun `a position past the end of the stack is ignored rather than counted`() {
        val doc = profileDocument(
            bible = BibleSettings(translations = listOf(BibleTranslationSettings(fileName = "kjv.spb"))),
            profile = OutputProfile(bibleTranslations = listOf(0, 7)),
        )
        profilesTab(doc) { _ ->
            openPicker()
            // One real translation, one stale position: the stale one must not be counted.
            onNodeWithText("1 of 1 translations enabled").assertExists()
        }
    }

    @Test
    fun `a selection whose positions have all gone shows nothing rather than everything`() {
        val doc = profileDocument(
            bible = BibleSettings(translations = listOf(BibleTranslationSettings(fileName = "kjv.spb"))),
            profile = OutputProfile(bibleTranslations = listOf(4, 5)),
        )
        profilesTab(doc) { _ ->
            openPicker()
            onNodeWithText("0 of 1 translations enabled").assertExists()
        }
    }

    @Test
    fun `the song-language picker is a second, independent one`() {
        profilesTab(threeTranslations()) { get ->
            onNodeWithTag(TranslationPickerTags.SONG.trigger).performClick()
            waitForIdle()
            onNodeWithTag(TranslationPickerTags.SONG.master).performClick()
            waitForIdle()

            assertEquals(Constants.SONG_LANG_OFF, get().profile().songMode, "songs went off")
            assertEquals(
                Constants.SONG_LANG_BOTH,
                get().profile().bibleMode,
                "and scripture was left exactly as it was",
            )
        }
    }

    // ── The song-language picker, which writes the same way ─────────────────────────────────────

    private fun SkikoComposeUiTest.openSongPicker() {
        onNodeWithTag(TranslationPickerTags.SONG.trigger).performClick()
        waitForIdle()
    }

    private fun SkikoComposeUiTest.toggleLanguage(index: Int) {
        onNodeWithTag(TranslationPickerTags.SONG.row(index)).performClick()
        waitForIdle()
    }

    @Test
    fun `unticking a language narrows the profile's song selection`() {
        profilesTab(threeTranslations()) { get ->
            openSongPicker()
            assertEquals(emptyList(), get().profile().songTranslations, "empty means all of them")

            toggleLanguage(0)

            assertEquals(
                listOf(1, 2, 3),
                get().profile().songTranslations,
                "the unticked language is gone from the selection",
            )
        }
    }

    @Test
    fun `unticking every language switches songs off`() {
        profilesTab(threeTranslations()) { get ->
            openSongPicker()
            repeat(MAX_SONG_LANGUAGES) { toggleLanguage(it) }

            // The same normalisation the Bible picker uses: showing none of them is a mode, not an
            // empty selection, because an empty selection reads back as "all".
            assertEquals(Constants.SONG_LANG_OFF, get().profile().songMode)
        }
    }

    @Test
    fun `ticking one back on switches songs on with just that one`() {
        profilesTab(threeTranslations()) { get ->
            openSongPicker()
            repeat(MAX_SONG_LANGUAGES) { toggleLanguage(it) }
            assertEquals(Constants.SONG_LANG_OFF, get().profile().songMode)

            toggleLanguage(2)

            assertEquals(Constants.SONG_LANG_BOTH, get().profile().songMode, "songs come back on")
            assertEquals(listOf(2), get().profile().songTranslations, "with only the one ticked")
        }
    }

    private companion object {
        /** The four language slots a song can carry, which the picker always lists. */
        const val MAX_SONG_LANGUAGES = 4
    }
}
