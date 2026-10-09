@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.controlin.ANY
import org.churchpresenter.controlin.ControlMapping
import org.churchpresenter.controlin.ControlSettings
import org.churchpresenter.controlin.Trigger
import org.churchpresenter.controlin.TriggerKinds
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The trigger editor's fields that the dialog's own suite leaves alone: a control change's channel
 * and an exact value, clamped to MIDI's range, and an OSC address.
 */
class ControlTriggerFieldsTest {

    private fun triggers(mapping: ControlMapping, block: ComposeUiTest.(() -> ControlSettings?) -> Unit) =
        runComposeUiTest {
            var saved: ControlSettings? = null
            setContent {
                MaterialTheme {
                    Box(Modifier.size(900.dp, 1200.dp)) {
                        ControlDialogContent(
                            settings = ControlSettings(mappings = listOf(mapping)),
                            data = ControlPanelData(),
                            actions = ControlPanelActions(onSave = { saved = it }),
                            onDismiss = {},
                        )
                    }
                }
            }
            onNodeWithTag(controlTabTag("TRIGGERS")).performClick()
            waitForIdle()
            onNodeWithTag(controlTriggerTag(mapping.id)).performClick()
            waitForIdle()
            block { saved }
        }

    private fun ComposeUiTest.type(label: String, text: String) {
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(controlFieldTag(label)))).performTextReplacement(text)
        waitForIdle()
    }

    private fun ComposeUiTest.saveAll(saved: () -> ControlSettings?): ControlSettings {
        onNodeWithTag(CONTROL_TRIGGER_SAVE_TAG).performClick()
        waitForIdle()
        onNodeWithTag(CONTROL_SAVE_TAG).performClick()
        waitForIdle()
        return checkNotNull(saved())
    }

    @Test
    fun `a control change takes its channel and an exact value, clamped to MIDI's range`() =
        triggers(ControlMapping("trigger1", "Fader", Trigger(TriggerKinds.MIDI_CC, 1, 7, ANY))) { saved ->
            type("Channel (0 = any)", "40")
            type("Value (blank = any)", "300")
            assertEquals(Trigger(TriggerKinds.MIDI_CC, 16, 7, 127), saveAll(saved).mappings.single().trigger)
        }

    @Test
    fun `an OSC trigger takes its address`() =
        triggers(ControlMapping("trigger1", "Go", Trigger(TriggerKinds.MIDI_NOTE, 1, 60))) { saved ->
            onNodeWithTag(controlFieldTag("Kind")).performClick()
            waitForIdle()
            onAllNodesWithText("OSC message").onLast().performClick()
            waitForIdle()
            type("OSC address", "/go")
            assertEquals(Trigger(TriggerKinds.OSC, address = "/go"), saveAll(saved).mappings.single().trigger)
        }
}
