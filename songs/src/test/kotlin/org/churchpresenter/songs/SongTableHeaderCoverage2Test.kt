@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import kotlin.test.Test
import kotlin.test.assertEquals

class SongTableHeaderCoverage2Test {

    private fun ComposeUiTest.roots() = onAllNodes(isRoot()).fetchSemanticsNodes().size

    private fun ComposeUiTest.escapeTopPopup() {
        val roots = onAllNodes(isRoot())
        roots[roots.fetchSemanticsNodes().size - 1].performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
    }

    @Test
    fun `title cannot be hidden from the column menu, and the menu closes on escape`() = songsTab { _, reports ->
        onNodeWithContentDescription("Filter columns").performClick()
        waitForIdle()
        val titles = onAllNodesWithText("Title")
        titles[titles.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()

        assertEquals(0, reports.settingsChanges)
        escapeTopPopup()
        assertEquals(1, roots())
    }

    @Test
    fun `a right click on the header opens the column menu where it was clicked`() = songsTab { _, _ ->
        onAllNodesWithText("Title")[0].performMouseInput { rightClick() }
        waitForIdle()
        assertEquals(2, roots())

        escapeTopPopup()
        assertEquals(1, roots())
    }
}
