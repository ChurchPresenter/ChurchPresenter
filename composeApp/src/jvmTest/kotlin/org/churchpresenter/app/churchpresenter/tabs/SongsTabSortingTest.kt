@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sorting the song table by clicking a column header, and the second, right-click route to the same
 * column-visibility menu `SongsTabColumnsTest` reaches from the Tune button.
 *
 * See `SongsTabTestSupport.kt` for the harness.
 */
class SongsTabSortingTest {

    private fun ComposeUiTest.header(label: String) = onAllNodes(hasText(label))[0]

    // ── Sorting ─────────────────────────────────────────────────────────────────

    @Test
    fun `clicking a header sorts by that column, ascending`() = songsTab { vm, _ ->
        header("Title").performClick()
        waitForIdle()

        assertEquals(Constants.SORT_TITLE, vm.sortColumn.value)
        assertTrue(vm.sortAscending.value)
    }

    @Test
    fun `clicking the same header again reverses the direction`() = songsTab { vm, _ ->
        header("Title").performClick()
        waitForIdle()
        header("Title").performClick()
        waitForIdle()

        assertEquals(Constants.SORT_TITLE, vm.sortColumn.value)
        assertFalse(vm.sortAscending.value)
    }

    @Test
    fun `clicking a different header switches which column is sorted`() = songsTab { vm, _ ->
        header("Title").performClick()
        waitForIdle()
        header("Number").performClick()
        waitForIdle()

        assertEquals(Constants.SORT_NUMBER, vm.sortColumn.value)
        assertTrue(vm.sortAscending.value, "a newly-sorted column starts ascending, not wherever Title left off")
    }

    // ── The right-click column menu ────────────────────────────────────────────

    private val allShown = emptySet<String>()

    private fun ComposeUiTest.openMenuByRightClick() {
        header("Title").performMouseInput { rightClick() }
        waitForIdle()
    }

    private fun ComposeUiTest.clickColumnItem(label: String) {
        val nodes = onAllNodesWithText(label)
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
    }

    @Test
    fun `right-clicking a header opens the same column menu as the Tune button`() =
        songsTab(hiddenCols = allShown) { _, _ ->
            openMenuByRightClick()

            assertEquals(2, headerCount("Tune"), "the menu item joins the header on screen: ${rendered()}")
        }

    @Test
    fun `hiding a column from the right-click menu removes it and saves the choice`() =
        songsTab(hiddenCols = allShown) { _, reports ->
            openMenuByRightClick()
            clickColumnItem("Tune")

            assertFalse(headerCount("Tune") > 1, "the column has to leave the table: ${rendered()}")
            assertEquals(setOf("tune"), reports.settingsAfterChange?.songHiddenCols)
        }

    private fun ComposeUiTest.headerCount(label: String) =
        onAllNodesWithText(label).fetchSemanticsNodes(atLeastOneRootRequired = false).size
}
