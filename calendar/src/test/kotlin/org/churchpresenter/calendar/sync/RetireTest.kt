package org.churchpresenter.calendar.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RetireTest {

    private val relay = FakeRelay().also { it.desktopToken = "desk-token" }
    private val client = RelayClient("https://relay.example", "inst", "install-A", relay)

    private fun holding() {
        relay.rev = 3
        relay.records["s1"] = SealedRecord("s1", "2026-12-01", "Ym94", rev = 1)
        val book = "catalog:Hymnal-cf49adf4"
        relay.records[book] = SealedRecord(book, "2028-12-01", "Ym94", rev = 2)
        relay.devices["phone-1"] = RemoteDevice("phone-1")
        relay.devices["phone-2"] = RemoteDevice("phone-2")
    }

    @Test
    fun `an instance is emptied of services, songbooks and phones`() {
        holding()

        client.retire("desk-token")

        assertTrue(relay.records.isEmpty(), "nothing left for the old key to open")
        assertTrue(relay.devices.isEmpty(), "no phone's token accepted any more")
    }

    @Test
    fun `a phone writing in between is read past and the instance still emptied`() {
        holding()
        val racing = RelayTransport { method, url, headers, body ->
            if (method == "PUT" && url.endsWith("/state") && relay.calls.none { it.startsWith("PUT /state") }) {
                relay.calls += "PUT /state"
                relay.rev += 1
                RelayReply(412, """{"error":"precondition_failed"}""")
            } else {
                relay.send(method, url, headers, body)
            }
        }

        RelayClient("https://relay.example", "inst", "install-A", racing).retire("desk-token")

        assertTrue(relay.records.isEmpty())
        assertTrue(relay.devices.isEmpty())
    }

    @Test
    fun `a relay that keeps moving is given up on rather than half emptied`() {
        holding()
        val moving = RelayTransport { method, url, headers, body ->
            if (method == "PUT" && url.endsWith("/state")) {
                RelayReply(412, """{"error":"precondition_failed"}""")
            } else {
                relay.send(method, url, headers, body)
            }
        }

        assertFailsWith<RelayFailure.Conflict> {
            RelayClient("https://relay.example", "inst", "install-A", moving).retire("desk-token")
        }
        assertEquals(setOf("phone-1", "phone-2"), relay.devices.keys, "no phone revoked on a half-done job")
    }
}
