package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.sync.Envelope
import org.churchpresenter.calendar.sync.RelayReply
import org.churchpresenter.calendar.sync.RelayTransport
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.CalendarSyncSettings
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarSyncServiceTest {

    private val folder: File = Files.createTempDirectory("calendar-sync-service").toFile()
    private var settings = CalendarSyncSettings(enabled = true, relayUrl = "https://relay.example")
    private val saved = mutableListOf<CalendarSyncSettings>()

    /** The relay and the website's key endpoint, as the service sees them over one transport. */
    private inner class Relay : RelayTransport {
        var registered = false
        var rev = 0L
        val calls = mutableListOf<String>()
        val enrolled = mutableMapOf<String, String>()
        var clientKey = "key-1"
        var refuseKeyOnce = false
        var relayDown = false
        var keyEndpointDown = false
        var refuseRecords = false
        val catalogPuts = mutableListOf<String>()
        var otherInstall = ""
        var slowMs = 0L

        private val devicesJson: String
            get() = enrolled.keys.joinToString(",", "[", "]") {
                """{"id":"$it","nameBox":"","pairedAt":"2026-09-20T00:00:00Z","lastSeen":""}"""
            }

        private fun clientKeyReply(): RelayReply =
            if (keyEndpointDown) RelayReply(503, "{}") else RelayReply(200, """{"clientKey":"$clientKey"}""")

        override fun send(method: String, url: String, headers: Map<String, String>, body: String?): RelayReply {
            calls += "$method $url"
            if (relayDown) throw IOException("relay down")
            if (slowMs > 0) Thread.sleep(slowMs)
            if (url == CalendarSyncSettings.CLIENT_KEY_URL) return clientKeyReply()
            if (headers["X-Client-Key"] != clientKey || refuseKeyOnce) {
                refuseKeyOnce = false
                return RelayReply(401, """{"error":"client_key"}""")
            }
            val path = url.substringAfter("/i/").substringAfter("/").substringBefore("?")
            return when {
                path == "register" && registered -> RelayReply(409, "{}")
                path == "register" -> {
                    registered = true
                    RelayReply(201, """{"desktopToken":"tok"}""")
                }
                headers["Authorization"] != "Bearer tok" -> RelayReply(401, """{"error":"unauthorized"}""")
                path == "changes" -> RelayReply(
                    200,
                    """{"rev":$rev,"records":[],"tombstones":[],"devices":$devicesJson,""" +
                        """"lastDesktopInstall":"$otherInstall"}""",
                )
                path == "state" -> { rev += 1; RelayReply(200, """{"rev":$rev}""") }
                path.startsWith("devices/") && method == "PUT" -> {
                    enrolled[path.removePrefix("devices/")] = body.orEmpty()
                    RelayReply(200, "{}")
                }
                path.startsWith("records/") && method == "PUT" && refuseRecords ->
                    RelayReply(401, """{"error":"unauthorized"}""")
                path.startsWith("records/") && method == "PUT" -> {
                    catalogPuts += path.removePrefix("records/")
                    rev += 1
                    RelayReply(200, """{"rev":$rev}""")
                }
                path.startsWith("devices/") && method == "DELETE" -> {
                    enrolled.remove(path.removePrefix("devices/"))
                    RelayReply(200, "{}")
                }
                else -> RelayReply(404, "{}")
            }
        }
    }

    private val relay = Relay()

    private fun service(songFolder: File? = null) = CalendarSyncService(
        folder = folder,
        songFolder = songFolder,
        settings = { settings },
        saveSettings = { settings = it; saved += it },
        transport = relay,
        typicalSeconds = { 270 },
    )

    /** A library of one songbook with one song, in the file form the desktop reads. */
    private fun songFolder(): File {
        val root = File(folder, "songs").also { File(it, "Hymnal").mkdirs() }
        SongFileParser().writeSongFile(
            SongItem(
                number = "42",
                title = "Here I Am to Worship",
                songbook = "Hymnal",
                lyrics = listOf("Light of the world"),
            ),
            File(root, "Hymnal/0042 - Here I Am to Worship.song").path,
        )
        return root
    }

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `sync off means no network and status Off`() = runBlocking<Unit> {
        settings = settings.copy(enabled = false)
        val service = service()

        assertFalse(service.syncOnStartup())
        assertEquals(CalendarSyncStatus.Off, service.status.value)

        // Even a desktop that was paired before the switch went off stays silent.
        settings = settings.copy(instanceId = "inst", desktopToken = "tok", instanceKey = "k".repeat(43))
        assertFalse(service.syncNow())
        assertNull(service.enroll("phone-1", "Anna's iPhone"))
        service.revokeDevice("phone-1")
        assertEquals(CalendarSyncStatus.Off, service.status.value)
        assertTrue(relay.calls.isEmpty())
    }

    @Test
    fun `the first sync registers, mints a key, and completes a round`() = runBlocking<Unit> {
        val service = service()

        assertTrue(service.syncOnStartup())

        assertTrue(settings.isPaired)
        assertEquals("tok", settings.desktopToken)
        assertNotNull(Envelope.decodeKey(settings.instanceKey))
        assertTrue(settings.instanceId.isNotBlank())
        assertTrue(settings.installId.isNotBlank())
        assertEquals("key-1", settings.clientKey)
        assertEquals(relay.rev, settings.cursor)
        assertIs<CalendarSyncStatus.Synced>(service.status.value)
        assertTrue(relay.calls.any { it.contains("/register") })
        assertTrue(relay.calls.any { it.startsWith("PUT") && it.contains("/state") })
    }

    @Test
    fun `a desktop the relay could not register when sync was switched on is registered by the loop`() =
        runBlocking<Unit> {
            relay.relayDown = true
            val service = service()
            assertFalse(service.syncOnStartup())
            assertIs<CalendarSyncStatus.Failed>(service.status.value)
            assertFalse(settings.isPaired)

            relay.relayDown = false
            val loop = launch { service.run(pullIntervalMs = 5) }
            withTimeout(5_000) { service.status.first { it is CalendarSyncStatus.Synced } }
            loop.cancelAndJoin()

            assertTrue(settings.isPaired)
            assertTrue(relay.calls.any { it.contains("/register") })
        }

    @Test
    fun `the songbooks go after the startup round, in the loop, and not inside its budget`() = runBlocking<Unit> {
        val service = service(songFolder = songFolder())

        assertTrue(service.syncOnStartup())
        assertTrue(relay.catalogPuts.isEmpty(), "the startup round does not carry the songbooks")

        val loop = launch { service.run(pullIntervalMs = 60_000) }
        withTimeout(5_000) { while (relay.catalogPuts.isEmpty()) delay(10) }
        loop.cancelAndJoin()

        assertEquals(listOf("catalog:Hymnal-cf49adf4"), relay.catalogPuts)
        // Unchanged since: nothing more to write.
        assertEquals(0, service.pushCatalog())
        assertEquals(1, relay.catalogPuts.size)
    }

    @Test
    fun `the songbooks are not pushed while sync is off or the desktop is unpaired`() = runBlocking<Unit> {
        val service = service(songFolder = songFolder())
        assertEquals(0, service.pushCatalog())
        settings = settings.copy(
            enabled = false,
            instanceId = "inst",
            desktopToken = "tok",
            instanceKey = "k".repeat(43),
        )
        assertEquals(0, service.pushCatalog())
        assertTrue(relay.calls.isEmpty())
    }

    @Test
    fun `a taken instance id is retried with a fresh one`() = runBlocking<Unit> {
        relay.registered = true
        settings = settings.copy(instanceId = "taken")

        assertFalse(service().syncOnStartup())

        // Two register attempts: the stored id, then a fresh one, both refused by this relay.
        assertEquals(2, relay.calls.count { it.contains("/register") })
        assertFalse(settings.isPaired)
    }

    @Test
    fun `a rotated client key is fetched again and the round retried`() = runBlocking<Unit> {
        service().syncOnStartup()
        relay.clientKey = "key-2"
        val service = service()

        assertTrue(service.syncNow())

        assertEquals("key-2", settings.clientKey)
        assertIs<CalendarSyncStatus.Synced>(service.status.value)
    }

    @Test
    fun `enrolling a phone hands the relay a hash and the phone the token and key`() = runBlocking<Unit> {
        val service = service()

        val enrollment = service.enroll("phone-1", "Anna's iPhone")

        assertNotNull(enrollment)
        assertEquals(settings.instanceId, enrollment.instanceId)
        assertEquals(settings.instanceKey, enrollment.instanceKey)
        val digest = MessageDigest.getInstance("SHA-256").digest(enrollment.deviceToken.toByteArray())
        val hash = digest.joinToString("") { "%02x".format(it) }
        assertTrue(hash in relay.enrolled.getValue("phone-1"))
        assertFalse(enrollment.deviceToken in relay.enrolled.getValue("phone-1"))
        val qrPrefix = "churchpresenter://calendar-enroll?relay=https://relay.example&instance="
        assertTrue(enrollment.qrContent.startsWith(qrPrefix))
        assertTrue("token=${enrollment.deviceToken}" in enrollment.qrContent)
        assertTrue("key=${settings.instanceKey}" in enrollment.qrContent)
    }

    @Test
    fun `a refused token is reported as unauthorized`() = runBlocking<Unit> {
        service().syncOnStartup()
        settings = settings.copy(desktopToken = "stale")
        val service = service()

        assertFalse(service.syncNow())

        assertEquals(CalendarSyncStatus.Unauthorized, service.status.value)
    }

    @Test
    fun `unpairing forgets everything and the next round starts over with a new key`() = runBlocking<Unit> {
        service().syncOnStartup()
        val firstKey = settings.instanceKey
        val service = service()

        service.unpair()

        assertFalse(settings.isPaired)
        assertEquals(CalendarSyncStatus.Unpaired, service.status.value)
        // A plain sync does nothing until the next startup round registers again.
        assertFalse(service.syncNow())
        relay.registered = false
        assertTrue(service.syncOnStartup())
        assertTrue(settings.instanceKey != firstKey)
    }

    @Test
    fun `a phone can be revoked, and the relay refusing that is reported`() = runBlocking<Unit> {
        val service = service()
        service.enroll("phone-1", "")
        service.enroll("phone-2", "Ben")
        assertTrue(service.syncNow())
        assertEquals(setOf("phone-1", "phone-2"), service.devices.value.map { it.id }.toSet())

        service.revokeDevice("phone-1")
        assertEquals(listOf("phone-2"), service.devices.value.map { it.id })
        assertFalse("phone-1" in relay.enrolled)

        settings = settings.copy(desktopToken = "stale")
        service.revokeDevice("phone-2")
        assertEquals(CalendarSyncStatus.Unauthorized, service.status.value)
        assertTrue("phone-2" in relay.enrolled)
    }

    @Test
    fun `another desktop pushing to the same instance stops this one and says so`() = runBlocking<Unit> {
        service().syncOnStartup()
        relay.otherInstall = "install-B"
        val service = service()
        val pushesBefore = relay.calls.count { it.startsWith("PUT") && it.endsWith("/state") }

        assertTrue(service.syncNow())

        assertEquals(CalendarSyncStatus.OtherDesktop("install-B"), service.status.value)
        assertEquals(pushesBefore, relay.calls.count { it.startsWith("PUT") && it.endsWith("/state") })
    }

    @Test
    fun `a relay too slow at startup is reported as timed out and the app goes on`() = runBlocking<Unit> {
        relay.slowMs = 200
        val service = service()

        assertFalse(service.syncOnStartup(timeoutMs = 50))

        assertEquals(CalendarSyncStatus.TimedOut, service.status.value)
    }

    @Test
    fun `an install id is minted once and then kept`() = runBlocking<Unit> {
        settings = settings.copy(installId = "")
        service().syncOnStartup()
        val minted = settings.installId
        assertTrue(minted.isNotBlank())
        service().syncNow()
        assertEquals(minted, settings.installId)
        assertTrue(relay.calls.isNotEmpty())
    }

    @Test
    fun `an invite enrolls a nameless device and becomes the QR the card opens`() = runBlocking<Unit> {
        val service = service()

        val invite = service.invitePhone().asInvite(service)

        val ready = assertIs<CalendarInvite.Ready>(invite)
        val enrollment = ready.enrollment
        // A device of its own, not one the operator named: the phone that scans it names itself.
        assertEquals(setOf(enrollment.deviceId), relay.enrolled.keys)
        assertTrue("\"nameBox\":\"\"" in relay.enrolled.getValue(enrollment.deviceId))
        assertTrue("device=${enrollment.deviceId}" in enrollment.qrContent)
        assertEquals(settings.instanceKey, enrollment.instanceKey)
    }

    @Test
    fun `an invite the relay refused opens the dialog on the reason instead of a QR`() = runBlocking<Unit> {
        relay.relayDown = true
        val service = service()

        val invite = service.invitePhone().asInvite(service)

        val failed = assertIs<CalendarInvite.Failed>(invite)
        assertIs<CalendarSyncStatus.Failed>(failed.status)
        assertEquals(service.status.value, failed.status)
    }

    @Test
    fun `an invite is never sent without a client key, and says so`() = runBlocking<Unit> {
        service().syncOnStartup()
        // The website cannot be asked: no key to send, and a keyless call is a wrong-key attempt.
        settings = settings.copy(clientKey = "")
        relay.keyEndpointDown = true
        val service = service()

        val invite = service.invitePhone().asInvite(service)

        assertIs<CalendarInvite.Failed>(invite)
        assertIs<CalendarSyncStatus.Failed>(service.status.value)
        assertTrue(relay.calls.none { it.contains("/devices/") })
    }

    @Test
    fun `a relay that refuses the songbooks is reported, and the app carries on`() = runBlocking<Unit> {
        val service = service(songFolder = songFolder())
        assertTrue(service.syncOnStartup())
        relay.refuseRecords = true

        assertEquals(0, service.pushCatalog())

        assertIs<CalendarSyncStatus.Unauthorized>(service.status.value)
        // The failure is a status, not an exception: the next good push clears it.
        relay.refuseRecords = false
        assertEquals(1, service.pushCatalog())
    }

    @Test
    fun `a stored key that is not a key is treated as a lost pairing`() = runBlocking<Unit> {
        service().syncOnStartup()
        settings = settings.copy(instanceKey = "not-a-key")
        val service = service()

        assertFalse(service.syncNow())

        assertEquals(CalendarSyncStatus.Unauthorized, service.status.value)
    }

    @Test
    fun `a desktop with no song folder pushes no songbooks`() = runBlocking<Unit> {
        val service = service(songFolder = null)
        assertTrue(service.syncOnStartup())

        assertEquals(0, service.pushCatalog())

        assertTrue(relay.catalogPuts.isEmpty())
    }

    @Test
    fun `a local plan is pushed and the cursor advances`() = runBlocking<Unit> {
        val sunday = PlannedService("s", "2026-09-27", "Sunday", "10:00")
        CalendarStore(folder).save(CalendarDocument(services = listOf(sunday)))

        service().syncOnStartup()

        assertEquals(relay.rev, settings.cursor)
        assertTrue(settings.lastSyncAt.isNotBlank())
        assertNull(saved.firstOrNull { it.cursor > relay.rev })
    }
}
