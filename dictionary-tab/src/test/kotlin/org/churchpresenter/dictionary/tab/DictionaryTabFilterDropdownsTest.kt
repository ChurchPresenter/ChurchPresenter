@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary.tab

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.dictionary.DictionaryFixture
import org.churchpresenter.ui.showsContainingText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The chapter and verse dropdowns that only appear once the filter above them has been narrowed.
 *
 * Both filter rows — the entry list's own, and the "In Scripture" card's — start as a single Book
 * control and grow a Chapter one when a book is picked, then a Verse one when a chapter is. Neither
 * of the later two had ever been rendered by a test, because reaching them needs a fixture with more
 * than one chapter under a book.
 */
class DictionaryTabFilterDropdownsTest {

    private val agape = DictionaryFixture.agape

    /** The filter row control captioned [caption], nearest the left edge (the entry list's own). */
    private fun ComposeUiTest.leftmost(caption: String) {
        val nodes = onAllNodesWithText(caption, substring = true).fetchSemanticsNodes()
        val i = nodes.indices.minByOrNull { nodes[it].boundsInRoot.left } ?: error("no \"$caption\"")
        onAllNodesWithText(caption, substring = true)[i].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.rightmost(caption: String) {
        val nodes = onAllNodesWithText(caption, substring = true).fetchSemanticsNodes()
        val i = nodes.indices.maxByOrNull { nodes[it].boundsInRoot.left } ?: error("no \"$caption\"")
        onAllNodesWithText(caption, substring = true)[i].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.controlCount(caption: String) =
        onAllNodesWithText(caption, substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    @Test
    fun `the entry list grows a chapter and then a verse control as it is narrowed`() = dictionaryTab(
        booksWithGreekData = listOf(43),
        chaptersForBook = mapOf(43 to listOf(3, 4)),
        versesInChapter = mapOf((43 to 3) to listOf(16, 17)),
        strongsForBookChapter = mapOf(43 to setOf(agape.number)),
        getBookName = { "John" },
    ) { vm, _ ->
        assertEquals(0, controlCount("CHAPTER"), "no chapter control before a book is chosen")

        leftmost("BOOK")
        onNodeWithText("John").performClick()
        waitForIdle()
        assertEquals(43, vm.entryBookFilter)
        assertTrue(controlCount("CHAPTER") > 0, "two chapters are tagged, so the control appears")
        assertEquals(0, controlCount("VERSE"), "no verse control before a chapter is chosen")

        // Chosen through the view model rather than through the menu: opening a *second-level*
        // DropdownSelector does not expand under synthetic input in this fixture (the same limit
        // DictionaryTabFiltersTest records for the card's chapter menu). What is under test here is
        // that narrowing to a chapter is what puts the Verse control on screen.
        vm.filterEntryListByChapter(3)
        waitForIdle()
        assertEquals(3, vm.entryChapterFilter)
        assertTrue(controlCount("VERSE") > 0, "two verses are tagged in that chapter, so it appears")
    }

    @Test
    fun `the In Scripture card grows a chapter control when one book spans two chapters`() = dictionaryTab(
        verses = mapOf(
            agape.number to listOf(
                DictionaryFixture.verse(43, 3, 16, agape.number),
                DictionaryFixture.verse(43, 4, 1, agape.number),
                DictionaryFixture.verse(40, 5, 3, agape.number),
            ),
        ),
        getBookName = { if (it == 43) "John" else "Matthew" },
    ) { _, _ ->
        selectEntry(agape)
        assertEquals(0, controlCount("CHAPTER"), "the card offers only Book until one is chosen")

        rightmost("BOOK")
        onNodeWithText("John").performClick()
        waitForIdle()

        assertTrue(controlCount("CHAPTER") > 0, "John spans two tagged chapters, so Chapter appears")
        assertTrue(showsContainingText("John 3:16"), "and the card is narrowed to that book")
        assertFalse(showsContainingText("Matthew 5:3"), "the other book drops out")
    }

    @Test
    fun `a book tagged in a single chapter grows no chapter control`() = dictionaryTab(
        verses = mapOf(
            agape.number to listOf(
                DictionaryFixture.verse(43, 3, 16, agape.number),
                DictionaryFixture.verse(40, 5, 3, agape.number),
            ),
        ),
        getBookName = { if (it == 43) "John" else "Matthew" },
    ) { _, _ ->
        selectEntry(agape)
        rightmost("BOOK")
        onNodeWithText("John").performClick()
        waitForIdle()

        assertEquals(0, controlCount("CHAPTER"), "one chapter leaves nothing to choose between")
    }
}
