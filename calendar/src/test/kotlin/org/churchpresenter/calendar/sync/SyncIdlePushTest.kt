package org.churchpresenter.calendar.sync

import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.PlannedService
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncIdlePushTest {

    private val folder: File = Files.createTempDirectory("calendar-idle").toFile()
    private val relay = FakeRelay()
    private val envelope = Envelope(ByteArray(Envelope.KEY_BYTES) { 2 })
    private val store = CalendarStore(folder)
    private val pushedStore = PushedStateStore(folder)
    private val start = Instant.parse("2026-09-20T12:00:00Z")
    private var clock = start

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun coordinator(instanceId: String = "inst") = SyncCoordinator(
        store = store,
        presetStore = PresetStore(folder),
        client = RelayClient("https://relay.example", "inst", "install-A", relay),
        sealing = Sealing(envelope, instanceId),
        installId = "install-A",
        songs = { emptyList() },
        today = { LocalDate.of(2026, 9, 20) },
        now = { clock },
        pushed = pushedStore,
    )

    private fun service(id: String, name: String) = PlannedService(
        id = id, date = "2026-09-27", name = name, startTime = "10:00", updatedAt = "2026-09-19T00:00:00Z",
    )

    private fun statePushes() = relay.calls.count { it == "PUT /state" }

    private fun registered(): String {
        relay.desktopToken = "desk-token"
        return "desk-token"
    }

    @Test
    fun `a round with nothing new on either side does not push again`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(service("s1", "Sunday"))))
        val first = coordinator().sync(token, cursor = 0)
        assertEquals(1, statePushes())

        clock = start.plusSeconds(300)
        val second = coordinator().sync(token, first.cursor)

        assertEquals(1, statePushes())
        assertEquals(first.cursor, second.cursor)
    }

    @Test
    fun `a local edit is pushed`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(service("s1", "Sunday"))))
        val first = coordinator().sync(token, cursor = 0)

        store.save(CalendarDocument(services = listOf(service("s1", "Sunday Morning"))))
        coordinator().sync(token, first.cursor)

        assertEquals(2, statePushes())
    }

    @Test
    fun `anything arriving from the relay makes the round push`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(service("s1", "Sunday"))))
        val first = coordinator().sync(token, cursor = 0)

        relay.phoneDeleted("gone")
        coordinator().sync(token, first.cursor)

        assertEquals(2, statePushes())
    }

    @Test
    fun `an unchanged picture is pushed again once a day`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(service("s1", "Sunday"))))
        val first = coordinator().sync(token, cursor = 0)

        clock = start.plusSeconds(25L * 60L * 60L)
        coordinator().sync(token, first.cursor)

        assertEquals(2, statePushes())
    }

    @Test
    fun `a new instance is pushed to even when the picture is the same`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(service("s1", "Sunday"))))
        val first = coordinator().sync(token, cursor = 0)

        coordinator(instanceId = "fresh-instance").sync(token, first.cursor)

        assertEquals(2, statePushes())
    }

    @Test
    fun `a local save that changes nothing the relay carries is not pushed`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(service("s1", "Sunday"))))
        val first = coordinator().sync(token, cursor = 0)

        val outcome = coordinator().pushLocal(token, first.cursor)

        assertEquals(1, statePushes())
        assertEquals(first.cursor, outcome.cursor)
    }

    @Test
    fun `without a store every round pushes, as before`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(service("s1", "Sunday"))))
        val plain = SyncCoordinator(
            store = store,
            presetStore = PresetStore(folder),
            client = RelayClient("https://relay.example", "inst", "install-A", relay),
            sealing = Sealing(envelope, "inst"),
            installId = "install-A",
            songs = { emptyList() },
            today = { LocalDate.of(2026, 9, 20) },
            now = { clock },
        )
        val first = plain.sync(token, cursor = 0)
        plain.sync(token, first.cursor)

        assertEquals(2, statePushes())
    }
}
