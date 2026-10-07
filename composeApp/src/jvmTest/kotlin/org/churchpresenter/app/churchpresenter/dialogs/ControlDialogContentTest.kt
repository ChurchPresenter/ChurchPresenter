@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.controlin.ANY
import org.churchpresenter.controlin.ControlMapping
import org.churchpresenter.controlin.ControlOutput
import org.churchpresenter.controlin.ControlSettings
import org.churchpresenter.controlin.ControlStatus
import org.churchpresenter.controlin.OutMessage
import org.churchpresenter.controlin.OutputEvents
import org.churchpresenter.controlin.PortState
import org.churchpresenter.controlin.Trigger
import org.churchpresenter.controlin.TriggerKinds
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The MIDI & OSC dialog: its ports, triggers with Learn, outputs, and that only Save keeps them. */
class ControlDialogContentTest {

    private val walkIn = ControlMapping("trigger1", "Walk in", Trigger(TriggerKinds.MIDI_NOTE, 1, 60))
    private val clear = ControlOutput("output1", OutputEvents.CLEAR, OutMessage(TriggerKinds.OSC, address = "/off"))

    private class Seen {
        var saved: ControlSettings? = null
        var learnWith: ((Trigger) -> Unit)? = null
        var cancelled = 0
        var dismissed = 0
    }

    private fun dialog(
        settings: ControlSettings = ControlSettings(),
        status: ControlStatus = ControlStatus(),
        learning: Boolean = false,
        block: ComposeUiTest.(Seen) -> Unit,
    ) = runComposeUiTest {
        val seen = Seen()
        setContent {
            MaterialTheme {
                Box(Modifier.size(900.dp, 1200.dp)) {
                    ControlDialogContent(
                        settings = settings,
                        data = ControlPanelData(status, listOf("Pad"), listOf("Desk")),
                        actions = ControlPanelActions(
                            isLearning = learning,
                            onLearn = { seen.learnWith = it },
                            onCancelLearn = { seen.cancelled++ },
                            onSave = { seen.saved = it },
                        ),
                        onDismiss = { seen.dismissed++ },
                    )
                }
            }
        }
        block(seen)
    }

    private fun ComposeUiTest.save(seen: Seen): ControlSettings {
        onNodeWithTag(CONTROL_SAVE_TAG).performClick()
        waitForIdle()
        return checkNotNull(seen.saved)
    }

    private fun ComposeUiTest.pick(field: String, option: String) {
        onNodeWithTag(controlFieldTag(field)).performClick()
        waitForIdle()
        // The menu's item, drawn last, not the same word elsewhere on the page.
        onAllNodesWithText(option).onLast().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.type(field: String, text: String) {
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(controlFieldTag(field)))).performTextReplacement(text)
        waitForIdle()
    }

    private fun ComposeUiTest.typeLabelled(label: String, text: String) {
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(controlFieldTag(label))))
            .performTextReplacement(text)
        waitForIdle()
    }

    private fun ComposeUiTest.tab(name: String) {
        onNodeWithTag(controlTabTag(name)).performClick()
        waitForIdle()
    }

    @Test
    fun `the ports are picked and typed, each says how it stands, and only Save keeps them`() = dialog(
        status = ControlStatus(PortState.OPEN, PortState.FAILED, PortState.OFF, PortState.OFF),
    ) { seen ->
        onNodeWithText("Open").assertExists()
        onNodeWithText("Could not open").assertExists()
        pick("MIDI input", "Pad")
        pick("MIDI output", "Desk")
        type("OSC listen port (0 = off)", "99999")
        onNodeWithText("Close").performClick()
        assertNull(seen.saved, "Close keeps nothing")
        assertEquals(
            ControlSettings(midiInput = "Pad", midiOutput = "Desk", oscInPort = 65_535),
            save(seen),
        )
        assertEquals(1, seen.dismissed)
    }

    @Test
    fun `a device that is unplugged still shows, and Off turns it off`() =
        dialog(ControlSettings(midiInput = "Old keyboard")) { seen ->
            onNodeWithText("Old keyboard").assertExists()
            pick("MIDI input", "Off")
            assertEquals("", save(seen).midiInput)
        }

    @Test
    fun `a trigger is added, learned, and saved with a name typed while it waited`() = dialog { seen ->
        tab("TRIGGERS")
        onNodeWithText("No triggers yet", substring = true).assertExists()
        onNodeWithTag(CONTROL_TRIGGER_ADD_TAG).performClick()
        waitForIdle()
        onNodeWithTag(CONTROL_LEARN_TAG).performClick()
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(CONTROL_TRIGGER_NAME_TAG)))
            .performTextReplacement("Walk in")
        waitForIdle()
        checkNotNull(seen.learnWith)(Trigger(TriggerKinds.OSC, address = "/cp/walk"))
        waitForIdle()
        onNodeWithTag(CONTROL_TRIGGER_SAVE_TAG).performClick()
        waitForIdle()
        val mapping = save(seen).mappings.single()
        assertEquals("Walk in", mapping.name, "what was typed while Learn waited is kept")
        assertEquals(Trigger(TriggerKinds.OSC, address = "/cp/walk"), mapping.trigger)
    }

    @Test
    fun `while learning it says so, and Learn again cancels`() = dialog(learning = true) { seen ->
        tab("TRIGGERS")
        onNodeWithTag(CONTROL_TRIGGER_ADD_TAG).performClick()
        waitForIdle()
        onNodeWithText("Waiting", substring = true).assertExists()
        val before = seen.cancelled
        onNodeWithTag(CONTROL_LEARN_TAG).performClick()
        assertEquals(before + 1, seen.cancelled)
    }

    @Test
    fun `each kind of trigger has its own fields`() = dialog(ControlSettings(mappings = listOf(walkIn))) { seen ->
        tab("TRIGGERS")
        onNodeWithTag(controlTriggerTag("trigger1")).performClick()
        waitForIdle()
        typeLabelled("Channel (0 = any)", "0")
        typeLabelled("Note", "200")
        onNodeWithTag(CONTROL_TRIGGER_SAVE_TAG).performClick()
        waitForIdle()
        assertEquals(Trigger(TriggerKinds.MIDI_NOTE, 0, 127), save(seen).mappings.single().trigger)

        onNodeWithTag(controlTriggerTag("trigger1")).performClick()
        waitForIdle()
        pick("Kind", "MIDI control change")
        typeLabelled("Controller", "7")
        typeLabelled("Value (blank = any)", "")
        onNodeWithTag(CONTROL_TRIGGER_SAVE_TAG).performClick()
        waitForIdle()
        assertEquals(Trigger(TriggerKinds.MIDI_CC, 1, 7, ANY), save(seen).mappings.single().trigger)

        onNodeWithTag(controlTriggerTag("trigger1")).performClick()
        waitForIdle()
        pick("Kind", "MIDI Show Control")
        typeLabelled("Command", "go")
        typeLabelled("Cue (blank = any)", "12")
        onNodeWithTag(CONTROL_TRIGGER_SAVE_TAG).performClick()
        waitForIdle()
        assertEquals(Trigger(TriggerKinds.MSC, address = "GO", cue = "12"), save(seen).mappings.single().trigger)

        onNodeWithTag(controlTriggerTag("trigger1")).performClick()
        waitForIdle()
        onNodeWithText("Delete").performClick()
        waitForIdle()
        assertTrue(save(seen).mappings.isEmpty())
    }

    @Test
    fun `an output is chosen by event and message, edited, and deleted`() =
        dialog(ControlSettings(outputs = listOf(clear))) { seen ->
            tab("OUTPUTS")
            onNodeWithText("Everything is cleared", substring = true).assertExists()
            onNodeWithTag(CONTROL_OUTPUT_ADD_TAG).performClick()
            waitForIdle()
            pick("When", "Take")
            typeLabelled("Note", "36")
            typeLabelled("Velocity / value", "90")
            onNodeWithTag(CONTROL_OUTPUT_SAVE_TAG).performClick()
            waitForIdle()
            assertEquals(
                ControlOutput("output2", OutputEvents.TAKE, OutMessage(TriggerKinds.MIDI_NOTE, 1, 36, 90)),
                save(seen).outputs.last(),
            )

            onNodeWithTag(controlOutputTag("output1")).performClick()
            waitForIdle()
            typeLabelled("OSC argument (optional)", "1")
            onNodeWithTag(CONTROL_OUTPUT_SAVE_TAG).performClick()
            waitForIdle()
            assertEquals("1", save(seen).outputs.first().message.argument)

            onNodeWithTag(controlOutputTag("output2")).performClick()
            waitForIdle()
            pick("Kind", "MIDI control change")
            typeLabelled("Controller", "20")
            onNodeWithTag(CONTROL_OUTPUT_SAVE_TAG).performClick()
            waitForIdle()
            assertEquals(OutMessage(TriggerKinds.MIDI_CC, 1, 20, 127), save(seen).outputs.last().message)

            onNodeWithTag(controlOutputTag("output1")).performClick()
            waitForIdle()
            onNodeWithText("Delete").performClick()
            waitForIdle()
            assertEquals(listOf("output2"), save(seen).outputs.map { it.id })
        }

    @Test
    fun `an id is never one already taken`() {
        assertEquals("trigger3", nextControlId("trigger", listOf("trigger1", "trigger2")))
        assertEquals("trigger3", nextControlId("trigger", listOf("trigger2")))
    }

    @Test
    fun `the open editors follow the dialog's inputs as they change`() = runComposeUiTest {
        var settings by mutableStateOf(ControlSettings(mappings = listOf(walkIn), outputs = listOf(clear)))
        var data by mutableStateOf(ControlPanelData(ControlStatus(), listOf("Pad"), listOf("Desk")))
        var learning by mutableStateOf(false)
        setContent {
            MaterialTheme {
                Box(Modifier.size(900.dp, 1200.dp)) {
                    ControlDialogContent(settings, data, ControlPanelActions(isLearning = learning), onDismiss = {})
                }
            }
        }
        waitForIdle()
        data = ControlPanelData(ControlStatus(PortState.OPEN, PortState.OPEN, PortState.OPEN, PortState.OPEN))
        waitForIdle()

        tab("TRIGGERS")
        onNodeWithTag(controlTriggerTag("trigger1")).performClick()
        waitForIdle()
        learning = true
        waitForIdle()
        onNodeWithText("Waiting", substring = true).assertExists()
        learning = false
        data = ControlPanelData(rows = listOf(ScheduleItem.AnnouncementItem("a1", "Welcome")))
        waitForIdle()
        pick("Kind", "MIDI control change")
        pick("Kind", "MIDI Show Control")
        pick("Kind", "OSC message")
        settings = ControlSettings(mappings = listOf(walkIn.copy(name = "Renamed")), outputs = listOf(clear))
        waitForIdle()
        onNodeWithText("Renamed", substring = true).assertExists()

        tab("OUTPUTS")
        onNodeWithTag(controlOutputTag("output1")).performClick()
        waitForIdle()
        pick("Kind", "MIDI note")
        pick("Kind", "MIDI control change")
        settings = ControlSettings(outputs = listOf(clear, clear.copy(id = "output2")))
        waitForIdle()
        onNodeWithTag(controlOutputTag("output2")).assertExists()
    }

    @Test
    fun `the ports tab follows the ports' state and the devices as they change`() = runComposeUiTest {
        var data by mutableStateOf(ControlPanelData())
        var settings by mutableStateOf(ControlSettings())
        setContent {
            MaterialTheme {
                Box(Modifier.size(900.dp, 1200.dp)) {
                    ControlDialogContent(settings, data, ControlPanelActions(), onDismiss = {})
                }
            }
        }
        waitForIdle()
        PortState.entries.forEach { state ->
            data = ControlPanelData(ControlStatus(state, state, state, state), listOf("Pad"), listOf("Desk"))
            waitForIdle()
        }
        settings = ControlSettings(
            midiInput = "Pad",
            midiOutput = "Gone",
            oscInPort = 9000,
            oscOutHost = "10.0.0.2",
            oscOutPort = 9001,
        )
        waitForIdle()
        onNodeWithText("Gone").assertExists()
        data = ControlPanelData()
        settings = ControlSettings()
        waitForIdle()
    }

    @Test
    fun `a new trigger being added survives the saved ones changing underneath it`() = runComposeUiTest {
        var settings by mutableStateOf(ControlSettings())
        var learning by mutableStateOf(false)
        setContent {
            MaterialTheme {
                Box(Modifier.size(900.dp, 1200.dp)) {
                    ControlDialogContent(
                        settings,
                        ControlPanelData(),
                        ControlPanelActions(isLearning = learning),
                        onDismiss = {},
                    )
                }
            }
        }
        waitForIdle()
        tab("TRIGGERS")
        onNodeWithTag(CONTROL_TRIGGER_ADD_TAG).performClick()
        waitForIdle()
        learning = true
        waitForIdle()
        learning = false
        settings = ControlSettings(mappings = listOf(walkIn))
        waitForIdle()
        onNodeWithTag(controlTriggerTag("trigger1")).assertExists()
        tab("OUTPUTS")
        onNodeWithTag(CONTROL_OUTPUT_ADD_TAG).performClick()
        waitForIdle()
        settings = ControlSettings(outputs = listOf(clear))
        waitForIdle()
        onNodeWithTag(controlOutputTag("output1")).assertExists()
    }
}
