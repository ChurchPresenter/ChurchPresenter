@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.showcontrol.MediaCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The row-action editor: adding every kind, editing each kind's fields, ordering, and saving. */
class RowActionsDialogContentTest {

    private val song = ScheduleItem.SongItem("s1", 1, "Amazing Grace", "Hymns", "Hymns::1")
    private val other = ScheduleItem.SongItem("s2", 2, "Be Thou My Vision", "Hymns", "Hymns::2")
    private val heading = ScheduleItem.LabelItem("h", "Word", "#FFFFFF", "#000000")
    private val rows = listOf(song, heading, other)

    private val choices = ActionChoices(
        clearGroups = listOf("Clear text", "Clear graphics"),
        messages = listOf(MessageChoice("Nursery", listOf("number")), MessageChoice("Parking")),
        props = listOf("Logo", "Badge"),
        lowerThirds = listOf("Pastor", "Guest"),
        obsScenes = listOf("Wide", "Band"),
        companion = listOf(CompanionChoice("c1", "Deck"), CompanionChoice("c2", "Mini")),
        macros = listOf("Walk in"),
    )

    private class Seen {
        var saved: List<Action>? = null
        var dismissed = 0
        var opened = 0
    }

    private fun editor(actions: List<Action>, block: ComposeUiTest.(Seen) -> Unit) = runComposeUiTest {
        val seen = Seen()
        setContent {
            MaterialTheme {
                Box(Modifier.size(900.dp, 1400.dp)) {
                    RowActionsDialogContent(
                        song,
                        actions,
                        rows,
                        choices.copy(onOpen = { seen.opened++ }),
                        onSave = { seen.saved = it },
                        onDismiss = { seen.dismissed++ },
                    )
                }
            }
        }
        block(seen)
    }

    /** Saves the draft and hands back what was saved. */
    private fun ComposeUiTest.save(seen: Seen): List<Action> {
        onNodeWithTag(ROW_ACTIONS_SAVE_TAG).performClick()
        waitForIdle()
        return checkNotNull(seen.saved)
    }

    private fun ComposeUiTest.pick(field: String, option: String) {
        onNodeWithTag(rowActionFieldTag(field)).performClick()
        waitForIdle()
        onNodeWithText(option).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.type(field: String, text: String) {
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(rowActionFieldTag(field))))
            .performTextReplacement(text)
        waitForIdle()
    }

    @Test
    fun `with no actions it says so, asks for its choices, and Close keeps nothing`() = editor(emptyList()) { seen ->
        onNodeWithText("No actions yet", substring = true).assertExists()
        onNodeWithText("Close").performClick()
        assertEquals(1, seen.dismissed)
        assertEquals(1, seen.opened)
        assertNull(seen.saved)
    }

    @Test
    fun `an action is added from the menu, aimed at the first thing on offer`() = editor(emptyList()) { seen ->
        listOf(ActionKind.OBS_SCENE, ActionKind.COMPANION, ActionKind.LOWER_THIRD).forEach { kind ->
            onNodeWithTag(ROW_ACTIONS_ADD_TAG).performClick()
            waitForIdle()
            onNodeWithTag(addActionKindTag(kind)).performClick()
            waitForIdle()
        }
        assertEquals(
            listOf(Action.ObsScene("Wide"), Action.CompanionPress("c1", 0), Action.LowerThird("Pastor")),
            save(seen),
        )
        assertEquals(1, seen.dismissed, "Save closes the editor")
    }

    @Test
    fun `actions move, and the ends cannot move further`() =
        editor(listOf(Action.NextItem, Action.ClearAll, Action.PreviousItem)) { seen ->
            onAllMoveButtons("Move Up")[0].assertIsNotEnabled()
            onAllMoveButtons("Move Down")[2].assertIsNotEnabled()
            onAllMoveButtons("Move Down")[0].performClick()
            onAllMoveButtons("Move Up")[2].performClick()
            onAllMoveButtons("Remove")[0].performClick()
            waitForIdle()
            assertEquals(listOf(Action.PreviousItem, Action.NextItem), save(seen))
        }

    @Test
    fun `scenes, presets, groups, layers and media are picked from what is on offer`() = editor(
        listOf(
            Action.ObsScene("Wide"), Action.LowerThird("Pastor"), Action.ClearGroup("Clear text"),
            Action.Clear("SLIDE"), Action.Media(MediaCommand.PLAY), Action.RunMacro("Walk in"),
        ),
    ) { seen ->
        pick("Scene", "Band")
        pick("Preset", "Guest")
        pick("Group", "Clear graphics")
        pick("Layer", "Props")
        pick("Mode", "Pause")
        assertEquals(
            listOf(
                Action.ObsScene("Band"), Action.LowerThird("Guest"), Action.ClearGroup("Clear graphics"),
                Action.Clear("PROPS"), Action.Media(MediaCommand.PAUSE), Action.RunMacro("Walk in"),
            ),
            save(seen),
        )
    }

    @Test
    fun `a name not on offer is typed`() = editor(listOf(Action.ObsScene("Gone"))) { seen ->
        type("Scene", "Balcony")
        assertEquals(listOf(Action.ObsScene("Balcony")), save(seen))
    }

    @Test
    fun `a wait takes seconds, and anything that is not a number is ignored`() =
        editor(listOf(Action.Wait(1.0))) { seen ->
        type("Seconds", "x")
        type("Seconds", "1.5")
        assertEquals(listOf(Action.Wait(1.5)), save(seen))
    }

    @Test
    fun `an ATEM key is upstream or downstream, counted from one, on or off`() =
        editor(listOf(Action.AtemKey(downstream = true), Action.AtemMacro(0))) { seen ->
            pick("Key", "Upstream")
            type("M/E", "2")
            type("Keyer", "0")
            type("Keyer", "3")
            pick("Switch", "Off")
            type("Macro", "4")
            assertEquals(
                listOf(Action.AtemKey(downstream = false, mixEffect = 1, keyer = 2, on = false), Action.AtemMacro(3)),
                save(seen),
            )
        }

    @Test
    fun `a Companion button is on a connection, counted from one`() =
        editor(listOf(Action.CompanionPress("c1", 0))) { seen ->
            pick("Connection", "Mini")
            type("Button", "5")
            assertEquals(listOf(Action.CompanionPress("c2", 4)), save(seen))
        }

    @Test
    fun `a message is typed, or a saved one with its blanks, for a time or until cleared`() =
        editor(listOf(Action.Message(text = ""))) { seen ->
            type("Words", "Hello")
            type("Stays up (s)", "45")
            assertEquals(listOf(Action.Message(text = "Hello", durationSeconds = 45)), save(seen))
            pick("Message", "Nursery")
            type("number", "12")
            type("Stays up (s)", "")
            assertEquals(
                listOf(Action.Message(text = "Hello", template = "Nursery", tokens = mapOf("number" to "12"))),
                save(seen),
            )
        }

    @Test
    fun `a prop goes on, off, or the other way`() = editor(listOf(Action.Prop("Logo", on = true))) { seen ->
        pick("Switch", "Toggle")
        assertEquals(listOf(Action.Prop("Logo", on = null)), save(seen))
        pick("Switch", "Off")
        assertEquals(listOf(Action.Prop("Logo", on = false)), save(seen))
    }

    @Test
    fun `a timer counts down seconds, to a time, or up`() =
        editor(listOf(Action.Timer(TimerModes.DURATION, 300))) { seen ->
        type("Seconds", "90")
        assertEquals(listOf(Action.Timer(TimerModes.DURATION, 90)), save(seen))
        pick("Mode", "Until a time")
        type("Until (HH:mm)", "10:30:00")
        assertEquals(listOf(Action.Timer(TimerModes.CLOCK, 90, "10:30")), save(seen))
        pick("Mode", "Count up")
        onNodeWithTag(rowActionFieldTag("Until (HH:mm)")).assertDoesNotExist()
        assertEquals(TimerModes.COUNT_UP, (save(seen).single() as Action.Timer).mode)
    }

    @Test
    fun `go live and preview pick a content row, and one from a newer build is shown but not edited`() = editor(
        listOf(Action.GoLive("s1"), Action.Unknown(JsonObject(emptyMap()))),
    ) { seen ->
        pick("Row", "2 - Be Thou My Vision")
        onNodeWithText("From a newer version", substring = true).assertExists()
        assertEquals(listOf(Action.GoLive("s2"), Action.Unknown(JsonObject(emptyMap()))), save(seen))
    }

    @Test
    fun `a row is cued on Preview by picking it, and a prop by its name`() = editor(
        listOf(Action.ToPreview("s1"), Action.Prop("Logo")),
    ) { seen ->
        pick("Row", "2 - Be Thou My Vision")
        pick("Prop", "Badge")
        assertEquals(listOf(Action.ToPreview("s2"), Action.Prop("Badge")), save(seen))
    }

    @Test
    fun `a list entry moves within its ends only`() {
        assertEquals(listOf(2, 1, 3), listOf(1, 2, 3).moved(1, 0))
        assertEquals(listOf(1, 2, 3), listOf(1, 2, 3).moved(0, -1))
        assertEquals(listOf(1, 2, 3), listOf(1, 2, 3).moved(2, 3))
    }

    private fun ComposeUiTest.onAllMoveButtons(tooltip: String) =
        onAllNodes(hasContentDescription(tooltip))
}
