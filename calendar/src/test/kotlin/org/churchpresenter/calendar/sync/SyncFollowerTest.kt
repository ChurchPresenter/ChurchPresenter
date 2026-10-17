package org.churchpresenter.calendar.sync

import kotlinx.serialization.json.Json
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncFollowerTest {

    private val folder: File = Files.createTempDirectory("calendar-follower").toFile()
    private val today = LocalDate.of(2026, 9, 20)
    private val now = Instant.parse("2026-09-20T12:00:00Z")
    private val relay = FakeRelay().apply {
        desktopToken = TOKEN
        role = RemoteRole.FOLLOWER
        mainLastSeen = "2026-09-20T11:00:00Z"
    }
    private val sealing = Sealing(Envelope(ByteArray(Envelope.KEY_BYTES) { 4 }), "inst")
    private val store = CalendarStore(folder)
    private val shadow = FollowerShadowStore(folder)
    private val recordWrites = mutableListOf<Map<String, String>>()

    /** Refuses a record write for these ids, as the relay does when somebody wrote it first. */
    private val stale = HashSet<String>()

    private val transport = RelayTransport { method, url, headers, body ->
        val id = url.substringAfter("/records/", "")
        if (method == "PUT" && id.isNotEmpty()) {
            recordWrites += headers
            if (id in stale) return@RelayTransport RelayReply(412, """{"error":"precondition_failed"}""")
        }
        relay.send(method, url, headers, body)
    }

    private val mainService = RemoteService(
        id = "s1",
        date = "2026-09-27",
        startTime = "10:00",
        name = "Sunday",
        rows = listOf(
            RemoteRow.Section("h", "Welcome", "#5B9DF5"),
            RemoteRow.Ref("m", "Intro video", RemoteKind.MEDIA),
        ),
        version = 3L,
        editedAt = "2026-09-19T10:00:00Z",
    )

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun coordinator(role: SyncRole = SyncRole.FOLLOWER, shadow: FollowerShadowStore? = this.shadow) =
        SyncCoordinator(
            store = store,
            presetStore = PresetStore(folder),
            client = RelayClient("https://relay.example", "inst", "install-F", transport),
            sealing = sealing,
            installId = "install-F",
            songs = { emptyList() },
            today = { today },
            now = { now },
            role = role,
            shadow = shadow,
        )

    /** The main computer's own write of [service], as the relay stamps it. */
    private fun mainWrote(service: RemoteService) {
        relay.rev += 1
        relay.records[service.id] = sealing.seal(service).copy(updatedBy = Projection.DESKTOP, rev = relay.rev)
    }

    private fun renameLocally(id: String, name: String) {
        val document = store.load().document
        store.save(document.withService(document.serviceById(id)!!.copy(name = name)))
    }

    @Test
    fun `a follower keeps the main computer's rows it cannot open and sends nothing it did not change`() {
        mainWrote(mainService)

        val outcome = coordinator().sync(TOKEN, cursor = 0)

        val saved = store.load().document.serviceById("s1")!!
        assertEquals(listOf("h", "m"), saved.items.map { it.id })
        assertIs<ScheduleItem.MinistryItem>(saved.items[1])
        assertEquals(Resolver.UNRESOLVED_ELSEWHERE, saved.unresolvedRows["m"])
        assertEquals(1L, shadow.load("inst").getValue("s1").rev, "the copy is remembered with its revision")
        assertEquals(0, outcome.sentServices)
        assertTrue(recordWrites.isEmpty())
        assertTrue(relay.calls.none { it.startsWith("PUT /state") }, "a follower never pushes the whole picture")
        assertEquals(RemoteRole.FOLLOWER, outcome.role)
        assertEquals("2026-09-20T11:00:00Z", outcome.mainLastSeen)
        assertEquals(relay.rev, outcome.cursor)
    }

    @Test
    fun `a follower's edit goes back as one record, its unopened rows as they arrived`() {
        mainWrote(mainService)
        val first = coordinator().sync(TOKEN, cursor = 0)
        renameLocally("s1", "Sunday Evening")

        val second = coordinator().sync(TOKEN, first.cursor)

        assertEquals(1, second.sentServices)
        assertEquals("1", recordWrites.single()["If-Match"], "written against the copy it edited")
        val written = sealing.open(relay.records.getValue("s1"))!!
        assertEquals("Sunday Evening", written.name)
        assertEquals(mainService.rows[1], written.rows[1])
        assertEquals(4L, written.version)
        assertEquals(relay.rev, shadow.load("inst").getValue("s1").rev)
        assertEquals(first.cursor, second.cursor, "the next pull reads this write back")

        val third = coordinator().sync(TOKEN, second.cursor)

        assertEquals(0, third.sentServices, "its own write read back is not sent again")
        assertEquals("Sunday Evening", store.load().document.serviceById("s1")!!.name)
    }

    @Test
    fun `pushing the local file on a follower is a joined round`() {
        mainWrote(mainService)
        val first = coordinator().sync(TOKEN, cursor = 0)
        renameLocally("s1", "Renamed")

        val outcome = coordinator().pushLocal(TOKEN, first.cursor)

        assertEquals(1, outcome.sentServices)
        assertTrue(relay.calls.none { it.startsWith("PUT /state") })
    }

    @Test
    fun `a service deleted on a follower is sent as a sealed deletion`() {
        mainWrote(mainService)
        val first = coordinator().sync(TOKEN, cursor = 0)
        store.save(store.load().document.withoutService("s1", now))

        val outcome = coordinator().sync(TOKEN, first.cursor)

        assertEquals(1, outcome.sentServices)
        val deletion = sealing.open(relay.records.getValue("s1"))!!
        assertTrue(deletion.deleted)
        assertEquals(4L, deletion.version)
        assertTrue(shadow.load("inst").getValue("s1").deleted)
    }

    @Test
    fun `a deletion from the main computer is remembered as the follower's copy`() {
        mainWrote(mainService)
        val first = coordinator().sync(TOKEN, cursor = 0)
        mainWrote(
            RemoteService(id = "s1", date = "2026-09-27", startTime = "", name = "", deleted = true, version = 4L),
        )

        val outcome = coordinator().sync(TOKEN, first.cursor)

        assertEquals(null, store.load().document.serviceById("s1"))
        assertTrue(shadow.load("inst").getValue("s1").deleted)
        assertEquals(0, outcome.sentServices, "nothing left to send for it")
    }

    @Test
    fun `a write the relay moved on from waits, and the others still go`() {
        mainWrote(mainService)
        mainWrote(mainService.copy(id = "s2", name = "Monday", date = "2026-09-28"))
        val first = coordinator().sync(TOKEN, cursor = 0)
        renameLocally("s1", "Sunday Evening")
        renameLocally("s2", "Monday Evening")
        stale += "s1"

        val outcome = coordinator().sync(TOKEN, first.cursor)

        assertEquals(1, outcome.sentServices)
        assertEquals(2, recordWrites.size)
        val copies = shadow.load("inst")
        assertEquals("Sunday", copies.getValue("s1").name, "the refused one keeps the copy it was edited from")
        assertEquals("Monday Evening", copies.getValue("s2").name)
    }

    @Test
    fun `nothing is remembered when every write was refused`() {
        mainWrote(mainService)
        val first = coordinator().sync(TOKEN, cursor = 0)
        val remembered = shadow.file.readText()
        renameLocally("s1", "Sunday Evening")
        stale += "s1"

        val outcome = coordinator().sync(TOKEN, first.cursor)

        assertEquals(0, outcome.sentServices)
        assertEquals(remembered, shadow.file.readText())
    }

    @Test
    fun `a viewer never writes, whatever it changed`() {
        relay.role = RemoteRole.VIEWER
        mainWrote(mainService)
        val first = coordinator(SyncRole.VIEWER).sync(TOKEN, cursor = 0)
        renameLocally("s1", "Viewer's edit")

        val outcome = coordinator(SyncRole.VIEWER).sync(TOKEN, first.cursor)

        assertEquals(0, outcome.sentServices)
        assertTrue(recordWrites.isEmpty())
        assertEquals(RemoteRole.VIEWER, outcome.role)
    }

    @Test
    fun `a follower the relay now takes for the main computer does not write records`() {
        mainWrote(mainService)
        val first = coordinator().sync(TOKEN, cursor = 0)
        renameLocally("s1", "Promoted")
        relay.role = RemoteRole.MAIN

        val outcome = coordinator().sync(TOKEN, first.cursor)

        assertEquals(0, outcome.sentServices)
        assertTrue(recordWrites.isEmpty())
    }

    @Test
    fun `a follower with no memory of the relay's copies merges but sends nothing`() {
        mainWrote(mainService)
        val first = coordinator(shadow = null).sync(TOKEN, cursor = 0)
        renameLocally("s1", "Edited")

        val outcome = coordinator(shadow = null).sync(TOKEN, first.cursor)

        assertEquals(0, outcome.sentServices)
        assertTrue(recordWrites.isEmpty())
        assertEquals("Edited", store.load().document.serviceById("s1")!!.name)
        assertTrue(!shadow.file.exists())
    }

    @Test
    fun `the main computer lists the computers that joined, names opened, and a joined one lists none`() {
        relay.role = RemoteRole.MAIN
        relay.desktops["desk-2"] = RemoteComputer(
            id = "desk-2",
            role = RemoteRole.FOLLOWER,
            nameBox = sealing.sealText("Office iMac", "desk-2"),
            pairedAt = "2026-09-01T00:00:00Z",
            lastSeen = "2026-09-20T10:00:00Z",
        )
        relay.desktops["desk-3"] = RemoteComputer(id = "desk-3", role = RemoteRole.VIEWER)

        val outcome = coordinator(SyncRole.MAIN).sync(TOKEN, cursor = 0)

        val computers = outcome.computers.associateBy { it.id }
        assertEquals("Office iMac", computers.getValue("desk-2").name)
        assertEquals(RemoteRole.FOLLOWER, computers.getValue("desk-2").role)
        assertEquals("2026-09-20T10:00:00Z", computers.getValue("desk-2").lastSeen)
        assertEquals("", computers.getValue("desk-3").name, "an unsealed name is shown as none")
        assertTrue(coordinator().sync(TOKEN, cursor = 0).computers.isEmpty())
    }

    @Test
    fun `of two deletions of one service in a pull, the higher version stands`() {
        val resolver = Resolver(emptyList(), emptyList(), today, now)
        val current = resolver.resolve(mainService.copy(version = 4L), null)!!.service
        store.save(CalendarDocument(services = listOf(current)))
        val deletion = RemoteService(id = "s1", date = "2026-09-27", startTime = "", name = "", deleted = true)
        val changes = ChangesResponse(
            rev = 5L,
            records = listOf(
                sealing.seal(deletion.copy(version = 5L)).copy(rev = 4L),
                sealing.seal(deletion.copy(version = 3L)).copy(rev = 5L),
            ),
        )
        val replying = RelayTransport { method, url, _, _ ->
            when {
                url.contains("/changes") ->
                    RelayReply(200, RELAY_JSON.encodeToString(ChangesResponse.serializer(), changes))
                method == "PUT" && url.endsWith("/state") -> RelayReply(200, """{"rev":6}""")
                else -> RelayReply(404, "{}")
            }
        }
        val main = SyncCoordinator(
            store, null, RelayClient("https://relay.example", "inst", "install-M", replying), sealing, "install-M",
            songs = { emptyList() }, today = { today }, now = { now },
        )

        main.sync(TOKEN, cursor = 0)

        assertEquals(null, store.load().document.serviceById("s1"), "version 5 outranks the local 4")
        assertEquals(5L, store.load().document.deletedVersions["s1"])
    }

    private companion object {
        const val TOKEN = "desk-token"
        val RELAY_JSON = Json { encodeDefaults = true; classDiscriminator = "type" }
    }
}
