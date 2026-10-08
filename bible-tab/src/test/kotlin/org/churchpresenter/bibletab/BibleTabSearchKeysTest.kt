@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import org.churchpresenter.core.models.schedule.ScheduleItem
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.settings.AppSettings
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

    // ── Edges ────────────────────────────────────────────────────────────────────────────────────

    private fun split(settings: AppSettings) =
        settings.copy(bibleSettings = settings.bibleSettings.copy(splitBrowseMode = true))

    @Test
    fun `enter on the tab for the verse already on screen changes nothing`() {
        val output = genesisLive()
        bibleTab(isPresenting = true, presenter = output) { vm, reports ->
            waitForIdle()
            assertEquals(Triple("Genesis", 1, 1), vm.selectedReference())
            reports.presenting.clear()

            onRoot().performKeyInput { pressKey(Key.Enter) }
            waitForIdle()

            assertTrue(reports.presenting.isEmpty(), "a second Enter must not send it again")
        }
    }

    @Test
    fun `enter in the search box on the verse already on screen leaves the search box and sends nothing`() {
        val output = genesisLive()
        bibleTab(isPresenting = true, presenter = output, settings = ::split) { _, reports ->
            reports.presenting.clear()
            bibleSearchBox().requestFocus()
            bibleSearch("Genesis 1:1")
            pressInSearch(Key.Enter)

            assertTrue(reports.presenting.isEmpty())
            bibleSearchBox().assertIsNotFocused()
        }
    }

    @Test
    fun `enter after a text search with nothing highlighted takes the first result`() = bibleTab { _, reports ->
        bibleSearchBox().requestFocus()
        bibleSearch("beginning")
        pressInSearch(Key.Enter)
        pressInSearch(Key.Enter)

        assertEquals("Genesis", reports.live?.firstOrNull()?.bookName)
    }

    @Test
    fun `stepping stops at the last chapter and the last book`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        bibleSearch("Genesis 2")
        pressInSearch(Key.DirectionDown)
        assertEquals("Genesis 2", vm.searchQuery.value, "Genesis has two chapters")

        bibleSearch("John")
        pressInSearch(Key.DirectionDown)
        assertEquals("John", vm.searchQuery.value, "John is the last book here")
    }

    @Test
    fun `up and down with nothing typed are the field's`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        pressInSearch(Key.DirectionDown)

        assertEquals("", vm.searchQuery.value)
        bibleSearchBox().assertIsFocused()
    }

    @Test
    fun `in split browse ctrl tab just leaves the search box`() {
        val output = genesisLive()
        bibleTab(isPresenting = true, presenter = output, settings = ::split) { _, _ ->
            bibleSearchBox().requestFocus()
            bibleSearch("John 3:16")
            switchSearchLive()

            bibleSearchBox().assertIsNotFocused()
            assertFalse(output.bibleHold.value)
        }
    }

    @Test
    fun `with nothing on screen ctrl tab still leaves the search box`() =
        bibleTab(isPresenting = true, presenter = FakeBibleOutput()) { _, _ ->
            bibleSearchBox().requestFocus()
            switchSearchLive()

            bibleSearchBox().assertIsNotFocused()
        }

    // ── Text mode, and what counts as already live ──────────────────────────────────────────────

    private fun BibleViewModel.textMode() {
        repeat(2) { cycleSearchMode() }
        check(searchMode.value == BibleSearchMode.TEXT)
    }

    @Test
    fun `in text mode a reference is searched for as words, and the arrows are the field's until it has results`() =
        bibleTab { vm, reports ->
            vm.textMode()
            bibleSearchBox().requestFocus()
            bibleSearch("John 3:16")
            pressInSearch(Key.DirectionDown)
            assertEquals("John 3:16", vm.searchQuery.value, "no reference stepping in text mode")

            pressInSearch(Key.Enter)
            assertFalse(Presenting.BIBLE in reports.presenting, "Enter searched rather than going live")
        }

    @Test
    fun `enter on the tab sends a verse other than the one on screen`() {
        val output = genesisLive()
        bibleTab(isPresenting = true, presenter = output) { vm, reports ->
            vm.navigateNextVerse()
            waitForIdle()
            reports.presenting.clear()

            onRoot().performKeyInput { pressKey(Key.Enter) }
            waitForIdle()

            assertTrue(Presenting.BIBLE in reports.presenting)
        }
    }

    @Test
    fun `in split browse a reference beside the one on screen still goes live`() {
        val output = genesisLive()
        bibleTab(isPresenting = true, presenter = output, settings = ::split) { _, reports ->
            reports.presenting.clear()
            bibleSearchBox().requestFocus()
            bibleSearch("Genesis 1:2")
            pressInSearch(Key.Enter)

            assertTrue(Presenting.BIBLE in reports.presenting)
        }
    }

    @Test
    fun `a passage on screen is not mistaken for a single verse typed from it`() {
        val output = FakeBibleOutput().apply {
            setDisplayedVerses(
                listOf(verse("Genesis", 1, 1, "In the beginning"), verse("Genesis", 1, 2, "And the earth")),
            )
        }
        bibleTab(isPresenting = true, presenter = output, settings = ::split) { _, reports ->
            reports.presenting.clear()
            bibleSearchBox().requestFocus()
            bibleSearch("Genesis 1:1")
            pressInSearch(Key.Enter)

            assertTrue(Presenting.BIBLE in reports.presenting)
        }
    }

    @Test
    fun `going back to a live verse in the chapter already open stays on it`() {
        val output = genesisLive()
        bibleTab(isPresenting = true, presenter = output) { vm, _ ->
            bibleSearchBox().requestFocus()
            bibleSearch("Genesis 1:3")
            switchSearchLive()

            assertEquals(Triple("Genesis", 1, 1), vm.selectedReference())
            assertFalse(output.bibleHold.value)
        }
    }

    @Test
    fun `a live verse from a book this bible lacks leaves the selection where it is`() {
        val output = FakeBibleOutput().apply { setDisplayedVerses(listOf(verse("Obadiah", 1, 1, "The vision"))) }
        bibleTab(isPresenting = true, presenter = output) { vm, _ ->
            bibleSearchBox().requestFocus()
            bibleSearch("John 3:16")
            switchSearchLive()

            assertEquals("John", vm.selectedReference()?.first)
            bibleSearchBox().assertIsNotFocused()
        }
    }

    @Test
    fun `with no output to hold, a search while live still browses`() = bibleTab(isPresenting = true) { vm, _ ->
        bibleSearchBox().requestFocus()
        bibleSearch("John 3:16")

        assertEquals(Triple("John", 3, 16), vm.selectedReference())
    }

    // ── References against what is on screen ─────────────────────────────────────────────────────

    private fun ComposeUiTest.enterInSplit(query: String, reports: BibleReports): Boolean {
        reports.presenting.clear()
        bibleSearchBox().requestFocus()
        bibleSearch(query)
        pressInSearch(Key.Enter)
        return Presenting.BIBLE in reports.presenting
    }

    @Test
    fun `a chapter typed whose first verse is on screen is already live`() {
        bibleTab(isPresenting = true, presenter = genesisLive(), settings = ::split) { _, reports ->
            assertFalse(enterInSplit("Genesis 1", reports))
        }
    }

    @Test
    fun `another book, or a range from the verse on screen, goes live`() {
        bibleTab(isPresenting = true, presenter = genesisLive(), settings = ::split) { _, reports ->
            assertTrue(enterInSplit("Psalms 1:1", reports), "another book")
            assertTrue(enterInSplit("Genesis 1:1-2", reports), "a range is not the single verse on screen")
        }
    }

    @Test
    fun `up from the top of the results highlights the first`() = bibleTab { _, reports ->
        bibleSearchBox().requestFocus()
        bibleSearch("beginning")
        pressInSearch(Key.Enter)
        pressInSearch(Key.DirectionUp)
        pressInSearch(Key.Enter)

        assertEquals("Genesis", reports.live?.firstOrNull()?.bookName)
    }

    @Test
    fun `a verse typed in a chapter that is not the one open steps without a known end`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        bibleSearch("Psalms 23:1")
        vm.selectBook(0)
        waitForIdle()

        pressInSearch(Key.DirectionDown)

        assertEquals("Psalms 23:2", vm.searchQuery.value)
    }

    @Test
    fun `stepping stops at the last verse of the chapter and before the first chapter`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        bibleSearch("Genesis 1:3")
        pressInSearch(Key.DirectionDown)
        assertEquals("Genesis 1:3", vm.searchQuery.value, "verse 3 is the last of Genesis 1 here")

        bibleSearch("Genesis 1")
        pressInSearch(Key.DirectionUp)
        assertEquals("Genesis 1", vm.searchQuery.value)
    }

    @Test
    fun `after a text search that found nothing the arrows are the field's`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        bibleSearch("zzzz")
        pressInSearch(Key.Enter)
        pressInSearch(Key.DirectionDown)

        assertEquals("zzzz", vm.searchQuery.value)
        bibleSearchBox().assertIsFocused()
    }

    @Test
    fun `ctrl tab from an empty search goes back to the live verse with no hold to release`() {
        val output = genesisLive()
        bibleTab(isPresenting = true, presenter = output) { vm, _ ->
            vm.navigateNextVerse()
            waitForIdle()
            bibleSearchBox().requestFocus()
            switchSearchLive()

            assertEquals(Triple("Genesis", 1, 1), vm.selectedReference())
            assertFalse(output.bibleHold.value)
        }
    }

    @Test
    fun `another chapter of the book on screen goes live`() {
        bibleTab(isPresenting = true, presenter = genesisLive(), settings = ::split) { _, reports ->
            assertTrue(enterInSplit("Genesis 2:1", reports))
        }
    }

    @Test
    fun `while the operator holds the output, the verse on screen can be sent again`() {
        val output = genesisLive().apply { setBibleHold(true) }
        bibleTab(isPresenting = true, presenter = output, settings = ::split) { _, reports ->
            assertTrue(enterInSplit("Genesis 1:1", reports), "held, so what is staged is not what shows")
        }
    }

    @Test
    fun `a dialog closing after the tab opened hands the keyboard to the tab, not the search box`() {
        val dismissed = mutableStateOf(0)
        bibleTab(focusSearchOnOpen = true, dialogDismissSignal = dismissed) { _, _ ->
            waitForIdle()
            bibleSearchBox().assertIsFocused()

            dismissed.value++
            waitForIdle()

            bibleSearchBox().assertIsNotFocused()
        }
    }

    @Test
    fun `a tab opened from the schedule keeps the keyboard on the verse`() {
        val row = ScheduleItem.BibleVerseItem(
            id = "v", bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved the world.",
        )
        bibleTab(focusSearchOnOpen = true, selectedVerseItem = row) { _, _ ->
            waitForIdle()
            bibleSearchBox().assertIsNotFocused()
        }
    }

    @Test
    fun `a new schedule verse, or the same one handed over again, is acted on each time`() {
        val john = ScheduleItem.BibleVerseItem(
            id = "j", bookName = "John", chapter = 3, verseNumber = 16, verseText = "x",
        )
        val psalm = ScheduleItem.BibleVerseItem(
            id = "p", bookName = "Psalms", chapter = 23, verseNumber = 1, verseText = "y",
        )
        val verse = mutableStateOf<ScheduleItem.BibleVerseItem?>(john)
        val version = mutableStateOf(0)
        bibleTab(scheduleVerse = verse, selectedVerseItemVersion = version) { vm, _ ->
            waitForIdle()
            assertEquals("John", vm.selectedReference()?.first)

            verse.value = psalm
            waitForIdle()
            assertEquals("Psalms", vm.selectedReference()?.first, "a different verse is a new hand-over")

            vm.selectBook(0)
            waitForIdle()
            version.value++
            waitForIdle()
            assertEquals("Psalms", vm.selectedReference()?.first, "the same verse handed over again")
        }
    }

    @Test
    fun `enter in an empty search box does nothing`() = bibleTab { _, reports ->
        bibleSearchBox().requestFocus()
        pressInSearch(Key.Enter)

        assertTrue(reports.presenting.isEmpty())
        bibleSearchBox().assertIsFocused()
    }

    @Test
    fun `enter again after a search that found nothing searches again rather than going live`() =
        bibleTab { _, reports ->
            bibleSearchBox().requestFocus()
            bibleSearch("zzzz")
            pressInSearch(Key.Enter)
            pressInSearch(Key.Enter)

            assertFalse(Presenting.BIBLE in reports.presenting)
        }

    @Test
    fun `ctrl tab goes from the tab into the search box, and back with nothing live`() = bibleTab { _, _ ->
        waitForIdle()
        bibleSearchBox().assertIsNotFocused()
        switchSearchLive()
        bibleSearchBox().assertIsFocused()

        switchSearchLive()
        bibleSearchBox().assertIsNotFocused()
    }

    @Test
    fun `up on the first book stays on it`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        bibleSearch("Genesis")
        pressInSearch(Key.DirectionUp)

        assertEquals("Genesis", vm.searchQuery.value)
    }

    @Test
    fun `a verse typed in the book open but another chapter steps without a known end`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        bibleSearch("Genesis 1:3")
        vm.navigateNextChapter()
        waitForIdle()

        pressInSearch(Key.DirectionDown)

        assertEquals("Genesis 1:4", vm.searchQuery.value, "chapter 1 is not the one open, so its end is unknown")
    }

    @Test
    fun `going back to live from another chapter of the same book returns to the live verse`() {
        val output = genesisLive()
        bibleTab(isPresenting = true, presenter = output) { vm, _ ->
            bibleSearchBox().requestFocus()
            bibleSearch("Genesis 2:1")
            switchSearchLive()

            assertEquals(Triple("Genesis", 1, 1), vm.selectedReference())
        }
    }
}
