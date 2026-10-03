package org.churchpresenter.calendar.sync

import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.PlannedService
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a pull can hand back besides a phone's clean edit: the desktop's own copy, a service that
 * cannot be rebuilt, and a sealed deletion whose edit time cannot be read. None of them is fatal,
 * and none is counted as something a phone did.
 */
class SyncRoundsTest {

    private val folder: File = Files.createTempDirectory("calendar-rounds").toFile()
    private val relay = FakeRelay().apply { desktopToken = "desk-token" }
    private val sealing = Sealing(Envelope(ByteArray(Envelope.KEY_BYTES) { 3 }), "inst")
    private val store = CalendarStore(folder)
    private val now = Instant.parse("2026-09-20T12:00:00Z")

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun coordinator() = SyncCoordinator(
        store = store,
        presetStore = null,
        client = RelayClient("https://relay.example", "inst", "install-A", relay),
        sealing = sealing,
        installId = "install-A",
        songs = { emptyList() },
        today = { LocalDate.of(2026, 9, 20) },
        now = { now },
    )

    private fun remote(id: String, name: String, date: String = "2026-09-27") = RemoteService(
        id = id, date = date, startTime = "10:00", name = name,
        rows = listOf(RemoteRow.Ministry("m", "Welcome")), version = 2L, editedAt = "2026-09-20T09:00:00Z",
    )

    @Test
    fun `the desktop's own copy coming back is not a phone's change`() {
        relay.phoneWrote(sealing.seal(remote("s1", "Sunday")), deviceId = Projection.DESKTOP)

        val outcome = coordinator().sync("desk-token", cursor = 0)

        assertEquals(0, outcome.phoneChanges)
        assertEquals("Sunday", store.load().document.serviceById("s1")?.name)
    }

    @Test
    fun `a service with a date that cannot be read is passed over`() {
        relay.phoneWrote(sealing.seal(remote("s1", "Someday", date = "next week")))
        relay.phoneWrote(sealing.seal(remote("s2", "Sunday")))

        val outcome = coordinator().sync("desk-token", cursor = 0)

        val saved = store.load().document
        assertNull(saved.serviceById("s1"))
        assertEquals("Sunday", saved.serviceById("s2")?.name)
        assertEquals(1, outcome.phoneChanges)
    }

    @Test
    fun `a deletion whose edit time cannot be read is dated now`() {
        store.save(
            CalendarDocument(
                services = listOf(
                    PlannedService(
                        id = "s1", date = "2026-09-27", name = "Sunday", startTime = "10:00",
                        updatedAt = "2026-09-19T00:00:00Z", version = 1L,
                    ),
                ),
            ),
        )
        relay.phoneWrote(
            sealing.seal(
                RemoteService(
                    id = "s1", date = "2026-09-27", startTime = "", name = "",
                    deleted = true, version = 2L, editedAt = "yesterday",
                ),
            ),
        )

        coordinator().sync("desk-token", cursor = 0)

        val saved = store.load().document
        assertNull(saved.serviceById("s1"))
        assertTrue("s1" in saved.deletedServices)
        assertEquals(now, Instant.parse(saved.deletedServices.getValue("s1")))
    }
}
