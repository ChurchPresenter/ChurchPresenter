@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Bible tab's keyboard from search to live (#797, #798, #801).
 *
 * The search box is where the keyboard browses. Up and down step at the level the reference was
 * typed to -- book, chapter or verse -- and rewrite the query; through text-search results they move
 * the highlight. Enter sends what they reached live in one press. While a verse is live, a search
 * that moves the selection holds the output, so nothing typed reaches the screen until Go Live;
 * Ctrl+Tab goes back to what is live and releases that hold.
 */
class BibleTabSearchKeysTest {

    private fun ComposeUiTest.pressInSearch(key: Key) {
        bibleSearchBox().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.switchSearchLive() {
        onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Tab) } }
        waitForIdle()
    }

    private fun BibleViewModel.selectedReference(): Triple<String, Int, Int>? =
        getSelectedVerses().firstOrNull()?.let { Triple(it.bookName, it.chapter, it.verseNumber) }

    private fun verse(book: String, chapter: Int, number: Int, text: String) = SelectedVerse(
        bibleName = "Test Bible", bookName = book, chapter = chapter, verseNumber = number, verseText = text,
    )

    /** Genesis 1:1 on screen, as if it had gone live. */
    private fun genesisLive() = FakeBibleOutput().apply {
        setDisplayedVerses(listOf(verse("Genesis", 1, 1, "In the beginning God created the heaven and the earth.")))
    }

    // ── Opening the tab ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `opening the tab puts the caret in the search box`() = bibleTab(focusSearchOnOpen = true) { _, _ ->
        waitForIdle()
        bibleSearchBox().assertIsFocused()
    }

    @Test
    fun `with the setting off the tab keeps the keyboard`() = bibleTab(focusSearchOnOpen = false) { _, _ ->
        waitForIdle()
        bibleSearchBox().assertIsNotFocused()
    }

    @Test
    fun `a tab whose verse is live opens on the verse, not the search box`() =
        bibleTab(isPresenting = true, focusSearchOnOpen = true) { _, _ ->
        waitForIdle()
        bibleSearchBox().assertIsNotFocused()
    }

    // ── Stepping from the search box ─────────────────────────────────────────────────────────────

    @Test
    fun `down steps the verse when one was typed, and rewrites the query`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        bibleSearch("Genesis 1:1")
        pressInSearch(Key.DirectionDown)

        assertEquals("Genesis 1:2", vm.searchQuery.value)
        assertEquals(Triple("Genesis", 1, 2), vm.selectedReference())
    }

    @Test
    fun `down steps the chapter when only a chapter was typed`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        bibleSearch("Genesis 1")
        pressInSearch(Key.DirectionDown)

        assertEquals("Genesis 2", vm.searchQuery.value)
        assertEquals(2, vm.selectedChapter.value)
    }

    @Test
    fun `down steps the book when only a book was typed, and up comes back`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        bibleSearch("Genesis")
        pressInSearch(Key.DirectionDown)
        assertEquals("Psalms", vm.searchQuery.value)

        pressInSearch(Key.DirectionUp)
        assertEquals("Genesis", vm.searchQuery.value)
    }

    @Test
    fun `stepping stops at the edge of the chapter`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        bibleSearch("Genesis 1:1")
        pressInSearch(Key.DirectionUp)

        assertEquals("Genesis 1:1", vm.searchQuery.value, "there is no verse before the first")
    }

    // ── Going live from the search box ───────────────────────────────────────────────────────────

    @Test
    fun `enter in the search box puts the typed verse live in one press`() = bibleTab { _, reports ->
        bibleSearchBox().requestFocus()
        bibleSearch("John 3:16")
        pressInSearch(Key.Enter)

        val live = reports.live?.firstOrNull()
        assertEquals(Triple("John", 3, 16), live?.let { Triple(it.bookName, it.chapter, it.verseNumber) })
        assertTrue(Presenting.BIBLE in reports.presenting)
        bibleSearchBox().assertIsNotFocused()
    }

    @Test
    fun `a text search runs on the first enter and goes live with the highlighted result on the next`() =
        bibleTab { _, reports ->
            bibleSearchBox().requestFocus()
            bibleSearch("beginning")
            pressInSearch(Key.Enter)
            assertFalse(Presenting.BIBLE in reports.presenting, "the first Enter only searches")

            pressInSearch(Key.DirectionDown)
            pressInSearch(Key.DirectionDown)
            pressInSearch(Key.Enter)

            assertTrue(Presenting.BIBLE in reports.presenting)
            assertEquals("John", reports.live?.firstOrNull()?.bookName, "the second result was the one highlighted")
        }

    // ── While a verse is live ────────────────────────────────────────────────────────────────────

    @Test
    fun `a search that moves the selection while live holds the output`() {
        val output = genesisLive()
        bibleTab(isPresenting = true, presenter = output) { _, _ ->
            bibleSearchBox().requestFocus()
            bibleSearch("Genesis 1:3")

            assertTrue(output.bibleHold.value, "the screen must not follow the search")
        }
    }

    @Test
    fun `ctrl tab goes back to the live verse and releases the search's hold`() {
        val output = genesisLive()
        bibleTab(isPresenting = true, presenter = output) { vm, _ ->
            bibleSearchBox().requestFocus()
            bibleSearch("John 3:16")
            assertTrue(output.bibleHold.value)

            switchSearchLive()

            assertFalse(output.bibleHold.value, "going back to live releases the hold the search set")
            assertEquals(Triple("Genesis", 1, 1), vm.selectedReference())
            bibleSearchBox().assertIsNotFocused()
        }
    }

    @Test
    fun `a hold the operator set is not released by going back to live`() {
        val output = genesisLive().apply { setBibleHold(true) }
        bibleTab(isPresenting = true, presenter = output) { _, _ ->
            bibleSearchBox().requestFocus()
            bibleSearch("John 3:16")
            switchSearchLive()

            assertTrue(output.bibleHold.value)
        }
    }

    @Test
    fun `split browse never holds for a search, since browsing there never reaches the screen`() {
        val output = genesisLive()
        bibleTab(
            isPresenting = true,
            presenter = output,
            settings = { it.copy(bibleSettings = it.bibleSettings.copy(splitBrowseMode = true)) },
        ) { _, _ ->
            bibleSearchBox().requestFocus()
            bibleSearch("John 3:16")

            assertFalse(output.bibleHold.value)
        }
    }
}
