@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BibleTabHistoryEntryTest {

    private val verseOne = "1. In the beginning God created the heaven and the earth."
    private val verseThree = "3. And God said, Let there be light."

    private fun ComposeUiTest.goLiveWith(text: String) {
        onNodeWithText(text).performClick()
        waitForIdle()
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.entry(reference: String) =
        onNode(hasText("$reference  ", substring = true))

    private fun ComposeUiTest.hasEntry(reference: String) =
        onAllNodes(hasText("$reference  ", substring = true)).fetchSemanticsNodes(false).isNotEmpty()

    @Test
    fun `clicking a history entry selects that verse again`() = bibleTab { vm, _ ->
        goLiveWith(verseOne)
        goLiveWith(verseThree)

        entry("Genesis 1:1").performClick()
        waitForIdle()

        assertEquals(0, vm.selectedVerseIndex.value)
        assertEquals(2, vm.history.size, "a click is not a go-live")
    }

    @Test
    fun `double-clicking a history entry puts it back on screen`() = bibleTab { vm, reports ->
        goLiveWith(verseOne)
        goLiveWith(verseThree)

        entry("Genesis 1:1").performMouseInput { doubleClick() }
        waitForIdle()

        assertEquals(1, reports.live?.first()?.verseNumber)
        assertEquals("Genesis 1:1", vm.history.first().displayText)
    }

    @Test
    fun `the history header folds the list away and back`() = bibleTab { _, _ ->
        goLiveWith(verseOne)
        assertTrue(hasEntry("Genesis 1:1"))

        onNodeWithText(BibleLabel.HISTORY).performClick()
        waitForIdle()
        assertFalse(hasEntry("Genesis 1:1"), "folded away")

        onNodeWithText(BibleLabel.HISTORY).performClick()
        waitForIdle()
        assertTrue(hasEntry("Genesis 1:1"), "and back")
    }

    @Test
    fun `clearing history removes the panel`() = bibleTab { vm, _ ->
        goLiveWith(verseOne)

        actionButton("Clear").performClick()
        waitForIdle()

        assertTrue(vm.history.isEmpty())
        assertFalse(hasEntry("Genesis 1:1"))
    }
}
