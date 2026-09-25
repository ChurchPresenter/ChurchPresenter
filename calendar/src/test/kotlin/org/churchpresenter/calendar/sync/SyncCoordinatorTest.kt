package org.churchpresenter.calendar.sync

import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.PresetDocument
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncCoordinatorTest {

    private val folder: File = Files.createTempDirectory("calendar-sync").toFile()
    private val today = LocalDate.of(2026, 9, 20)
    private val relay = FakeRelay()
    private val envelope = Envelope(ByteArray(Envelope.KEY_BYTES) { 2 })
    private val sealing = Sealing(envelope, "inst")
    private val store = CalendarStore(folder)
    private val presetStore = PresetStore(folder)
    private var saves = 0
    private val songs = listOf(SongItem(number = "42", title = "Here I Am to Worship", songbook = "Hymnal"))

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun coordinator(installId: String = "install-A") = SyncCoordinator(
        store = store,
        presetStore = presetStore,
        client = RelayClient("https://relay.example", "inst", installId, relay),
        sealing = sealing,
        installId = installId,
        songs = { songs },
        today = { today },
        now = { Instant.parse("2026-09-20T12:00:00Z") },
        onSaved = { saves++ },
    )

    private fun catalogSync() = CatalogSync(
        CatalogSyncStore(folder),
        RelayClient("https://relay.example", "inst", "install-A", relay),
        sealing,
        { songs },
        { 270 },
        { today },
        { Instant.parse("2026-09-20T12:00:00Z") },
    )

    @Test
    fun `the songbooks survive a calendar push and are passed over when a pull hands them back`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(localService("s1", "Sunday"))))
        val first = coordinator().sync(token, cursor = 0)
        assertEquals(1, catalogSync().push(token))
        assertTrue("catalog:Hymnal-cf49adf4" in relay.records)

        // The next round replaces the calendar: the songbook stands, and the pull that hands it
        // back (the cursor stopped before it) does not turn it into a service.
        val second = coordinator().sync(token, first.cursor)
        assertTrue("catalog:Hymnal-cf49adf4" in relay.records)
        assertTrue("s1" in relay.records)
        assertEquals(listOf("s1"), store.load().document.services.map { it.id })
        assertEquals(0, second.unreadableRecords)
        assertEquals(0, catalogSync().push(token))
    }

    private fun localService(
        id: String,
        name: String,
        updatedAt: String = "2026-09-19T00:00:00Z",
        version: Long = 1L,
    ) = PlannedService(
        id = id, date = "2026-09-27", name = name, startTime = "10:00", updatedAt = updatedAt, version = version,
        items = listOf(ScheduleItem.WebsiteItem("w", "https://church.example/live", "Stream")),
    )

    /** A phone's edit of the desktop's version-1 copy, unless [version] says otherwise. */
    private fun phoneService(
        id: String,
        name: String,
        vararg rows: RemoteRow,
        version: Long = 2L,
        editedAt: String = "2026-09-20T09:00:00Z",
    ) = RemoteService(
        id = id, date = "2026-09-27", startTime = "10:00", name = name, rows = rows.toList(),
        version = version, editedAt = editedAt,
    )

    private fun phoneDeletion(id: String, version: Long = 2L) =
        RemoteService(id = id, date = "2026-09-20", startTime = "", name = "", deleted = true, version = version,
            editedAt = "2026-09-20T09:00:00Z")

    private fun registered(): String {
        relay.desktopToken = "desk-token"
        return "desk-token"
    }

    @Test
    fun `a phone's new service is rebuilt, merged, saved and pushed back`() {
        val token = registered()
        val phoneService = phoneService(
            "p1",
            "Evening Prayer",
            RemoteRow.Song("r", "42 - Here I Am to Worship"),
            RemoteRow.Ref("x", "Not ours", RemoteKind.CUE),
        )
        relay.phoneWrote(sealing.seal(phoneService))

        val outcome = coordinator().sync(token, cursor = 0)

        val saved = store.load().document.serviceById("p1")!!
        assertEquals("Evening Prayer", saved.name)
        val song = assertIs<ScheduleItem.SongItem>(saved.items.single())
        assertEquals("Hymnal::42", song.songId)
        assertEquals(1, outcome.phoneChanges)
        assertEquals(1, outcome.droppedRows)
        assertEquals(1, saves)
        assertEquals(relay.rev, outcome.cursor)
        assertEquals("desktop", relay.records.getValue("p1").updatedBy)
        assertEquals("install-A", relay.lastDesktopInstall)
    }

    @Test
    fun `the desktop's own rows come back intact by reference and newer edits win`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(localService("s1", "Sunday Morning"))))
        val renamedRow = RemoteRow.Ref("w", "Renamed on the phone", RemoteKind.WEBSITE)
        val renamed = phoneService("s1", "Sunday Renamed", renamedRow)
        relay.phoneWrote(sealing.seal(renamed))

        coordinator().sync(token, cursor = 0)

        val merged = store.load().document.serviceById("s1")!!
        assertEquals("Sunday Renamed", merged.name)
        val site = assertIs<ScheduleItem.WebsiteItem>(merged.items.single())
        assertEquals("https://church.example/live", site.url)
    }

    @Test
    fun `a local edit newer than the phone's copy is kept`() {
        val token = registered()
        val local = localService("s1", "Local wins", version = 3L)
        store.save(CalendarDocument(services = listOf(local)))
        relay.phoneWrote(sealing.seal(phoneService("s1", "Phone version")))

        coordinator().sync(token, cursor = 0)

        assertEquals("Local wins", store.load().document.serviceById("s1")!!.name)
    }

    @Test
    fun `a sealed deletion from a phone removes the service here`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(localService("s1", "Doomed"))))
        relay.phoneWrote(sealing.seal(phoneDeletion("s1")))

        coordinator().sync(token, cursor = 0)

        assertTrue(store.load().document.serviceById("s1") == null)
        assertTrue("s1" in store.load().document.deletedServices)
        assertEquals(2L, store.load().document.deletedVersions["s1"])
    }

    @Test
    fun `the relay's own plaintext tombstone deletes nothing`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(localService("s1", "Kept"))))
        relay.phoneDeleted("s1")

        coordinator().sync(token, cursor = 0)

        assertEquals("Kept", store.load().document.serviceById("s1")?.name, "only a deletion that opens is believed")
    }

    @Test
    fun `an old copy handed back by the relay cannot overwrite a newer one`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(localService("s1", "Current", version = 5L))))
        // A copy the relay kept from earlier, restamped by it as the newest thing it has.
        relay.phoneWrote(sealing.seal(phoneService("s1", "Stale", version = 3L, editedAt = "2026-09-20T11:00:00Z")))

        coordinator().sync(token, cursor = 0)

        assertEquals("Current", store.load().document.serviceById("s1")?.name)
    }

    @Test
    fun `an old deletion handed back cannot remove a service edited since`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(localService("s1", "Edited since", version = 4L))))
        relay.phoneWrote(sealing.seal(phoneDeletion("s1", version = 2L)))

        coordinator().sync(token, cursor = 0)

        assertEquals("Edited since", store.load().document.serviceById("s1")?.name)
    }

    @Test
    fun `an edit time ahead of this machine's clock is taken as now`() {
        val token = registered()
        relay.phoneWrote(sealing.seal(phoneService("s9", "From the future", editedAt = "2099-01-01T00:00:00Z")))

        coordinator().sync(token, cursor = 0)

        assertEquals("2026-09-20T12:00:00Z", store.load().document.serviceById("s9")?.updatedAt)
    }

    @Test
    fun `records that do not open are counted and skipped, not fatal`() {
        val token = registered()
        val other = Sealing(Envelope(ByteArray(Envelope.KEY_BYTES) { 9 }), "inst")
        relay.phoneWrote(other.seal(phoneService("bad", "Forged")))
        relay.phoneWrote(sealing.seal(phoneService("good", "Real")))

        val outcome = coordinator().sync(token, cursor = 0)

        assertEquals(1, outcome.unreadableRecords)
        assertTrue(store.load().document.serviceById("bad") == null)
        assertEquals("Real", store.load().document.serviceById("good")!!.name)
    }

    @Test
    fun `a push that races a phone write is retried after pulling again`() {
        val token = registered()
        val racing = RelayTransport { m, u, h, b ->
            val reply = relay.send(m, u, h, b)
            // The moment the desktop reads the changes, a phone writes: the push must see it.
            if (u.contains("/changes") && relay.records.isEmpty()) {
                relay.phoneWrote(sealing.seal(phoneService("late", "Late edit")))
            }
            reply
        }
        val coordinator = SyncCoordinator(
            store, presetStore, RelayClient("https://relay.example", "inst", "install-A", racing), sealing, "install-A",
            songs = { songs }, today = { today }, now = { Instant.parse("2026-09-20T12:00:00Z") },
        )

        coordinator.sync(token, cursor = 0)

        assertEquals("Late edit", store.load().document.serviceById("late")!!.name)
        assertTrue(relay.calls.count { it.startsWith("PUT /state") } >= 2)
    }

    @Test
    fun `a conflict that never resolves is reported`() {
        val token = registered()
        val alwaysStale = RelayTransport { m, u, h, b ->
            if (u.contains("/state")) RelayReply(412, "{}") else relay.send(m, u, h, b)
        }
        val coordinator = SyncCoordinator(
            store,
            presetStore,
            RelayClient("https://relay.example", "inst", "install-A", alwaysStale),
            sealing,
            "install-A",
            songs = { songs }, today = { today },
        )

        assertFailsWith<RelayFailure.Conflict> { coordinator.sync(token, 0) }
    }

    @Test
    fun `another desktop on the instance stops this one pushing`() {
        val token = registered()
        relay.lastDesktopInstall = "install-B"
        store.save(CalendarDocument(services = listOf(localService("s1", "Mine"))))

        val outcome = coordinator("install-A").sync(token, cursor = 0)

        assertEquals("install-B", outcome.otherDesktop)
        assertTrue(relay.records.isEmpty(), "nothing was pushed")
    }

    @Test
    fun `presets are pushed as a sealed index and phone names are opened`() {
        val token = registered()
        val countdown = ItemPreset("p1", "Countdown", ScheduleItem.AnnouncementItem("a", "5:00", isTimer = true))
        presetStore.save(PresetDocument(presets = listOf(countdown)))
        val nameBox = sealing.sealText("Anna's iPhone", "phone-1")
        relay.devices["phone-1"] = RemoteDevice("phone-1", nameBox, "2026-09-20T00:00:00Z")

        val outcome = coordinator().sync(token, cursor = 0)

        val index = envelope.open(relay.presetsBox, "inst", PRESETS_RECORD)!!.decodeToString()
        assertTrue("Countdown" in index)
        assertEquals("Anna's iPhone", outcome.devices.single().name)
    }

    @Test
    fun `pushing local state falls back to a full round when the relay moved on`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(localService("s1", "Mine"))))
        relay.phoneWrote(sealing.seal(phoneService("p1", "Theirs")))

        val outcome = coordinator().pushLocal(token, cursor = 0)

        assertEquals(setOf("s1", "p1"), relay.records.keys)
        assertEquals("Theirs", store.load().document.serviceById("p1")!!.name)
        assertEquals(relay.rev, outcome.cursor)
    }

    @Test
    fun `nothing is saved when the relay holds nothing new`() {
        val token = registered()
        store.save(CalendarDocument(services = listOf(localService("s1", "Mine"))))
        saves = 0

        coordinator().sync(token, cursor = 0)

        assertEquals(0, saves)
        assertEquals(setOf("s1"), relay.records.keys)
    }
}
