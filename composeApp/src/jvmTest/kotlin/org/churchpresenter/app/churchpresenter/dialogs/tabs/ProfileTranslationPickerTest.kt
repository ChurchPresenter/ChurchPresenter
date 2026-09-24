@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
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
 * The profile's Bible and song-language sources, on the Sources row of the Profiles tab.
 *
 * Which translations a profile shows is stored as [OutputProfile.bibleTranslations], a list of
 * positions in the configured stack, in the order the profile draws them, where **empty means all
 * of them** -- so a translation added later appears on every profile that was showing everything.
 * Most of what is tested here follows from that one normalisation:
 *
 *  * it makes "none selected" unrepresentable as a selection, so showing none of them has to be
 *    stored as `bibleMode = SONG_LANG_OFF` instead, which is the same statement about the profile.
 *    An empty list written for "none" reads straight back as *all*;
 *  * and a **position** is only meaningful against the stack it was stored for. One past the end of
 *    the current stack is ignored rather than counted, and a selection left with none of its
 *    positions surviving shows nothing rather than reading as the empty "all of them".
 *
 * The Bible source is an ordered list -- remove, add back, reorder -- rather than a checklist, since
 * a profile can put its translations in its own order; the song languages keep their checklist.
 * Controls are addressed by test tag: the rows live in a popup, and their captions repeat.
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
        onNodeWithTag(BIBLE_SOURCE_TRIGGER_TAG).performClick()
        waitForIdle()
    }

    /** Removes the translation drawn in [slot] -- a position in the profile's order, not the stack's. */
    private fun SkikoComposeUiTest.removeSlot(slot: Int) {
        onNode(hasContentDescription("Remove translation") and hasAnyAncestor(hasTestTag(bibleOrderRowTag(slot))))
            .performClick()
        waitForIdle()
    }

    private fun AppSettings.picked(): List<Int> = profile().bibleTranslations

    @Test
    fun `an untouched profile shows all of them`() {
        profilesTab(threeTranslations()) { get ->
            openPicker()
            assertEquals(emptyList(), get().picked(), "empty means all of them")
            onNodeWithTag(bibleOrderRowTag(2)).assertExists()
        }
    }

    @Test
    fun `removing one narrows the selection to what is left`() {
        profilesTab(threeTranslations()) { get ->
            openPicker()
            removeSlot(0)

            assertEquals(listOf(1, 2), get().picked(), "the removed one is gone from the selection")
        }
    }

    @Test
    fun `removing the last translation switches scripture off`() {
        profilesTab(threeTranslations()) { get ->
            openPicker()
            removeSlot(0)
            removeSlot(0)
            assertEquals(listOf(2), get().picked())

            removeSlot(0)

            // Showing none of them is the same statement as switching scripture off. Storing it as
            // an empty selection instead is what used to read back as "all".
            assertEquals(Constants.SONG_LANG_OFF, get().profile().bibleMode)
            onNodeWithText("0 of 3 translations").assertExists()
        }
    }

    @Test
    fun `adding one back switches scripture on with just that one`() {
        profilesTab(threeTranslations()) { get ->
            openPicker()
            repeat(3) { removeSlot(0) }
            assertEquals(Constants.SONG_LANG_OFF, get().profile().bibleMode)

            onNodeWithTag(bibleAddRowTag(1)).performClick()
            waitForIdle()

            assertEquals(Constants.SONG_LANG_BOTH, get().profile().bibleMode, "scripture comes back on")
            assertEquals(listOf(1), get().picked(), "with only the one that was added")
        }
    }

    @Test
    fun `the menu stays open across a removal`() {
        profilesTab(threeTranslations()) { _ ->
            openPicker()
            removeSlot(0)

            onNodeWithTag(bibleOrderRowTag(0)).assertExists("the menu must still be open")
        }
    }

    @Test
    fun `a position past the end of the stack is ignored rather than counted`() {
        val doc = profileDocument(
            bible = BibleSettings(translations = listOf(BibleTranslationSettings(fileName = "kjv.spb"))),
            profile = OutputProfile(bibleTranslations = listOf(0, 7)),
        )
        profilesTab(doc) { _ ->
            // One real translation, one stale position: the stale one must not be counted.
            onNodeWithText("1 of 1 translations").assertExists()
        }
    }

    @Test
    fun `a selection whose positions have all gone shows nothing rather than everything`() {
        val doc = profileDocument(
            bible = BibleSettings(translations = listOf(BibleTranslationSettings(fileName = "kjv.spb"))),
            profile = OutputProfile(bibleTranslations = listOf(4, 5)),
        )
        profilesTab(doc) { _ ->
            onNodeWithText("0 of 1 translations").assertExists()
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
