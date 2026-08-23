@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText
import org.churchpresenter.ui.showsExactly

/**
 * The unified smart-search box in `BibleTab`: one field that both navigates to a reference and
 * searches verse text, plus the mode chip that forces either interpretation.
 *
 * Text search is always driven through the search **button** rather than by typing and waiting: the
 * as-you-type search is debounced, so waiting for it would put a fixed delay in the test, while the
 * button runs the same search immediately (`submitSmartQuery`).
 *
 * See `BibleTabTestSupport.kt` for the harness.
 */
class BibleTabSearchTest {

    @Test
    fun `the search box starts empty, showing its hint`() = bibleTab { _, _ ->
        assertTrue(
            showsExactly(BibleLabel.SEARCH_PLACEHOLDER),
            "the placeholder is shown until something is typed",
        )
        assertTrue(showsExactly("Auto"), "and the mode chip starts on Auto")
    }

    @Test
    fun `typing a reference navigates the browser instead of searching`() = bibleTab { vm, _ ->
        bibleSearch("John 3:16")

        assertEquals(2, vm.selectedBookIndex.value, "moved to John")
        assertEquals(3, vm.selectedChapter.value, "and to chapter 3")
        assertFalse(vm.isSearchMode.value, "a reference navigates — it does not open the result list")
        assertEquals(
            listOf("16. For God so loved the world."),
            listedVerseLines(),
            "John 3 is on screen",
        )
    }

    @Test
    fun `a book-and-chapter reference without a verse still navigates`() = bibleTab { vm, _ ->
        bibleSearch("ps 23")

        assertEquals(1, vm.selectedBookIndex.value, "moved to Psalms")
        assertEquals(23, vm.selectedChapter.value)
        assertEquals(listOf("1. The LORD is my shepherd; I shall not want."), listedVerseLines())
    }

    @Test
    fun `searching text lists the matching verses`() = bibleTab { vm, _ ->
        bibleSearch("shepherd")
        actionButton(BibleLabel.SEARCH).performClick()
        waitForIdle()

        assertTrue(vm.isSearchMode.value, "the result list replaced the browser")
        assertTrue(showsExactly("Found 1 result(s)"), "the result count: ${renderedText().take(20)}")
        assertTrue(
            showsExactly("Psalms 23:1 The LORD is my shepherd; I shall not want."),
            "the matching verse, with its reference shown exactly once: ${renderedText().take(20)}",
        )
    }

    @Test
    fun `clicking a result leaves the search and shows that verse in the browser`() =
        bibleTab { vm, _ ->
            bibleSearch("shepherd")
            actionButton(BibleLabel.SEARCH).performClick()
            waitForIdle()

            onNodeWithText("Psalms 23:1 The LORD is my shepherd; I shall not want.").performClick()
            waitForIdle()

            assertFalse(vm.isSearchMode.value, "the result list closed")
            assertEquals(1, vm.selectedBookIndex.value, "and the browser moved to Psalms")
            assertEquals(23, vm.selectedChapter.value)
            assertEquals(
                listOf("1. The LORD is my shepherd; I shall not want."),
                listedVerseLines(),
            )
        }

    @Test
    fun `a text search that matches nothing says so`() = bibleTab { _, _ ->
        bibleSearch("nothingmatchesthis")
        actionButton(BibleLabel.SEARCH).performClick()
        waitForIdle()

        assertTrue(
            showsExactly("""No results found for "nothingmatchesthis""""),
            "the empty-result message names the query: ${renderedText().take(20)}",
        )
    }

    @Test
    fun `the mode chip cycles Auto, Reference and Text`() = bibleTab { _, _ ->
        onNodeWithText("Auto").performClick()
        waitForIdle()
        assertTrue(showsExactly("Reference"), "Auto → Reference")

        onNodeWithText("Reference").performClick()
        waitForIdle()
        assertTrue(showsExactly("Text"), "Reference → Text")

        onNodeWithText("Text").performClick()
        waitForIdle()
        assertTrue(showsExactly("Auto"), "Text → Auto, back round")
    }

    @Test
    fun `in Text mode a reference is searched for rather than navigated to`() = bibleTab { vm, _ ->
        onNodeWithText("Auto").performClick()
        onNodeWithText("Reference").performClick()
        waitForIdle()

        bibleSearch("John 3:16")
        actionButton(BibleLabel.SEARCH).performClick()
        waitForIdle()

        // Nothing in the fixture contains the literal string "John 3:16", so forcing Text mode has
        // to produce an empty search rather than the navigation Auto mode would have done.
        assertEquals(0, vm.selectedBookIndex.value, "the browser did not move to John")
        assertTrue(
            showsExactly("""No results found for "John 3:16""""),
            "it searched instead: ${renderedText().take(20)}",
        )
    }

    @Test
    fun `clearing the search returns to the browser`() = bibleTab { vm, _ ->
        bibleSearch("shepherd")
        actionButton(BibleLabel.SEARCH).performClick()
        waitForIdle()
        assertTrue(vm.isSearchMode.value, "in search mode to begin with")

        clearSearchButton().performClick()
        waitForIdle()

        assertFalse(vm.isSearchMode.value)
        assertTrue(showsExactly(BibleLabel.SEARCH_PLACEHOLDER), "the box is empty again")
        assertTrue(showsExactly(BibleLabel.BOOK.uppercase()), "and the browser is back")
    }

    @Test
    fun `the scope and mode selectors are shown with their defaults`() = bibleTab { _, _ ->
        // DropdownSelector merges its caption and value into one node, so these are substring checks.
        assertTrue(showsContainingText(BibleLabel.ENTIRE_BIBLE), "scope defaults to the whole Bible")
        assertTrue(showsContainingText(BibleLabel.CONTAINS_PHRASE), "mode defaults to Contains")
    }
}
