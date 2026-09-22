package org.churchpresenter.calendar.sync

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RelayClientTest {

    private val relay = FakeRelay(clientKey = "ck")
    private val client = RelayClient("https://relay.example/", "inst", "install-1", relay, clientKey = "ck")

    @Test
    fun `registers once and is refused the second time`() {
        assertEquals("desk-token", client.register())
        assertFailsWith<RelayFailure.Taken> { client.register() }
    }

    @Test
    fun `every call carries the install id, the client key and the token`() {
        val recorded = mutableListOf<Map<String, String>>()
        val spy = RelayTransport { m, u, h, b -> recorded += h; relay.send(m, u, h, b) }
        val spied = RelayClient("https://relay.example", "inst", "install-1", spy, clientKey = "ck")
        spied.register()

        spied.changes("desk-token", since = 0)

        val headers = recorded.last()
        assertEquals("install-1", headers["X-Install"])
        assertEquals("ck", headers["X-Client-Key"])
        assertEquals("Bearer desk-token", headers["Authorization"])
        assertTrue(relay.calls.last().endsWith("/changes?since=0"))
    }

    @Test
    fun `a wrong client key is told apart from a wrong token`() {
        client.register()
        val wrongKey = RelayClient("https://relay.example", "inst", "install-1", relay, clientKey = "nope")

        assertFailsWith<RelayFailure.ClientKey> { wrongKey.changes("desk-token", 0) }
        assertFailsWith<RelayFailure.Unauthorized> { client.changes("wrong-token", 0) }
    }

    @Test
    fun `a stale state push is a conflict`() {
        client.register()
        val state = StateRequest(emptyList(), emptyList(), "")

        assertEquals(1, client.putState("desk-token", state, ifRev = 0))
        assertFailsWith<RelayFailure.Conflict> { client.putState("desk-token", state, ifRev = 0) }
    }

    @Test
    fun `enrolling and revoking a phone need a well-formed device id`() {
        client.register()

        client.enrollDevice("desk-token", "phone-1", EnrollRequest(tokenHash = "ab".repeat(32)))
        assertEquals(setOf("phone-1"), relay.devices.keys)
        assertFailsWith<IllegalArgumentException> { client.enrollDevice("desk-token", "bad id", EnrollRequest("x")) }
        client.revokeDevice("desk-token", "phone-1")
        assertTrue(relay.devices.isEmpty())
    }

    @Test
    fun `a network failure and an unreadable reply are reported, not thrown raw`() {
        val down = RelayTransport { _, _, _, _ -> throw IOException("no route") }
        assertFailsWith<RelayFailure.Unreachable> { RelayClient("https://relay.example", "inst", "i", down).register() }

        relay.nextReply = RelayReply(200, "<html>")
        assertFailsWith<RelayFailure.Rejected> { client.register() }
        relay.nextReply = RelayReply(500, "boom")
        assertFailsWith<RelayFailure.Rejected> { client.register() }
    }

    @Test
    fun `the transport refuses a plain http relay before sending`() {
        assertFailsWith<IllegalArgumentException> {
            HttpRelayTransport().send("GET", "http://relay.example/x", emptyMap(), null)
        }
    }

    @Test
    fun `the client key endpoint is read and its shape checked`() {
        val ok = RelayTransport { _, _, _, _ -> RelayReply(200, """{"clientKey":"abc","relayUrl":"x"}""") }
        val empty = RelayTransport { _, _, _, _ -> RelayReply(200, """{"clientKey":""}""") }
        val html = RelayTransport { _, _, _, _ -> RelayReply(200, "<html>") }
        val down = RelayTransport { _, _, _, _ -> throw IOException() }

        assertEquals("abc", fetchClientKey("https://site", ok))
        assertEquals(null, fetchClientKey("https://site", empty))
        assertEquals(null, fetchClientKey("https://site", html))
        assertEquals(null, fetchClientKey("https://site", down))
    }
}
