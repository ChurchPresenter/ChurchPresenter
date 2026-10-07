@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.controlin.ControlMapping
import org.churchpresenter.controlin.ControlOutput
import org.churchpresenter.controlin.OutMessage
import org.churchpresenter.controlin.OutputEvents
import org.churchpresenter.controlin.Trigger
import org.churchpresenter.controlin.TriggerKinds
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.settings.Macro
import org.churchpresenter.settings.MessageTemplate
import org.churchpresenter.settings.PropCorner
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.settings.PropKind
import org.churchpresenter.showcontrol.Action
import kotlin.test.Test

/**
 * The show-control dialogs with an editor open while what they are handed changes underneath:
 * another prop going on air, a message going live, the schedule's rows changing.
 */
class ShowControlDialogsRecomposeTest {

    private fun ComposeUiTest.framed(content: @Composable () -> Unit) = setContent {
        MaterialTheme { Box(Modifier.size(900.dp, 1200.dp)) { content() } }
    }

    @Test
    fun `the props editor follows the props and what is on air`() = runComposeUiTest {
        val logo = PropDefinition("p1", "Logo")
        var props by mutableStateOf(listOf(logo))
        var onAir by mutableStateOf(emptySet<String>())
        framed { PropsDialogContent(props, {}, onAir, { _, _ -> }, { null }, {}) }
        waitForIdle()
        onNodeWithTag(propEditTag("p1")).performClick()
        waitForIdle()
        PropKind.entries.forEach { kind ->
            onNodeWithTag(propKindTag(kind)).performClick()
            waitForIdle()
        }
        onAir = setOf("p1")
        props = listOf(logo, PropDefinition("p2", "Clock", PropKind.CLOCK))
        waitForIdle()
        onNodeWithTag(propSwitchTag("p2")).assertExists()
        onAir = emptySet()
        props = emptyList()
        waitForIdle()
    }

    @Test
    fun `the message dialog follows a message going live and clearing`() = runComposeUiTest {
        var templates by mutableStateOf(listOf(MessageTemplate("m1", "Car", "Car {plate} please")))
        var onAir by mutableStateOf<Cue.Message?>(null)
        framed { MessageDialogContent(templates, {}, onAir, {}, {}, {}) }
        waitForIdle()
        onNodeWithTag(messageTemplateTag("m1")).performClick()
        waitForIdle()
        onAir = Cue.Message("Car ABC please", "Car")
        waitForIdle()
        templates = templates + MessageTemplate("m2", "Kids", "Kids {room}")
        waitForIdle()
        onNodeWithTag(messageTemplateTag("m2")).assertExists()
        onAir = null
        templates = emptyList()
        waitForIdle()
    }

    @Test
    fun `the macro editor follows the macros and the schedule's rows`() = runComposeUiTest {
        val walkIn = Macro("macro1", "Walk in", listOf(Action.ClearAll))
        var macros by mutableStateOf(listOf(walkIn))
        var rows by mutableStateOf(emptyList<ScheduleItem>())
        var control by mutableStateOf<(() -> Unit)?>(null)
        framed { MacrosDialogContent(macros, rows, {}, {}, {}, control) }
        waitForIdle()
        onNodeWithTag(macroEditTag("macro1")).performClick()
        waitForIdle()
        rows = listOf(ScheduleItem.AnnouncementItem("a1", "Welcome"))
        control = {}
        waitForIdle()
        macros = listOf(walkIn.copy(name = "Doors"), Macro("macro2", "Out"))
        waitForIdle()
        onNodeWithTag(macroRunTag("macro2")).assertExists()
        control = null
        macros = emptyList()
        waitForIdle()
    }

    @Test
    fun `the clear group editor follows the groups`() = runComposeUiTest {
        val gfx = ClearGroup("g1", "Graphics", listOf(Layer.GRAPHICS.name))
        var groups by mutableStateOf(listOf(gfx))
        framed { ClearGroupsDialogContent(groups, {}, {}, {}) }
        waitForIdle()
        onNodeWithTag(clearGroupEditTag("g1")).performClick()
        waitForIdle()
        onNodeWithTag(clearGroupLayerTag(Layer.SLIDE)).performClick()
        waitForIdle()
        groups = listOf(gfx.copy(name = "Gfx"), ClearGroup("g2", "Text", listOf(Layer.SLIDE.name)))
        waitForIdle()
        onNodeWithTag(clearGroupButtonTag("g2")).assertExists()
        onNodeWithText("Gfx", substring = true).assertExists()
        groups = emptyList()
        waitForIdle()
    }

    @Test
    fun `the trigger editor follows the triggers, the rows and Learn as they change`() = runComposeUiTest {
        val note = ControlMapping("trigger1", "Walk in", Trigger(TriggerKinds.MIDI_NOTE, 1, 60))
        var mappings by mutableStateOf(listOf(note))
        var rows by mutableStateOf(emptyList<ScheduleItem>())
        var learningOn by mutableStateOf(false)
        framed {
            ControlTriggersTab(mappings, rows, TriggerLearning(learningOn, {}, {})) { mappings = it }
        }
        waitForIdle()
        onNodeWithTag(controlTriggerTag("trigger1")).performClick()
        waitForIdle()
        listOf(
            Trigger(TriggerKinds.MIDI_CC, 2, 7, 1),
            Trigger(TriggerKinds.MSC, address = "GO", cue = "3"),
            Trigger(TriggerKinds.OSC, address = "/cp/go"),
            Trigger(TriggerKinds.MIDI_NOTE, 3, 61),
        ).forEach { trigger ->
            mappings = listOf(note.copy(trigger = trigger), note.copy(id = "trigger2", name = ""))
            learningOn = !learningOn
            rows = rows + ScheduleItem.AnnouncementItem("a${rows.size}", "Row ${rows.size}")
            waitForIdle()
            onNodeWithTag(controlTriggerTag("trigger1")).performClick()
            waitForIdle()
        }
        mappings = emptyList()
        waitForIdle()
    }

    @Test
    fun `the output editor follows the outputs as they change`() = runComposeUiTest {
        val off = ControlOutput("output1", OutputEvents.CLEAR, OutMessage(TriggerKinds.OSC, address = "/off"))
        var outputs by mutableStateOf(listOf(off))
        framed { ControlOutputsTab(outputs) { outputs = it } }
        waitForIdle()
        onNodeWithTag(controlOutputTag("output1")).performClick()
        waitForIdle()
        listOf(
            OutMessage(TriggerKinds.MIDI_NOTE, 1, 36, 90),
            OutMessage(TriggerKinds.MIDI_CC, 2, 20, 127),
            OutMessage(TriggerKinds.OSC, address = "/on", argument = "1"),
        ).forEach { message ->
            outputs = listOf(off.copy(message = message, on = OutputEvents.TAKE), off.copy(id = "output2"))
            waitForIdle()
            onNodeWithTag(controlOutputTag("output1")).performClick()
            waitForIdle()
        }
        outputs = emptyList()
        waitForIdle()
    }

    @Test
    fun `the prop editor follows each kind and corner of the prop it edits`() = runComposeUiTest {
        var props by mutableStateOf(listOf(PropDefinition("p1", "Logo")))
        framed { PropsDialogContent(props, { props = it }, emptySet(), { _, _ -> }, { null }, {}) }
        waitForIdle()
        PropKind.entries.forEach { kind ->
            PropCorner.entries.forEach { corner ->
                props = listOf(PropDefinition("p1", "Logo ${kind.name}", kind, corner))
                waitForIdle()
                onNodeWithTag(propEditTag("p1")).performClick()
                waitForIdle()
                onNodeWithTag(propCornerTag(corner)).performClick()
                waitForIdle()
            }
        }
    }
}
