package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Walking away from an open menu without choosing anything.
 *
 * Every dropdown test here opens a menu and picks from it; the other way out — escape, or a click
 * anywhere else — goes through `onDismissRequest`, which has to shut the menu *without* reporting a
 * value. A dismiss that reported one would change the setting the operator just decided not to
 * change.
 */
@OptIn(ExperimentalTestApi::class)
class DropdownDismissTest {

    /** Menu rows live in a popup of their own, so they are not in the field's compose root. */
    private fun ComposeUiTest.rowsShowing(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    @Test
    fun `escape shuts a DropdownSelector without choosing`() = runComposeUiTest {
        var chosen: String? = null
        setContent {
            MaterialTheme {
                DropdownSelector(
                    label = "Book",
                    value = "43",
                    options = listOf("43" to "John", "40" to "Matthew"),
                    onValueChange = { chosen = it },
                )
            }
        }

        onNodeWithText("John").performClick()
        waitForIdle()
        assertTrue(rowsShowing("Matthew") > 0, "the menu is open")

        onAllNodesWithText("John")[0].performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertNull(chosen, "walking away must not report a value")
    }

    @Test
    fun `escape shuts the plain-list DropdownSelector without choosing`() = runComposeUiTest {
        var chosen: String? = null
        setContent {
            MaterialTheme {
                DropdownSelector(
                    label = "Mode",
                    items = listOf("Full screen", "Lower third"),
                    selected = "Full screen",
                    onSelectedChange = { chosen = it },
                )
            }
        }

        onNodeWithText("Full screen").performClick()
        waitForIdle()
        assertTrue(rowsShowing("Lower third") > 0, "the menu is open")

        onAllNodesWithText("Full screen")[0].performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertNull(chosen)
    }

    @Test
    fun `escape shuts a DropdownSettingsField without choosing`() = runComposeUiTest {
        var chosen: String? = null
        setContent {
            MaterialTheme {
                DropdownSettingsField(
                    value = "Georgia",
                    options = listOf("Georgia", "Arial"),
                    onValueChange = { chosen = it },
                    label = "Font",
                )
            }
        }

        onNodeWithText("Georgia").performClick()
        waitForIdle()
        assertTrue(rowsShowing("Arial") > 0, "the menu is open")

        onAllNodesWithText("Georgia")[0].performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        // Only that nothing was committed. Whether the popup is torn down is up to the harness's
        // window focus, which is not something this test can pin deterministically.
        assertNull(chosen)
    }
}
