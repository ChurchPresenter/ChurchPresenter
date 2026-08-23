@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText
import org.churchpresenter.ui.showsExactly

/**
 * What `ScheduleTab` draws for a service order, and what selecting a row does.
 *
 * See `ScheduleTabTestSupport.kt` for the harness.
 */
class ScheduleTabListTest {

    @Test
    fun `an empty schedule invites the operator to drop files on it`() = scheduleTab { vm, _ ->
        assertTrue(vm.scheduleItems.isEmpty())
        assertTrue(showsExactly(ScheduleLabel.DROP_HINT), "the empty-state hint is shown")
    }

    @Test
    fun `the hint gives way to the items once there are any`() =
        scheduleTab(seed = { seedService() }) { _, _ ->
            assertFalse(showsExactly(ScheduleLabel.DROP_HINT), "the hint is gone")
        }

    @Test
    fun `every item is listed, in the order of service`() =
        scheduleTab(seed = { seedService() }) { _, _ ->
            assertEquals(
                listOf("Welcome", "Amazing Grace", "John 3:16", "Notices"),
                orderOf("Welcome", "Amazing Grace", "John 3:16", "Notices"),
                "each item drawn once, in schedule order",
            )
        }

    @Test
    fun `a song row breaks out the number and songbook the operator picks it by`() =
        scheduleTab(seed = { seedService() }) { _, _ ->
            // Deliberately three fields rather than one "42 - Amazing Grace" line: the number and
            // songbook are what disambiguate two songs with the same title.
            assertTrue(showsExactly("42"), "the song number")
            assertTrue(showsExactly("Amazing Grace"), "the title")
            assertTrue(showsExactly("Hymnal"), "the songbook")
        }

    @Test
    fun `each kind of item shows its own supporting detail`() =
        scheduleTab(seed = { seedService() }) { _, _ ->
            // The row renderer branches per item type; these are the lines it draws below the title.
            assertTrue(
                showsContainingText("For God so loved the world."),
                "a verse shows its text: ${renderedText()}",
            )
            assertTrue(
                showsContainingText("https://example.org"),
                "a website shows its address: ${renderedText()}",
            )
        }

    @Test
    fun `clicking a row selects it and tells the host which item it was`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            onNodeWithText("Amazing Grace").performClick()
            waitForIdle()

            val song = vm.scheduleItems[1]
            assertEquals(song.id, vm.selectedItemId, "the row is selected in the view model")
            assertEquals(listOf(song), reports.clicked, "and the host is handed the item itself")
            assertTrue(
                reports.selectionChanges.contains(song.id),
                "the selection change is reported: ${reports.selectionChanges}",
            )
        }

    @Test
    fun `selecting a different row moves the selection rather than adding one`() =
        scheduleTab(seed = { seedService() }) { vm, _ ->
            onNodeWithText("Amazing Grace").performClick()
            waitForIdle()
            onNodeWithText("Notices").performClick()
            waitForIdle()

            assertEquals(vm.scheduleItems[3].id, vm.selectedItemId, "only the last click is selected")
        }

    @Test
    fun `a countdown is listed by its duration rather than by empty text`() =
        scheduleTab(
            seed = {
                addAnnouncement(
                    text = "", textColor = "#FFFFFF", backgroundColor = "#000000", fontSize = 48,
                    animationType = "none", animationDuration = 0,
                    isTimer = true, timerHours = 0, timerMinutes = 5, timerSeconds = 30,
                )
            }
        ) { vm, _ ->
            // An announcement with no text would otherwise render as a blank row.
            assertEquals("Timer 05:30", vm.scheduleItems.single().displayText)
            assertTrue(showsExactly("Timer 05:30"), "and that is what the row draws: ${renderedText()}")
        }

    @Test
    fun `every row can be removed, but only content rows can go live`() =
        scheduleTab(seed = { seedService() }) { vm, _ ->
            assertEquals(vm.scheduleItems.size, buttonCount(ScheduleLabel.REMOVE), "one Remove per row")
            // A label is a section heading, not something to put on screen — it offers Edit instead.
            assertEquals(3, buttonCount(ScheduleLabel.GO_LIVE), "one per presentable item")
            assertEquals(1, buttonCount(ScheduleLabel.EDIT_LABEL), "and the label row edits instead")
        }
}
