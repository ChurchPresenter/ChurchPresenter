package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [AtemClient]'s socket layer — the handshake, the reliable-delivery bookkeeping and the media
 * pool transfer flow — driven against [FakeAtemSwitcher] over loopback UDP.
 *
 * `AtemClientProtocolTest` covers the pure byte builders and `AtemStateParsingTest` the parsers;
 * both explicitly leave "the socket I/O around these" untested, which was ~250 lines of the file.
 * This suite is that missing half. See [FakeAtemSwitcher] for why the fake's byte layouts come
 * from a capture of real hardware rather than from [AtemClient] itself.
 *
 * **Not covered here, deliberately:**
 * - `isReachable`'s failure path and `connect`'s no-response path against a *silent* host. Both
 *   end only when a socket timeout expires (2s and 5s respectively), and neither timeout is
 *   injectable, so a test of them would cost its whole timeout — the shape `AGENT.md` rules out.
 *   The success paths are covered below.
 * - The keepalive loop. Its cadence is a hard-coded 1.5s `delay`, so any assertion about it is an
 *   assertion about a duration.
 * - `retransmitFrom`'s throw when the requested packet has already been evicted: it needs
 *   MAX_IN_FLIGHT (2048) packets sent to force eviction, which no upload this size reaches.
 */
class AtemClientSocketTest {

    private fun connected(fake: FakeAtemSwitcher, collectState: Boolean = true): AtemClient =
        AtemClient("127.0.0.1", fake.port).also { runBlocking { it.connect(collectState = collectState) } }

    // ── Handshake and state ───────────────────────────────────────────────────

    @Test
    fun `connect completes the hello handshake and reads the state dump`() {
        FakeAtemSwitcher().use { fake ->
            val client = connected(fake)
            try {
                assertTrue(client.isAlive(), "the socket stays open after a successful connect")
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `queryState reports the switcher's video mode, topology and media pool`() {
        FakeAtemSwitcher(mixEffects = 4, downstreamKeyers = 2, keyersPerMe = 4, stillSlotCount = 64, clipSlotCount = 2)
            .use { fake ->
                val state = runBlocking { AtemClient("127.0.0.1", fake.port).queryState() }
                assertEquals("1080p59.94", state.videoMode)
                assertEquals(60000.0 / 1001.0, state.fps, 0.0001)
                assertEquals(4, state.mixEffectCount)
                assertEquals(2, state.downstreamKeyers)
                assertEquals(listOf(4, 4, 4, 4), state.keyersPerMe)
                assertEquals(64, state.stillSlots.size)
                assertEquals(2, state.clipSlots.size)
                assertTrue(state.stillSlots.none { it.isUsed }, "an empty pool reports every slot free")
                assertEquals(listOf(0, 1), state.clipSlots.map { it.index })
            }
    }

    @Test
    fun `a one M-E switcher reports its own smaller topology`() {
        // The second captured device: 1 M/E, 32 stills. Guards against the fake — and the
        // parser — being fitted to the 4 M/E model alone.
        FakeAtemSwitcher(mixEffects = 1, downstreamKeyers = 2, keyersPerMe = 4, stillSlotCount = 32)
            .use { fake ->
                val state = runBlocking { AtemClient("127.0.0.1", fake.port).queryState() }
                assertEquals(1, state.mixEffectCount)
                assertEquals(listOf(4), state.keyersPerMe)
                assertEquals(32, state.stillSlots.size)
            }
    }

    @Test
    fun `queryState closes the socket it opened`() {
        FakeAtemSwitcher().use { fake ->
            val client = AtemClient("127.0.0.1", fake.port)
            runBlocking { client.queryState() }
            assertFalse(client.isAlive(), "queryState is a one-shot — it disconnects in its finally")
        }
    }

    @Test
    fun `isReachable is true for a switcher that answers the hello`() {
        FakeAtemSwitcher().use { fake ->
            assertTrue(runBlocking { AtemClient.isReachable("127.0.0.1", fake.port, timeoutMs = 1_000) })
        }
    }

    // ── Still upload ──────────────────────────────────────────────────────────

    private fun frame(bytes: Int) = EncodedFrame(ByteArray(bytes) { (it and 0x7F).toByte() }, rawLen = bytes * 4)

    @Test
    fun `uploading a still locks the store, transfers every byte and unlocks`() {
        FakeAtemSwitcher().use { fake ->
            val payload = frame(4_000)
            fake.expectedTransferBytes = payload.data.size
            val client = connected(fake)
            try {
                runBlocking { client.uploadStillEncoded(slot = 3, frame = payload, name = "test") }
            } finally {
                client.disconnect()
            }

            // The closing unlock is sent without waiting for its ack, so the upload returning
            // does not mean it has arrived — wait for the datagram itself.
            val locks = fake.awaitCommandsNamed("LOCK", 2)
            assertEquals(2, locks.size, "the store is locked before the transfer and unlocked after")
            assertEquals(1, locks.first()[2].toInt(), "first LOCK acquires")
            assertEquals(0, locks.last()[2].toInt(), "last LOCK releases")

            assertEquals(1, fake.commandsNamed("FTSD").size)
            assertEquals(1, fake.commandsNamed("FTFD").size, "the file description is sent once")

            val sent = fake.commandsNamed("FTDa").sumOf {
                ((it[2].toInt() and 0xFF) shl 8) or (it[3].toInt() and 0xFF)
            }
            assertEquals(payload.data.size, sent, "every encoded byte reaches the switcher")
        }
    }

    @Test
    fun `a transfer larger than one grant is split across several FTCD grants`() {
        // The captured 307 KB frame took 221 chunks across two grants; this reproduces that
        // shape in miniature — 10 chunks per grant against a payload needing more than 10.
        FakeAtemSwitcher(grantChunkSize = 400, chunksPerGrant = 10).use { fake ->
            val payload = frame(9_000)   // 23 chunks of 400 ⇒ 3 grants
            fake.expectedTransferBytes = payload.data.size
            val client = connected(fake)
            try {
                runBlocking { client.uploadStillEncoded(slot = 0, frame = payload, name = "big") }
            } finally {
                client.disconnect()
            }

            val chunks = fake.commandsNamed("FTDa")
            assertTrue(chunks.size > 10, "more chunks than a single grant allows, got ${chunks.size}")
            assertEquals(1, fake.commandsNamed("FTFD").size, "the description is not re-sent per grant")
            val sent = chunks.sumOf { ((it[2].toInt() and 0xFF) shl 8) or (it[3].toInt() and 0xFF) }
            assertEquals(payload.data.size, sent)
        }
    }

    @Test
    fun `upload progress runs from nothing to complete`() {
        FakeAtemSwitcher(grantChunkSize = 400, chunksPerGrant = 10).use { fake ->
            val payload = frame(9_000)
            fake.expectedTransferBytes = payload.data.size
            val client = connected(fake)
            val progress = mutableListOf<Float>()
            try {
                runBlocking { client.uploadStillEncoded(0, payload, "p") { progress.add(it) } }
            } finally {
                client.disconnect()
            }
            assertTrue(progress.isNotEmpty(), "progress is reported")
            assertEquals(1f, progress.last(), 0.0001f, "the last report is completion")
            assertEquals(progress, progress.sorted(), "progress never goes backwards")
        }
    }

    @Test
    fun `a busy switcher's FTDE retry restarts the transfer and it still completes`() {
        FakeAtemSwitcher(ftdeRetriesBeforeSuccess = 1).use { fake ->
            val payload = frame(2_000)
            fake.expectedTransferBytes = payload.data.size
            val client = connected(fake)
            try {
                runBlocking { client.uploadStillEncoded(slot = 1, frame = payload, name = "retry") }
            } finally {
                client.disconnect()
            }
            assertEquals(2, fake.commandsNamed("FTSD").size, "the transfer is restarted after FTDE code 1")
            val sent = fake.commandsNamed("FTDa").sumOf {
                ((it[2].toInt() and 0xFF) shl 8) or (it[3].toInt() and 0xFF)
            }
            assertEquals(payload.data.size, sent, "the restarted transfer sends the whole frame")
        }
    }

    @Test
    fun `an empty frame is refused before anything is sent`() {
        FakeAtemSwitcher().use { fake ->
            val client = connected(fake)
            try {
                assertFailsWith<Exception> {
                    runBlocking { client.uploadStillEncoded(0, EncodedFrame(ByteArray(0), 0), "empty") }
                }
                assertTrue(fake.commandsNamed("LOCK").isEmpty(), "nothing is locked for a refused upload")
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `a slot the switcher does not have is refused with its real slot count`() {
        FakeAtemSwitcher(stillSlotCount = 32).use { fake ->
            val client = connected(fake)
            try {
                val error = assertFailsWith<Exception> {
                    runBlocking { client.uploadStillEncoded(99, frame(100), "oob") }
                }
                assertTrue(error.message!!.contains("1–32"), "reports the real range, was: ${error.message}")
            } finally {
                client.disconnect()
            }
        }
    }

    // ── Clip upload ───────────────────────────────────────────────────────────

    @Test
    fun `uploading a clip clears the bank, sends every frame and commits it`() {
        FakeAtemSwitcher().use { fake ->
            val payload = frame(1_500)
            fake.expectedTransferBytes = payload.data.size
            val client = connected(fake)
            try {
                runBlocking {
                    client.uploadClipEncoded(slot = 0, frameCount = 3, name = "anim", nextFrame = { payload })
                }
            } finally {
                client.disconnect()
            }
            assertEquals(1, fake.commandsNamed("CMPC").size, "the bank is cleared once, before the frames")
            assertEquals(3, fake.commandsNamed("FTSD").size, "one transfer per frame")
            assertEquals(1, fake.commandsNamed("SMPC").size, "the clip is committed once")
            val commit = fake.commandsNamed("SMPC").single()
            val frameCount = ((commit[commit.size - 2].toInt() and 0xFF) shl 8) or
                (commit[commit.size - 1].toInt() and 0xFF)
            assertEquals(3, frameCount,
                "the commit carries the frame count")
        }
    }

    @Test
    fun `a clip slot the switcher does not have is refused with its real slot count`() {
        // Locking a store the ATEM does not have is silently ignored by the device -- LKOB never
        // comes and the upload would sit until its timeout -- so the slot is checked against the
        // state dump first and the operator is told the real range instead.
        FakeAtemSwitcher(clipSlotCount = 2).use { fake ->
            val client = connected(fake)
            try {
                val error = assertFailsWith<Exception> {
                    runBlocking {
                        client.uploadClipEncoded(slot = 7, frameCount = 2, name = "oob", nextFrame = { frame(10) })
                    }
                }
                assertTrue(error.message!!.contains("1–2"), "reports the real range, was: ${error.message}")
                assertTrue(error.message!!.contains("8"), "names the slot the way the ATEM numbers it")
                assertTrue(fake.commandsNamed("LOCK").isEmpty(), "nothing is locked for a refused upload")
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `a clip with no frames is refused`() {
        FakeAtemSwitcher().use { fake ->
            val client = connected(fake)
            try {
                assertFailsWith<Exception> {
                    runBlocking { client.uploadClipEncoded(0, frameCount = 0, name = "x", nextFrame = { frame(10) }) }
                }
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `awaitClipReady returns true once the switcher reports the bank fully ingested`() {
        FakeAtemSwitcher().use { fake ->
            val payload = frame(1_000)
            fake.expectedTransferBytes = payload.data.size
            fake.clipFramesOnCommit = 2
            val client = connected(fake)
            try {
                val ready = runBlocking {
                    client.uploadClipEncoded(0, frameCount = 2, name = "c", nextFrame = { payload })
                    // The MPCS the commit produced is buffered, so this returns on a real
                    // frame rather than by waiting the timeout out.
                    client.awaitClipReady(slot = 0, expectedFrames = 2, timeoutMs = 5_000)
                }
                assertTrue(ready, "a bank reporting isUsed with enough frames is ready")
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `awaitClipReady succeeds immediately when no frames are expected`() {
        FakeAtemSwitcher().use { fake ->
            val client = connected(fake)
            try {
                assertTrue(runBlocking { client.awaitClipReady(0, expectedFrames = 0, timeoutMs = 1_000) })
            } finally {
                client.disconnect()
            }
        }
    }

    // ── Keyers ────────────────────────────────────────────────────────────────

    @Test
    fun `setKeyOnAir sends an upstream keyer command carrying the M-E and keyer`() {
        FakeAtemSwitcher().use { fake ->
            val client = connected(fake, collectState = false)
            try {
                runBlocking { client.setKeyOnAir(useDsk = false, mixEffect = 1, keyer = 2, onAir = true) }
            } finally {
                client.disconnect()
            }
            val keyed = synchronized(fake.received) { fake.received.map { it.first } }
            assertTrue(keyed.any { it == "CKOn" }, "an upstream keyer command was sent, saw $keyed")
            val cmd = fake.commandsNamed("CKOn").single()
            assertEquals(1, cmd[0].toInt(), "byte 0 is the M/E index")
            assertEquals(2, cmd[1].toInt(), "byte 1 is the keyer index")
            assertEquals(1, cmd[2].toInt(), "byte 2 is on-air")
        }
    }

    @Test
    fun `setKeyOnAir with useDsk sends a downstream keyer command instead`() {
        FakeAtemSwitcher().use { fake ->
            val client = connected(fake, collectState = false)
            try {
                runBlocking { client.setKeyOnAir(useDsk = true, mixEffect = 0, keyer = 1, onAir = false) }
            } finally {
                client.disconnect()
            }
            val cmd = fake.commandsNamed("CDsL").singleOrNull() ?: fake.commandsNamed("DDsA").single()
            assertEquals(1, cmd[0].toInt(), "byte 0 is the DSK index")
        }
    }

    @Test
    fun `cutKey connects, sends the keyer command and disconnects on its own`() {
        FakeAtemSwitcher().use { fake ->
            runBlocking {
                AtemClient.cutKey("127.0.0.1", fake.port, useDsk = false, mixEffect = 0, keyer = 0, onAir = true)
            }
            assertTrue(fake.commandsNamed("CKOn").isNotEmpty(), "the keyer command reached the switcher")
        }
    }

    // ── Reliable delivery ─────────────────────────────────────────────────────

    @Test
    fun `an acknowledged command is dropped from the in-flight buffer`() {
        FakeAtemSwitcher().use { fake ->
            val client = connected(fake, collectState = false)
            try {
                // setKeyOnAir waits for its own ack (sendCommandAndWait with no expected
                // response), so its return IS the positive signal that the ack landed — no
                // polling, and nothing here depends on a timeout.
                runBlocking { client.setKeyOnAir(useDsk = false, mixEffect = 0, keyer = 0, onAir = true) }
                assertEquals(0, client.inFlightCount(), "an acked packet is no longer awaiting delivery")
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `the fire-and-forget unlock is the only packet left unacked after an upload`() {
        // uploadStillEncoded's closing LOCK is sent without waiting for its ack (deliberately —
        // a dead socket there must not mask the original failure), so exactly one packet is
        // still outstanding when it returns. Everything before it was awaited.
        FakeAtemSwitcher().use { fake ->
            val payload = frame(1_200)
            fake.expectedTransferBytes = payload.data.size
            val client = connected(fake)
            try {
                runBlocking { client.uploadStillEncoded(0, payload, "ack") }
                assertTrue(
                    client.inFlightCount() <= 1,
                    "only the unawaited unlock may remain, got ${client.inFlightCount()}"
                )
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `a command sent on a closed connection fails at once, naming the socket not the switcher`() {
        // Found while capturing from a real switcher: sendRaw was `socket?.send(...)`, so a
        // command sent after disconnect vanished and the caller waited out its whole 8s
        // timeout before reporting "ATEM did not respond" — pointing the blame at the device.
        // This must now fail immediately and say the connection is closed.
        FakeAtemSwitcher().use { fake ->
            val client = connected(fake, collectState = false)
            client.disconnect()

            val started = System.currentTimeMillis()
            val error = assertFailsWith<IllegalStateException> {
                runBlocking { client.setKeyOnAir(useDsk = false, mixEffect = 0, keyer = 0, onAir = true) }
            }
            val elapsed = System.currentTimeMillis() - started

            assertTrue(error.message!!.contains("closed"), "names the real cause, was: ${error.message}")
            assertTrue(
                elapsed < 1_000,
                "fails immediately rather than waiting out the 8s command timeout, took ${elapsed}ms"
            )
            assertTrue(fake.commandsNamed("CKOn").isEmpty(), "nothing reached the switcher")
        }
    }

    @Test
    fun `disconnect closes the socket and clears buffered state`() {
        FakeAtemSwitcher().use { fake ->
            val client = connected(fake)
            client.disconnect()
            assertFalse(client.isAlive())
            assertEquals(0, client.inFlightCount())
        }
    }

    @Test
    fun `the client adopts the session id the switcher assigns after the handshake`() {
        FakeAtemSwitcher().use { fake ->
            val client = connected(fake, collectState = false)
            try {
                runBlocking { client.setKeyOnAir(false, 0, 0, true) }
                // Every command must carry the switcher's real session id, not the client's
                // 0x53AB placeholder — a real ATEM silently ignores commands that don't.
                assertContentEquals(
                    byteArrayOf(0x12, 0x34),
                    fake.lastCommandSessionId(),
                    "commands go out with the assigned session id"
                )
            } finally {
                client.disconnect()
            }
        }
    }
}
