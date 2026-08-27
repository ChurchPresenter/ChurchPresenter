package org.churchpresenter.atem

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * A loopback ATEM switcher: enough of the real UDP protocol for [AtemClient] to connect to it,
 * read a state dump from it, and upload stills and clips to it — with no hardware.
 *
 * **Every byte layout here was taken from a capture of two real switchers** (a 4 M/E with 64
 * still slots and a 1 M/E with 32, both 1080p59.94), not from reading [AtemClient]. That
 * distinction is the whole point: a fake derived from the client under test would encode the
 * same assumptions as the client, so any place the client misread the protocol would be baked
 * into the fake, the test would pass, and the misreading would be pinned in place. The captured
 * values agree with the parsers — real `_top` byte 0 was `0x04` for the 4 M/E and byte 2 `0x02`
 * for its two DSKs, real `VidM` was `0x0d` for 1080p59.94 — so the parsers are confirmed against
 * hardware, and the numbers below are what a device actually sent.
 *
 * The captures also fixed the transfer shape this fake reproduces:
 * `LOCK`→`LKOB`→`FTSD`→`FTCD`→`FTFD`→`FTDa`×N→`FTDC`→unlock, with the switcher granting 1396-byte
 * chunks in batches (one real 307 KB frame took 221 chunks across two grants).
 *
 * **Nothing here waits on a clock.** Every response is emitted in reaction to a packet that
 * arrived, and a transfer completes the moment [expectedTransferBytes] have been received, so
 * tests end on a positive signal rather than by outlasting a timeout.
 *
 * Public rather than `internal` because it is a test *fixture* of `:atem`: `:composeApp`'s ATEM
 * suites — the bridge, the upload routes, the lower third — drive the same fake through
 * `testFixtures(projects.atem)`, and `internal` does not cross a module boundary.
 */
class FakeAtemSwitcher(
    private val videoMode: Int = VIDEO_MODE_1080P5994,
    private val mixEffects: Int = 4,
    private val downstreamKeyers: Int = 2,
    private val keyersPerMe: Int = 4,
    private val stillSlotCount: Int = 64,
    private val clipSlotCount: Int = 2,
    /** Grant size in bytes. The real switchers both granted 1396. */
    private val grantChunkSize: Int = 1396,
    /** Chunks per FTCD grant. Lower than the transfer needs ⇒ several grants, as on hardware. */
    private val chunksPerGrant: Int = 320,
    /** Emit FTDE(code 1, "busy — retry") this many times before letting a transfer through. */
    private val ftdeRetriesBeforeSuccess: Int = 0,
    /**
     * When set, every transfer is refused with FTDE carrying this code instead of being granted.
     * Same four-byte FTDE the captures show and [ftdeRetriesBeforeSuccess] already emits — only the
     * code byte differs, and anything other than 1 is a refusal the client must not retry.
     */
    private val ftdeFatalCode: Int? = null,
    /** When false the hello is ignored, so a connect attempt fails as if nothing is listening. */
    private val answerHello: Boolean = true,
    /**
     * Run on the switcher's receive thread when a hello arrives, before the reply goes out.
     *
     * A seam for the window a connect is *inside*: the client has sent its hello and is waiting, so
     * anything this does happens strictly between "connect started" and "connect finished". That is
     * the only way to test what a concurrent [AtemConnectionManager.invalidate] does to a connection
     * still being opened, and it needs no sleep to hit it.
     */
    private val onHelloReceived: (() -> Unit)? = null,
    /**
     * When false, commands are received and recorded but never ACKed — a switcher that has gone
     * deaf mid-session. Withholding a reply, like [answerHello]; nothing here invents a layout the
     * captures did not show.
     */
    private val ackCommands: Boolean = true,
) : AutoCloseable {

    companion object {
        const val VIDEO_MODE_1080P5994 = 13
        private const val HEADER = 12
        private const val FLAG_ACK_REQUEST = 0x01
        private const val FLAG_HELLO = 0x02
        private const val FLAG_ACK = 0x10
        /** The placeholder the client opens with; a real ATEM echoes it in the hello reply. */
        private const val TEMP_SESSION_ID = 0x53AB
        private const val REAL_SESSION_ID = 0x1234
    }

    private val socket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = socket.localPort

    private val running = AtomicBoolean(true)
    private val client = AtomicReference<InetSocketAddress?>(null)
    private var outPacketId = 0

    /** Every command the switcher received, in order — the record tests assert against. */
    val received: MutableList<Pair<String, ByteArray>> = Collections.synchronizedList(mutableListOf())

    /**
     * Encoded byte count the next transfer will carry. The client announces only the *raw*
     * length in FTSD, so the fake cannot infer this — the test supplies it, and the fake then
     * completes the transfer on the exact byte rather than after an idle window.
     */
    @Volatile var expectedTransferBytes: Int = 0

    /** Frame count reported for a clip bank once committed, so awaitClipReady has a real signal. */
    @Volatile var clipFramesOnCommit: Int = 0

    private var bytesThisTransfer = 0
    private var chunksSinceGrant = 0
    private var grantedThisRound = 0
    private var ftdeSent = 0

    private val loop = thread(isDaemon = true, name = "fake-atem") {
        val buf = ByteArray(65536)
        while (running.get()) {
            val p = DatagramPacket(buf, buf.size)
            runCatching { socket.receive(p) }.getOrElse { return@thread }
            client.set(p.socketAddress as InetSocketAddress)
            runCatching { handle(p.data.copyOf(p.length)) }
        }
    }

    private fun handle(pkt: ByteArray) {
        if (pkt.size < HEADER) return
        val flags = (pkt[0].toInt() and 0xFF) shr 3

        if (flags and FLAG_HELLO != 0) {
            if (!answerHello) return
            onHelloReceived?.invoke()
            // The hello reply still carries the client's placeholder session id; the real one
            // only appears in the packets that follow. AtemClient depends on exactly this.
            send(header(FLAG_HELLO, TEMP_SESSION_ID, packetId = 0, extra = 8))
            outPacketId = 0
            sendStateDump()
            return
        }
        if (flags and FLAG_ACK_REQUEST == 0) return

        // Record BEFORE acking. The ack is the only signal `AtemClient.sendCommandAndWait` waits
        // on, so a test whose call has returned is entitled to assume the command it sent is in
        // `received`. Acking first made that false: the client could observe the ack, return,
        // and have the test read `received` while this thread was still between the two — which
        // is one preemption on a loaded runner, and surfaced as
        // `an upstream keyer command was sent, saw []`.
        //
        // The ack still goes out ahead of `respondTo`, which is the order the client expects on
        // the wire; only the bookkeeping moved.
        val commands = parseCommands(pkt)
        if (commands.isNotEmpty()) lastCommandSession = byteArrayOf(pkt[2], pkt[3])
        for ((name, payload) in commands) {
            received.add(name to payload)
        }
        if (ackCommands) ack(u16(pkt, 10))
        for ((name, payload) in commands) {
            respondTo(name, payload)
        }
    }

    @Volatile private var lastCommandSession: ByteArray? = null

    /** Session id bytes carried by the most recent packet that contained a command. */
    fun lastCommandSessionId(): ByteArray? = lastCommandSession

    private fun respondTo(name: String, payload: ByteArray) {
        when (name) {
            "LOCK" -> if (payload.size >= 3 && payload[2].toInt() == 1) {
                emit("LKOB", ByteArray(4))
            }
            "FTSD" -> {
                bytesThisTransfer = 0
                chunksSinceGrant = 0
                val transferId = u16(payload, 0)
                val fatal = ftdeFatalCode
                if (fatal != null) {
                    emit("FTDE", ByteArray(4).also { writeU16(it, 0, transferId); it[2] = fatal.toByte() })
                } else if (ftdeSent < ftdeRetriesBeforeSuccess) {
                    ftdeSent++
                    // code 1 = "busy, retry the whole transfer"
                    emit("FTDE", ByteArray(4).also { writeU16(it, 0, transferId); it[2] = 1 })
                } else {
                    grant(transferId)
                }
            }
            "FTDa" -> {
                val transferId = u16(payload, 0)
                // payload: transferId(2) + size(2) + data
                bytesThisTransfer += u16(payload, 2)
                chunksSinceGrant++
                if (bytesThisTransfer >= expectedTransferBytes) {
                    emit("FTDC", ByteArray(4).also { writeU16(it, 0, transferId) })
                } else if (chunksSinceGrant >= grantedThisRound) {
                    grant(transferId)
                }
            }
            "SMPC" -> {
                // Clip committed — report the bank as used and fully ingested so
                // awaitClipReady ends on a real MPCS rather than on its timeout.
                // SMPC byte 0 is a field mask (3) — the clip index is byte 1.
                val slot = payload.getOrNull(1)?.toInt()?.and(0xFF) ?: 0
                emit("MPCS", clipDescription(slot, used = true, frames = clipFramesOnCommit))
            }
        }
    }

    private fun grant(transferId: Int) {
        val remaining = expectedTransferBytes - bytesThisTransfer
        val needed = if (remaining <= 0) 1 else (remaining + grantChunkSize - 1) / grantChunkSize
        grantedThisRound = minOf(chunksPerGrant, needed)
        chunksSinceGrant = 0
        val p = ByteArray(12)
        writeU16(p, 0, transferId)
        writeU16(p, 6, grantChunkSize)
        writeU16(p, 8, grantedThisRound)
        emit("FTCD", p)
    }

    // ── State dump ────────────────────────────────────────────────────────────

    private fun sendStateDump() {
        val cmds = mutableListOf<Pair<String, ByteArray>>()
        cmds += "VidM" to byteArrayOf(videoMode.toByte(), 0, 0, 0)
        cmds += "_top" to ByteArray(24).also {
            it[0] = mixEffects.toByte()
            it[2] = downstreamKeyers.toByte()
        }
        repeat(mixEffects) { me ->
            cmds += "_MeC" to byteArrayOf(me.toByte(), keyersPerMe.toByte(), 0, 0)
        }
        cmds += "_mpl" to byteArrayOf(stillSlotCount.toByte(), clipSlotCount.toByte(), 0, 0)
        cmds += "MPSp" to ByteArray(12).also {
            repeat(minOf(4, clipSlotCount)) { i -> writeU16(it, i * 2, 720) }
            writeU16(it, 8, 1000)
        }
        repeat(stillSlotCount) { i -> cmds += "MPfe" to stillDescription(i) }
        repeat(clipSlotCount) { i -> cmds += "MPCS" to clipDescription(i, used = false, frames = 0) }
        // Batch like a real device rather than one command per datagram.
        cmds.chunked(8).forEach { batch -> emitAll(batch) }
    }

    /** MPfe: pool(1) pad(1) index(2) isUsed(1) hash(16) pad(2) nameLen(1) name — see the parser. */
    private fun stillDescription(index: Int): ByteArray = ByteArray(24).also {
        it[0] = 0
        writeU16(it, 2, index)
        it[4] = 0
        it[23] = 0
    }

    /** MPCS: index(1) isUsed(1) name(64, NUL-terminated) frames(2). */
    private fun clipDescription(index: Int, used: Boolean, frames: Int): ByteArray =
        ByteArray(68).also {
            it[0] = index.toByte()
            it[1] = if (used) 1 else 0
            if (used) "clip".toByteArray(Charsets.UTF_8).copyInto(it, 2)
            writeU16(it, 66, frames)
        }

    // ── Wire helpers ──────────────────────────────────────────────────────────

    private fun emit(name: String, payload: ByteArray) = emitAll(listOf(name to payload))

    private fun emitAll(cmds: List<Pair<String, ByteArray>>) {
        val body = cmds.fold(ByteArray(0)) { acc, (n, p) -> acc + command(n, p) }
        val total = HEADER + body.size
        val pkt = header(FLAG_ACK_REQUEST, REAL_SESSION_ID, ++outPacketId, extra = body.size)
        body.copyInto(pkt, HEADER)
        check(pkt.size == total)
        send(pkt)
    }

    private fun header(flags: Int, session: Int, packetId: Int, extra: Int): ByteArray {
        val total = HEADER + extra
        return ByteArray(total).also {
            it[0] = ((flags shl 3) or ((total shr 8) and 0x07)).toByte()
            it[1] = (total and 0xFF).toByte()
            writeU16(it, 2, session)
            writeU16(it, 10, packetId)
        }
    }

    private fun ack(packetId: Int) {
        val pkt = header(FLAG_ACK, REAL_SESSION_ID, packetId = 0, extra = 0)
        writeU16(pkt, 4, packetId)
        send(pkt)
    }

    private fun command(name: String, payload: ByteArray): ByteArray {
        val len = 8 + payload.size
        return ByteArray(len).also {
            writeU16(it, 0, len)
            name.toByteArray(Charsets.US_ASCII).copyInto(it, 4)
            payload.copyInto(it, 8)
        }
    }

    private fun parseCommands(pkt: ByteArray): List<Pair<String, ByteArray>> {
        val out = mutableListOf<Pair<String, ByteArray>>()
        var off = HEADER
        while (off + 8 <= pkt.size) {
            val len = u16(pkt, off)
            if (len < 8 || off + len > pkt.size) break
            out.add(String(pkt, off + 4, 4, Charsets.US_ASCII) to pkt.copyOfRange(off + 8, off + len))
            off += len
        }
        return out
    }

    private fun send(bytes: ByteArray) {
        val dest = client.get() ?: return
        runCatching { socket.send(DatagramPacket(bytes, bytes.size, dest)) }
    }

    private fun u16(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun writeU16(b: ByteArray, o: Int, v: Int) {
        b[o] = ((v shr 8) and 0xFF).toByte()
        b[o + 1] = (v and 0xFF).toByte()
    }

    /** Commands of one name that the switcher received. */
    fun commandsNamed(name: String): List<ByteArray> =
        synchronized(received) { received.filter { it.first == name }.map { it.second } }

    /**
     * Waits until [count] commands named [name] have arrived, then returns them.
     *
     * Needed for the one command the client sends without awaiting its ack — the closing
     * `LOCK` release, deliberately fire-and-forget so a dead socket there cannot mask the
     * failure that preceded it. The upload returning is therefore *not* a signal that the
     * unlock has landed, and asserting on it directly races the datagram: it passes on a
     * quiet machine and fails on a loaded two-core CI runner.
     *
     * The arrival is the positive signal; the timeout exists only to fail the test.
     */
    fun awaitCommandsNamed(name: String, count: Int, timeoutMs: Long = 5_000): List<ByteArray> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val got = commandsNamed(name)
            if (got.size >= count) return got
            Thread.sleep(5)
        }
        throw AssertionError(
            "timed out after ${timeoutMs}ms waiting for $count $name commands, got ${commandsNamed(name).size}"
        )
    }

    override fun close() {
        running.set(false)
        socket.close()
        loop.join(1_000)
    }
}
