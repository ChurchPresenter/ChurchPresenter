@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.Macro
import org.churchpresenter.showcontrol.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The Macros dialog: running, adding, editing, naming, deleting, and the way to MIDI & OSC. */
class MacrosDialogContentTest {

    private val walkIn = Macro("macro1", "Walk in", listOf(Action.ClearAll))
    private val empty = Macro("macro2", "Nothing yet")

    private class Seen {
        var macros: List<Macro>? = null
        val ran = mutableListOf<Macro>()
        var dismissed = 0
        var control = 0
    }

    private fun dialog(
        macros: List<Macro> = listOf(walkIn, empty),
        withControl: Boolean = true,
        block: ComposeUiTest.(Seen) -> Unit,
    ) = runComposeUiTest {
        val seen = Seen()
        setContent {
            MaterialTheme {
                Box(Modifier.size(700.dp, 900.dp)) {
                    MacrosDialogContent(
                        macros = macros,
                        rows = emptyList(),
                        onMacrosChange = { seen.macros = it },
                        onRun = { seen.ran += it },
                        onDismiss = { seen.dismissed++ },
                        onOpenControl = if (withControl) ({ seen.control++ }) else null,
                    )
                }
            }
        }
        block(seen)
    }

    private fun ComposeUiTest.nameField() = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(MACRO_NAME_TAG)))

    @Test
    fun `with none it says so, Close closes, and MIDI and OSC opens from here`() = dialog(emptyList()) { seen ->
        onNodeWithText("No macros yet", substring = true).assertExists()
        onNodeWithTag(MACRO_CONTROL_TAG).performClick()
        onNodeWithText("Close").performClick()
        assertEquals(1, seen.control)
        assertEquals(1, seen.dismissed)
    }

    @Test
    fun `without a hub there is no way to MIDI and OSC`() = dialog(withControl = false) {
        onAllNodesWithTag(MACRO_CONTROL_TAG).assertCountEquals(0)
    }

    @Test
    fun `Run runs a macro, and one with no actions cannot be run`() = dialog { seen ->
        onNodeWithTag(macroRunTag("macro1")).performClick()
        onNodeWithTag(macroRunTag("macro2")).assertIsNotEnabled()
        assertEquals(listOf(walkIn), seen.ran)
    }

    @Test
    fun `a new macro gets a fresh id, its name trimmed, or its id when unnamed`() = dialog { seen ->
        onNodeWithTag(MACRO_ADD_TAG).performClick()
        waitForIdle()
        nameField().performTextReplacement("  Closing  ")
        onNodeWithTag(MACRO_SAVE_TAG).performClick()
        waitForIdle()
        assertEquals(Macro("macro3", "Closing"), seen.macros?.last())

        onNodeWithTag(MACRO_ADD_TAG).performClick()
        waitForIdle()
        onNodeWithTag(MACRO_SAVE_TAG).performClick()
        waitForIdle()
        assertEquals("macro3", seen.macros?.last()?.name)
    }

    @Test
    fun `an action is added to a macro being edited`() = dialog { seen ->
        onNodeWithTag(macroEditTag("macro2")).performClick()
        onNodeWithTag(ADD_ACTION_TAG).performClick()
        onNodeWithTag(ADD_TIMER_ACTION_TAG).performClick()
        onNodeWithTag(MACRO_SAVE_TAG).performClick()
        assertEquals(1, seen.macros?.single { it.id == "macro2" }?.actions?.size)
    }

    @Test
    fun `a saved macro is edited in place, or deleted`() = dialog { seen ->
        onNodeWithTag(macroEditTag("macro1")).performClick()
        waitForIdle()
        nameField().performTextReplacement("Walk in loop")
        onNodeWithTag(MACRO_SAVE_TAG).performClick()
        waitForIdle()
        assertEquals(listOf(walkIn.copy(name = "Walk in loop"), empty), seen.macros)

        onNodeWithTag(macroEditTag("macro2")).performClick()
        waitForIdle()
        onNodeWithText("Delete").performClick()
        waitForIdle()
        assertEquals(listOf(walkIn), seen.macros)
    }

    @Test
    fun `nothing is kept until Save`() = dialog { seen ->
        onNodeWithTag(MACRO_ADD_TAG).performClick()
        waitForIdle()
        onNodeWithText("Close").performClick()
        assertNull(seen.macros)
    }

    @Test
    fun `an id is never one a macro already has`() {
        assertEquals("macro3", newMacroId(listOf(walkIn, empty)))
        assertEquals("macro4", newMacroId(listOf(walkIn, Macro("macro3", "x"))))
    }
}
