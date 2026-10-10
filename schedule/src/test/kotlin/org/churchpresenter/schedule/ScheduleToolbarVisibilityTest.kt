@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.sharedui.testing.renderedText

/**
 * Turning schedule toolbar buttons off from the panel's options menu.
 *
 * Two things are easy to get wrong here and invisible when they are: a pill divider left behind
 * with nothing on one side of it, and an empty pill still taking a row's worth of header when every
 * button in it is gone. Both are asserted directly rather than through a screenshot.
 */
class ScheduleToolbarVisibilityTest {

    private val all = ScheduleToolbarButton.entries.map { it.name }.toSet()

    @Test
    fun `a hidden button leaves the toolbar`() =
        scheduleTab(hiddenToolbarButtons = setOf(ScheduleToolbarButton.CLEAR.name), seed = { seedService() }) { _, _ ->
            button(ScheduleLabel.NEW).assertExists("the buttons that were left alone stay")
            assertEquals(0, buttonCount(ScheduleLabel.CLEAR), "and the hidden one is gone")
        }

    @Test
    fun `the menu asks the parent to toggle a button`() =
        scheduleTab(seed = { seedService() }) { _, reports ->
            taggedButton(ScheduleToolbarTags.OPTIONS).performClick()
            waitForIdle()
            taggedButton(ScheduleToolbarButton.CLEAR.menuTag).performClick()
            waitForIdle()

            assertEquals(listOf(ScheduleToolbarButton.CLEAR), reports.toolbarButtonToggles)
        }

    @Test
    fun `each menu entry is checked while its button is shown`() =
        scheduleTab(hiddenToolbarButtons = setOf(ScheduleToolbarButton.CLEAR.name), seed = { seedService() }) { _, _ ->
            taggedButton(ScheduleToolbarTags.OPTIONS).performClick()
            waitForIdle()

            taggedButton(ScheduleToolbarButton.NEW.menuTag).assertIsOn()
            taggedButton(ScheduleToolbarButton.CLEAR.menuTag).assertIsOff()
        }

    @Test
    fun `the menu stays open between picks`() =
        scheduleTab(seed = { seedService() }) { _, reports ->
            taggedButton(ScheduleToolbarTags.OPTIONS).performClick()
            waitForIdle()
            taggedButton(ScheduleToolbarButton.CLEAR.menuTag).performClick()
            waitForIdle()
            taggedButton(ScheduleToolbarButton.UNDO.menuTag).performClick()
            waitForIdle()

            assertEquals(listOf(ScheduleToolbarButton.CLEAR, ScheduleToolbarButton.UNDO), reports.toolbarButtonToggles)
        }

    @Test
    fun `the menu counts the buttons shown and hides Show all when nothing is hidden`() =
        scheduleTab(seed = { seedService() }) { _, _ ->
            taggedButton(ScheduleToolbarTags.OPTIONS).performClick()
            waitForIdle()

            val total = ScheduleToolbarButton.entries.size
            onNodeWithText("$total of $total").assertExists()
            assertTrue(onAllNodesWithText("Show all").fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `Show all asks the parent to bring back each hidden button, and only those`() =
        scheduleTab(
            hiddenToolbarButtons = setOf(ScheduleToolbarButton.UNDO.name, ScheduleToolbarButton.CLEAR.name),
            seed = { seedService() },
        ) { _, reports ->
            taggedButton(ScheduleToolbarTags.OPTIONS).performClick()
            waitForIdle()
            val total = ScheduleToolbarButton.entries.size
            onNodeWithText("${total - 2} of $total").assertExists()
            onNodeWithText("Show all").performClick()
            waitForIdle()

            assertEquals(listOf(ScheduleToolbarButton.CLEAR, ScheduleToolbarButton.UNDO), reports.toolbarButtonToggles)
        }

    @Test
    fun `the title row readouts are hideable too`() =
        scheduleTab(
            hiddenToolbarButtons = setOf(ScheduleToolbarButton.ZOOM.name, ScheduleToolbarButton.ITEM_COUNT.name),
            seed = { seedService() },
        ) { vm, _ ->
            assertEquals(0, buttonCount(ScheduleLabel.ZOOM_IN), "the zoom pill goes with them")
            val nonLabelItems = vm.scheduleItems.count { it !is ScheduleItem.LabelItem }
            assertFalse(
                renderedText().any { it.contains("$nonLabelItems items") },
                "and so does the item count",
            )
        }

    @Test
    fun `hiding every button removes the toolbar row rather than leaving an empty pill`() =
        scheduleTab(hiddenToolbarButtons = all, seed = { seedService() }) { _, _ ->
            assertFalse(scheduleToolbarVisible(all), "nothing is left to draw")
            listOf(ScheduleLabel.NEW, ScheduleLabel.CLEAR, ScheduleLabel.ADD_LABEL).forEach {
                assertEquals(0, buttonCount(it), "$it must be gone")
            }
            taggedButton(ScheduleToolbarTags.OPTIONS).assertExists("but the options menu stays reachable")
        }

    @Test
    fun `a divider only survives while it still separates two groups`() {
        assertTrue(scheduleToolbarDividerVisible(0, emptySet()), "file | history")
        assertTrue(scheduleToolbarDividerVisible(1, emptySet()), "history | extras")

        val noHistory = setOf(ScheduleToolbarButton.UNDO.name, ScheduleToolbarButton.REDO.name)
        assertTrue(scheduleToolbarDividerVisible(0, noHistory), "file | extras — the divider still divides")
        assertFalse(scheduleToolbarDividerVisible(1, noHistory), "nothing on its left any more")

        val onlyFile = all - setOf(
            ScheduleToolbarButton.NEW.name, ScheduleToolbarButton.OPEN.name,
            ScheduleToolbarButton.SAVE.name, ScheduleToolbarButton.CLEAR.name,
        )
        assertFalse(scheduleToolbarDividerVisible(0, onlyFile), "one group left, so no dividers at all")
        assertFalse(scheduleToolbarDividerVisible(1, onlyFile))
    }
}
