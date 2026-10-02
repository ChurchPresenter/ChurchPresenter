package org.churchpresenter.calendar.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What each relay reply means, beyond the cases [RelayClientTest] walks through with [FakeRelay]:
 * every status a stubbed relay can answer with, and the record calls the desktop makes for its own
 * catalog. A reply is never surfaced raw -- each is one [RelayFailure] the caller acts on.
 */
class RelayClientRepliesTest {

    private fun answering(status: Int, body: String = "{}"): RelayClient =
        RelayClient("https://relay.example", "inst", "install-1", { _, _, _, _ -> RelayReply(status, body) })

    @Test
    fun `a 403 is a ban only when the relay says so`() {
        assertFailsWith<RelayFailure.Banned> { answering(403, """{"error":"banned"}""").changes("t", 0) }
        assertFailsWith<RelayFailure.Unauthorized> { answering(403, """{"error":"forbidden"}""").changes("t", 0) }
    }

    @Test
    fun `a failed precondition is a conflict, registering or not`() {
        assertFailsWith<RelayFailure.Conflict> { answering(412).changes("t", 0) }
        assertFailsWith<RelayFailure.Conflict> { answering(412).register() }
    }

    @Test
    fun `an interrupted call is unreachable, and the interrupt is kept`() {
        val interrupted = RelayClient(
            "https://relay.example", "inst", "i", { _, _, _, _ -> throw InterruptedException() },
        )
        try {
            assertFailsWith<RelayFailure.Unreachable> { interrupted.register() }
            assertTrue(Thread.currentThread().isInterrupted, "the thread is told it was interrupted")
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `the desktop's own records are written and deleted, and their ids checked first`() {
        val sent = mutableListOf<String>()
        val client = RelayClient("https://relay.example", "inst", "i", { m, u, _, _ ->
            sent += "$m $u"
            RelayReply(200, """{"rev":7}""")
        })
        val record = SealedRecord(id = "catalog-1", keepUntil = "2026-12-31", box = "sealed")

        assertEquals(7, client.putRecord("t", record, ifRev = 0))
        assertEquals(7, client.deleteRecord("t", "catalog-1"))
        assertEquals(
            listOf(
                "PUT https://relay.example/i/inst/records/catalog-1",
                "DELETE https://relay.example/i/inst/records/catalog-1",
            ),
            sent,
            "under this instance, by the record's own id",
        )

        assertFailsWith<IllegalArgumentException> { client.putRecord("t", record.copy(id = "bad id"), 0) }
        assertFailsWith<IllegalArgumentException> { client.deleteRecord("t", "../x") }
        assertFailsWith<IllegalArgumentException> { client.revokeDevice("t", "") }
    }

    @Test
    fun `a client with no client key sends none`() {
        var headers: Map<String, String> = emptyMap()
        val client = RelayClient("https://relay.example", "inst", "i", { _, _, h, _ ->
            headers = h
            RelayReply(200, """{"desktopToken":"x"}""")
        })
        client.register()
        assertFalse("X-Client-Key" in headers)
        assertFalse("Authorization" in headers, "registering carries no token yet")
    }

    @Test
    fun `a client key endpoint that answers an error has no key`() {
        assertEquals(null, fetchClientKey("https://site", { _, _, _, _ -> RelayReply(503, """{"clientKey":"abc"}""") }))
    }

    @Test
    fun `the real transport refuses a relay that is not https before sending anything`() {
        assertFailsWith<IllegalArgumentException> {
            HttpRelayTransport().send("GET", "http://relay.example/x", emptyMap(), null)
        }
    }

    @Test
    fun `a 409 is a taken id only while registering`() {
        assertFailsWith<RelayFailure.Taken> { answering(409).register() }
        assertFailsWith<RelayFailure.Conflict> { answering(409).changes("t", 0) }
    }

    @Test
    fun `only a 2xx is a success, on either side of the range`() {
        assertFailsWith<RelayFailure.Rejected> { answering(199).changes("t", 0) }
        assertFailsWith<RelayFailure.Rejected> { answering(300).changes("t", 0) }
        fun keyAt(status: Int) =
            fetchClientKey("https://site", { _, _, _, _ -> RelayReply(status, """{"clientKey":"abc"}""") })
        assertEquals(null, keyAt(199))
        assertEquals("abc", keyAt(299))
    }
}
