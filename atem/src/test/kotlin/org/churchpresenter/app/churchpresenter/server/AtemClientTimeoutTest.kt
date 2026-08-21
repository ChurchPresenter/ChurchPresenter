package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The paths that end when a deadline expires: a switcher that never answers the hello, a command
 * that is never responded to, and the keepalive tearing down a session that has gone quiet.
 *
 * These used to be listed as untestable, and at the shipped values they are — 5s to fail a connect,
 * 8s to fail a command, 1.5s per keepalive tick. `AtemClient` takes all four as **defaulted
 * constructor parameters**, so each test below drives them in tens of milliseconds and asserts on a
 * real deadline rather than on a stubbed one. Production passes none of them.
 *
 * Every test still ends on a positive signal — a thrown `AtemProtocolException`, a `false` return,
 * `isAlive()` flipping — never on the test's own timeout, which exists only to fail.
 */
class AtemClientTimeoutTest {

    private companion object {
        const val LOOPBACK = "127.0.0.1"

        /** Short enough that a test costs milliseconds, long enough to outlast a loopback round trip. */
        const val SHORT_MS = 60L
    }

    /** Bounded poll on observable state; throws rather than returning on expiry. */
    private fun awaitTrue(what: String, timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(2)
        }
        fail("$what did not happen within ${timeoutMs}ms")
    }

    // ── A switcher that never answers ─────────────────────────────────────────

    @Test
    fun `connect gives up on a host that never answers the hello`() {
        // A switcher on the network but not listening, or the wrong IP entirely: the UDP hello just
        // goes nowhere, so there is no connection-refused to react to and only the deadline ends it.
        FakeAtemSwitcher(answerHello = false).use { fake ->
            val client = AtemClient(LOOPBACK, fake.port, connectTimeoutMs = SHORT_MS.toInt())
            val failure = assertFailsWith<AtemProtocolException> { runBlocking { client.connect() } }
            assertTrue(
                failure.message!!.contains("No response from ATEM"),
                "the message must name the device, not the socket: ${failure.message}",
            )
            assertFalse(client.isAlive(), "a failed connect leaves no half-open socket behind")
        }
    }

    @Test
    fun `isReachable is false for a host that never answers`() {
        // What the Lower Third tab polls on. It must answer false rather than throw, because the
        // poll runs on a loop while the operator has the tab open.
        FakeAtemSwitcher(answerHello = false).use { fake ->
            assertFalse(runBlocking { AtemClient.isReachable(LOOPBACK, fake.port, timeoutMs = SHORT_MS.toInt()) })
        }
    }

    @Test
    fun `isReachable is false for a port nothing is bound to`() {
        // The fake's port is released on close, so nothing is listening on it at all.
        val port = FakeAtemSwitcher().use { it.port }
        assertFalse(runBlocking { AtemClient.isReachable(LOOPBACK, port, timeoutMs = SHORT_MS.toInt()) })
    }

    // ── A command that is never answered ──────────────────────────────────────

    @Test
    fun `awaitClipReady reports not-ready when the switcher never sends MPCS`() {
        // The best-effort path: no clip was uploaded, so no MPCS is coming. The caller keys the clip
        // on anyway rather than hanging, which is why this returns false instead of throwing.
        FakeAtemSwitcher().use { fake ->
            val client = AtemClient(LOOPBACK, fake.port)
            try {
                runBlocking { client.connect(collectState = false) }
                val ready = runBlocking { client.awaitClipReady(slot = 0, expectedFrames = 5, timeoutMs = SHORT_MS) }
                assertFalse(ready, "no MPCS within the window means not ready")
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `awaitClipReady is immediately ready when no frames are expected`() {
        FakeAtemSwitcher().use { fake ->
            val client = AtemClient(LOOPBACK, fake.port)
            try {
                runBlocking { client.connect(collectState = false) }
                assertTrue(runBlocking { client.awaitClipReady(slot = 0, expectedFrames = 0, timeoutMs = SHORT_MS) })
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `a command the switcher never acknowledges fails naming the switcher`() {
        // A device that has gone deaf mid-session: the datagram leaves, nothing comes back, and only
        // the deadline ends the wait. The operator has to be told the ATEM stopped answering rather
        // than be left with a lower third that silently never cut.
        FakeAtemSwitcher(ackCommands = false).use { fake ->
            val client = AtemClient(LOOPBACK, fake.port, commandTimeoutMs = SHORT_MS)
            try {
                runBlocking { client.connect(collectState = false) }
                val failure = assertFailsWith<AtemProtocolException> {
                    runBlocking { client.setKeyOnAir(AtemKey(useDsk = false, mixEffect = 0, keyer = 0), onAir = true) }
                }
                assertTrue(
                    failure.message!!.contains("did not acknowledge"),
                    "the message must say what went unanswered: ${failure.message}",
                )
                assertEquals(1, fake.commandsNamed("CKOn").size, "the command did reach the switcher")
            } finally {
                client.disconnect()
            }
        }
    }

    // ── The keepalive loop ────────────────────────────────────────────────────

    @Test
    fun `the keepalive tears the socket down once the switcher goes silent`() {
        // The reason AtemConnectionManager can hand out a cached client at all: a session the ATEM
        // has expired must stop reporting itself alive, so the next use() reconnects instead of
        // sending commands into a dead session. The fake answers the handshake and then says
        // nothing, which is exactly what an expired session looks like.
        FakeAtemSwitcher().use { fake ->
            val client = AtemClient(
                LOOPBACK,
                fake.port,
                keepAliveIntervalMs = 10,
                silenceTimeoutMs = 40,
            )
            try {
                runBlocking { client.connect(collectState = false, keepAlive = true) }
                assertTrue(client.isAlive(), "the precondition: the handshake completed")
                awaitTrue("the keepalive tearing down the dead session") { !client.isAlive() }
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `a keepalive connection stays alive while the switcher is still answering`() {
        // The other side of the same branch. Each command's reply refreshes the liveness clock, so
        // a session in use is never torn down under the operator.
        FakeAtemSwitcher().use { fake ->
            val client = AtemClient(
                LOOPBACK,
                fake.port,
                keepAliveIntervalMs = 10,
                silenceTimeoutMs = 400,
            )
            try {
                runBlocking { client.connect(collectState = false, keepAlive = true) }
                repeat(4) {
                    runBlocking { client.setKeyOnAir(AtemKey(useDsk = false, mixEffect = 0, keyer = 0), onAir = true) }
                    assertTrue(client.isAlive(), "a session being used must not be torn down")
                }
                assertEquals(4, fake.commandsNamed("CKOn").size, "every command reached the switcher")
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `awaitClipReady ignores a report for a different clip bank`() {
        // MPCS is broadcast per bank, so a wait on bank 1 sees bank 0's report. Reacting to it would
        // key a clip on before it had finished ingesting.
        FakeAtemSwitcher().use { fake ->
            val payload = EncodedFrame(ByteArray(600) { it.toByte() }, rawLen = 2_400)
            fake.expectedTransferBytes = payload.data.size
            fake.clipFramesOnCommit = 2
            val client = AtemClient(LOOPBACK, fake.port)
            try {
                runBlocking {
                    client.connect()
                    client.uploadClipEncoded(0, frameCount = 2, name = "c", nextFrame = { payload })
                    assertFalse(
                        client.awaitClipReady(slot = 1, expectedFrames = 2, timeoutMs = SHORT_MS),
                        "bank 0's report says nothing about bank 1",
                    )
                }
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `awaitClipReady keeps waiting while the bank is still filling`() {
        // The partial-ingest path: the bank is in use but short of the frames this clip needs, so
        // the report drives progress rather than completion.
        FakeAtemSwitcher().use { fake ->
            val payload = EncodedFrame(ByteArray(600) { it.toByte() }, rawLen = 2_400)
            fake.expectedTransferBytes = payload.data.size
            fake.clipFramesOnCommit = 2
            val client = AtemClient(LOOPBACK, fake.port)
            val seen = mutableListOf<Float>()
            try {
                runBlocking {
                    client.connect()
                    client.uploadClipEncoded(0, frameCount = 2, name = "c", nextFrame = { payload })
                    assertFalse(
                        client.awaitClipReady(slot = 0, expectedFrames = 400, timeoutMs = SHORT_MS) { seen += it },
                        "2 of 400 frames ingested is not ready",
                    )
                }
                assertTrue(seen.isNotEmpty(), "the partial count is still reported as progress")
                assertTrue(seen.all { it in 0f..1f }, "progress stays in range: $seen")
            } finally {
                client.disconnect()
            }
        }
    }

    // ── Retransmit ────────────────────────────────────────────────────────────

    @Test
    fun `a retransmit request for a packet no longer buffered is a protocol failure`() {
        // The ATEM asking for a packet this client has already evicted is unrecoverable: it cannot
        // be resent, and pretending otherwise would leave the switcher waiting for ever.
        val client = AtemClient(LOOPBACK, 9910)
        val failure = assertFailsWith<AtemProtocolException> { client.retransmitFrom(fromId = 7) }
        assertTrue(failure.message!!.contains("no longer buffered"), failure.message!!)
    }

    @Test
    fun `a retransmit request resends the buffered packet`() {
        FakeAtemSwitcher().use { fake ->
            val payload = EncodedFrame(ByteArray(800) { it.toByte() }, rawLen = 3_200)
            fake.expectedTransferBytes = payload.data.size
            val client = AtemClient(LOOPBACK, fake.port)
            try {
                runBlocking { client.connect() }
                runBlocking { client.uploadStillEncoded(slot = 0, frame = payload, name = "r") }

                // The closing unlock is sent fire-and-forget, so it is still awaiting its ACK and is
                // the one packet the buffer can resend.
                val locksBefore = fake.awaitCommandsNamed("LOCK", 2).size
                val buffered = client.inFlightIds()
                assertEquals(1, buffered.size, "the unacked unlock is the only packet in flight")

                client.retransmitFrom(buffered.single())

                val locksAfter = fake.awaitCommandsNamed("LOCK", locksBefore + 1)
                assertEquals(locksBefore + 1, locksAfter.size, "the buffered packet reached the switcher again")
                assertEquals(0, locksAfter.last()[2].toInt(), "and it is the unlock, resent verbatim")
            } finally {
                client.disconnect()
            }
        }
    }
}
