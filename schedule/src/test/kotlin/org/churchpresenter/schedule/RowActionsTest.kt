@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule

import androidx.compose.material3.Text
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.showcontrol.MediaCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The row-action editor's model: new actions, their kinds, and the one-line summary. */
class RowActionsTest {

    private val song = ScheduleItem.SongItem("s1", 1, "Amazing Grace", "Hymns", "Hymns::1")
    private val heading = ScheduleItem.LabelItem("h", "Word", "#FFFFFF", "#000000")
    private val choices = ActionChoices(
        clearGroups = listOf("Clear text"),
        messages = listOf(MessageChoice("Nursery")),
        props = listOf("Logo"),
        lowerThirds = listOf("Pastor"),
        obsScenes = listOf("Wide"),
        companion = listOf(CompanionChoice("c1", "Deck")),
        macros = listOf("Walk in"),
    )

    @Test
    fun `a new action aims at the first thing on offer, and every kind round-trips`() {
        val made = ActionKind.entries.map { newAction(it, choices, listOf(heading, song)) }
        assertEquals(ActionKind.entries, made.map { it.kind })
        assertEquals(Action.ObsScene("Wide"), made[ActionKind.OBS_SCENE.ordinal])
        assertEquals(Action.GoLive("s1"), made[ActionKind.GO_LIVE.ordinal], "a heading is no row to go live with")
        assertEquals(Action.Timer(TimerModes.DURATION, seconds = 300), made[ActionKind.TIMER.ordinal])
        assertNull(Action.Unknown(JsonObject(emptyMap())).kind)
    }

    @Test
    fun `with nothing on offer a new action names nothing`() {
        val made = ActionKind.entries.map { newAction(it, ActionChoices(), emptyList()) }
        assertEquals(Action.ObsScene(""), made[ActionKind.OBS_SCENE.ordinal])
        assertEquals(Action.CompanionPress("", 0), made[ActionKind.COMPANION.ordinal])
        assertEquals(Action.ToPreview(""), made[ActionKind.PREVIEW.ordinal])
    }

    @Test
    fun `seconds read as typed, and only content rows are rows to put on`() {
        assertEquals("2", 2.0.secondsText())
        assertEquals("1.5", 1.5.secondsText())
        assertTrue(song.isContent())
        assertFalse(heading.isContent())
        assertFalse(ScheduleItem.CueItem(id = "c", action = "blank").isContent())
    }

    @Test
    fun `outside the app the editor is offered nothing`() = runComposeUiTest {
        setContent { Text(if (LocalActionChoices.current == ActionChoices()) "nothing" else "something") }
        onNodeWithText("nothing").assertExists()
    }

    @Test
    fun `the summary says the kind and what it names`() = runComposeUiTest {
        val actions = listOf(
            Action.ObsScene("Wide"), Action.Wait(2.0), Action.GoLive("s1"), Action.Clear("PROPS"),
            Action.Message(text = "Hi"), Action.AtemMacro(2), Action.ClearAll, Action.ToPreview("gone"),
            Action.LowerThird("Pastor"), Action.Prop("Logo"), Action.ClearGroup("Text"), Action.RunMacro("M"),
            Action.Media(MediaCommand.STOP), Action.Unknown(JsonObject(emptyMap())), Action.ToPreview("s1"),
            Action.GoLive("gone"),
        )
        setContent { Text(actions.map { actionSummary(it, listOf(song)) }.joinToString(" | ")) }
        onNodeWithText(
            "Switch OBS scene: Wide | Wait: 2 s | Go live with a row: 1 - Amazing Grace | Clear a layer: Props | " +
                "Show a message: Hi | Run an ATEM macro: 3 | Clear everything | Cue a row on Preview | " +
                "Run a lower third: Pastor | Switch a prop: Logo | Clear a group: Text | Run a macro: M | " +
                "Control media | From a newer version | Cue a row on Preview: 1 - Amazing Grace | Go live with a row",
        ).assertExists()
    }
}
