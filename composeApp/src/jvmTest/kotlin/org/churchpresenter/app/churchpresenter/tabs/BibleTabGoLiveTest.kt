@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.showsExactly

/**
 * The `BibleTab` action row — Go Live and Add to Schedule — and the history panel that Go Live
 * fills.
 *
 * Note that the tab reports every *selection* to its host too (see `BibleTabBrowseTest`), so the
 * thing that distinguishes going live from merely selecting is the history entry it leaves behind.
 *
 * See `BibleTabTestSupport.kt` for the harness.
 */
class BibleTabGoLiveTest {

    @Test
    fun `there is no history panel until something has gone live`() = bibleTab { _, _ ->
        assertFalse(showsExactly(BibleLabel.HISTORY), "no history header on a fresh tab")
    }

    @Test
    fun `going live sends the selected verse and records it in history`() = bibleTab { vm, reports ->
        onNodeWithText("3. And God said, Let there be light.").performClick()
        waitForIdle()
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()

        val live = reports.live?.single()
        assertEquals("Genesis", live?.bookName)
        assertEquals(1, live?.chapter)
        assertEquals(3, live?.verseNumber)
        assertEquals("And God said, Let there be light.", live?.verseText)

        assertEquals(1, vm.history.size, "one history entry")
        assertEquals("Genesis 1:3", vm.history.first().displayText)
        assertTrue(showsExactly(BibleLabel.HISTORY), "and the history panel appeared")
    }

    @Test
    fun `double-clicking a verse takes it live`() = bibleTab { vm, _ ->
        val verse = onNodeWithText("2. And the earth was without form, and void.")
        verse.performClick()
        verse.performClick()
        waitForIdle()

        assertEquals(
            listOf("Genesis 1:2"),
            vm.history.map { it.displayText },
            "a second click within the double-click window goes live",
        )
    }

    @Test
    fun `history lists the most recent passage first`() = bibleTab { vm, _ ->
        onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
        waitForIdle()
        actionButton(BibleLabel.GO_LIVE).performClick()
        onNodeWithText("3. And God said, Let there be light.").performClick()
        waitForIdle()
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()

        assertEquals(listOf("Genesis 1:3", "Genesis 1:1"), vm.history.map { it.displayText })
    }

    @Test
    fun `showing the same passage twice leaves one history entry`() = bibleTab { vm, _ ->
        onNodeWithText("3. And God said, Let there be light.").performClick()
        waitForIdle()
        actionButton(BibleLabel.GO_LIVE).performClick()
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()

        assertEquals(listOf("Genesis 1:3"), vm.history.map { it.displayText }, "no duplicate")
    }

    @Test
    fun `clicking a history entry navigates back to that passage`() = bibleTab { vm, _ ->
        actionButton(BibleLabel.GO_LIVE).performClick()   // Genesis 1:1, the opening selection
        waitForIdle()
        onNodeWithText("John").performClick()
        waitForIdle()
        assertEquals(2, vm.selectedBookIndex.value, "moved away to John")

        onNodeWithText("Genesis 1:1  In the beginning God created the heaven and the earth.")
            .performClick()
        waitForIdle()

        assertEquals(0, vm.selectedBookIndex.value, "the history entry took the browser back")
        assertEquals(1, vm.selectedChapter.value)
        assertEquals(0, vm.selectedVerseIndex.value)
    }

    @Test
    fun `double-clicking a history entry takes it live again`() = bibleTab { vm, reports ->
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()
        onNodeWithText("John").performClick()
        waitForIdle()
        assertEquals(2, vm.selectedBookIndex.value, "moved away to John")

        val historyEntry = onNodeWithText("Genesis 1:1  In the beginning God created the heaven and the earth.")
        historyEntry.performClick()
        historyEntry.performClick()
        waitForIdle()

        assertEquals(
            listOf(Presenting.BIBLE, Presenting.BIBLE),
            reports.presenting,
            "the original go-live, then the history double-click going live again",
        )
        assertEquals("Genesis", reports.live?.firstOrNull()?.bookName)
        assertEquals(1, reports.live?.firstOrNull()?.verseNumber)
    }

    @Test
    fun `clearing the history removes the panel`() = bibleTab { vm, _ ->
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()
        assertTrue(showsExactly(BibleLabel.HISTORY), "history is there to clear")

        actionButton(BibleLabel.CLEAR_HISTORY).performClick()
        waitForIdle()

        assertTrue(vm.history.isEmpty())
        assertFalse(showsExactly(BibleLabel.HISTORY), "and the panel went away with it")
    }

    @Test
    fun `adding to the schedule hands the host the selected verse`() = bibleTab { _, reports ->
        onNodeWithText("2. And the earth was without form, and void.").performClick()
        waitForIdle()
        actionButton(BibleLabel.ADD_TO_SCHEDULE).performClick()
        waitForIdle()

        assertEquals(listOf("Genesis 1:2"), reports.scheduled)
    }

    @Test
    fun `adding to the schedule does not put the verse on screen`() = bibleTab { vm, _ ->
        actionButton(BibleLabel.ADD_TO_SCHEDULE).performClick()
        waitForIdle()

        assertTrue(
            vm.history.isEmpty(),
            "scheduling is not presenting — nothing was recorded as shown: ${vm.history}",
        )
    }

    @Test
    fun `going live with multiple verses selected clears the multi-verse selection afterward`() =
        bibleTab { vm, _ ->
            vm.ctrlClickVerse(1)
            waitForIdle()
            assertTrue(vm.multiVerseEnabled.value, "ctrl-clicking a second verse enters multi-verse mode")

            actionButton(BibleLabel.GO_LIVE).performClick()
            waitForIdle()

            assertFalse(
                vm.multiVerseEnabled.value,
                "multi-verse mode must not linger into the next selection after going live",
            )
        }
}
