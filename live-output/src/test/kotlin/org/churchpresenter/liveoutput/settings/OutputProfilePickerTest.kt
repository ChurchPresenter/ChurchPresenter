@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.BLANK_OUTPUT_PROFILE_ID
import org.churchpresenter.settings.OutputProfile
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

/** The per-output profile picker's "Don't use" item: offered only when a monitor can be marked. */
class OutputProfilePickerTest {

    private val profiles = listOf(OutputProfile(id = "p0", name = "Main"), OutputProfile(id = "p1", name = "Foyer"))

    private fun picker(
        onDontUse: (() -> Unit)?,
        picked: MutableList<String> = mutableListOf(),
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            MaterialTheme {
                OutputProfilePicker(
                    profiles = profiles,
                    activeProfileId = "p0",
                    onPick = { picked += it },
                    onDontUse = onDontUse,
                )
            }
        }
        onNodeWithText("Main").performClick()
        waitForIdle()
        block()
    }

    private fun ComposeUiTest.item(text: String) = onNode(hasClickAction() and hasTextExactly(text))

    @Test
    fun `Don't use is offered when the row has a monitor, and picking it marks it`() {
        var marked = 0
        val picked = mutableListOf<String>()
        picker(onDontUse = { marked++ }, picked = picked) {
            item("Blank").assertExists()
            item("Don't use").performClick()
            waitForIdle()

            assertEquals(1, marked)
            assertEquals(emptyList(), picked, "it is not a profile pick")
            item("Don't use").assertDoesNotExist()
        }
    }

    @Test
    fun `Don't use is not offered without a monitor to mark, and Blank still picks`() {
        val picked = mutableListOf<String>()
        picker(onDontUse = null, picked = picked) {
            item("Don't use").assertDoesNotExist()
            item("Blank").performClick()
            waitForIdle()

            assertEquals(listOf(BLANK_OUTPUT_PROFILE_ID), picked)
        }
    }

    @Test
    fun `a click outside closes the menu without picking anything`() {
        val picked = mutableListOf<String>()
        picker(onDontUse = null, picked = picked) {
            item("Blank").assertExists()
            onAllNodes(isRoot())[0].performTouchInput { click(bottomRight - Offset(2f, 2f)) }
            waitForIdle()
            item("Blank").assertDoesNotExist()
            assertEquals(emptyList(), picked)
        }
    }
}
