@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two schedule card layouts: the default hover overlay, and the legacy line of buttons under
 * the title.
 *
 * The legacy line exists because the overlay is drawn *over* the title's right-hand end, which is
 * also where an operator double-clicks to send an item live — the second click lands on whichever
 * button the overlay put there and the item reorders instead. So what matters here is not that the
 * legacy row renders, but that it carries the same actions and that only one of the two layouts is
 * ever present.
 */
class ScheduleLegacyRowActionsTest {

    private companion object {
        /** One song, one Bible verse, one website — enough for a move to be observable. */
        fun titlesOf(items: List<ScheduleItem>): List<String> = items.map { it.displayText }
    }

    @Test
    fun `by default the actions are the hover overlay and there is no legacy line`() =
        scheduleTab(seed = { seedService() }) { _, _ ->
            assertTrue(
                onAllNodesWithTag(SCHEDULE_ROW_ACTIONS_TAG).fetchSemanticsNodes().isNotEmpty(),
                "the default layout keeps the hover overlay",
            )
            assertEquals(
                0,
                onAllNodesWithTag(SCHEDULE_ROW_LEGACY_ACTIONS_TAG).fetchSemanticsNodes().size,
                "and draws no legacy action line",
            )
        }

    @Test
    fun `the legacy layout replaces the overlay rather than adding to it`() =
        scheduleTab(legacyRowActions = true, seed = { seedService() }) { vm, _ ->
            assertEquals(
                vm.scheduleItems.size,
                onAllNodesWithTag(SCHEDULE_ROW_LEGACY_ACTIONS_TAG).fetchSemanticsNodes().size,
                "every card gets its own action line",
            )
            assertEquals(
                0,
                onAllNodesWithTag(SCHEDULE_ROW_ACTIONS_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).size,
                "and nothing is left painting over the title",
            )
        }

    @Test
    fun `the legacy line moves an item down`() =
        scheduleTab(legacyRowActions = true, seed = { seedService() }) { vm, _ ->
            val before = titlesOf(vm.scheduleItems)
            buttonAt(ScheduleLabel.MOVE_DOWN, 0).performClick()
            waitForIdle()

            val after = titlesOf(vm.scheduleItems)
            assertEquals(before[1], after[0], "the second item takes the first's place")
            assertEquals(before[0], after[1], "and the first moves down one")
        }

    @Test
    fun `the legacy line moves an item up`() =
        scheduleTab(legacyRowActions = true, seed = { seedService() }) { vm, _ ->
            val before = titlesOf(vm.scheduleItems)
            buttonAt(ScheduleLabel.MOVE_UP, 1).performClick()
            waitForIdle()

            val after = titlesOf(vm.scheduleItems)
            assertEquals(before[1], after[0], "the second item is now first")
            assertEquals(before[0], after[1])
        }

    @Test
    fun `the legacy line removes an item`() =
        scheduleTab(legacyRowActions = true, seed = { seedService() }) { vm, _ ->
            val before = titlesOf(vm.scheduleItems)
            buttonAt(ScheduleLabel.REMOVE, 0).performClick()
            waitForIdle()

            assertEquals(before.drop(1), titlesOf(vm.scheduleItems), "the first card is gone")
        }

    @Test
    fun `the legacy line sends an item live`() =
        scheduleTab(legacyRowActions = true, seed = { seedService() }) { vm, reports ->
            buttonAt(ScheduleLabel.GO_LIVE, 0).performClick()
            waitForIdle()

            assertEquals(
                listOf(vm.scheduleItems.first { it !is ScheduleItem.LabelItem }.displayText),
                reports.presented.map { it.displayText },
                "Go Live presents the item it belongs to",
            )
        }

    @Test
    fun `the legacy line opens the note editor`() =
        scheduleTab(legacyRowActions = true, seed = { seedService() }) { _, _ ->
            buttonAt(ScheduleLabel.NOTE, 0).performClick()
            waitForIdle()

            button(ScheduleLabel.NOTE_SAVE).assertExists("the note editor opens from the legacy line too")
        }

    @Test
    fun `a label row keeps its edit button in the legacy line`() =
        scheduleTab(legacyRowActions = true, seed = { seedService() }) { _, reports ->
            button(ScheduleLabel.EDIT_LABEL).performClick()
            waitForIdle()

            assertEquals(1, reports.editedLabels.size, "the section's own action is still reachable")
        }

    @Test
    fun `the options menu toggles the layout`() =
        scheduleTab(seed = { seedService() }) { _, reports ->
            taggedButton(ScheduleToolbarTags.OPTIONS).performClick()
            waitForIdle()
            taggedButton(ScheduleToolbarTags.OPTIONS_LEGACY_ACTIONS).performClick()
            waitForIdle()

            assertEquals(
                listOf(true),
                reports.legacyRowActionChanges,
                "the menu asks the parent to turn the legacy layout on",
            )
        }

    @Test
    fun `the options menu turns the layout back off`() =
        scheduleTab(legacyRowActions = true, seed = { seedService() }) { _, reports ->
            taggedButton(ScheduleToolbarTags.OPTIONS).performClick()
            waitForIdle()
            taggedButton(ScheduleToolbarTags.OPTIONS_LEGACY_ACTIONS).performClick()
            waitForIdle()

            assertEquals(listOf(false), reports.legacyRowActionChanges)
        }
}
