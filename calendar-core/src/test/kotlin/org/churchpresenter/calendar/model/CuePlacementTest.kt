package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Where a cue lands among rows of every kind, and what pinning does when there is no clock. */
class CuePlacementTest {

    private fun song(id: String) = ScheduleItem.SongItem(id, 1, "Song $id", "", "")

    private fun heading(id: String) = ScheduleItem.LabelItem(id, "Worship", "#FFFFFF", "#5B9DF5")

    private fun cue(id: String, at: String = "", offset: Int = 0) =
        ScheduleItem.CueItem(id = id, action = CueAction.BLANK, absoluteTime = at, offsetMinutes = offset)

    private fun service(vararg items: ScheduleItem, planned: Map<String, Int> = emptyMap()) = PlannedService(
        id = "s", date = "2026-09-20", name = "Sunday", startTime = "10:00",
        items = items.toList(), plannedSeconds = planned,
    )

    @Test
    fun `a cue with no clock to pin to is left as it is`() {
        val relative = cue("c", offset = -5)

        assertSame(relative, relative.pinnedTo("soon"))
        assertNull(cueFireTime(relative, null))
    }

    @Test
    fun `a cue goes after the cues and rows before its time, and before the first at or after it`() {
        val placed = service(
            cue("early", at = "09:50"), heading("h"), song("a"), song("b"), planned = mapOf("a" to 600),
        ).withCue(cue("c", at = "10:05"))

        assertEquals(listOf("early", "h", "a", "c", "b"), placed.items.map { it.id })
    }

    @Test
    fun `a cue earlier than every other cue goes first`() {
        val placed = service(cue("later", at = "10:30"), song("a")).withCue(cue("c", at = "09:00"))

        assertEquals(listOf("c", "later", "a"), placed.items.map { it.id })
    }

    @Test
    fun `a cue whose row already exists is moved rather than doubled`() {
        val placed = service(cue("c", at = "09:00"), song("a")).withCue(cue("c", at = "10:30"))

        assertEquals(listOf("a", "c"), placed.items.map { it.id })
        assertEquals("10:30", (placed.items[1] as ScheduleItem.CueItem).absoluteTime)
    }

    @Test
    fun `a row with no clock of its own does not stop a cue`() {
        // "a" has no planned length, so "b" has an inexact clock but still one; the heading has none.
        val placed = service(song("a"), heading("h"), song("b")).withCue(cue("c", at = "10:00"))

        assertEquals(listOf("c", "a", "h", "b"), placed.items.map { it.id })
    }

    @Test
    fun `the service's own counts see rows, cues and timings alike`() {
        val service = service(song("a"), cue("c", at = "09:55"), heading("h"))
            .copy(timing = mapOf("a" to RowTiming(startAt = "10:00")), plannedSeconds = mapOf("a" to 300))

        assertEquals(2, service.autoStartCount(), "one row that starts on its own, one cue")
        assertEquals(listOf("a"), service.contentItems().map { it.id })
        assertEquals(listOf("c"), service.cueRows().map { it.id })
        assertEquals(LocalTime.of(10, 0), runClocks(service).getValue("a").time)
    }
}
