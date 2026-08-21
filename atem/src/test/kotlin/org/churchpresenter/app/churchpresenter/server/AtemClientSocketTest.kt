package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
 * **Elsewhere, deliberately:** every path that ends on an expiring deadline — a silent host, an
 * unacknowledged command, the keepalive dropping a dead session — is in `AtemClientTimeoutTest`,
 * which drives `AtemClient`'s four timeout constructor parameters in milliseconds. This suite only
 * covers what a switcher that *answers* does.
 *
 * **Not covered anywhere:** the in-flight eviction at MAX_IN_FLIGHT (2048 packets), which no upload
 * of a testable size reaches. See `AGENT.md` for the rest.
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

    @Test
    fun `isReachable uses its default timeout when none is given`() {
        // The shape every caller in the app actually uses -- LowerThirdTab's reachability poll and
        // AtemSettingsTab's probe both omit timeoutMs. A switcher that answers returns before the
        // default deadline matters, so this costs a round trip rather than the 2s default.
        FakeAtemSwitcher().use { fake ->
            assertTrue(runBlocking { AtemClient.isReachable("127.0.0.1", fake.port) })
        }
    }

    @Test
    fun `host and port are the ones the client was constructed with`() {
        FakeAtemSwitcher().use { fake ->
            val client = AtemClient("127.0.0.1", fake.port)
            assertEquals("127.0.0.1", client.host)
            assertEquals(fake.port, client.port)
        }
    }

    @Test
    fun `the default port is the ATEM control port`() {
        // 9910 is fixed by the protocol; a client built without a port must not invent another.
        assertEquals(9910, AtemClient("192.0.2.1").port)
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

    @Test
    fun `a transfer the switcher refuses outright fails without retrying`() {
        // FTDE code 1 means "busy, try again" and is retried; any other code is a refusal. Retrying
        // one of those would hammer the switcher for the full 40-retry budget and still fail.
        FakeAtemSwitcher(ftdeFatalCode = 5).use { fake ->
            val client = connected(fake)
            try {
                val failure = assertFailsWith<AtemProtocolException> {
                    runBlocking { client.uploadStillEncoded(slot = 0, frame = frame(600), name = "refused") }
                }
                assertTrue(failure.message!!.contains("5"), "the code the switcher gave: ${failure.message}")
            } finally {
                client.disconnect()
            }
            assertEquals(1, fake.commandsNamed("FTSD").size, "the transfer was not attempted a second time")
        }
    }

    @Test
    fun `a clip frame the switcher refuses outright fails without retrying`() {
        FakeAtemSwitcher(ftdeFatalCode = 3).use { fake ->
            val client = connected(fake)
            try {
                assertFailsWith<AtemProtocolException> {
                    runBlocking {
                        client.uploadClipEncoded(0, frameCount = 2, name = "refused", nextFrame = { frame(400) })
                    }
                }
            } finally {
                client.disconnect()
            }
            assertEquals(1, fake.commandsNamed("FTSD").size, "it stopped at the first refused frame")
        }
    }

    // ── Keyers ────────────────────────────────────────────────────────────────

    @Test
    fun `setKeyOnAir sends an upstream keyer command carrying the M-E and keyer`() {
        FakeAtemSwitcher().use { fake ->
            val client = connected(fake, collectState = false)
            try {
                runBlocking {
                    client.setKeyOnAir(AtemKey(useDsk = false, mixEffect = 1, keyer = 2), onAir = true)
                }
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
                runBlocking {
                    client.setKeyOnAir(AtemKey(useDsk = true, mixEffect = 0, keyer = 1), onAir = false)
                }
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
                AtemClient.cutKey(
                    "127.0.0.1", fake.port, AtemKey(useDsk = false, mixEffect = 0, keyer = 0), onAir = true,
                )
            }
            assertTrue(fake.commandsNamed("CKOn").isNotEmpty(), "the keyer command reached the switcher")
        }
    }

    @Test
    fun `cutUpstreamKeyer connects, cuts the upstream keyer and disconnects on its own`() {
        // The upstream-only sibling of cutKey, still called where the DSK case cannot arise.
        FakeAtemSwitcher().use { fake ->
            runBlocking {
                AtemClient.cutUpstreamKeyer("127.0.0.1", fake.port, mixEffect = 1, keyer = 2, onAir = true)
            }
            val cmd = fake.commandsNamed("CKOn").single()
            assertEquals(1, cmd[0].toInt(), "the M-E index travels in byte 0")
            assertEquals(2, cmd[1].toInt(), "the keyer index in byte 1")
            assertEquals(1, cmd[2].toInt(), "and the on-air flag in byte 2")
        }
    }

    @Test
    fun `taking an upstream keyer off air sends a zero on-air byte`() {
        // The other half of CKOn's on-air flag. A stuck 1 here would leave a lower third on screen
        // after the operator cut it away, which is the failure this byte exists to prevent.
        FakeAtemSwitcher().use { fake ->
            val client = connected(fake)
            try {
                runBlocking { client.setKeyOnAir(AtemKey(useDsk = false, mixEffect = 0, keyer = 1), onAir = false) }
            } finally {
                client.disconnect()
            }
            val cmd = fake.commandsNamed("CKOn").single()
            assertEquals(0, cmd[2].toInt(), "off air is a zero flag")
        }
    }

    @Test
    fun `putting a downstream keyer on air sends a one on-air byte`() {
        FakeAtemSwitcher().use { fake ->
            val client = connected(fake)
            try {
                runBlocking { client.setKeyOnAir(AtemKey(useDsk = true, mixEffect = 0, keyer = 1), onAir = true) }
            } finally {
                client.disconnect()
            }
            val cmd = fake.commandsNamed("CDsL").single()
            assertEquals(1, cmd[0].toInt(), "the DSK index travels in byte 0")
            assertEquals(1, cmd[1].toInt(), "on air is a one flag")
        }
    }

    // ── Uploading without a state dump ────────────────────────────────────────

    @Test
    fun `a switcher that reports no still slots is not treated as rejecting the upload`() {
        // A device whose firmware sends no MPfe at all reads as an empty pool, which is not the same
        // as "slot 0 does not exist". Refusing here would block every upload to such a switcher;
        // the device itself rejects a slot it really lacks.
        FakeAtemSwitcher(stillSlotCount = 0).use { fake ->
            val payload = frame(1_200)
            fake.expectedTransferBytes = payload.data.size
            val client = connected(fake)
            try {
                assertTrue(client.lastKnownState!!.stillSlots.isEmpty(), "the precondition: an empty pool")
                runBlocking { client.uploadStillEncoded(slot = 0, frame = payload, name = "empty-pool") }
            } finally {
                client.disconnect()
            }
            assertEquals(2, fake.awaitCommandsNamed("LOCK", 2).size, "the transfer ran to its unlock")
        }
    }

    @Test
    fun `a switcher that reports no clip banks is not treated as rejecting the upload`() {
        FakeAtemSwitcher(clipSlotCount = 0).use { fake ->
            val payload = frame(900)
            fake.expectedTransferBytes = payload.data.size
            val client = connected(fake)
            try {
                assertTrue(client.lastKnownState!!.clipSlots.isEmpty(), "the precondition: no clip banks")
                runBlocking {
                    client.uploadClipEncoded(slot = 0, frameCount = 1, name = "empty-pool", nextFrame = { payload })
                }
            } finally {
                client.disconnect()
            }
            assertTrue(fake.commandsNamed("FTSD").isNotEmpty(), "the transfer was not refused up front")
        }
    }

    @Test
    fun `a still uploads on a connection that never read the media pool`() {
        // AtemConnectionManager opens stateless connections (needsState = false), so the slot
        // validation has no pool to check against. It must skip the check rather than refuse the
        // upload — the switcher itself rejects a bad slot, and refusing here would break every
        // upload over a reused connection.
        FakeAtemSwitcher().use { fake ->
            val payload = frame(2_000)
            fake.expectedTransferBytes = payload.data.size
            val client = connected(fake, collectState = false)
            try {
                assertNull(client.lastKnownState, "the precondition: no pool was ever read")
                runBlocking { client.uploadStillEncoded(slot = 5, frame = payload, name = "stateless") }
            } finally {
                client.disconnect()
            }
            assertEquals(2, fake.awaitCommandsNamed("LOCK", 2).size, "the transfer ran to its unlock")
        }
    }

    @Test
    fun `a clip uploads on a connection that never read the media pool`() {
        FakeAtemSwitcher().use { fake ->
            val payload = frame(1_600)
            fake.expectedTransferBytes = payload.data.size
            val client = connected(fake, collectState = false)
            try {
                runBlocking {
                    client.uploadClipEncoded(
                        slot = 1, frameCount = 1, name = "stateless", nextFrame = { payload },
                    )
                }
            } finally {
                client.disconnect()
            }
            assertTrue(fake.commandsNamed("FTSD").isNotEmpty(), "the transfer was not refused up front")
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
                runBlocking {
                    client.setKeyOnAir(AtemKey(useDsk = false, mixEffect = 0, keyer = 0), onAir = true)
                }
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
                runBlocking {
                    client.setKeyOnAir(AtemKey(useDsk = false, mixEffect = 0, keyer = 0), onAir = true)
                }
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
                runBlocking { client.setKeyOnAir(AtemKey(false, 0, 0), true) }
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
