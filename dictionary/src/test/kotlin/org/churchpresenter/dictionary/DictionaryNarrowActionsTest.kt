@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The entry's actions when the detail pane is narrower than one row of them: the pane is whatever
 * the list beside it leaves, so the buttons wrap onto a second line rather than being squeezed —
 * and Go Live, last on every tab, stays last and full size.
 */
class DictionaryNarrowActionsTest {

    private val agape = DictionaryFixture.agape

    private fun ComposeUiTest.bounds(label: String): Rect = dictButton(label).fetchSemanticsNode().boundsInRoot

    /** Go Live's size with the room for one row, to hold the narrow layout to. */
    private fun fullSizeGoLive(): Rect {
        var size = Rect.Zero
        dictionaryTab(width = 1200.dp) { _, _ ->
            selectEntry(agape)
            size = bounds(DictionaryLabel.GO_LIVE)
        }
        return size
    }

    @Test
    fun `with no room for one row the actions wrap, keeping their size and their order`() {
        val full = fullSizeGoLive()
        dictionaryTab(width = 520.dp) { _, reports ->
            selectEntry(agape)
            val language = onNodeWithText("EN").fetchSemanticsNode().boundsInRoot
            val schedule = bounds(DictionaryLabel.ADD_TO_SCHEDULE)
            val live = bounds(DictionaryLabel.GO_LIVE)

            assertEquals(full.width, live.width, "Go Live is not squeezed")
            assertEquals(full.height, live.height)
            assertTrue(schedule.width > 0f, "Add to Schedule is still there to press")
            assertTrue(live.top > language.bottom, "Go Live wraps below the first row, not off the end")
            assertTrue(live.left >= schedule.left || live.top > schedule.top, "Go Live stays last")

            dictButton(DictionaryLabel.GO_LIVE).performClick()
            waitForIdle()
            assertEquals(listOf(agape), reports.live, "and it still sends the entry")
        }
    }
}
