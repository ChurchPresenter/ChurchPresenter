package org.churchpresenter.calendar.sync

import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FollowerPushTest {

    private val today = LocalDate.of(2026, 9, 20)
    private val now = Instant.parse("2026-09-20T12:00:00Z")
    private val songs = listOf(SongItem(number = "42", title = "Here I Am to Worship", songbook = "Hymnal"))
    private val resolver = Resolver(songs, emptyList(), today, now, keepForeignRows = true)

    /** The main computer's copy as it arrived: a video and a preset this follower cannot open. */
    private val arrived = RemoteService(
        id = "s1",
        date = "2026-09-27",
        startTime = "10:00",
        name = "Sunday",
        rows = listOf(
            RemoteRow.Section("h", "Welcome", "#5B9DF5"),
            RemoteRow.Ref("m", "Intro video", RemoteKind.MEDIA),
            RemoteRow.Preset("p", "Countdown", "p1", RemoteKind.TIMER),
            RemoteRow.Bible("b", "John 3:16", preview = "For God so loved the world"),
            RemoteRow.Song("s", "Here I Am to Worship", songId = "Hymnal::42"),
        ),
        version = 3L,
        editedAt = "2026-09-19T10:00:00Z",
        updatedAt = "2026-09-19T10:00:01Z",
        updatedBy = "desktop",
        rev = 7L,
    )

    /** What this follower keeps of [arrived]: the same service, rebuilt here. */
    private val local: PlannedService = resolver.resolve(arrived, null)!!.service

    private fun outgoing(document: CalendarDocument, shadow: Map<String, RemoteService>) =
        FollowerPush.outgoing(document, shadow, resolver, today, now)

    @Test
    fun `a service left as it arrived is not sent`() {
        assertTrue(outgoing(CalendarDocument(services = listOf(local)), mapOf("s1" to arrived)).isEmpty())
    }

    @Test
    fun `an edited service goes back with every untouched row exactly as it arrived`() {
        val renamed = local.copy(name = "Sunday Evening")

        val sent = outgoing(CalendarDocument(services = listOf(renamed)), mapOf("s1" to arrived)).single()

        assertEquals(7L, sent.ifRev)
        assertEquals("Sunday Evening", sent.service.name)
        assertEquals(arrived.rows, sent.service.rows, "placeholders and lost previews never travel")
        assertEquals(4L, sent.service.version)
        assertEquals("2026-09-19T10:00:00Z", sent.service.editedAt)
        assertEquals("", sent.service.updatedAt)
        assertEquals("", sent.service.updatedBy)
    }

    @Test
    fun `a row changed or added here goes as this computer has it`() {
        val items = local.items.map { item ->
            if (item.id == "h") (item as ScheduleItem.LabelItem).copy(text = "Greeting") else item
        } + ScheduleItem.MinistryItem("n", "Offering")
        val edited = local.copy(items = items, version = 6L)

        val sent = outgoing(CalendarDocument(services = listOf(edited)), mapOf("s1" to arrived)).single()

        val rows = sent.service.rows.associateBy { it.id }
        assertEquals("Greeting", assertIs<RemoteRow.Section>(rows.getValue("h")).title)
        assertEquals(RemoteRow.Ministry("n", "Offering"), rows.getValue("n"))
        assertEquals(arrived.rows[1], rows.getValue("m"))
        assertEquals(arrived.rows[2], rows.getValue("p"))
        assertEquals(6L, sent.service.version, "a local version past the copy's is kept")
    }

    @Test
    fun `a ref this computer can open resolves to its own item and travels as it arrived`() {
        val video = ScheduleItem.MediaItem("m", "/videos/intro.mp4", "Intro video", "local")
        val mine = local.copy(items = local.items.map { if (it.id == "m") video else it })
        val base = resolver.resolve(arrived, mine)!!.service
        assertEquals(video, base.items.first { it.id == "m" }, "the real item is kept, not a placeholder")

        val sent = outgoing(CalendarDocument(services = listOf(base.copy(name = "Renamed"))), mapOf("s1" to arrived))

        assertEquals(arrived.rows[1], sent.single().service.rows.first { it.id == "m" })
    }

    @Test
    fun `a service the relay never had is sent whole, stamped now when it has no edit time`() {
        val fresh = PlannedService(
            id = "n1", date = "2026-09-28", name = "Prayer", startTime = "19:00", version = 0L,
            items = listOf(ScheduleItem.MinistryItem("r", "Prayer time")),
        )

        val sent = outgoing(CalendarDocument(services = listOf(fresh)), emptyMap()).single()

        assertEquals(0L, sent.ifRev)
        assertEquals(0L, sent.service.version)
        assertEquals(now.toString(), sent.service.editedAt)
        assertEquals(listOf(RemoteRow.Ministry("r", "Prayer time")), sent.service.rows)
    }

    @Test
    fun `a service restored over the relay's deletion is sent whole, past the deletion's version`() {
        val deletion = arrived.copy(rows = emptyList(), deleted = true, version = 5L, rev = 9L)

        val sent = outgoing(CalendarDocument(services = listOf(local)), mapOf("s1" to deletion)).single()

        assertEquals(9L, sent.ifRev)
        assertEquals(6L, sent.service.version)
        assertEquals(Projection.service(local).rows, sent.service.rows)
        assertTrue(!sent.service.deleted)
    }

    @Test
    fun `a copy that no longer resolves is written over with this computer's rows`() {
        val broken = arrived.copy(startTime = "not a time")

        val sent = outgoing(CalendarDocument(services = listOf(local)), mapOf("s1" to broken)).single()

        assertEquals(7L, sent.ifRev)
        assertEquals(Projection.service(local).rows, sent.service.rows)
    }

    @Test
    fun `a service past the relay's retention is never sent`() {
        val old = local.copy(id = "old", date = today.minusDays(WireLimits.RETENTION_DAYS + 1).toString())

        assertTrue(outgoing(CalendarDocument(services = listOf(old)), emptyMap()).isEmpty())
    }

    @Test
    fun `a service removed here is sent as a sealed deletion against the copy's revision`() {
        val document = CalendarDocument(
            deletedServices = mapOf("s1" to "2026-09-20T08:00:00Z"),
            deletedVersions = mapOf("s1" to 2L),
        )

        val sent = outgoing(document, mapOf("s1" to arrived)).single()

        assertEquals(7L, sent.ifRev)
        assertTrue(sent.service.deleted)
        assertEquals("2026-09-20", sent.service.date)
        assertEquals(4L, sent.service.version, "outranks the copy it deletes")
        assertEquals("2026-09-20T08:00:00Z", sent.service.editedAt)
        assertEquals("", sent.service.name)
    }

    @Test
    fun `a deletion with an unreadable stamp keeps the copy's date and outranks it`() {
        val document = CalendarDocument(deletedServices = mapOf("s1" to "garbage"))

        val sent = outgoing(document, mapOf("s1" to arrived)).single()

        assertEquals("2026-09-27", sent.service.date)
        assertEquals(4L, sent.service.version)
    }

    @Test
    fun `no deletion goes for a service the relay never had, already deleted, or still held here`() {
        val document = CalendarDocument(
            services = listOf(local),
            deletedServices = mapOf(
                "s1" to "2026-09-20T08:00:00Z",
                "gone" to "2026-09-20T08:00:00Z",
                "never" to "2026-09-20T08:00:00Z",
            ),
        )
        val shadow = mapOf("s1" to arrived, "gone" to arrived.copy(id = "gone", deleted = true))

        assertTrue(outgoing(document, shadow).isEmpty())
    }
}
