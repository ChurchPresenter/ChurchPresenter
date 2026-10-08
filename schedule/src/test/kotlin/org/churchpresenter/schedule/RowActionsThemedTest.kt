@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test

/**
 * The row-action editor and a row's actions chip, kept on screen while the theme and the actions
 * change under them. Not shot: they are dev mode only, and screenshots show production (AGENT.md).
 */
class RowActionsThemedTest {

    private val row = ScheduleItem.LabelItem("x", "Sermon", "#FFFFFF", "#000000")
    private val choices = ActionChoices(
        clearGroups = listOf("Clear text"),
        messages = listOf(MessageChoice("Nursery", listOf("number"))),
        props = listOf("Logo"),
        lowerThirds = listOf("Pastor", "Worship leader"),
        obsScenes = listOf("Wide", "Pulpit"),
        companion = listOf(CompanionChoice("c1", "Stream Deck")),
    )
    private val first = listOf(
        Action.ObsScene("Pulpit"),
        Action.Wait(2.0),
        Action.LowerThird("Pastor"),
        Action.AtemKey(downstream = false, mixEffect = 0, keyer = 1, on = true),
        Action.Message(template = "Nursery", tokens = mapOf("number" to "12"), durationSeconds = 30),
        Action.Prop("Logo", on = true),
        Action.Timer(TimerModes.DURATION, seconds = 300),
        Action.CompanionPress("c1", 3),
    )
    private val second = listOf(
        Action.ObsScene("Gone"),
        Action.AtemKey(downstream = true, keyer = 0, on = false),
        Action.Message(text = "Typed", durationSeconds = 0),
        Action.Prop("Logo", on = null),
        Action.Timer(TimerModes.CLOCK, until = "10:30"),
        Action.CompanionPress("other", 0),
    )

    @Test
    fun `the editor keeps every card through a change of theme and of actions`() = runComposeUiTest {
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var actions by mutableStateOf<List<Action>>(first)
        setContent {
            ChurchPresenterTheme(themeMode = mode) {
                Box(Modifier.size(900.dp, 1600.dp)) {
                    RowActionsDialogContent(row, actions, emptyList(), choices, {}, {})
                }
            }
        }
        onNodeWithTag(rowActionCardTag(first.lastIndex)).assertExists()
        mode = ThemeMode.DARK
        waitForIdle()
        actions = second
        waitForIdle()
        onNodeWithTag(rowActionCardTag(second.lastIndex)).assertExists()
        onAllNodesWithTag(rowActionCardTag(first.lastIndex)).assertCountEquals(0)
        actions = emptyList()
        waitForIdle()
        onAllNodesWithTag(rowActionCardTag(0)).assertCountEquals(0)
    }

    @Test
    fun `a row with actions keeps its chip in either theme`() {
        val seed: ScheduleViewModel.() -> Unit = {
            seedEveryItemType()
            setActions(scheduleItems.first { it !is ScheduleItem.LabelItem }.id, first.take(3))
        }
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
            scheduleTab(width = 360.dp, themeMode = mode, seed = seed, showControl = true) { _, _ ->
                onAllNodesWithTag(SCHEDULE_ROW_ACTIONS_CHIP_TAG).assertCountEquals(1)
            }
        }
    }

    @Test
    fun `the editor the macro and trigger dialogs use keeps its cards through the same changes`() =
        runComposeUiTest {
            var mode by mutableStateOf(ThemeMode.LIGHT)
            var actions by mutableStateOf<List<Action>>(first)
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    CompositionLocalProvider(LocalActionChoices provides choices) {
                        Box(Modifier.size(900.dp, 1600.dp)) { ActionListEditor(actions, emptyList(), { actions = it }) }
                    }
                }
            }
            onNodeWithTag(rowActionCardTag(first.lastIndex)).assertExists()
            mode = ThemeMode.DARK
            waitForIdle()
            actions = second
            waitForIdle()
            onNodeWithTag(rowActionCardTag(second.lastIndex)).assertExists()
            actions = emptyList()
            waitForIdle()
            onAllNodesWithTag(rowActionCardTag(0)).assertCountEquals(0)
            onNodeWithTag(ROW_ACTIONS_ADD_TAG).assertExists()
        }
}
