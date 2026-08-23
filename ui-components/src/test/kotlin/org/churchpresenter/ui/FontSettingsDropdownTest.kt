@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The font field and the panel it opens, as a settings tab uses them.
 *
 * The panel's own behaviour is covered by [FontPickerPanelTest] against a catalog it wrote; what is
 * left here is the pair — that the field opens it, that a pick reaches the caller and is remembered,
 * and that the panel closes when it should.
 */
class FontSettingsDropdownTest {

    private val installed = listOf("Arial", "Georgia", "Papyrus")

    /** Renders the picker and returns a reader for the last family it committed. */
    private fun ComposeUiTest.picker(
        initial: String = "Georgia",
        fonts: List<String> = installed,
        fillWidth: Boolean = false,
    ): () -> String? {
        var committed: String? = null
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf(initial) }
                Box(Modifier.width(240.dp)) {
                    FontSettingsDropdown(
                        label = "Font",
                        value = value,
                        fonts = fonts,
                        fillWidth = fillWidth,
                        onValueChange = { value = it; committed = it },
                    )
                }
            }
        }
        waitForIdle()
        return { committed }
    }

    /** The field, which is the only node carrying the label. */
    private fun ComposeUiTest.field() = onNodeWithText("FONT")

    /**
     * A family's row in the panel.
     *
     * Not just "clickable text": the field carries the current family's name too, and is clickable,
     * so the row is the one of the two that does not also carry the label.
     */
    private fun row(name: String) = hasText(name) and hasClickAction() and !hasText("FONT")

    private fun ComposeUiTest.open() {
        field().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.panelIsOpen() =
        onAllNodes(isEditable()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    @BeforeTest
    fun clearRecents() = RecentFonts.clear()

    @AfterTest
    fun forgetRecents() = RecentFonts.clear()

    @Test
    fun `the field shows the family it is set to`() = runComposeUiTest {
        picker(initial = "Georgia")

        onNodeWithText("Georgia").assertIsDisplayed()
    }

    @Test
    fun `nothing is open until the field is clicked`() = runComposeUiTest {
        picker()

        assertEquals(false, panelIsOpen())
    }

    @Test
    fun `clicking the field opens the panel on every installed family`() = runComposeUiTest {
        picker()
        open()

        assertEquals(true, panelIsOpen())
        installed.forEach { onNode(row(it)).assertExists() }
    }

    @Test
    fun `picking a family reports it, shows it, and closes the panel`() = runComposeUiTest {
        val committed = picker()
        open()
        onNode(row("Papyrus")).performClick()
        waitForIdle()

        assertEquals("Papyrus", committed())
        assertEquals(false, panelIsOpen())
        onNodeWithText("Papyrus").assertIsDisplayed()
    }

    @Test
    fun `a pick is remembered for the next picker to lead with`() = runComposeUiTest {
        picker()
        open()
        onNode(row("Papyrus")).performClick()
        waitForIdle()

        assertEquals(listOf("Papyrus"), RecentFonts.names)
    }

    @Test
    fun `escape closes the panel without committing anything`() = runComposeUiTest {
        val committed = picker()
        open()
        onNode(isEditable()).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertEquals(false, panelIsOpen())
        assertNull(committed())
    }

    @Test
    fun `searching from the field's own panel narrows it`() = runComposeUiTest {
        picker()
        open()
        onNode(isEditable()).performTextInput("papy")
        waitForIdle()

        onNode(row("Papyrus")).assertExists()
        onNode(row("Arial")).assertDoesNotExist()
    }

    @Test
    fun `a field stretched to its parent still opens and picks`() = runComposeUiTest {
        // fillWidth is what the canvas source panels pass; the popup must still anchor to the field.
        val committed = picker(fillWidth = true)
        open()
        onNode(row("Arial")).performClick()
        waitForIdle()

        assertEquals("Arial", committed())
    }

    @Test
    fun `a machine with no fonts at all still opens`() = runComposeUiTest {
        picker(fonts = emptyList())
        open()

        assertEquals(true, panelIsOpen())
        onNodeWithText("Showing 0 of 0").assertExists()
    }
}
