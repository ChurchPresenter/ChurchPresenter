@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The schedule toolbar's icon size, chosen from the panel's options menu.
 *
 * The size is asserted on a toolbar key itself rather than through a screenshot: a key drawn at the
 * wrong size is a layout fact, and the screenshot suite already shows what each size looks like.
 */
class ScheduleToolbarIconSizeTest {

    @Test
    fun `the menu asks the parent for the size picked`() =
        scheduleTab(seed = { seedService() }) { _, reports ->
            taggedButton(ScheduleToolbarTags.OPTIONS).performClick()
            waitForIdle()
            taggedButton(ScheduleToolbarIconSize.LARGE.menuTag).performClick()
            waitForIdle()

            assertEquals(listOf(ScheduleToolbarIconSize.LARGE), reports.toolbarIconSizeChanges)
        }

    @Test
    fun `every size is offered in the menu`() =
        scheduleTab(seed = { seedService() }) { _, _ ->
            taggedButton(ScheduleToolbarTags.OPTIONS).performClick()
            waitForIdle()

            ScheduleToolbarIconSize.entries.forEach { taggedButton(it.menuTag).assertExists("$it is offered") }
        }

    @Test
    fun `the toolbar keys are drawn at the chosen size`() =
        ScheduleToolbarIconSize.entries.forEach { size ->
            scheduleTab(toolbarIconSize = size, seed = { seedService() }) { _, _ ->
                taggedButton(ScheduleToolbarTags.UNDO)
                    .assertWidthIsEqualTo(size.buttonSize)
                    .assertHeightIsEqualTo(size.buttonSize)
            }
        }

    @Test
    fun `the sizes grow from small to large`() {
        val sizes = ScheduleToolbarIconSize.entries
        assertEquals(sizes.sortedBy { it.buttonSize }, sizes, "keys, in menu order")
        assertEquals(sizes.sortedBy { it.iconSize }, sizes, "and the icons inside them")
    }

    @Test
    fun `a saved name reads back as its size`() =
        ScheduleToolbarIconSize.entries.forEach { assertEquals(it, ScheduleToolbarIconSize.fromName(it.name)) }

    @Test
    fun `a name this build does not know falls back to small`() {
        assertEquals(ScheduleToolbarIconSize.SMALL, ScheduleToolbarIconSize.fromName("HUGE"))
        assertEquals(ScheduleToolbarIconSize.SMALL, ScheduleToolbarIconSize.fromName(""))
    }
}
