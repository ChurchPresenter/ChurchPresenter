package org.churchpresenter.calendar.sync

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RelayComputerCallsTest {

    private class Sent(val method: String, val url: String, val headers: Map<String, String>, val body: String?)

    private val sent = mutableListOf<Sent>()
    private val client = RelayClient("https://relay.example/", "inst", "install-1", { m, u, h, b ->
        sent += Sent(m, u, h, b)
        RelayReply(200, """{"ok":true}""")
    })
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `adding a computer puts its token hash, role and sealed name under its id`() {
        client.addComputer("desk-token", "desk-2", AddComputerRequest("ab".repeat(32), RemoteRole.FOLLOWER, "box"))

        val call = sent.single()
        assertEquals("PUT", call.method)
        assertEquals("https://relay.example/i/inst/desktops/desk-2", call.url)
        assertEquals("Bearer desk-token", call.headers["Authorization"])
        assertEquals(
            AddComputerRequest("ab".repeat(32), RemoteRole.FOLLOWER, "box"),
            json.decodeFromString(AddComputerRequest.serializer(), call.body!!),
        )
    }

    @Test
    fun `removing a computer deletes it by id with no body`() {
        client.removeComputer("desk-token", "desk-2")

        val call = sent.single()
        assertEquals("DELETE", call.method)
        assertEquals("https://relay.example/i/inst/desktops/desk-2", call.url)
        assertEquals(null, call.body)
    }

    @Test
    fun `promoting a computer posts whether the former main stays on`() {
        val request = PromoteRequest(keepFormer = true, formerId = "desk-1", formerNameBox = "old")

        client.promote("desk-token", "desk-2", request)

        val call = sent.single()
        assertEquals("POST", call.method)
        assertEquals("https://relay.example/i/inst/desktops/desk-2/promote", call.url)
        assertEquals(request, json.decodeFromString(PromoteRequest.serializer(), call.body!!))
    }

    @Test
    fun `a malformed computer id is refused before anything is sent`() {
        assertFailsWith<IllegalArgumentException> {
            client.addComputer("t", "bad id", AddComputerRequest("x", RemoteRole.VIEWER))
        }
        assertFailsWith<IllegalArgumentException> { client.removeComputer("t", "../state") }
        assertFailsWith<IllegalArgumentException> { client.promote("t", "", PromoteRequest(keepFormer = false)) }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a refused computer call is reported as the relay's failure`() {
        val refusing = RelayClient("https://relay.example", "inst", "i", { _, _, _, _ -> RelayReply(401, "{}") })

        assertFailsWith<RelayFailure.Unauthorized> { refusing.removeComputer("t", "desk-2") }
    }
}
