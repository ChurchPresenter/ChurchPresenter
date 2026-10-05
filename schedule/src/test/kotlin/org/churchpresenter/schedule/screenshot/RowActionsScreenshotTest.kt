@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.schedule.ActionChoices
import org.churchpresenter.schedule.CompanionChoice
import org.churchpresenter.schedule.MessageChoice
import org.churchpresenter.schedule.ROW_ACTIONS_ADD_TAG
import org.churchpresenter.schedule.RowActionsDialogContent
import org.churchpresenter.schedule.ScheduleViewModel
import org.churchpresenter.schedule.scheduleTab
import org.churchpresenter.schedule.seedEveryItemType
import org.churchpresenter.schedule.setActions
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.sharedui.screenshot.stackedThemes
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.Test

/**
 * The row-action editor and the chip a row with actions shows, in both themes. The editor is shot
 * through its content, since a `DialogWindow` cannot be photographed headless.
 */
class RowActionsScreenshotTest {

    private val choices = ActionChoices(
        clearGroups = listOf("Clear text"),
        messages = listOf(MessageChoice("Nursery", listOf("number"))),
        props = listOf("Logo"),
        lowerThirds = listOf("Pastor", "Worship leader"),
        obsScenes = listOf("Wide", "Pulpit", "Band"),
        companion = listOf(CompanionChoice("c1", "Stream Deck")),
    )
    private val actions = listOf(
        Action.ObsScene("Pulpit"),
        Action.Wait(2.0),
        Action.LowerThird("Pastor"),
        Action.AtemKey(downstream = false, mixEffect = 0, keyer = 1, on = true),
        Action.Message(template = "Nursery", tokens = mapOf("number" to "12"), durationSeconds = 30),
    )
    private val row = ScheduleItem.LabelItem("x", "Sermon", "#FFFFFF", "#000000")

    private fun shoot(
        name: String,
        rootIndex: Int = 0,
        drive: ComposeUiTest.() -> Unit = {},
        content: @Composable () -> Unit,
    ) = stackedThemes(SECTION, name) { mode, file ->
        runComposeUiTest {
            setContent { ChurchPresenterTheme(themeMode = mode) { content() } }
            drive()
            waitForIdle()
            captureTo(file, rootIndex)
        }
    }

    @Test
    fun `the editor with five actions`() = shoot("editor") {
        Box(Modifier.size(620.dp, 760.dp)) { RowActionsDialogContent(row, actions, emptyList(), choices, {}, {}) }
    }

    @Test
    fun `the editor with none`() = shoot("empty") {
        Box(Modifier.size(620.dp, 300.dp)) { RowActionsDialogContent(row, emptyList(), emptyList(), choices, {}, {}) }
    }

    @Test
    fun `the Add action menu`() =
        shoot("addMenu", rootIndex = 1, drive = { onNodeWithTag(ROW_ACTIONS_ADD_TAG).performClick() }) {
        Box(Modifier.size(620.dp, 300.dp)) { RowActionsDialogContent(row, emptyList(), emptyList(), choices, {}, {}) }
    }

    @Test
    fun `a row with actions shows them under its title`() = stackedThemes(SECTION, "rowChip") { mode, file ->
        val seed: ScheduleViewModel.() -> Unit = {
            seedEveryItemType()
            val target = scheduleItems.first { it !is ScheduleItem.LabelItem }
            setActions(target.id, this@RowActionsScreenshotTest.actions.take(3))
        }
        scheduleTab(width = 360.dp, themeMode = mode, seed = seed) { _, _ -> captureTo(file) }
    }

    private companion object {
        const val SECTION = "rowActions"
    }
}
