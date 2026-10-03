@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.calendar.model.suggestedPresetName
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Naming a preset before it is saved: the name it starts with, what may be typed, the warning when
 * a preset of that name is about to be replaced, and the three ways out -- OK, Enter and Cancel.
 */
class SavePresetDialogTest {

    private val scene = ScheduleItem.SceneItem("s", "scene-1", "Welcome Loop")

    private class Outcome {
        val confirmed = mutableListOf<String>()
        var dismissed = 0
    }

    private fun sheet(existing: List<String> = emptyList(), body: ComposeUiTest.(Outcome) -> Unit) = runComposeUiTest {
        val outcome = Outcome()
        setContent {
            MaterialTheme {
                SavePresetDialogContent(scene, existing, { outcome.confirmed += it }, { outcome.dismissed++ })
            }
        }
        waitForIdle()
        body(outcome)
    }

    private fun ComposeUiTest.field() = onNode(hasSetTextAction())

    @Test
    fun `the name starts as the item's own, and OK saves it and closes`() = sheet { outcome ->
        field().assertExists()
        onNodeWithText(suggestedPresetName(scene)).assertExists()

        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(listOf(suggestedPresetName(scene)), outcome.confirmed)
        assertEquals(1, outcome.dismissed)
    }

    @Test
    fun `a name already used is said to be replaced, whatever its case`() =
        sheet(existing = listOf("Opener")) { outcome ->
            field().performTextReplacement("opener ")
            waitForIdle()

            onNodeWithText("A preset called Opener already exists and will be replaced.").assertExists()
            field().performKeyInput { pressKey(Key.Enter) }
            waitForIdle()
            assertEquals(listOf("opener"), outcome.confirmed, "trimmed on the way out")
        }

    @Test
    fun `what cannot be in a name is dropped as it is typed, and a blank name cannot be saved`() = sheet { outcome ->
        field().performTextReplacement("Sunday 😀Choir")
        waitForIdle()
        onNodeWithText("Sunday Choir").assertExists()

        field().performTextReplacement("   ")
        waitForIdle()
        onNodeWithText("OK").performClick()
        field().performKeyInput { pressKey(Key.NumPadEnter) }
        waitForIdle()
        assertTrue(outcome.confirmed.isEmpty(), "nothing to save")

        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEquals(1, outcome.dismissed)
    }

    @Test
    fun `with nothing to save the dialog draws nothing`() = runComposeUiTest {
        setContent { MaterialTheme { SavePresetDialog(null, emptyList(), {}, {}) } }
        waitForIdle()
        assertEquals(1, onAllNodes(isRoot()).fetchSemanticsNodes().size, "no dialog window opened")
    }
}
