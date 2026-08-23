package org.churchpresenter.app.churchpresenter.viewmodel

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The teardown guard that stops a replaced engine link from writing over the live one's state.
 *
 * [BibleEngineClient.stop] cancels the connect job but cannot wait for it — it is called from the UI
 * thread. So a restart genuinely does run two `connectLoop`s at once for a moment, and the outgoing
 * one clears `_connected`/`session`/`_engineSttConnected` on its way out. Landing that after the new
 * link came up left the app holding a live socket it believed was down, with nothing further on a
 * healthy socket to correct it — the race
 * [BibleEngineClientLinkTest] timed out on.
 *
 * The fix is a generation counter: every write goes through [BibleEngineClient.isCurrentLink] first.
 * **That decision is what this class pins**, and it is pinned here rather than through a socket
 * because the losing interleaving cannot be forced on a real one — it needs the outgoing teardown to
 * be slower than a loopback handshake, which is exactly what a test cannot arrange. The
 * end-to-end behaviour stays covered by `BibleEngineClientLinkTest`; what this adds is that the rule
 * itself cannot be quietly deleted.
 *
 * No server, no coroutines, no waiting.
 */
class BibleEngineClientRestartTest {

    private val created = mutableListOf<BibleEngineClient>()

    private fun client(): BibleEngineClient =
        BibleEngineClient(onScripture = {}, onVersion = {}).also { created.add(it) }

    @AfterTest
    fun cleanup() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
    }

    @Test
    fun `a link speaks for the client until it is stopped`() {
        val c = client()
        val link = c.currentLinkGeneration()

        assertTrue(c.isCurrentLink(link), "a link that has not been replaced owns the client's state")

        c.stop()

        assertFalse(
            c.isCurrentLink(link),
            "a stopped link must not clear the state of whichever link replaces it",
        )
    }

    @Test
    fun `each restart takes ownership from the one before it`() {
        val c = client()
        val links = List(3) {
            c.currentLinkGeneration().also { _ -> c.stop() }
        }

        assertEquals(links.size, links.distinct().size, "two links must never share an identity")
        links.forEach { assertFalse(c.isCurrentLink(it), "every superseded link is stale") }
        assertTrue(c.isCurrentLink(c.currentLinkGeneration()), "the newest link owns the state")
    }

    @Test
    fun `disposing invalidates the link too`() {
        val c = client()
        val link = c.currentLinkGeneration()

        c.dispose()

        assertFalse(c.isCurrentLink(link), "dispose goes through stop, so it invalidates the same way")
    }
}
