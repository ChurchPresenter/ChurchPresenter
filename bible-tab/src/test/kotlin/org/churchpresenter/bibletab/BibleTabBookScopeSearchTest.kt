@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsExactly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Searching the Current Book in Auto mode (#995): a word is searched in that book rather than read
 * as the start of a book's name, only numbers are a reference -- a chapter or verse of that book --
 * and the result line says where the search looked.
 *
 * The fixture's books are Genesis, Psalms and John, and the tab opens on Genesis. Text searches are
 * run with the search button, which searches at once; typing waits out the as-you-type debounce.
 */
class BibleTabBookScopeSearchTest {

    private fun ComposeUiTest.scope(vm: BibleViewModel, index: Int) {
        runOnIdle { vm.updateSelectedScopeIndex(index) }
        waitForIdle()
    }

    private fun ComposeUiTest.searchNow() {
        actionButton(BibleLabel.SEARCH).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.pressInSearch(key: Key) {
        bibleSearchBox().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun `a word that starts like a book name stays in the current book until the scope is the whole Bible`() =
        bibleTab { vm, _ ->
            scope(vm, SCOPE_CURRENT_BOOK)
            bibleSearch("Joh")

            assertEquals(0, vm.selectedBookIndex.value, "'Joh' is not read as John while searching Genesis")
            assertEquals(BibleSearchMode.AUTO, vm.searchMode.value, "and the chip stays on Auto")

            scope(vm, 0)
            assertEquals(2, vm.selectedBookIndex.value, "over the whole Bible the same query opens John")
        }

    @Test
    fun `a word is searched in the current book and the count names the book`() = bibleTab { vm, _ ->
        scope(vm, SCOPE_CURRENT_BOOK)
        bibleSearch("God")
        searchNow()

        assertTrue(showsExactly("Found 2 result(s) in Genesis"), "John 3:16 is left out: ${renderedText().take(20)}")
        assertEquals(0, vm.selectedBookIndex.value)
    }

    @Test
    fun `a search that finds nothing in the current book names it`() = bibleTab { vm, _ ->
        scope(vm, SCOPE_CURRENT_BOOK)
        bibleSearch("shepherd")
        searchNow()

        assertTrue(
            showsExactly("""No results found for "shepherd" in Genesis"""),
            "Psalms has it, Genesis does not: ${renderedText().take(20)}",
        )
    }

    @Test
    fun `a number alone opens that chapter of the current book`() = bibleTab { vm, _ ->
        scope(vm, SCOPE_CURRENT_BOOK)
        bibleSearch("2")

        assertEquals(0, vm.selectedBookIndex.value)
        assertEquals(2, vm.selectedChapter.value)
        assertEquals(listOf("1. Thus the heavens were finished."), listedVerseLines())
    }

    @Test
    fun `a chapter and verse alone go to that verse of the current book`() = bibleTab { vm, _ ->
        bibleSearch("John 1")
        scope(vm, SCOPE_CURRENT_BOOK)
        bibleSearch("3:16")

        assertEquals(2, vm.selectedBookIndex.value, "still John")
        assertEquals(3, vm.selectedChapter.value)
        assertEquals(listOf("16. For God so loved the world."), listedVerseLines())
    }

    @Test
    fun `a book and chapter typed in the current book is searched as text`() = bibleTab { vm, _ ->
        scope(vm, SCOPE_CURRENT_BOOK)
        bibleSearch("John 3")
        searchNow()

        assertEquals(0, vm.selectedBookIndex.value, "the browser did not move to John")
        assertTrue(showsExactly("""No results found for "John 3" in Genesis"""), renderedText().take(20).toString())
    }

    @Test
    fun `Reference mode still reads a book name in the current book`() = bibleTab { vm, _ ->
        scope(vm, SCOPE_CURRENT_BOOK)
        onNodeWithText("Auto").performClick()
        waitForIdle()
        bibleSearch("John 3")

        assertEquals(2, vm.selectedBookIndex.value)
        assertEquals(3, vm.selectedChapter.value)
    }

    @Test
    fun `only Auto in the current book takes numbers alone, and Text takes nothing`() = bibleTab { vm, _ ->
        runOnIdle {
            assertEquals(2, vm.referenceFor("John 3")?.bookIndex, "Auto over the whole Bible reads the book")
            vm.updateSelectedScopeIndex(SCOPE_CURRENT_BOOK)
            assertNull(vm.referenceFor("John 3"), "Auto in the current book does not")
            assertEquals(3, vm.referenceFor("3:16")?.chapter)
            vm.cycleSearchMode()
            vm.cycleSearchMode()
            assertEquals(BibleSearchMode.TEXT, vm.searchMode.value)
            assertNull(vm.referenceFor("3:16"), "Text mode reads no reference at all")
        }
    }

    @Test
    fun `enter on a word in the current book searches instead of opening a book`() = bibleTab { vm, reports ->
        bibleSearchBox().requestFocus()
        scope(vm, SCOPE_CURRENT_BOOK)
        bibleSearch("Joh")
        pressInSearch(Key.Enter)

        assertEquals("Joh", vm.searchQuery.value, "not rewritten to John 1")
        assertEquals(0, vm.selectedBookIndex.value)
        assertTrue(reports.presenting.isEmpty())
    }

    @Test
    fun `enter on a chapter number opens its first verse, written without the book`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        scope(vm, SCOPE_CURRENT_BOOK)
        bibleSearch("1")
        pressInSearch(Key.Enter)

        assertEquals("1:1", vm.searchQuery.value)
        assertEquals(0, vm.selectedBookIndex.value)
    }

    @Test
    fun `the arrows step the verses of the current book without writing its name`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        scope(vm, SCOPE_CURRENT_BOOK)
        bibleSearch("1:1")
        pressInSearch(Key.DirectionDown)

        assertEquals("1:2", vm.searchQuery.value, "a book name here would turn the next step into a text search")
        assertEquals(1, vm.selectedVerseIndex.value)

        pressInSearch(Key.DirectionDown)
        assertEquals("1:3", vm.searchQuery.value)
    }

    @Test
    fun `down in an empty box offers no books while searching the current book`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        scope(vm, SCOPE_CURRENT_BOOK)
        pressInSearch(Key.DirectionDown)

        assertEquals("", vm.searchQuery.value, "the book's name would only be searched as text")
    }
}
