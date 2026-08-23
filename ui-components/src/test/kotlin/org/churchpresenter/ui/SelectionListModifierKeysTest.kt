package org.churchpresenter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The modified clicks a selection list reports separately from a plain one.
 *
 * A caller wires Ctrl and Shift to extend a selection rather than replace it, so the list has to
 * tell the two apart — and must fall back to a plain selection when the caller has not wired the
 * handler, or holding Ctrl would make rows unselectable. Right-click and plain click are covered
 * by [SelectionListModifiedClickTest]; this is the keyboard-modifier half.
 */
@OptIn(ExperimentalTestApi::class)
class SelectionListModifierKeysTest {

    private val items = listOf("Alpha", "Beta", "Gamma")

    private class Seen {
        var selected: Pair<Int, String>? = null
        var ctrl: Pair<Int, String>? = null
        var shift: Pair<Int, String>? = null
    }

    private fun ComposeUiTest.clickHolding(modifier: Key?, text: String) {
        if (modifier != null) onNodeWithText(text).performKeyInput { keyDown(modifier) }
        onNodeWithText(text).performMouseInput { click() }
        if (modifier != null) onNodeWithText(text).performKeyInput { keyUp(modifier) }
        waitForIdle()
    }

    private fun run(
        wireCtrl: Boolean = true,
        wireShift: Boolean = true,
        body: ComposeUiTest.(Seen) -> Unit,
    ) {
        val seen = Seen()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(300.dp)) {
                        SelectionListWithIndex(
                            list = items,
                            onItemSelected = { i, s -> seen.selected = i to s },
                            onItemCtrlClicked = if (wireCtrl) ({ i, s -> seen.ctrl = i to s }) else null,
                            onItemShiftClicked = if (wireShift) ({ i, s -> seen.shift = i to s }) else null,
                        )
                    }
                }
            }
            body(seen)
        }
    }

    @Test
    fun `ctrl-click reports the ctrl handler, not a plain selection`() = run { seen ->
        clickHolding(Key.CtrlLeft, "Beta")
        assertEquals(1 to "Beta", seen.ctrl)
        assertNull(seen.selected, "ctrl extends a selection; it must not replace it")
    }

    @Test
    fun `shift-click reports the shift handler`() = run { seen ->
        clickHolding(Key.ShiftLeft, "Gamma")
        assertEquals(2 to "Gamma", seen.shift)
        assertNull(seen.selected)
    }

    @Test
    fun `a plain click still reports a plain selection`() = run { seen ->
        clickHolding(null, "Alpha")
        assertEquals(0 to "Alpha", seen.selected)
        assertNull(seen.ctrl)
        assertNull(seen.shift)
    }

    @Test
    fun `ctrl-click falls back to selecting when no ctrl handler is wired`() = run(wireCtrl = false) { seen ->
        clickHolding(Key.CtrlLeft, "Beta")
        assertEquals(1 to "Beta", seen.selected, "without a handler the row must still be selectable")
    }

    @Test
    fun `shift-click falls back to selecting when no shift handler is wired`() = run(wireShift = false) { seen ->
        clickHolding(Key.ShiftLeft, "Beta")
        assertEquals(1 to "Beta", seen.selected)
    }

    @Test
    fun `each modifier reaches its own handler independently`() = run { seen ->
        clickHolding(Key.CtrlLeft, "Alpha")
        clickHolding(Key.ShiftLeft, "Gamma")
        assertEquals(0 to "Alpha", seen.ctrl)
        assertEquals(2 to "Gamma", seen.shift)
    }
}
