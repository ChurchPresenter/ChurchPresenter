package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Two machines keeping the same calendar.
 *
 * The rule is per service, not per file, and this is where that is pinned: a whole-file "newest
 * wins" loses the Sunday somebody added on the laptop the moment the desktop saves anything at
 * all — which is the failure that makes a shared folder unusable and the reason
 * [CalendarDocument.mergedWith] exists.
 */
class MergeTest {

    private fun at(minute: Int): Instant = Instant.parse("2026-09-20T09:%02d:00Z".format(minute))

    private fun service(
        id: String,
        name: String = "Sunday Morning",
        date: String = "2026-09-20",
        updated: Instant = at(0),
        items: List<ScheduleItem> = emptyList(),
        version: Long = 0L,
    ) = PlannedService(
        id = id, date = date, name = name, startTime = "10:00", items = items,
        updatedAt = storedInstant(updated), version = version,
    )

    private fun song(id: String) = ScheduleItem.SongItem(id, 1, "Song", "Hymns", "Hymns::1")

    private fun document(vararg services: PlannedService) = CalendarDocument(services = services.toList())

    // ── Adding ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a service added on one machine arrives on the other`() {
        val here = document(service("a"))
        val there = document(service("a"), service("b", name = "Evening Prayer"))

        val merged = here.mergedWith(there, at(10))

        assertEquals(listOf("a", "b"), merged.services.map { it.id })
    }

    @Test
    fun `both machines adding at once keeps both`() {
        val here = document(service("a"), service("laptop", name = "Youth Night"))
        val there = document(service("a"), service("desktop", name = "Prayer Meeting"))

        val merged = here.mergedWith(there, at(10))

        assertEquals(setOf("a", "laptop", "desktop"), merged.services.map { it.id }.toSet())
    }

    /**
     * Byte-identical, not merely equivalent.
     *
     * Each machine writes its merge back, so a merge that only agreed on *content* would have the
     * two of them rewriting the file at each other over the ordering for ever.
     */
    @Test
    fun `the merge is the same whichever machine performs it`() {
        val here = document(service("a", updated = at(5)), service("laptop"))
        val there = document(service("a", name = "Renamed", updated = at(7)), service("desktop"))

        assertEquals(
            here.mergedWith(there, at(10)).services.map { it.id to it.name },
            there.mergedWith(here, at(10)).services.map { it.id to it.name },
        )
    }

    // ── Editing ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the copy edited later wins`() {
        val here = document(service("a", name = "Morning", updated = at(5)))
        val there = document(service("a", name = "Morning Worship", updated = at(9)))

        assertEquals("Morning Worship", here.mergedWith(there, at(10)).services.single().name)
        assertEquals("Morning Worship", there.mergedWith(here, at(10)).services.single().name, "either way round")
    }

    @Test
    fun `a service edited on one machine does not lose what the other added`() {
        val here = document(service("a", name = "Renamed", updated = at(9)), service("b"))
        val there = document(service("a", updated = at(5)))

        val merged = here.mergedWith(there, at(10))

        assertEquals("Renamed", merged.serviceById("a")?.name)
        assertNotNull(merged.serviceById("b"), "the other machine's service is still there")
    }

    /**
     * A service written before this field existed loses to one that has been edited since.
     *
     * An empty stamp sorts before every real one, which is the right way round: the file that has
     * been opened by a version that stamps is the one somebody has actually touched.
     */
    @Test
    fun `a service from an older file loses to one that has been edited`() {
        val old = document(service("a", name = "Old").copy(updatedAt = ""))
        val edited = document(service("a", name = "Edited", updated = at(1)))

        assertEquals("Edited", old.mergedWith(edited, at(10)).services.single().name)
    }

    // ── Deleting ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a deleted service stays deleted rather than coming back`() {
        val here = document(service("a"), service("b")).withoutService("b", at(5))
        val there = document(service("a"), service("b"))

        val merged = here.mergedWith(there, at(10))

        assertEquals(listOf("a"), merged.services.map { it.id }, "the other machine still had it, and does not win")
    }

    @Test
    fun `a service edited after it was deleted elsewhere is kept`() {
        val deleted = document(service("a"), service("b")).withoutService("b", at(5))
        // An edit of the same copy the deletion was made from: the same version, but later.
        val edited = document(service("a"), service("b", name = "Back on", updated = at(8), version = 1L))

        val merged = deleted.mergedWith(edited, at(10))

        assertEquals("Back on", merged.serviceById("b")?.name, "somebody went back to it after the delete")
    }

    @Test
    fun `more edits win over a later clock`() {
        val edited = document(service("a", name = "Edited twice", updated = at(2), version = 2L))
        val stale = document(service("a", name = "Old copy, restamped", updated = at(9), version = 1L))

        assertEquals("Edited twice", edited.mergedWith(stale, at(10)).serviceById("a")?.name)
        assertEquals("Edited twice", stale.mergedWith(edited, at(10)).serviceById("a")?.name)
    }

    @Test
    fun `a deletion outranks every copy it was made from`() {
        val here = document(service("b", version = 3L)).withoutService("b", at(5))
        val there = document(service("b", name = "Stale", updated = at(9), version = 3L))

        assertNull(here.mergedWith(there, at(10)).serviceById("b"))
        assertEquals(4L, here.deletedVersions["b"], "one more edit than the copy it deleted")
    }

    @Test
    fun `a local edit counts as one more edit than the copy it was made from`() {
        val before = document(service("a", version = 3L))
        val after = document(service("a", name = "Renamed", version = 3L), service("new"))

        val stamped = after.stampingChanged(before, at(4))

        assertEquals(4L, stamped.serviceById("a")?.version)
        assertEquals(1L, stamped.serviceById("new")?.version)
    }

    @Test
    fun `a deletion is remembered for both sides of the merge`() {
        val here = document(service("a"), service("b")).withoutService("b", at(5))
        val there = document(service("a"), service("b"))

        val merged = here.mergedWith(there, at(10))

        assertTrue("b" in merged.deletedServices, "or the next merge would put it back")
    }

    @Test
    fun `a deletion nobody could still be carrying is forgotten`() {
        val ancient = Instant.parse("2020-01-01T00:00:00Z")
        val here = document(service("a")).copy(deletedServices = mapOf("old" to storedInstant(ancient)))

        val merged = here.mergedWith(document(service("a")), at(10))

        assertTrue(merged.deletedServices.isEmpty(), "the list cannot grow for ever")
    }

    // ── What is not shared ──────────────────────────────────────────────────────────────────────

    @Test
    fun `preferences stay with the machine that set them`() {
        val here = document(service("a")).copy(preferences = CalendarPreferences(use24HourClock = true))
        val there = document(service("a")).copy(preferences = CalendarPreferences(use24HourClock = false))

        assertTrue(here.mergedWith(there, at(10)).preferences.use24HourClock, "the clock format is about the screen")
    }

    @Test
    fun `templates are pooled rather than fought over`() {
        val here = document().copy(templates = listOf(SavedTemplate("t1", "Sunday Morning", "10:00")))
        val there = document().copy(templates = listOf(SavedTemplate("t2", "Carol Service", "18:00")))

        assertEquals(2, here.mergedWith(there, at(10)).templates.size)
    }

    // ── The stamp itself ────────────────────────────────────────────────────────────────────────

    @Test
    fun `only the services that changed are stamped`() {
        val before = document(service("a", updated = at(1)), service("b", updated = at(1)))
        val after = before.withService(before.serviceById("a")!!.copy(items = listOf(song("x"))))

        val stamped = after.stampingChanged(before, at(9))

        assertEquals(storedInstant(at(9)), stamped.serviceById("a")?.updatedAt, "the edited one")
        assertEquals(storedInstant(at(1)), stamped.serviceById("b")?.updatedAt, "and only it")
    }

    @Test
    fun `a service added is stamped as new`() {
        val before = document(service("a", updated = at(1)))
        val after = before.withService(service("b").copy(updatedAt = ""))

        assertEquals(storedInstant(at(9)), after.stampingChanged(before, at(9)).serviceById("b")?.updatedAt)
    }

    @Test
    fun `saving a document nobody touched changes nothing`() {
        val before = document(service("a", updated = at(1)))

        assertEquals(before, before.stampingChanged(before, at(9)), "or an untouched copy would look newer")
    }

    @Test
    fun `a merge of two identical calendars is that calendar`() {
        val one = document(service("a", updated = at(1)))

        assertEquals(one.services, one.mergedWith(one, at(10)).services)
        assertNull(one.mergedWith(one, at(10)).deletedServices["a"])
    }
}
