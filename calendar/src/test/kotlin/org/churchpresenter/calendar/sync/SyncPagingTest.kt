package org.churchpresenter.calendar.sync

import kotlinx.serialization.json.Json
import org.churchpresenter.calendar.CalendarStore
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The desktop reads every page the relay offers before it pushes, and does not loop forever. */
class SyncPagingTest {

    private val folder: File = Files.createTempDirectory("sync-paging").toFile()
    private val sealing = Sealing(Envelope(Envelope.newKey()), INSTANCE)
    private val today = LocalDate.of(2026, 9, 20)

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun record(id: String, rev: Long): SealedRecord {
        val service = RemoteService(id = id, date = "2026-09-27", startTime = "10:00", name = "Service $id")
        return sealing.seal(service).copy(updatedAt = "2026-09-20T10:00:00Z", updatedBy = "phone-1", rev = rev)
    }

    /** A relay stand-in: [changes] answers each `GET /changes?since=N`; a state push is accepted at revision 10. */
    private class FakeRelay(private val changes: (Long) -> ChangesResponse) : RelayTransport {
        val reads = mutableListOf<Long>()
        var pushedIfMatch: String? = null

        override fun send(method: String, url: String, headers: Map<String, String>, body: String?): RelayReply = when {
            method == "GET" && "/changes?since=" in url -> {
                val since = url.substringAfter("since=").toLong()
                reads += since
                RelayReply(200, Json.encodeToString(ChangesResponse.serializer(), changes(since)))
            }
            method == "PUT" && url.endsWith("/state") -> {
                pushedIfMatch = headers["If-Match"]
                RelayReply(200, """{"rev":10}""")
            }
            else -> RelayReply(404, "")
        }
    }

    private fun coordinator(relay: FakeRelay) = SyncCoordinator(
        store = CalendarStore(folder),
        presetStore = null,
        client = RelayClient("https://relay.test", INSTANCE, "install-1", relay),
        sealing = sealing,
        installId = "install-1",
        songs = { emptyList() },
        today = { today },
        now = { Instant.parse("2026-09-20T12:00:00Z") },
    )

    @Test
    fun `a full page is followed to the end before anything is pushed`() {
        val relay = FakeRelay { since ->
            when (since) {
                0L -> ChangesResponse(rev = 5, more = true, records = listOf(record("a", 3)))
                else -> ChangesResponse(rev = 9, more = false, records = listOf(record("b", 8)))
            }
        }

        val outcome = coordinator(relay).sync("token", cursor = 0)

        assertEquals(listOf(0L, 5L), relay.reads, "the second call starts where the first page ended")
        assertEquals("9", relay.pushedIfMatch, "the push is conditioned on the last page's revision")
        assertEquals(10L, outcome.cursor)
        assertEquals(2, outcome.phoneChanges)
        assertEquals(setOf("a", "b"), CalendarStore(folder).load().document.services.map { it.id }.toSet())
    }

    @Test
    fun `a relay that never stops paging is given up on`() {
        val relay = FakeRelay { since -> ChangesResponse(rev = since + 1, more = true) }

        assertFailsWith<RelayFailure.Rejected> { coordinator(relay).sync("token", cursor = 0) }
        assertEquals(null, relay.pushedIfMatch, "nothing is pushed on a half-read relay")
    }

    private companion object {
        const val INSTANCE = "test-instance"
    }
}
