@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The outline and backdrop dialogs' Cancel / Apply / OK: edits sit in a draft and reach the caller
 * only on Apply, which keeps the dialog open, or OK, which closes it. Cancel writes nothing it has
 * not already applied.
 */
class StyleDialogButtonsTest {

    private class Outcome {
        val writes = mutableListOf<Any>()
        var dismissed = 0
    }

    private fun outline(block: ComposeUiTest.(Outcome) -> Unit) = runComposeUiTest {
        val outcome = Outcome()
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(TextOutline()) }
                TextOutlineDialog(
                    outline = state,
                    onChange = { state = it; outcome.writes += it },
                    onDismiss = { outcome.dismissed++ },
                )
            }
        }
        block(outcome)
    }

    private fun ComposeUiTest.showOutline() {
        onNode(isToggleable() and hasText("Show outline")).performClick()
        waitForIdle()
    }

    @Test
    fun `an edit writes nothing until it is applied`() = outline { outcome ->
        showOutline()
        assertEquals(emptyList(), outcome.writes)
    }

    @Test
    fun `Apply is off until there is something to apply`() = outline { _ ->
        onNodeWithText("Apply").assertIsNotEnabled()
    }

    @Test
    fun `Apply writes the draft and leaves the dialog open`() = outline { outcome ->
        showOutline()
        onNodeWithText("Apply").performClick()
        waitForIdle()

        assertTrue((outcome.writes.single() as TextOutline).enabled)
        assertEquals(0, outcome.dismissed)
    }

    @Test
    fun `OK writes the draft and closes`() = outline { outcome ->
        showOutline()
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertTrue((outcome.writes.single() as TextOutline).enabled)
        assertEquals(1, outcome.dismissed)
    }

    @Test
    fun `Cancel closes and writes nothing`() = outline { outcome ->
        showOutline()
        onNodeWithText("Cancel").performClick()
        waitForIdle()

        assertEquals(emptyList(), outcome.writes)
        assertEquals(1, outcome.dismissed)
    }

    @Test
    fun `the backdrop dialog holds its edits the same way`() = runComposeUiTest {
        val writes = mutableListOf<TextBackdrop>()
        setContent {
            MaterialTheme {
                TextBackdropDialog(backdrop = TextBackdrop(), onChange = { writes += it }, onDismiss = {})
            }
        }
        onNodeWithText("Border").performClick()
        waitForIdle()
        assertEquals(emptyList(), writes, "not yet")

        onNodeWithText("OK").performClick()
        waitForIdle()
        assertTrue(writes.single().border)
    }
}
