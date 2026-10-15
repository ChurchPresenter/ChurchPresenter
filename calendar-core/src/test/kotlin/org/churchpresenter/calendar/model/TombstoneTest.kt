package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The corners of deletion that [MergeTest] does not reach: deleting what is already gone, a
 * deletion from a file too old to carry its version, and a tie where the edit came first.
 */
class TombstoneTest {

    private fun at(minute: Int): Instant = Instant.parse("2026-09-20T09:%02d:00Z".format(minute))

    private fun service(id: String, updated: Instant = at(0), version: Long = 0L) = PlannedService(
        id = id, date = "2026-09-20", name = "Sunday", startTime = "10:00",
        updatedAt = storedInstant(updated), version = version,
    )

    @Test
    fun `deleting a service that is already gone counts one more edit to it`() {
        val once = CalendarDocument(services = listOf(service("a", version = 3L))).withoutService("a", at(1))
        val twice = once.withoutService("a", at(2))
        assertEquals(4L, once.deletedVersions["a"])
        assertEquals(5L, twice.deletedVersions["a"], "counted on from the deletion, not from nothing")
    }

    @Test
    fun `a deletion from a file with no versions still holds against an older copy`() {
        // A file from before deletions carried a version: the deletion is version 0, at minute 5
        val old = CalendarDocument(deletedServices = mapOf("a" to storedInstant(at(5))))
        val copy = CalendarDocument(services = listOf(service("a", updated = at(3))))
        assertNull(old.mergedWith(copy, at(10)).serviceById("a"), "made before the deletion, so it stays deleted")
    }

    @Test
    fun `an edit tied with the deletion's version but made before it does not bring it back`() {
        val deleted = CalendarDocument(services = listOf(service("a"))).withoutService("a", at(5))
        val earlier = CalendarDocument(services = listOf(service("a", updated = at(4), version = 1L)))
        assertNull(deleted.mergedWith(earlier, at(10)).serviceById("a"))
    }

    @Test
    fun `a template's content leaves out its sections and its cues`() {
        val template = SavedTemplate(
            id = "t", name = "Sunday", startTime = "10:00",
            items = listOf(
                ScheduleItem.LabelItem("h", "Worship", "#FFFFFF", "#5B9DF5"),
                ScheduleItem.SongItem("a", 1, "Song", "Hymns", "Hymns::1"),
                ScheduleItem.CueItem(id = "c", action = "blank", absoluteTime = "10:30"),
            ),
        )
        assertEquals(listOf("a"), template.contentItems().map { it.id })
    }
}
