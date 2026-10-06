@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.showcontrol.Action
import kotlin.test.Test
import kotlin.test.assertEquals

/** The action list as the macro and trigger editors use it, and the row actions following dev mode. */
class ActionListEditorTest {

    private val song = ScheduleItem.SongItem("s1", 1, "Amazing Grace", "Hymns", "Hymns::1")

    @Test
    fun `the list is edited in place, and the choices are asked for once`() = runComposeUiTest {
        var actions by mutableStateOf(listOf<Action>(Action.NextItem))
        var opened = 0
        setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalActionChoices provides ActionChoices(obsScenes = listOf("Wide"), onOpen = { opened++ }),
                ) {
                    Box(Modifier.size(800.dp, 900.dp)) { ActionListEditor(actions, listOf(song), { actions = it }) }
                }
            }
        }
        onNodeWithTag(ROW_ACTIONS_ADD_TAG).performClick()
        waitForIdle()
        onNodeWithTag(addActionKindTag(ActionKind.OBS_SCENE)).performClick()
        waitForIdle()
        assertEquals(listOf(Action.NextItem, Action.ObsScene("Wide")), actions)

        onAllNodesWithTag(rowActionCardTag(1)).assertCountEquals(1)
        actions = actions.reversed()
        waitForIdle()
        assertEquals(1, opened)

        actions = emptyList()
        waitForIdle()
        onNodeWithText("No actions yet", substring = true).assertExists()
    }

    @Test
    fun `a row offers its actions button and chip only while show control is on`() {
        val seed: ScheduleViewModel.() -> Unit = {
            seedEveryItemType()
            setActions(scheduleItems.first { it !is ScheduleItem.LabelItem }.id, listOf(Action.ClearAll))
        }
        scheduleTab(legacyRowActions = true, seed = seed, showControl = false) { _, _ ->
            onAllNodesWithTag(SCHEDULE_ROW_ACTIONS_BUTTON_TAG).assertCountEquals(0)
            onAllNodesWithTag(SCHEDULE_ROW_ACTIONS_CHIP_TAG).assertCountEquals(0)
        }
        scheduleTab(legacyRowActions = true, seed = seed, showControl = true) { _, _ ->
            onAllNodesWithTag(SCHEDULE_ROW_ACTIONS_CHIP_TAG).assertCountEquals(1)
            check(onAllNodesWithTag(SCHEDULE_ROW_ACTIONS_BUTTON_TAG).fetchSemanticsNodes().isNotEmpty()) {
                "the rows offer their Actions button"
            }
        }
    }
}
