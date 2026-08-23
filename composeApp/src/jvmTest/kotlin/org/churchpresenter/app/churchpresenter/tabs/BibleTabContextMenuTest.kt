@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText

class BibleTabContextMenuTest {

    private fun ComposeUiTest.openMenuOn(text: String) {
        onNodeWithText(text).performMouseInput { rightClick() }
        waitForIdle()
    }

    private fun ComposeUiTest.clickMenuItem(label: String) {
        val nodes = onAllNodesWithText(label)
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
    }

    @Test
    fun `right-clicking a verse opens its menu and selects it`() = bibleTab { vm, _ ->
        openMenuOn("3. And God said, Let there be light.")

        assertTrue(showsContainingText("Copy Verse"), renderedText().toString())
        assertTrue(showsContainingText("Add to Schedule"))
        assertTrue(showsContainingText("Go Live"))
        assertEquals(2, vm.selectedVerseIndex.value, "right-clicking a verse also selects it")
    }

    @Test
    fun `Go Live in the context menu takes the right-clicked verse live`() = bibleTab { _, reports ->
        openMenuOn("3. And God said, Let there be light.")

        clickMenuItem("Go Live")

        val live = reports.live?.single()
        assertEquals("Genesis", live?.bookName)
        assertEquals(3, live?.verseNumber)
    }

    @Test
    fun `Add to Schedule in the context menu schedules the right-clicked verse`() = bibleTab { _, reports ->
        openMenuOn("2. And the earth was without form, and void.")

        clickMenuItem("Add to Schedule")

        assertEquals(listOf("Genesis 1:2"), reports.scheduled)
    }

    @Test
    fun `Add to Schedule from the menu does not put the verse on screen`() = bibleTab { vm, _ ->
        openMenuOn("2. And the earth was without form, and void.")

        clickMenuItem("Add to Schedule")

        assertTrue(vm.history.isEmpty(), "scheduling from the menu must not also present it")
    }

    @Test
    fun `the menu acts on the row that was right-clicked, not a previously selected one`() = bibleTab { vm, reports ->
        onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
        waitForIdle()
        assertEquals(0, vm.selectedVerseIndex.value, "verse 1 is selected first")

        openMenuOn("3. And God said, Let there be light.")
        clickMenuItem("Go Live")

        assertEquals(3, reports.live?.single()?.verseNumber, "the menu must act on verse 3, not the earlier selection")
    }
}
