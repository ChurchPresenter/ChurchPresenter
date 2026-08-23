@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary.tab

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import org.churchpresenter.dictionary.DictionaryFixture
import org.churchpresenter.dictionary.StrongsEntry
import org.churchpresenter.ui.showsContainingText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Entries that are missing a field, and the controls that only make sense once one is chosen.
 *
 * Not every Strong's number carries a KJV usage — the section has to disappear for those rather
 * than draw its heading over nothing.
 */
class DictionaryTabEntryShapesTest {

    private val noUsage = StrongsEntry(
        number = "G9001",
        word = "λόγος",
        transliteration = "logos",
        pronunciation = "log'-os",
        definition = "a word, saying or speech",
        kjvUsage = "",
    )

    @Test
    fun `an entry with no KJV usage omits the section rather than heading an empty one`() = dictionaryTab(
        extraEntries = listOf(noUsage),
    ) { _, _ ->
        selectEntry(noUsage)

        assertTrue(showsContainingText(noUsage.definition), "the entry is open")
        assertFalse(showsContainingText(DictionaryLabel.KJV_USAGE), "and its empty usage is not headed")
    }

    @Test
    fun `an entry that has one still shows it`() = dictionaryTab(extraEntries = listOf(noUsage)) { _, _ ->
        selectEntry(DictionaryFixture.agape)

        assertTrue(showsContainingText(DictionaryLabel.KJV_USAGE))
    }

    @Test
    fun `clearing the search box puts every entry back`() = dictionaryTab { vm, _ ->
        dictSearch(DictionaryFixture.agape.transliteration)
        assertFalse(listShows(DictionaryFixture.elohim), "the search narrowed the list")

        onAllNodesWithContentDescription("Clear search")[0].performClick()
        waitForIdle()

        assertEquals("", vm.searchQuery)
        assertTrue(listShows(DictionaryFixture.elohim), "clearing puts the rest back")
    }

    @Test
    fun `narrowing the entry list to one verse keeps only what is tagged there`() = dictionaryTab(
        booksWithGreekData = listOf(43),
        chaptersForBook = mapOf(43 to listOf(3, 4)),
        versesInChapter = mapOf((43 to 3) to listOf(16, 17)),
        strongsForBookChapter = mapOf(43 to setOf(DictionaryFixture.agape.number)),
        getBookName = { "John" },
    ) { vm, _ ->
        vm.filterEntryListByBook(43)
        vm.filterEntryListByChapter(3)
        waitForIdle()

        vm.filterEntryListByVerse(16)
        waitForIdle()

        assertEquals(16, vm.entryVerseFilter)
        assertTrue(listShows(DictionaryFixture.agape))
        assertFalse(listShows(DictionaryFixture.elohim), "nothing else is tagged in John 3:16")
    }
}
