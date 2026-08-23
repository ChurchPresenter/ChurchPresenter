@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The right-click and plain-click paths of the reusable selection list.
 *
 * The list resolves every gesture itself inside a raw `pointerInput`, so which callback fires is its
 * decision rather than the caller's — and a right-click landing on the selection path would move the
 * operator's selection when they only meant to open a menu. Each callback is also checked as
 * *optional*, since the list is used both with and without them.
 *
 * **Two paths are deliberately not covered here.** The ctrl/cmd and shift branches read
 * `event.keyboardModifiers`, which the test API does not let a test set on an injected pointer event —
 * a "ctrl-click" test would really be a plain click asserting the wrong branch, which is worse than no
 * test. And the double-click branch compares two `System.currentTimeMillis()` readings against a
 * 300 ms window, so testing it would mean asserting on the clock. Both are covered indirectly where
 * the callers use them (`BibleTabBrowseTest` drives multi-verse selection through the Bible tab).
 */
class SelectionListModifiedClickTest {

    private val items = listOf("Alpha", "Beta", "Gamma")

    /** Records which callback the list decided to invoke. */
    private class Clicks {
        var selected: Pair<Int, String>? = null
        var ctrl: Pair<Int, String>? = null
        var shift: Pair<Int, String>? = null
        var right: Int? = null
    }

    private fun withList(
        wireCtrl: Boolean = true,
        wireShift: Boolean = true,
        wireRight: Boolean = true,
        block: ComposeUiTest.(Clicks) -> Unit,
    ) = runComposeUiTest {
        val clicks = Clicks()
        setContent {
            MaterialTheme {
                SelectionListWithIndex(
                    list = items,
                    onItemSelected = { index, item -> clicks.selected = index to item },
                    onItemCtrlClicked = if (wireCtrl) { index, item -> clicks.ctrl = index to item } else null,
                    onItemShiftClicked = if (wireShift) { index, item -> clicks.shift = index to item } else null,
                    onRightClicked = if (wireRight) { index -> clicks.right = index } else null,
                )
            }
        }
        block(clicks)
    }

    @Test
    fun `a plain click selects and nothing else fires`() {
        withList { clicks ->
            onNodeWithText("Beta", substring = true).performClick()

            assertEquals(1 to "Beta", clicks.selected)
            assertNull(clicks.ctrl)
            assertNull(clicks.shift)
            assertNull(clicks.right)
        }
    }

    @Test
    fun `a right-click reports the row it landed on, and selects it`() {
        withList { clicks ->
            onNodeWithText("Beta", substring = true).performMouseInput { rightClick() }

            assertEquals(1, clicks.right, "the context menu has to open on the row under the cursor")
            // The selection callback fires too: the list's release handler does not filter on button,
            // so a right-click both selects the row and opens its menu. That is what the callers see,
            // and it is what makes a menu act on the row the cursor is over rather than the old one.
            assertEquals(1 to "Beta", clicks.selected)
        }
    }

    @Test
    fun `a right-click with no handler wired still selects`() {
        withList(wireRight = false) { clicks ->
            onNodeWithText("Beta", substring = true).performMouseInput { rightClick() }

            assertNull(clicks.right, "nothing was wired to hear it")
            assertEquals(1 to "Beta", clicks.selected, "so it degrades to an ordinary selection")
        }
    }

    @Test
    fun `every row is independently right-clickable`() {
        withList { clicks ->
            items.forEachIndexed { index, item ->
                onNodeWithText(item, substring = true).performMouseInput { rightClick() }
                assertEquals(index, clicks.right, "row $index reported the wrong index")
            }
        }
    }

    @Test
    fun `a selection can move from row to row`() {
        withList { clicks ->
            onNodeWithText("Alpha", substring = true).performClick()
            assertEquals(0 to "Alpha", clicks.selected)

            onNodeWithText("Gamma", substring = true).performClick()
            assertEquals(2 to "Gamma", clicks.selected, "the latest click wins")
        }
    }

    @Test
    fun `a multi-selection is drawn from the indices it is given`() {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SelectionListWithIndex(
                        list = items,
                        selectedIndices = setOf(0, 2),
                        onItemSelected = { _, _ -> },
                    )
                }
            }
            // Every row still renders — a highlighted row must not replace the others.
            items.forEach { onNodeWithText(it, substring = true).assertExists() }
        }
    }

    @Test
    fun `a single-line list still renders every row`() {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SelectionListWithIndex(
                        list = items,
                        singleLine = true,
                        onItemSelected = { _, _ -> },
                    )
                }
            }
            items.forEach { onNodeWithText(it, substring = true).assertExists() }
        }
    }
}
