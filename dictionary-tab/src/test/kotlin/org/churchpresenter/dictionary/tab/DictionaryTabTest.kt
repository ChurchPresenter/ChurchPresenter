@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary.tab

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import org.churchpresenter.dictionary.DictionaryFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText
import org.churchpresenter.ui.showsExactly

/**
 * The Strong's dictionary tab: finding an entry, reading it, and sending it somewhere.
 *
 * Built on [DictionaryFixture]'s miniature corpus rather than the bundled 14k entries, so every
 * assertion is about data a reader of the test can see. See `DictionaryTabTestSupport.kt`.
 */
class DictionaryTabTest {

    // ── The list ────────────────────────────────────────────────────────────────

    @Test
    fun `the tab opens on the whole dictionary with nothing selected`() = dictionaryTab { vm, _ ->
        assertTrue(vm.entries.isNotEmpty(), "the fixture loaded")
        assertTrue(showsExactly(DictionaryLabel.SEARCH_HINT), "the search box invites a query")
        assertTrue(
            showsExactly(DictionaryLabel.SELECT_ENTRY),
            "and the detail pane explains itself until something is picked",
        )
    }

    @Test
    fun `every entry is listed by its word`() = dictionaryTab { _, _ ->
        assertTrue(listShows(DictionaryFixture.elohim), "the Hebrew entry")
        assertTrue(listShows(DictionaryFixture.agape), "the Greek one")
    }

    @Test
    fun `the entry count reflects what is listed`() = dictionaryTab { vm, _ ->
        assertTrue(
            showsExactly("${vm.entries.size} entries"),
            "the count matches the corpus: ${renderedText().take(8)}",
        )
    }

    // ── Searching ───────────────────────────────────────────────────────────────

    @Test
    fun `searching by Strong's number finds that entry`() = dictionaryTab { _, _ ->
        dictSearch("G26")

        assertTrue(listShows(DictionaryFixture.agape), "the match is listed")
        assertFalse(listShows(DictionaryFixture.elohim), "and the rest is filtered out")
    }

    @Test
    fun `searching by transliteration finds the entry behind it`() = dictionaryTab { _, _ ->
        // An operator who cannot type Greek searches for what they can read.
        dictSearch("agape")

        assertTrue(listShows(DictionaryFixture.agape))
        assertFalse(listShows(DictionaryFixture.elohim))
    }

    @Test
    fun `searching by a word in the definition finds it too`() = dictionaryTab { _, _ ->
        dictSearch("benevolence")

        assertTrue(listShows(DictionaryFixture.agape), "matched on the definition")
    }

    @Test
    fun `a search matching nothing says so rather than showing everything`() = dictionaryTab { _, _ ->
        dictSearch("nothingmatchesthisquery")

        assertTrue(showsExactly(DictionaryLabel.NO_RESULTS))
        assertFalse(listShows(DictionaryFixture.agape), "the list really is empty")
    }

    @Test
    fun `clearing the search brings the whole dictionary back`() = dictionaryTab { _, _ ->
        dictSearch("G26")
        assertFalse(listShows(DictionaryFixture.elohim), "filtered to begin with")

        dictSearch("")

        assertTrue(listShows(DictionaryFixture.elohim), "everything is listed again")
    }

    // ── Filtering by language ───────────────────────────────────────────────────

    @Test
    fun `the Hebrew filter hides the Greek entries, and the reverse`() = dictionaryTab { _, _ ->
        clickLanguageFilter(DictionaryLabel.HEBREW)
        assertTrue(listShows(DictionaryFixture.elohim), "Hebrew stays")
        assertFalse(listShows(DictionaryFixture.agape), "Greek goes")

        clickLanguageFilter(DictionaryLabel.GREEK)
        assertTrue(listShows(DictionaryFixture.agape), "Greek stays")
        assertFalse(listShows(DictionaryFixture.elohim), "Hebrew goes")

        clickLanguageFilter(DictionaryLabel.ALL)
        assertTrue(listShows(DictionaryFixture.elohim), "and All brings both back")
        assertTrue(listShows(DictionaryFixture.agape))
    }

    @Test
    fun `the language filter and the search narrow together`() = dictionaryTab { _, _ ->
        clickLanguageFilter(DictionaryLabel.GREEK)
        dictSearch("elohiym")

        // A Hebrew word searched for under the Greek filter must not surface.
        assertTrue(showsExactly(DictionaryLabel.NO_RESULTS), "got ${renderedText().take(8)}")
    }

    // ── Reading an entry ────────────────────────────────────────────────────────

    @Test
    fun `selecting an entry shows every field of it`() = dictionaryTab { _, _ ->
        selectEntry(DictionaryFixture.agape)

        assertFalse(showsExactly(DictionaryLabel.SELECT_ENTRY), "the placeholder is gone")
        // The pronunciation, definition and KJV usage appear only in the detail pane; the number,
        // word and transliteration are on every list row too, so they prove nothing here.
        assertTrue(showsContainingText(DictionaryFixture.agape.pronunciation), "pronunciation")
        assertTrue(showsContainingText(DictionaryFixture.agape.definition), "definition")
        assertTrue(showsContainingText(DictionaryFixture.agape.kjvUsage), "KJV usage")
    }

    @Test
    fun `the detail pane is labelled, so the fields are readable without guessing`() =
        dictionaryTab { _, _ ->
            selectEntry(DictionaryFixture.agape)

            assertTrue(showsExactly(DictionaryLabel.TRANSLITERATION))
            assertTrue(showsExactly(DictionaryLabel.PRONUNCIATION))
            assertTrue(showsExactly(DictionaryLabel.DEFINITION))
            assertTrue(showsExactly(DictionaryLabel.KJV_USAGE))
        }

    @Test
    fun `an entry is badged with the language it comes from`() = dictionaryTab { _, _ ->
        selectEntry(DictionaryFixture.agape)
        assertTrue(showsExactly(DictionaryLabel.GREEK.uppercase()), "got ${renderedText().take(10)}")

        selectEntry(DictionaryFixture.elohim)
        assertTrue(showsExactly(DictionaryLabel.HEBREW.uppercase()))
    }

    @Test
    fun `selecting a different entry replaces the one on show`() = dictionaryTab { _, _ ->
        selectEntry(DictionaryFixture.agape)
        selectEntry(DictionaryFixture.elohim)

        assertTrue(detailShows(DictionaryFixture.elohim), "the new entry is open")
        assertFalse(detailShows(DictionaryFixture.agape), "and the old one is not still open")
    }

    // ── History ─────────────────────────────────────────────────────────────────

    @Test
    fun `there is nothing to go back to until a second entry is opened`() = dictionaryTab { _, _ ->
        dictButton(DictionaryLabel.BACK).assertIsNotEnabled()
        dictButton(DictionaryLabel.FORWARD).assertIsNotEnabled()

        selectEntry(DictionaryFixture.agape)
        dictButton(DictionaryLabel.BACK).assertIsNotEnabled()
    }

    @Test
    fun `back returns to the previous entry and forward comes again`() = dictionaryTab { _, _ ->
        selectEntry(DictionaryFixture.agape)
        selectEntry(DictionaryFixture.elohim)

        dictButton(DictionaryLabel.BACK).performClick()
        waitForIdle()
        assertTrue(detailShows(DictionaryFixture.agape), "back to the first entry")

        dictButton(DictionaryLabel.FORWARD).performClick()
        waitForIdle()
        assertTrue(detailShows(DictionaryFixture.elohim), "and forward again")
    }

    // ── Sending an entry on ─────────────────────────────────────────────────────

    @Test
    fun `going live hands the host the whole entry`() = dictionaryTab { _, reports ->
        selectEntry(DictionaryFixture.agape)
        dictButton(DictionaryLabel.GO_LIVE).performClick()
        waitForIdle()

        assertEquals(listOf(DictionaryFixture.agape), reports.live)
    }

    @Test
    fun `adding to the schedule hands over the fields a schedule row needs`() =
        dictionaryTab { _, reports ->
            selectEntry(DictionaryFixture.agape)
            dictButton(DictionaryLabel.ADD_TO_SCHEDULE).performClick()
            waitForIdle()

            assertEquals(
                listOf(
                    DictionaryFixture.agape.number,
                    DictionaryFixture.agape.word,
                    DictionaryFixture.agape.transliteration,
                    DictionaryFixture.agape.definition,
                ),
                reports.scheduled.single(),
            )
        }

    @Test
    fun `with nothing selected there is nothing to send`() = dictionaryTab { _, _ ->
        dictButton(DictionaryLabel.GO_LIVE).assertIsNotEnabled()
        dictButton(DictionaryLabel.ADD_TO_SCHEDULE).assertIsNotEnabled()
    }
}
