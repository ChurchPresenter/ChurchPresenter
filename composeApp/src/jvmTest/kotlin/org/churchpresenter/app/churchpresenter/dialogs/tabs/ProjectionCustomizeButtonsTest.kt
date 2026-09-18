@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The Customize dialog's closing row: Cancel puts the assignment back the way the dialog found it,
 * Apply saves and stays open, Done saves and closes.
 */
class ProjectionCustomizeButtonsTest {

    private val opened = ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN)

    private class Harness {
        var assignment by mutableStateOf(ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN))
        var applied = 0
        var dismissed = 0
    }

    private fun ComposeUiTest.open(harness: Harness, draft: Boolean = true) {
        setContent {
            val apply: (() -> Unit)? = if (draft) ({ harness.applied++ }) else null
            CompositionLocalProvider(LocalApplySettings provides apply) {
                OutputCustomizeDialog(
                    screenLabel = "Screen 1",
                    assignment = harness.assignment,
                    globalSettings = AppSettings(songSettings = SongSettings(lyricsFontSize = 61)),
                    onApply = { harness.assignment = it },
                    onDismiss = { harness.dismissed++ },
                )
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.click(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    @Test
    fun `Done runs the draft's apply and closes`() {
        val harness = Harness()
        runComposeUiTest {
            open(harness)
            click(CUSTOMIZE_DONE_TAG)
            assertEquals(1, harness.applied)
            assertEquals(1, harness.dismissed)
        }
    }

    @Test
    fun `Done with no draft to apply just closes`() {
        val harness = Harness()
        runComposeUiTest {
            open(harness, draft = false)
            click(CUSTOMIZE_DONE_TAG)
            assertEquals(0, harness.applied)
            assertEquals(1, harness.dismissed)
        }
    }

    @Test
    fun `Apply saves and stays open`() {
        val harness = Harness()
        runComposeUiTest {
            open(harness)
            click(CUSTOMIZE_APPLY_TAG)
            assertEquals(1, harness.applied)
            assertEquals(0, harness.dismissed)
        }
    }

    @Test
    fun `Apply is offered only where there is a draft to apply`() {
        runComposeUiTest {
            open(Harness(), draft = false)
            onNodeWithTag(CUSTOMIZE_APPLY_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun `Cancel puts back the assignment the dialog opened with and closes`() {
        val harness = Harness()
        runComposeUiTest {
            open(harness)
            click(CUSTOMIZE_OVERRIDE_SWITCH_TAG)
            assertNotEquals(opened, harness.assignment, "the switch wrote an override into the draft")
            click(CUSTOMIZE_CANCEL_TAG)
            assertEquals(opened, harness.assignment)
            assertEquals(1, harness.dismissed)
        }
    }

    @Test
    fun `Cancel leaves an untouched draft as it was`() {
        val harness = Harness()
        runComposeUiTest {
            open(harness)
            click(CUSTOMIZE_CANCEL_TAG)
            assertEquals(opened, harness.assignment)
            assertEquals(0, harness.applied)
            assertEquals(1, harness.dismissed)
        }
    }

    @Test
    fun `Cancel after Apply applies the restored assignment too`() {
        val harness = Harness()
        runComposeUiTest {
            open(harness)
            click(CUSTOMIZE_OVERRIDE_SWITCH_TAG)
            click(CUSTOMIZE_APPLY_TAG)
            click(CUSTOMIZE_CANCEL_TAG)
            assertEquals(opened, harness.assignment)
            assertEquals(2, harness.applied, "once for Apply, once more to undo it")
            assertEquals(1, harness.dismissed)
        }
    }
}
