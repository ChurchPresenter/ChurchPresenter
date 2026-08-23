@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.BibleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.showsContainingText
import org.churchpresenter.ui.showsExactly

/**
 * Browsing in `BibleTab`: the three columns, and what changes when a row in one of them is clicked.
 *
 * See `BibleTabTestSupport.kt` for the harness and for why this tab is testable.
 */
class BibleTabBrowseTest {

    @Test
    fun `opens on the first book, first chapter, with that chapter's verses listed`() =
        bibleTab { _, _ ->
            assertTrue(showsExactly("Genesis"), "book column lists Genesis")
            assertTrue(showsExactly("Psalms"), "book column lists Psalms")
            assertTrue(showsExactly("John"), "book column lists John")

            assertEquals(
                listOf(
                    "1. In the beginning God created the heaven and the earth.",
                    "2. And the earth was without form, and void.",
                    "3. And God said, Let there be light.",
                ),
                listedVerseLines(),
                "Genesis 1's three verses, in order",
            )
        }

    @Test
    fun `the column headers are labelled`() = bibleTab { _, _ ->
        // Uppercased by the tab, so this also pins that they are headers rather than data rows.
        assertTrue(showsExactly(BibleLabel.BOOK.uppercase()), "BOOK header")
        assertTrue(showsExactly(BibleLabel.CHAPTER.uppercase()), "CHAPTER header")
        assertTrue(showsExactly(BibleLabel.VERSE.uppercase()), "VERSE header")
    }

    @Test
    fun `clicking a book shows that book's first chapter`() = bibleTab { vm, _ ->
        onNodeWithText("John").performClick()
        waitForIdle()

        assertEquals(2, vm.selectedBookIndex.value, "John is the third book in the fixture")
        assertEquals(1, vm.selectedChapter.value, "a new book opens at its first chapter")
        assertEquals(
            listOf("1. In the beginning was the Word."),
            listedVerseLines(),
            "John 1's verse — and Genesis's verses are gone",
        )
    }

    @Test
    fun `clicking a chapter shows that chapter's verses`() = bibleTab { vm, _ ->
        // Genesis 2 in the fixture holds a single verse, distinct from all three of Genesis 1's.
        onNodeWithText("2").performClick()
        waitForIdle()

        assertEquals(2, vm.selectedChapter.value)
        assertEquals(listOf("1. Thus the heavens were finished."), listedVerseLines())
    }

    @Test
    fun `clicking a verse reports it as the selection`() = bibleTab { vm, reports ->
        onNodeWithText("2. And the earth was without form, and void.").performClick()
        waitForIdle()

        assertEquals(1, vm.selectedVerseIndex.value, "the second verse is selected")
        val reported = reports.live?.single()
        assertEquals("Genesis", reported?.bookName)
        assertEquals(1, reported?.chapter)
        assertEquals(2, reported?.verseNumber, "the host is told which verse is selected")
        assertEquals("And the earth was without form, and void.", reported?.verseText)
    }

    @Test
    fun `with no primary Bible configured the tab explains how to add one instead of browsing`() =
        bibleTab(settings = { it.copy(bibleSettings = BibleSettings(primaryBible = "")) }) { _, _ ->
            assertTrue(showsExactly(BibleLabel.NO_PRIMARY), "the empty state is shown")
            assertTrue(showsContainingText("Primary Bible"), "and how to fix it")
            // The browser is replaced, not merely empty — its headers are gone too.
            assertFalse(showsExactly(BibleLabel.BOOK.uppercase()), "no BOOK column header")
            assertFalse(showsExactly(BibleLabel.VERSE.uppercase()), "no VERSE column header")
        }

    @Test
    fun `the swap button appears only once a secondary Bible is configured`() {
        bibleTab { _, _ ->
            assertFalse(
                hasActionButton(BibleLabel.SWAP),
                "nothing to swap with when only a primary Bible is configured",
            )
        }
        bibleTab(
            settings = {
                it.copy(bibleSettings = it.bibleSettings.copy(secondaryBible = "second.spb"))
            }
        ) { _, _ ->
            assertTrue(hasActionButton(BibleLabel.SWAP), "swap is offered once there are two Bibles")
        }
    }

    @Test
    fun `swapping exchanges the primary and secondary Bibles`() = bibleTab(
        settings = { it.copy(bibleSettings = it.bibleSettings.copy(secondaryBible = "second.spb")) }
    ) { _, reports ->
        actionButton(BibleLabel.SWAP).performClick()
        waitForIdle()

        // The tab holds no settings of its own — it hands the host a transform — so the outcome to
        // assert is the settings that transform produces.
        val swapped = reports.settingsAfterChange?.bibleSettings
        assertEquals(1, reports.settingsChanges, "one settings change requested")
        assertEquals("second.spb", swapped?.primaryBible, "the secondary became primary")
        assertEquals("test.spb", swapped?.secondaryBible, "and the primary became secondary")
    }
}
