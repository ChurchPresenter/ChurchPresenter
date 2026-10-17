package org.churchpresenter.server

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.calendar.sync.Envelope
import org.churchpresenter.calendar.sync.RelayReply
import org.churchpresenter.calendar.sync.RelayTransport
import org.churchpresenter.calendar.sync.Sealing
import org.churchpresenter.settings.CalendarSyncSettings
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarComputersTest {

    private val folder: File = Files.createTempDirectory("calendar-computers").toFile()
    private val key = Envelope.encodeKey(Envelope.newKey())
    private val mainSettings = CalendarSyncSettings(
        enabled = true,
        relayUrl = "",
        instanceId = "inst",
        desktopToken = "main-token",
        instanceKey = key,
        installId = "install-A",
        clientKey = "key-1",
    )
    private var settings = mainSettings
    private var devMode = true
    private val clock = Instant.parse("2026-10-01T09:00:00Z")

    private class Call(val method: String, val path: String, val auth: String?, val body: String?)

    /** Records every call; answers each with [reply], and a round's `changes` with [changes]. */
    private inner class Relay : RelayTransport {
        val calls = mutableListOf<Call>()
        var reply: (Call) -> RelayReply? = { null }
        var changes = """{"rev":5}"""

        override fun send(method: String, url: String, headers: Map<String, String>, body: String?): RelayReply {
            if (url == CLIENT_KEY) return RelayReply(200, """{"clientKey":"key-1"}""")
            val call = Call(method, url.substringAfter(RELAY).substringBefore("?"), headers["Authorization"], body)
            calls += call
            reply(call)?.let { return it }
            return when {
                call.path.endsWith("/changes") -> RelayReply(200, changes)
                call.path.endsWith("/state") -> RelayReply(200, """{"rev":6}""")
                else -> RelayReply(200, "{}")
            }
        }
    }

    private val relay = Relay()

    private val service = CalendarSyncService(
        folder = folder,
        songFolder = null,
        settings = { settings },
        saveSettings = { settings = it },
        transport = relay,
        endpoints = RelayEndpoints(RELAY, CLIENT_KEY),
        now = { clock },
        multiComputer = { devMode },
    )
    private val computers = service.computers

    private fun sealing() = Sealing(Envelope(assertNotNull(Envelope.decodeKey(key))), "inst")

    private fun body(call: Call) = Json.parseToJsonElement(call.body.orEmpty()).jsonObject

    private val follower = mainSettings.copy(
        desktopToken = "",
        role = CalendarSyncSettings.ROLE_FOLLOWER,
        computerId = "comp-1",
        computerToken = "follower-token",
        mainName = "Office",
    )

    private fun join(relayUrl: String = RELAY, role: String = CalendarSyncSettings.ROLE_FOLLOWER) = CalendarJoin(
        relay = relayUrl,
        instance = "inst-2",
        desktop = "comp-9",
        token = "t".repeat(32),
        key = key,
        role = role,
        mainName = "Sanctuary",
        expires = clock.plus(Duration.ofHours(1)).toString(),
    )

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `with dev mode off nothing reaches the relay and nothing is saved`() = runBlocking<Unit> {
        devMode = false

        assertNull(computers.invite(CalendarSyncSettings.ROLE_FOLLOWER, "Lobby", "Office"))
        computers.remove("comp-1")
        assertFalse(computers.promote("comp-1", "Office"))
        assertFalse(computers.join(join()))
        settings = follower
        assertFalse(computers.takeOver())

        assertEquals(follower, settings)
        assertTrue(relay.calls.isEmpty())
    }

    @Test
    fun `an invite adds the computer on the relay and hands back a join that parses`() = runBlocking<Unit> {
        val join = assertNotNull(computers.invite(CalendarSyncSettings.ROLE_VIEWER, "Lobby", "O".repeat(200)))

        val call = relay.calls.single()
        assertEquals("PUT", call.method)
        assertEquals("/i/inst/desktops/${join.desktop}", call.path)
        assertEquals("Bearer main-token", call.auth)
        val sent = body(call)
        assertEquals(CalendarRelayAccess.sha256Hex(join.token), sent.getValue("tokenHash").jsonPrimitive.content)
        assertEquals("viewer", sent.getValue("role").jsonPrimitive.content)
        assertEquals("Lobby", sealing().openName(sent.getValue("nameBox").jsonPrimitive.content, join.desktop))
        assertFalse(join.token in call.body.orEmpty())

        assertEquals(RELAY, join.relay)
        assertEquals("inst", join.instance)
        assertEquals(key, join.key)
        assertEquals(CalendarSyncSettings.ROLE_VIEWER, join.role)
        assertEquals(120, join.mainName.length)
        assertEquals("2026-10-02T09:00:00Z", join.expires)
        assertEquals(JoinParse.Ok(join), CalendarJoin.parse(join.toCode(), clock))

        val listed = computers.list.value.single()
        assertEquals(join.desktop, listed.id)
        assertEquals("Lobby", listed.name)
        assertEquals("viewer", listed.role)
        assertEquals(clock.toString(), listed.pairedAt)
    }

    @Test
    fun `an invite with no name seals none`() = runBlocking<Unit> {
        val join = assertNotNull(computers.invite(CalendarSyncSettings.ROLE_FOLLOWER, "  ", "Office"))

        assertEquals("", body(relay.calls.single()).getValue("nameBox").jsonPrimitive.content)
        assertEquals("follower", join.role)
    }

    @Test
    fun `no invite while sync is off or this computer joined another's calendar`() = runBlocking<Unit> {
        settings = mainSettings.copy(enabled = false)
        assertNull(computers.invite(CalendarSyncSettings.ROLE_FOLLOWER, "Lobby", "Office"))
        settings = follower
        assertNull(computers.invite(CalendarSyncSettings.ROLE_FOLLOWER, "Lobby", "Office"))

        assertTrue(relay.calls.isEmpty())
    }

    @Test
    fun `an invite the relay refuses answers null and shows the failure`() = runBlocking<Unit> {
        relay.reply = { RelayReply(500, "boom") }

        assertNull(computers.invite(CalendarSyncSettings.ROLE_FOLLOWER, "Lobby", "Office"))

        assertIs<CalendarSyncStatus.Failed>(service.status.value)
        assertTrue(computers.list.value.isEmpty())
    }

    @Test
    fun `removing a computer deletes it on the relay and from the list`() = runBlocking<Unit> {
        val join = assertNotNull(computers.invite(CalendarSyncSettings.ROLE_FOLLOWER, "Lobby", "Office"))
        relay.calls.clear()

        computers.remove(join.desktop)

        val call = relay.calls.single()
        assertEquals("DELETE", call.method)
        assertEquals("/i/inst/desktops/${join.desktop}", call.path)
        assertEquals("Bearer main-token", call.auth)
        assertTrue(computers.list.value.isEmpty())
    }

    @Test
    fun `a removal the relay refuses keeps the computer listed and says why`() = runBlocking<Unit> {
        val join = assertNotNull(computers.invite(CalendarSyncSettings.ROLE_FOLLOWER, "Lobby", "Office"))
        relay.reply = { RelayReply(401, """{"error":"unauthorized"}""") }

        computers.remove(join.desktop)

        assertEquals(CalendarSyncStatus.Unauthorized, service.status.value)
        assertEquals(listOf(join.desktop), computers.list.value.map { it.id })
    }

    @Test
    fun `a joined computer removes nobody`() = runBlocking<Unit> {
        settings = follower

        computers.remove("comp-2")

        assertTrue(relay.calls.isEmpty())
    }

    @Test
    fun `promoting a computer makes this one its follower and runs a round as one`() = runBlocking<Unit> {
        val join = assertNotNull(computers.invite(CalendarSyncSettings.ROLE_FOLLOWER, "Lobby", "Office"))
        relay.calls.clear()
        relay.changes = """{"rev":7,"role":"follower"}"""

        assertTrue(computers.promote(join.desktop, "Office"))

        val promote = relay.calls.first()
        assertEquals("POST", promote.method)
        assertEquals("/i/inst/desktops/${join.desktop}/promote", promote.path)
        assertEquals("Bearer main-token", promote.auth)
        val sent = body(promote)
        assertTrue(sent.getValue("keepFormer").jsonPrimitive.boolean)
        val formerId = sent.getValue("formerId").jsonPrimitive.content
        assertEquals("Office", sealing().openName(sent.getValue("formerNameBox").jsonPrimitive.content, formerId))

        assertEquals(CalendarSyncSettings.ROLE_FOLLOWER, settings.role)
        assertEquals(formerId, settings.computerId)
        assertEquals("main-token", settings.computerToken)
        assertEquals("", settings.desktopToken)
        assertEquals("Lobby", settings.mainName)
        assertTrue(computers.list.value.isEmpty())

        // The round after it syncs as the follower, with the old main token.
        val round = relay.calls.drop(1).first()
        assertTrue(round.path.endsWith("/changes"))
        assertEquals("Bearer main-token", round.auth)
        assertEquals(7L, settings.cursor)
        assertTrue(relay.calls.none { it.path.endsWith("/state") })
    }

    @Test
    fun `promoting a computer not listed, with no name of its own, sends no name`() = runBlocking<Unit> {
        assertTrue(computers.promote("comp-5", ""))

        assertEquals("", body(relay.calls.first()).getValue("formerNameBox").jsonPrimitive.content)
        assertEquals("", settings.mainName)
    }

    @Test
    fun `a promotion the relay refuses changes nothing and runs no round`() = runBlocking<Unit> {
        relay.reply = { RelayReply(403, "{}") }

        assertFalse(computers.promote("comp-5", "Office"))

        assertEquals(mainSettings, settings)
        assertEquals(CalendarSyncStatus.Unauthorized, service.status.value)
        assertEquals(1, relay.calls.size)
    }

    @Test
    fun `a joined computer promotes nobody`() = runBlocking<Unit> {
        settings = follower

        assertFalse(computers.promote("comp-5", "Office"))

        assertTrue(relay.calls.isEmpty())
    }

    @Test
    fun `a follower takes over as the main computer with its own token`() = runBlocking<Unit> {
        settings = follower
        relay.changes = """{"rev":3,"role":"main"}"""

        assertTrue(computers.takeOver())

        val promote = relay.calls.first()
        assertEquals("POST", promote.method)
        assertEquals("/i/inst/desktops/comp-1/promote", promote.path)
        assertEquals("Bearer follower-token", promote.auth)
        assertFalse(body(promote).getValue("keepFormer").jsonPrimitive.boolean)

        assertEquals("follower-token", settings.desktopToken)
        assertEquals("", settings.role)
        assertEquals("", settings.computerToken)
        assertEquals("", settings.computerId)
        assertEquals("", settings.mainName)
        assertFalse(settings.isJoined)
        // The round after it is the main computer's: it pushes the whole picture.
        assertTrue(relay.calls.any { it.method == "PUT" && it.path.endsWith("/state") })
    }

    @Test
    fun `only a follower can take over`() = runBlocking<Unit> {
        assertFalse(computers.takeOver())
        settings = follower.copy(role = CalendarSyncSettings.ROLE_VIEWER)
        assertFalse(computers.takeOver())

        assertTrue(relay.calls.isEmpty())
    }

    @Test
    fun `a take-over the relay refuses keeps the follower as it was`() = runBlocking<Unit> {
        settings = follower
        relay.reply = { RelayReply(409, "{}") }

        assertFalse(computers.takeOver())

        assertEquals(follower, settings)
        assertIs<CalendarSyncStatus.Failed>(service.status.value)
        assertEquals(1, relay.calls.size)
    }

    @Test
    fun `joining on the relay this build was made for saves no relay address`() = runBlocking<Unit> {
        settings = mainSettings.copy(cursor = 40L, lastSyncAt = "2026-09-01T00:00:00Z")
        relay.changes = """{"rev":2,"role":"follower"}"""

        assertTrue(computers.join(join(relayUrl = "$RELAY/")))

        assertEquals("", settings.relayUrl)
        assertEquals("inst-2", settings.instanceId)
        assertEquals(key, settings.instanceKey)
        assertEquals("", settings.desktopToken)
        assertEquals(CalendarSyncSettings.ROLE_FOLLOWER, settings.role)
        assertEquals("comp-9", settings.computerId)
        assertEquals("t".repeat(32), settings.computerToken)
        assertEquals("Sanctuary", settings.mainName)
        assertEquals(2L, settings.cursor)
        val round = relay.calls.single()
        assertEquals("/i/inst-2/changes", round.path)
        assertEquals("Bearer ${"t".repeat(32)}", round.auth)
        assertIs<CalendarSyncStatus.Synced>(service.status.value)
    }

    @Test
    fun `joining on another relay keeps its address, and a failed first round says so`() = runBlocking<Unit> {
        settings = mainSettings.copy(enabled = false)
        relay.reply = { RelayReply(401, """{"error":"unauthorized"}""") }

        assertFalse(computers.join(join(relayUrl = "https://other.example", role = CalendarSyncSettings.ROLE_VIEWER)))

        assertTrue(settings.enabled)
        assertEquals("https://other.example", settings.relayUrl)
        assertEquals(CalendarSyncSettings.ROLE_VIEWER, settings.role)
        assertEquals(CalendarSyncStatus.Unauthorized, service.status.value)
    }

    @Test
    fun `leaving forgets the other calendar and keeps the switch and the install`() = runBlocking<Unit> {
        settings = follower.copy(cursor = 9L, lastSyncAt = "2026-09-01T00:00:00Z")

        computers.leave()

        assertTrue(settings.enabled)
        assertEquals("install-A", settings.installId)
        assertEquals("", settings.instanceId)
        assertEquals("", settings.instanceKey)
        assertEquals("", settings.role)
        assertEquals("", settings.computerId)
        assertEquals("", settings.computerToken)
        assertEquals("", settings.mainName)
        assertEquals(0L, settings.cursor)
        assertEquals("", settings.lastSyncAt)
        assertTrue(computers.list.value.isEmpty())
    }

    @Test
    fun `unpairing a joined computer leaves the calendar, whatever the start-over week says`() {
        settings = follower.copy(rotatedAt = clock.toString())

        assertTrue(service.unpair())

        assertFalse(settings.isJoined)
        assertFalse(settings.isPaired)
        assertEquals(clock.toString(), settings.rotatedAt)
        assertEquals(CalendarSyncStatus.Unpaired, service.status.value)
    }

    @Test
    fun `a joined computer with dev mode off does not sync, register or push songbooks`() = runBlocking<Unit> {
        settings = follower
        devMode = false

        assertFalse(service.syncNow())
        assertEquals(CalendarSyncStatus.Off, service.status.value)
        assertTrue(service.registerIfNeeded())
        assertEquals(0, service.pushCatalog())

        assertTrue(relay.calls.isEmpty())
        assertEquals(follower, settings)
    }

    @Test
    fun `the main computer's round lists the computers that joined, names opened`() = runBlocking<Unit> {
        val nameBox = sealing().sealText("Lobby", "comp-3")
        relay.changes = """{"rev":4,"role":"main","desktops":[{"id":"comp-3","role":"viewer",""" +
            """"nameBox":"$nameBox","pairedAt":"2026-09-30T00:00:00Z","lastSeen":"2026-10-01T08:00:00Z"}]}"""

        assertTrue(service.syncNow())

        val listed = computers.list.value.single()
        assertEquals("comp-3", listed.id)
        assertEquals("viewer", listed.role)
        assertEquals("Lobby", listed.name)
        assertEquals("2026-10-01T08:00:00Z", listed.lastSeen)
        assertEquals(mainSettings.desktopToken, settings.desktopToken)
    }

    @Test
    fun `a main computer the relay now takes for a follower keeps its token in the joined place`() =
        runBlocking<Unit> {
            relay.changes = """{"rev":4,"role":"follower"}"""

            assertTrue(service.syncNow())

            assertEquals(CalendarSyncSettings.ROLE_FOLLOWER, settings.role)
            assertEquals("main-token", settings.computerToken)
            assertEquals("", settings.desktopToken)
            assertTrue(computers.list.value.isEmpty())
        }

    @Test
    fun `a follower the relay calls a viewer becomes one, and one it calls main takes the desktop token`() =
        runBlocking<Unit> {
            settings = follower
            relay.changes = """{"rev":4,"role":"viewer"}"""
            assertTrue(service.syncNow())
            assertEquals(CalendarSyncSettings.ROLE_VIEWER, settings.role)
            assertEquals("follower-token", settings.computerToken)

            relay.changes = """{"rev":5,"role":"main"}"""
            assertTrue(service.syncNow())
            assertFalse(settings.isJoined)
            assertEquals("follower-token", settings.desktopToken)
            assertEquals(5L, settings.cursor)
        }

    @Test
    fun `the relay's role leaves settings alone when it already matches or is blank`() {
        assertEquals(follower, follower.withRelayRole("follower"))
        assertEquals(mainSettings, mainSettings.withRelayRole("main"))
        assertEquals(mainSettings, mainSettings.withRelayRole(""))
        assertEquals(follower, follower.withRelayRole(""))
    }
}

private const val RELAY = "https://relay.example"
private const val CLIENT_KEY = "https://keys.example/computers"
