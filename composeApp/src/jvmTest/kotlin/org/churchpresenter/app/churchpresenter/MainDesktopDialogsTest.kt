@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.schedule.ScheduleTabActions
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

internal class MainDesktopDialogsTest : MainDesktopScopeHarness() {

    private val label = ScheduleItem.LabelItem("label-1", "Offering", "#FFFFFF", "#000000")

    @Test
    fun `a new label is added and closes the sheet`() {
        val added = mutableListOf<String>()
        pane(
            actions = ScheduleTabActions(addLabel = { text, _, _ -> added += text }),
            content = {},
        ) { scope ->
            scope.state.showAddLabelDialog = true
            scope.confirmLabel("Welcome", "#FFFFFF", "#000000")

            assertEquals(listOf("Welcome"), added)
            assertFalse(scope.state.showAddLabelDialog)
        }
    }

    @Test
    fun `an edited label is updated in place and forgotten`() {
        val updated = mutableListOf<Pair<String, String>>()
        pane(
            actions = ScheduleTabActions(updateLabel = { id, text, _, _ -> updated += id to text }),
            content = { MainDesktopDialogs() },
        ) { scope ->
            scope.state.editingLabelItem = label
            waitForIdle()
            scope.confirmLabel("Tithes", "#FFFFFF", "#000000")

            assertEquals(listOf("label-1" to "Tithes"), updated)
            assertNull(scope.state.editingLabelItem)
        }
    }

    @Test
    fun `closing the label sheet forgets the label it was editing`() =
        pane(content = {}) { scope ->
            scope.state.editingLabelItem = label
            scope.state.showAddLabelDialog = true
            scope.closeLabelDialog()

            assertFalse(scope.state.showAddLabelDialog)
            assertNull(scope.state.editingLabelItem)
        }

    @Test
    fun `a preset sheet knows the saved names, and cancelling it clears the item`() {
        val calendar = File(dir, "calendar").apply { mkdirs() }
        PresetStore(calendar).add("Existing", label)
        val settings = settings().copy(calendarStorageDirectory = calendar.absolutePath)
        pane(appSettings = settings, content = { MainDesktopDialogs() }) { scope ->
            scope.state.presetToSave = label
            waitForIdle()
            onNodeWithText("Cancel").performClick()
            waitForIdle()

            assertNull(scope.state.presetToSave)
            assertEquals(1, PresetStore(calendar).load().presets.size)
        }
    }

    @Test
    fun `a named preset is saved to the calendar's store`() {
        val calendar = File(dir, "calendar").apply { mkdirs() }
        val settings = settings().copy(calendarStorageDirectory = calendar.absolutePath)
        pane(appSettings = settings, content = { MainDesktopDialogs() }) { scope ->
            scope.state.presetToSave = label
            waitForIdle()
            onNode(hasSetTextAction()).performTextReplacement("Offering slide")
            waitForIdle()
            onNodeWithText("OK").performClick()
            waitForIdle()

            assertNull(scope.state.presetToSave)
            assertEquals(listOf("Offering slide"), PresetStore(calendar).load().presets.map { it.name })
        }
    }

    @Test
    fun `the crash feedback prompt is sent and closes`() =
        pane(showCrashFeedback = true, content = { MainDesktopDialogs() }) { scope ->
            onNodeWithText("What were you doing?").performTextInput("Projecting a song")
            waitForIdle()
            onNodeWithText("Send report").performClick()
            waitForIdle()

            assertFalse(scope.state.showCrashFeedback)
        }

    @Test
    fun `the crash feedback prompt can be put off`() =
        pane(showCrashFeedback = true, content = { MainDesktopDialogs() }) { scope ->
            onNodeWithText("Not now").performClick()
            waitForIdle()

            assertFalse(scope.state.showCrashFeedback)
        }
}
