package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.runBlocking
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.sync.Envelope
import org.churchpresenter.calendar.sync.RelayReply
import org.churchpresenter.calendar.sync.RelayTransport
import org.churchpresenter.settings.CalendarSyncSettings
import java.io.File
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

        override fun send(method: String, url: String, headers: Map<String, String>, body: String?): RelayReply {
            calls += "$method $url"
            if (url == CalendarSyncSettings.CLIENT_KEY_URL) return RelayReply(200, """{"clientKey":"$clientKey"}""")
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
                    """{"rev":$rev,"records":[],"tombstones":[],"devices":[],"lastDesktopInstall":""}""",
                )
                path == "state" -> { rev += 1; RelayReply(200, """{"rev":$rev}""") }
                path.startsWith("devices/") && method == "PUT" -> {
                    enrolled[path.removePrefix("devices/")] = body.orEmpty()
                    RelayReply(200, "{}")
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

    private fun service() = CalendarSyncService(
        folder = folder,
        songFolder = null,
        settings = { settings },
        saveSettings = { settings = it; saved += it },
        transport = relay,
    )

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `sync off means no network and status Off`() = runBlocking<Unit> {
        settings = settings.copy(enabled = false)

        assertFalse(service().syncOnStartup())

        assertEquals(CalendarSyncStatus.Off, service().status.value)
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
    fun `a local plan is pushed and the cursor advances`() = runBlocking<Unit> {
        val sunday = PlannedService("s", "2026-09-27", "Sunday", "10:00")
        CalendarStore(folder).save(CalendarDocument(services = listOf(sunday)))

        service().syncOnStartup()

        assertEquals(relay.rev, settings.cursor)
        assertTrue(settings.lastSyncAt.isNotBlank())
        assertNull(saved.firstOrNull { it.cursor > relay.rev })
    }
}
