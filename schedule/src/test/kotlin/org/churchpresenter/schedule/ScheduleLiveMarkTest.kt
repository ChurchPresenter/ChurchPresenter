@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which Schedule row is on screen (#1049): the row last put live keeps a LIVE badge while the app
 * says it is still up, gives way to a "Left off" bar once it is not, and nothing shows with the
 * option off.
 */
class ScheduleLiveMarkTest {

    private val song = ScheduleItem.SongItem("s", 12, "Amazing Grace", "")
    private val label = ScheduleItem.LabelItem("l", "Welcome", "#FFFFFF", "#203040")
    private val cue = ScheduleItem.CueItem("c", CueAction.PROJECT, label = "Offering")

    @Test
    fun `the live row is marked on air while the app says it is up, and left off once it is not`() {
        assertEquals(LiveMark("s", onAir = true), liveMark(listOf(label, song), "s") { true })
        assertEquals(LiveMark("s", onAir = false), liveMark(listOf(label, song), "s") { false })
    }

    @Test
    fun `no mark for no live row, a row that is gone, or a label or cue`() {
        val never: (ScheduleItem) -> Boolean = { true }
        assertNull(liveMark(listOf(song), null, never))
        assertNull(liveMark(listOf(song), "gone", never))
        assertNull(liveMark(listOf(label, song), "l", never))
        assertNull(liveMark(listOf(cue, song), "c", never))
    }

    private fun ComposeUiTest.count(tag: String): Int =
        onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun ScheduleViewModel.songId(): String = scheduleItems.first { it is ScheduleItem.SongItem }.id

    @Test
    fun `on, the live row wears the badge until it goes off air, then a Left off bar sits under it`() {
        val onAir = mutableStateOf(true)
        scheduleTab(seed = { seedService() }, highlightLive = true, isOnAir = { onAir.value }) { vm, _ ->
            runOnIdle { vm.markLive(vm.songId()) }
            waitForIdle()
            assertEquals(1, count(SCHEDULE_ROW_LIVE_BADGE_TAG))
            assertEquals(0, count(SCHEDULE_LEFT_OFF_TAG))

            runOnIdle { onAir.value = false }
            waitForIdle()
            assertEquals(0, count(SCHEDULE_ROW_LIVE_BADGE_TAG))
            assertEquals(1, count(SCHEDULE_LEFT_OFF_TAG))
        }
    }

    @Test
    fun `off, neither the badge nor the bar shows`() =
        scheduleTab(seed = { seedService() }, isOnAir = { true }) { vm, _ ->
            runOnIdle { vm.markLive(vm.songId()) }
            waitForIdle()
            assertEquals(0, count(SCHEDULE_ROW_LIVE_BADGE_TAG))
            assertEquals(0, count(SCHEDULE_LEFT_OFF_TAG))
        }

    @Test
    fun `on, nothing is marked before a row goes live`() =
        scheduleTab(seed = { seedService() }, highlightLive = true, isOnAir = { true }) { _, _ ->
            waitForIdle()
            assertEquals(0, count(SCHEDULE_ROW_LIVE_BADGE_TAG))
            assertEquals(0, count(SCHEDULE_LEFT_OFF_TAG))
        }

    @Test
    fun `the options menu turns the highlight on`() =
        scheduleTab(seed = { seedService() }) { _, reports ->
            taggedButton(ScheduleToolbarTags.OPTIONS).performClick()
            waitForIdle()
            taggedButton(ScheduleToolbarTags.OPTIONS_HIGHLIGHT_LIVE).performClick()
            waitForIdle()

            assertEquals(listOf(true), reports.highlightLiveChanges)
        }

    @Test
    fun `the options menu turns the highlight back off`() =
        scheduleTab(seed = { seedService() }, highlightLive = true) { _, reports ->
            taggedButton(ScheduleToolbarTags.OPTIONS).performClick()
            waitForIdle()
            taggedButton(ScheduleToolbarTags.OPTIONS_HIGHLIGHT_LIVE).performClick()
            waitForIdle()

            assertEquals(listOf(false), reports.highlightLiveChanges)
        }
}
