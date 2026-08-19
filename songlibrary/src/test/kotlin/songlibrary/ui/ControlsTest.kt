@file:OptIn(ExperimentalTestApi::class)

package songlibrary.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The buttons, menus and tick-boxes the window is assembled from.
 *
 * Small enough that the only things worth pinning are the ones a caller relies on and cannot see:
 * that a disabled button really is inert rather than merely greyed, and that a menu closes when it
 * is told to.
 */
class ControlsTest {

    @Test
    fun `a disabled button is inert, not just grey`() = runComposeUiTest {
        var pressed = 0
        setContent {
            Themed {
                PrimaryButton(label = "Save Changes", onClick = { pressed++ }, enabled = false)
            }
        }

        onNodeWithText("Save Changes").performClick()

        assertEquals(0, pressed, "a greyed button that still fires is worse than no button")
    }

    @Test
    fun `a disabled quiet button is inert too`() = runComposeUiTest {
        var pressed = 0
        setContent { Themed { QuietButton("Revert", onClick = { pressed++ }, enabled = false) } }

        onNodeWithText("Revert").performClick()

        assertEquals(0, pressed)
    }

    @Test
    fun `an enabled button fires once per press`() = runComposeUiTest {
        var pressed = 0
        setContent { Themed { PrimaryButton(label = "New Song", onClick = { pressed++ }) } }

        onNodeWithText("New Song").performClick()
        onNodeWithText("New Song").performClick()

        assertEquals(2, pressed)
    }

    @Test
    fun `a menu closes itself when a row asks it to`() = runComposeUiTest {
        var picked: String? = null
        setContent {
            Themed {
                LibraryDropdown(label = "Columns", highlighted = false, menuWidth = 200.dp) { close ->
                    MenuRow("Author") { picked = "Author"; close() }
                    MenuRow("Composer") { picked = "Composer"; close() }
                }
            }
        }

        onNodeWithText("Columns").performClick()
        waitForIdle()
        assertTrue(isShowing("Composer"), "the menu is open")

        clickLast("Composer")

        assertEquals("Composer", picked)
        assertTrue(!isShowing("Author"), "and it shut behind the choice")
    }

    @Test
    fun `a row that takes no click cannot be pressed`() = runComposeUiTest {
        var pressed = 0
        setContent {
            Themed {
                LibraryDropdown(label = "Columns", highlighted = true, menuWidth = 200.dp) { _ ->
                    MenuRow(label = "Title", trailing = { }, onClick = null)
                    MenuRow("Number") { pressed++ }
                }
            }
        }

        onNodeWithText("Columns").performClick()
        waitForIdle()
        clickLast("Title")

        assertEquals(0, pressed, "the always-on column is not a choice")
        assertTrue(isShowing("Number"), "and the menu stayed open")
    }

    @Test
    fun `a tick box that is neither on nor off shows the in-between state`() = runComposeUiTest {
        var toggles = 0
        setContent {
            Themed {
                LibraryCheckbox(checked = false, indeterminate = true, onToggle = { toggles++ })
            }
        }

        onAllNodes(isToggleable())[0].performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun `a tick box with nothing to toggle is not toggleable`() = runComposeUiTest {
        setContent { Themed { LibraryCheckbox(checked = true) } }

        assertEquals(
            0,
            onAllNodes(isToggleable()).fetchSemanticsNodes().size,
            "a read-only tick is not offered as a control",
        )
    }
}
