package org.churchpresenter.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Sorting what arrives on the Browser Source's CDP socket.
 *
 * Chrome sends command responses and unsolicited events down one socket, and this is the only thing
 * that tells them apart. Both mistakes are silent and expensive: a response read as an event strands
 * whatever `sendAsync` was waiting on until its 30-second timeout, and an event read as a response
 * completes some unrelated command with the wrong payload.
 *
 * The other decision here is which navigations count. A page carrying an ad iframe emits
 * `Page.frameNavigated` for the iframe as well as for itself, and the properties panel shows this URL
 * to the operator — so reporting a sub-frame would display an advert's address as the source's
 * location. Only the frame with no `parentId` is the page.
 */
class CdpMessageTest {

    private fun response(id: Int, body: String) = """{"id":$id,$body}"""

    private fun navigation(url: String, parentId: String? = null): String {
        val parent = if (parentId == null) "" else ""","parentId":"$parentId""""
        return """{"method":"Page.frameNavigated","params":{"frame":{"id":"F1","url":"$url"$parent}}}"""
    }

    // ── Responses ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a frame carrying an id is the answer to the command sent with that id`() {
        val message = parseCdpMessage(response(7, """"result":{"data":"abc"}"""))

        val parsed = assertIs<CdpMessage.Response>(message)
        assertEquals(7, parsed.id)
        assertEquals("abc", parsed.result?.get("data")?.toString()?.trim('"'))
        assertNull(parsed.error)
    }

    @Test
    fun `an error response is still routed to its waiting command`() {
        // The waiter has to be released even when the command failed, or it hangs for 30 seconds.
        val message = parseCdpMessage(response(9, """"error":{"code":-32000,"message":"nope"}"""))

        val parsed = assertIs<CdpMessage.Response>(message)
        assertEquals(9, parsed.id)
        assertNull(parsed.result, "a failed command has no result to hand back")
    }

    @Test
    fun `a response with neither result nor error still releases its waiter`() {
        val parsed = assertIs<CdpMessage.Response>(parseCdpMessage("""{"id":3}"""))

        assertEquals(3, parsed.id)
        assertNull(parsed.result)
        assertNull(parsed.error)
    }

    @Test
    fun `an id that is not a number is not treated as a response`() {
        assertIs<CdpMessage.Ignored>(parseCdpMessage("""{"id":"seven","result":{}}"""))
    }

    // ── Navigation events ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the page navigating itself is reported with its new url`() {
        val message = parseCdpMessage(navigation("https://example.org/live"))

        assertEquals("https://example.org/live", assertIs<CdpMessage.MainFrameNavigated>(message).url)
    }

    @Test
    fun `an iframe navigating is not the page navigating`() {
        // The operator would otherwise see an advert's address as the source's URL.
        assertIs<CdpMessage.Ignored>(parseCdpMessage(navigation("https://ads.example/banner", parentId = "F1")))
    }

    @Test
    fun `a navigation frame with no url is ignored rather than reported as blank`() {
        val noUrl = """{"method":"Page.frameNavigated","params":{"frame":{"id":"F1"}}}"""

        assertIs<CdpMessage.Ignored>(parseCdpMessage(noUrl))
    }

    @Test
    fun `a navigation event carrying no frame at all is ignored`() {
        assertIs<CdpMessage.Ignored>(parseCdpMessage("""{"method":"Page.frameNavigated","params":{}}"""))
        assertIs<CdpMessage.Ignored>(parseCdpMessage("""{"method":"Page.frameNavigated"}"""))
    }

    @Test
    fun `other CDP events are ignored`() {
        // The session subscribes to a page domain that emits plenty besides navigation.
        listOf(
            """{"method":"Page.loadEventFired","params":{"timestamp":1.0}}""",
            """{"method":"Runtime.consoleAPICalled","params":{"type":"log"}}""",
            """{"method":"Page.lifecycleEvent"}""",
        ).forEach { assertIs<CdpMessage.Ignored>(parseCdpMessage(it), "$it must be ignored") }
    }

    @Test
    fun `a frame with neither an id nor a method is ignored`() {
        assertIs<CdpMessage.Ignored>(parseCdpMessage("""{"params":{"frame":{"url":"https://x"}}}"""))
    }

    // ── Malformed input ───────────────────────────────────────────────────────────────────────

    @Test
    fun `text that is not JSON is ignored rather than thrown from`() {
        // This runs on the WebSocket's own callback thread: an exception here takes the connection
        // down and freezes the source on its last frame.
        listOf("", "not json at all", "{", """{"id":}""", "[1,2,3]", "null").forEach {
            assertIs<CdpMessage.Ignored>(parseCdpMessage(it), "\"$it\" must be ignored, not thrown on")
        }
    }

    @Test
    fun `a JSON value that is not an object is ignored`() {
        assertIs<CdpMessage.Ignored>(parseCdpMessage("\"just a string\""))
        assertIs<CdpMessage.Ignored>(parseCdpMessage("42"))
    }
}
