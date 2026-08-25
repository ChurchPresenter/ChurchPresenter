package org.churchpresenter.stt

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which connection flags each socket event sets.
 *
 * The socket wiring itself needs a live STT server, but the state each event produces is ordinary
 * logic and the UI reads all four flags together — the tab shows a green dot, "connecting…",
 * "can't reach server" or "reconnecting…" depending on the combination, so a transition that leaves
 * a stale flag behind shows the wrong message. `applyConnected`/`applyDisconnected`/
 * `applyConnectError` are exactly the bodies `connect()`'s callbacks run, called directly.
 *
 * The distinction that matters most: a disconnect we asked for is not a drop, and only a drop should
 * say "reconnecting…".
 */
class STTManagerConnectionStateTest {

    private val created = mutableListOf<STTManager>()

    private fun manager() = STTManager().also { created.add(it) }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
    }

    @Test
    fun `a fresh manager is disconnected and idle`() {
        val stt = manager()

        assertFalse(stt.connected.value)
        assertFalse(stt.connecting.value)
        assertFalse(stt.connectError.value)
        assertFalse(stt.reconnecting.value)
    }

    @Test
    fun `connecting clears every failure flag`() {
        val stt = manager()
        stt.applyConnectError()
        stt.applyDisconnected(reason = "transport close")

        stt.applyConnected()

        assertTrue(stt.connected.value)
        assertFalse(stt.connecting.value)
        assertFalse(stt.connectError.value, "a successful connect clears the earlier failure")
        assertFalse(stt.reconnecting.value, "and stops saying it is retrying")
    }

    @Test
    fun `a disconnect we asked for is not a reconnect attempt`() {
        val stt = manager()
        stt.applyConnected()

        stt.applyDisconnected(reason = "io client disconnect")

        assertFalse(stt.connected.value)
        assertFalse(stt.reconnecting.value, "we closed it, so nothing is retrying")
    }

    @Test
    fun `an unexpected drop is a reconnect attempt`() {
        val stt = manager()
        stt.applyConnected()

        stt.applyDisconnected(reason = "ping timeout")

        assertFalse(stt.connected.value)
        assertTrue(stt.reconnecting.value)
    }

    @Test
    fun `a drop with no reason given counts as unexpected`() {
        val stt = manager()
        stt.applyConnected()

        stt.applyDisconnected(reason = null)

        assertTrue(stt.reconnecting.value)
    }

    @Test
    fun `a failed attempt stops connecting and flags the failure`() {
        val stt = manager()

        stt.applyConnectError()

        assertFalse(stt.connected.value)
        assertFalse(stt.connecting.value, "the attempt is over, not still in flight")
        assertTrue(stt.connectError.value)
    }

    @Test
    fun `disconnect clears everything including a pending failure`() {
        val stt = manager()
        stt.applyConnected()
        stt.applyDisconnected(reason = "transport close")
        stt.applyConnectError()

        stt.disconnect()

        assertFalse(stt.connected.value)
        assertFalse(stt.connecting.value)
        assertFalse(stt.connectError.value)
        assertFalse(stt.reconnecting.value)
    }

    @Test
    fun `a snapshot is only worth pulling with help dev on and a server known`() {
        val stt = manager()

        assertTrue(stt.shouldCaptureFinalSnapshot(helpDev = true, baseUrl = "http://192.0.2.1:1"))
        assertFalse(stt.shouldCaptureFinalSnapshot(helpDev = false, baseUrl = "http://192.0.2.1:1"))
        assertFalse(stt.shouldCaptureFinalSnapshot(helpDev = true, baseUrl = null))
        assertFalse(stt.shouldCaptureFinalSnapshot(helpDev = true, baseUrl = ""))
    }
}
