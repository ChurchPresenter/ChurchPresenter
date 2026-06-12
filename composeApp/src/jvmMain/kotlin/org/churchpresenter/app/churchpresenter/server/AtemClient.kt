package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/** A single slot in the ATEM media pool (still or clip). */
data class AtemMediaSlot(val index: Int, val name: String, val isUsed: Boolean)

/**
 * ATEM device state read on connect.
 *
 * @param fps        exact frame rate derived from [videoMode], e.g. 25.0, 29.97, 59.94
 * @param videoMode  human-readable video standard, e.g. "1080p25", "1080p29.97"
 * @param stillSlots list of still-store slots (from MPSP commands)
 * @param clipSlots  list of clip-store slots (from MPCP commands)
 */
data class AtemState(
    val fps: Double,
    val videoMode: String,
    val stillSlots: List<AtemMediaSlot>,
    val clipSlots: List<AtemMediaSlot>
)

/**
 * Minimal ATEM switcher UDP client for uploading stills and clips to the media pool.
 *
 * Implements a subset of the Blackmagic ATEM protocol (port 9910) sufficient for:
 *   - Connecting (hello handshake)
 *   - Reading video mode (FPS) and media pool slot info from the ATEM state dump
 *   - Uploading a still (single ARGB frame) to the still store
 *   - Uploading a clip (list of ARGB frames) to the clip store
 *
 * Input pixel format: ARGB ints — converted internally to the ATEM's native
 * 10-bit YUVA 4:2:2 media pool format (BT.709 for >=720p, BT.601 below).
 *
 * Upload flow (per sofie-atem-connection, https://github.com/nrkno/sofie-atem-connection):
 *   LOCK(store, 1) → LKOB                              lock the media pool store
 *   FTSD(transferId, store, index, size, mode=1)       request the upload
 *   ← FTCD(transferId, chunkSize, chunkCount)          ATEM grants a batch of chunks
 *   FTFD(transferId, name, md5) once + FTDa chunks     description, then granted data chunks
 *   …more FTCD grants / FTDa batches until all sent…
 *   ← FTDC(transferId)                                 transfer complete
 *   LOCK(store, 0)                                     unlock
 * FTDE(transferId, code) is the error response; code 1 means "please retry".
 * Clips additionally send CMPC (clear clip) before and SMPC (set name/frames) after,
 * and use store id = clipIndex + 1 (store 0 is the still pool).
 *
 * Packet header (12 bytes):
 *   byte 0   : (flags shl 3) or ((totalLength shr 8) and 0x07)
 *   byte 1   : totalLength and 0xFF
 *   bytes 2-3: sessionId (big-endian uint16) — assigned by ATEM on connect
 *   bytes 4-5: lastRemotePacketId (big-endian uint16) — ATEM's most recent packet ID, for ACK
 *   bytes 6-7: ackPacketId (big-endian uint16) — usually 0
 *   bytes 8-9: unknown (usually 0)
 *   bytes 10-11: packetId (big-endian uint16, wraps at 0x8000) — sender's sequence number
 *
 * Flag bit values (before left-shift-3):
 *   0x01 = AckRequest         — receiver must ACK this packet
 *   0x02 = Hello/NewSessionId — connection handshake
 *   0x08 = RetransmitRequest  — receiver asks for packets to be resent (from id at bytes 6-7)
 *   0x10 = AckReply           — pure ACK; acked packet id at bytes 4-5
 *
 * The real session id is NOT in the hello response — the ATEM assigns it in its first
 * post-handshake packet, so it is re-read from every incoming packet.
 */
class AtemClient(val host: String, val port: Int = 9910) {

    companion object {
        private const val HEADER_SIZE = 12
        private const val FLAG_ACK_REQUEST = 0x01
        private const val FLAG_HELLO = 0x02
        private const val FLAG_RETRANSMIT_REQUEST = 0x08
        private const val FLAG_ACK = 0x10
        private const val MAX_PACKET_ID = 0x8000   // ATEM wraps packet ids at 15 bits
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val CMD_TIMEOUT_MS = 8000
        private const val MAX_RECV_BUF = 65536
        private const val MAX_TRANSFER_RETRIES = 40   // ATEM sends "retry" while busy, e.g. clearing the clip pool
        private const val RETRY_BACKOFF_MS = 250L
        private const val MAX_IN_FLIGHT = 2048

        /** Client hello packet, verbatim from sofie-atem-connection (COMMAND_CONNECT_HELLO). */
        private val CONNECT_HELLO = byteArrayOf(
            0x10, 0x14, 0x53, 0xAB.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x3A, 0x00, 0x00,
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )

        /** Commands worth buffering when received while waiting for something else. */
        private val INTERESTING_COMMANDS = setOf("FTCD", "FTDC", "FTDE", "FTUA", "LKOB", "LKST")
    }

    private var socket: DatagramSocket? = null
    private val address: InetAddress by lazy { InetAddress.getByName(host) }
    private var sessionId: Int = 0
    private var lastReceivedPacketId: Int = 0
    private var nextSendPacketId: Int = 1
    private var helloReceived = false
    private val transferIdCounter = AtomicInteger(1)

    /** Sent-but-unacked packets, kept verbatim for ATEM retransmit requests (insertion order). */
    private val inFlight = LinkedHashMap<Int, ByteArray>()

    /** Interesting commands received while waiting for something else; consumed first on the next wait. */
    private val pendingCommands = ArrayDeque<Pair<String, ByteArray>>()

    /** State parsed from the ATEM state dump received on connect. Populated after [connect]. */
    var lastKnownState: AtemState? = null
        private set

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Connect to the ATEM and perform the hello handshake.
     * Must be called before any upload function.
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        val sock = DatagramSocket()
        sock.soTimeout = CONNECT_TIMEOUT_MS
        socket = sock

        try {
            sessionId = 0x53AB   // temporary client session id, replaced by the ATEM's
            lastReceivedPacketId = 0
            nextSendPacketId = 1
            helloReceived = false
            inFlight.clear()
            pendingCommands.clear()

            sendRaw(CONNECT_HELLO)

            // Wait for the hello response (receiveAndProcess ACKs it and flips the flag)
            val deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS
            while (!helloReceived) {
                if (System.currentTimeMillis() >= deadline ||
                    receiveAndProcess() == null
                ) throw Exception("No response from ATEM at $host:$port")
            }

            // Collect and parse the ATEM state dump
            val stateMap = collectState(sock)
            lastKnownState = parseAtemState(stateMap)
        } catch (e: Exception) {
            disconnect()
            throw e
        }
    }

    /** Disconnect and release the socket. */
    fun disconnect() {
        socket?.close()
        socket = null
        inFlight.clear()
        pendingCommands.clear()
    }

    /**
     * Connect, read device state (video mode + media pool), disconnect.
     * Convenience wrapper — callers that only need state info don't have to manage
     * connect/disconnect themselves.
     */
    suspend fun queryState(): AtemState = withContext(Dispatchers.IO) {
        try {
            connect()
            lastKnownState ?: AtemState(30.0, "Unknown", emptyList(), emptyList())
        } finally {
            disconnect()
        }
    }

    /**
     * Upload a single ARGB frame to the ATEM still store.
     *
     * @param slot         0-based still store slot index
     * @param argbPixels   width*height ARGB ints (A=byte0, R=byte1, G=byte2, B=byte3)
     * @param width        frame width in pixels
     * @param height       frame height in pixels
     * @param name         display name for the still (max 64 chars)
     * @param onProgress   called with 0f..1f as upload progresses
     */
    suspend fun uploadStill(
        slot: Int,
        argbPixels: IntArray,
        width: Int,
        height: Int,
        name: String,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        if (argbPixels.isEmpty()) throw Exception("Nothing to upload — frame rendering produced no pixels")
        if (argbPixels.size != width * height) {
            throw Exception("Pixel buffer is ${argbPixels.size} pixels, expected ${width}×${height}")
        }
        val knownStills = lastKnownState?.stillSlots
        if (!knownStills.isNullOrEmpty() && knownStills.none { it.index == slot }) {
            throw Exception("Still slot $slot does not exist on this ATEM (available: 0–${knownStills.maxOf { it.index }})")
        }
        val data = argbToYuv422(width, height, argbPixels)

        // Lock still store (storeId 0)
        sendCommandAndWait("LOCK", buildLockPayload(0, locked = true), "LKOB", timeout = CMD_TIMEOUT_MS.toLong())
        try {
            performTransfer(storeId = 0, frameIndex = slot, data = data, name = name, onProgress = onProgress)
        } finally {
            // Unlock even if the transfer failed midway; best-effort so a dead socket
            // here can't mask the original failure
            runCatching { sendCommand("LOCK", buildLockPayload(0, locked = false)) }
        }
    }

    /**
     * Upload an animated clip to the ATEM clip store, one frame at a time.
     *
     * Frames are pulled lazily through [nextFrame] so only a single frame is ever in
     * memory — a 1080p frame is ~8 MB, so buffering a whole clip would exhaust the heap.
     *
     * @param slot       0-based clip store slot index
     * @param frameCount total number of frames in the clip
     * @param width      frame width
     * @param height     frame height
     * @param name       clip name
     * @param nextFrame  returns the ARGB pixels for a frame index; called in order 0..frameCount-1.
     *                   The returned array may be a reused buffer — it is consumed before the next call.
     * @param onProgress called with 0f..1f
     */
    suspend fun uploadClip(
        slot: Int,
        frameCount: Int,
        width: Int,
        height: Int,
        name: String,
        nextFrame: suspend (Int) -> IntArray,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        if (frameCount <= 0) throw Exception("Nothing to upload — clip rendering produced no frames")
        // Locking a nonexistent store is silently ignored by the ATEM (LKOB never comes),
        // so validate the slot against the state dump up front for a clear error
        val knownClips = lastKnownState?.clipSlots
        if (!knownClips.isNullOrEmpty() && knownClips.none { it.index == slot }) {
            throw Exception("Clip slot $slot does not exist on this ATEM (available: 0–${knownClips.maxOf { it.index }})")
        }
        val storeId = slot + 1   // clip stores are 1-based; store 0 is the still pool

        sendCommandAndWait("LOCK", buildLockPayload(storeId, locked = true), "LKOB", timeout = CMD_TIMEOUT_MS.toLong())
        try {
            // Clear the clip slot before uploading new frames
            sendCommandAndWait("CMPC", ByteArray(4).also { it[0] = slot.toByte() }, expectedResponse = null)

            for (frameIdx in 0 until frameCount) {
                val argbPixels = nextFrame(frameIdx)
                if (argbPixels.size != width * height) {
                    throw Exception("Frame $frameIdx is ${argbPixels.size} pixels, expected ${width}×${height}")
                }
                val data = argbToYuv422(width, height, argbPixels)
                performTransfer(storeId = storeId, frameIndex = frameIdx, data = data, name = null) { p ->
                    onProgress((frameIdx + p) / frameCount)
                }
            }

            // Commit the clip: set its name and frame count
            sendCommandAndWait("SMPC", buildSetClipPayload(slot, name, frameCount), expectedResponse = null)
        } finally {
            // Unlock even if the transfer failed midway; best-effort so a dead socket
            // here can't mask the original failure
            runCatching { sendCommand("LOCK", buildLockPayload(storeId, locked = false)) }
        }
    }

    /**
     * Runs one FTSD upload transfer, honoring the ATEM's FTCD flow-control grants.
     * Sends the FTFD description on the first grant; finishes when FTDC arrives.
     * FTDE code 1 means the ATEM wants the transfer restarted (e.g. it was busy).
     */
    private fun performTransfer(
        storeId: Int,
        frameIndex: Int,
        data: ByteArray,
        name: String?,
        onProgress: (Float) -> Unit
    ) {
        val hash = MessageDigest.getInstance("MD5").digest(data)
        val transferId = transferIdCounter.getAndIncrement()
        var bytesSent = 0
        var descriptionSent = false
        var retries = 0

        sendCommand("FTSD", buildUploadRequestPayload(transferId, storeId, frameIndex, data.size))

        while (true) {
            val (cmd, payload) = waitForAnyCommand(setOf("FTCD", "FTDC", "FTDE"), CMD_TIMEOUT_MS.toLong())
            if (payload.size < 2) continue
            // Ignore messages that belong to other transfers
            val cmdTransferId = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
            if (cmdTransferId != transferId) continue

            when (cmd) {
                "FTCD" -> {
                    if (!descriptionSent) {
                        sendCommand("FTFD", buildFileDescriptionPayload(transferId, name, hash))
                        descriptionSent = true
                    }
                    if (payload.size < 10) continue
                    // ATEM grants chunkCount chunks of chunkSize bytes (rounded to 8)
                    val chunkSize = ((((payload[6].toInt() and 0xFF) shl 8) or (payload[7].toInt() and 0xFF)) / 8) * 8
                    val chunkCount = ((payload[8].toInt() and 0xFF) shl 8) or (payload[9].toInt() and 0xFF)
                    if (chunkSize <= 0) continue
                    var sent = 0
                    while (sent < chunkCount && bytesSent < data.size) {
                        val len = minOf(chunkSize, data.size - bytesSent)
                        sendCommand("FTDa", buildDataChunkPayload(transferId, data, bytesSent, len))
                        bytesSent += len
                        sent++
                    }
                    onProgress(bytesSent.toFloat() / data.size)
                }
                "FTDC" -> return
                "FTDE" -> {
                    val code = payload.getOrNull(2)?.toInt()?.and(0xFF) ?: -1
                    if (code == 1 && retries < MAX_TRANSFER_RETRIES) {
                        // Code 1 = "retry": the ATEM is busy (e.g. still clearing the clip
                        // pool after CMPC). Back off briefly, then restart the same transfer.
                        retries++
                        bytesSent = 0
                        descriptionSent = false
                        Thread.sleep(RETRY_BACKOFF_MS)
                        sendCommand("FTSD", buildUploadRequestPayload(transferId, storeId, frameIndex, data.size))
                    } else {
                        // Clip frames past index 0 usually fail because the device's clip
                        // pool ran out of frame capacity — surface an actionable hint
                        val what = if (name == null) "clip frame $frameIndex" else "still"
                        val hint = if (name == null && frameIndex > 0)
                            " — the clip may exceed the ATEM's clip pool capacity; try a shorter duration or lower fps"
                        else ""
                        if (code == 1) {
                            throw Exception("ATEM stayed busy uploading $what after $retries retries$hint")
                        } else {
                            throw Exception("ATEM rejected $what (error code $code)$hint")
                        }
                    }
                }
            }
        }
    }

    // ── Packet building ──────────────────────────────────────────────────────

    private fun u16(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 8) or (b[offset + 1].toInt() and 0xFF)

    private fun sendRaw(bytes: ByteArray) {
        socket?.send(DatagramPacket(bytes, bytes.size, address, port))
    }

    private fun buildCommandBytes(name: String, data: ByteArray): ByteArray {
        val cmdLen = 8 + data.size
        val cmd = ByteArray(cmdLen)
        cmd[0] = ((cmdLen shr 8) and 0xFF).toByte()
        cmd[1] = (cmdLen and 0xFF).toByte()
        // bytes 2-3 = 0 (unused)
        val nameBytes = name.toByteArray(Charsets.US_ASCII)
        System.arraycopy(nameBytes, 0, cmd, 4, minOf(4, nameBytes.size))
        System.arraycopy(data, 0, cmd, 8, data.size)
        return cmd
    }

    /**
     * Send a command in an AckRequest packet. The packet is kept in [inFlight] until the
     * ATEM acks it, so retransmit requests can be honored. Returns the packet id used.
     */
    private fun sendCommand(name: String, data: ByteArray): Int {
        val payload = buildCommandBytes(name, data)
        val totalLen = HEADER_SIZE + payload.size
        val pktId = nextSendPacketId
        nextSendPacketId = (nextSendPacketId + 1) % MAX_PACKET_ID
        val pkt = ByteArray(totalLen)
        pkt[0] = ((FLAG_ACK_REQUEST shl 3) or ((totalLen shr 8) and 0x07)).toByte()
        pkt[1] = (totalLen and 0xFF).toByte()
        writeU16(pkt, 2, sessionId)
        writeU16(pkt, 10, pktId)
        System.arraycopy(payload, 0, pkt, HEADER_SIZE, payload.size)
        inFlight[pktId] = pkt
        while (inFlight.size > MAX_IN_FLIGHT) inFlight.remove(inFlight.keys.first())
        sendRaw(pkt)
        return pktId
    }

    /** Pure ACK packet: AckReply flag, acked packet id at bytes 4-5, packet id 0. */
    private fun sendAck(ackId: Int) {
        val pkt = ByteArray(HEADER_SIZE)
        pkt[0] = (FLAG_ACK shl 3).toByte()
        pkt[1] = HEADER_SIZE.toByte()
        writeU16(pkt, 2, sessionId)
        writeU16(pkt, 4, ackId)
        sendRaw(pkt)
    }

    /** Send a command and wait for either its packet-level ACK or a named response command. */
    private fun sendCommandAndWait(
        name: String,
        data: ByteArray,
        expectedResponse: String?,
        timeout: Long = CMD_TIMEOUT_MS.toLong()
    ) {
        val pktId = sendCommand(name, data)
        if (expectedResponse == null) {
            waitForAckOf(pktId, timeout)
        } else {
            waitForCommand(expectedResponse, timeout)
        }
    }

    // ── Receive helpers ──────────────────────────────────────────────────────

    private fun receivePacket(): ByteArray? {
        val buf = ByteArray(MAX_RECV_BUF)
        val dp = DatagramPacket(buf, buf.size)
        return try {
            socket?.receive(dp)
            buf.copyOf(dp.length)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Receive one packet and run the reliable-layer bookkeeping (mirrors
     * sofie-atem-connection's _receivePacket): adopt the session id, handle hello /
     * retransmit-request / ack flags, ACK and deduplicate data packets by sequence id.
     *
     * Returns the commands carried by an in-sequence data packet (often empty),
     * or null if the socket timed out / closed.
     */
    private fun receiveAndProcess(): List<Pair<String, ByteArray>>? {
        val pkt = receivePacket() ?: return null
        if (pkt.size < HEADER_SIZE) return emptyList()
        val flags = (pkt[0].toInt() and 0xFF) shr 3
        // The ATEM assigns the real session id after the handshake — track it always
        sessionId = u16(pkt, 2)
        val remoteId = u16(pkt, 10)

        if (flags and FLAG_HELLO != 0) {
            helloReceived = true
            lastReceivedPacketId = remoteId
            sendAck(remoteId)
            return emptyList()
        }

        if (flags and FLAG_RETRANSMIT_REQUEST != 0) {
            retransmitFrom(u16(pkt, 6) % MAX_PACKET_ID)
        }

        var commands: List<Pair<String, ByteArray>> = emptyList()
        if (flags and FLAG_ACK_REQUEST != 0) {
            if (remoteId == (lastReceivedPacketId + 1) % MAX_PACKET_ID) {
                lastReceivedPacketId = remoteId
                sendAck(remoteId)
                commands = parseAllCommands(pkt)
            } else if (isCoveredByAck(lastReceivedPacketId, remoteId)) {
                // Retransmit of something we already processed — re-ack, don't reprocess
                sendAck(lastReceivedPacketId)
            }
            // else: a future packet — a gap means loss; the ATEM will retransmit
        }

        if (flags and FLAG_ACK != 0) {
            val ackId = u16(pkt, 4)
            inFlight.keys.removeAll { isCoveredByAck(ackId, it) }
        }

        return commands
    }

    /** Whether [packetId] is acknowledged by an ack for [ackId], allowing for 15-bit wrap. */
    private fun isCoveredByAck(ackId: Int, packetId: Int): Boolean {
        val tolerance = MAX_PACKET_ID / 2
        val shortlyBefore = packetId < ackId && packetId + tolerance > ackId
        val shortlyAfter = packetId > ackId && packetId < ackId + tolerance
        val beforeWrap = packetId > ackId + tolerance
        return packetId == ackId || ((shortlyBefore || beforeWrap) && !shortlyAfter)
    }

    /** Resend buffered in-flight packets starting from [fromId] (ATEM retransmit request). */
    private fun retransmitFrom(fromId: Int) {
        if (!inFlight.containsKey(fromId)) {
            throw Exception("ATEM requested retransmit of packet $fromId, which is no longer buffered")
        }
        var resending = false
        for ((id, bytes) in inFlight) {
            if (id == fromId) resending = true
            if (resending) sendRaw(bytes)
        }
    }

    /** Buffer any interesting commands so a later wait can consume them. */
    private fun stashInteresting(commands: List<Pair<String, ByteArray>>) {
        for (c in commands) {
            if (c.first in INTERESTING_COMMANDS) pendingCommands.add(c)
        }
    }

    /** Receive until the ATEM acks our packet [pktId]; throws on timeout. */
    private fun waitForAckOf(pktId: Int, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (inFlight.containsKey(pktId)) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) throw Exception("ATEM did not acknowledge within ${timeoutMs}ms")
            socket?.soTimeout = remaining.coerceAtLeast(1).toInt()
            val commands = receiveAndProcess()
                ?: throw Exception("ATEM did not acknowledge within ${timeoutMs}ms")
            stashInteresting(commands)
        }
    }

    private fun waitForCommand(cmdName: String, timeoutMs: Long) {
        waitForAnyCommand(setOf(cmdName), timeoutMs)
    }

    /**
     * Receive packets until one contains a command named in [names]; other interesting
     * commands are buffered for later waits. Throws on timeout.
     */
    private fun waitForAnyCommand(names: Set<String>, timeoutMs: Long): Pair<String, ByteArray> {
        val pendingIdx = pendingCommands.indexOfFirst { it.first in names }
        if (pendingIdx >= 0) return pendingCommands.removeAt(pendingIdx)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            socket?.soTimeout = (deadline - System.currentTimeMillis()).coerceAtLeast(1).toInt()
            val commands = receiveAndProcess() ?: break
            var result: Pair<String, ByteArray>? = null
            for (c in commands) {
                if (result == null && c.first in names) result = c
                else if (c.first in INTERESTING_COMMANDS) pendingCommands.add(c)
            }
            if (result != null) return result
        }
        throw Exception("ATEM did not respond with ${names.joinToString("/")} within ${timeoutMs}ms")
    }

    /**
     * Receive and ACK all ATEM state-dump packets, collecting every command by name.
     * Uses a 2-second overall deadline; exits early once the device goes idle
     * (no packet for 300 ms), so a fast dump doesn't wait out the full deadline.
     */
    private fun collectState(sock: DatagramSocket): Map<String, List<ByteArray>> {
        val result = mutableMapOf<String, MutableList<ByteArray>>()
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline) {
            sock.soTimeout = (deadline - System.currentTimeMillis()).coerceAtLeast(1).toInt().coerceAtMost(300)
            val commands = receiveAndProcess() ?: break
            commands.forEach { (name, payload) ->
                result.getOrPut(name) { mutableListOf() }.add(payload)
            }
        }
        return result
    }

    /** Parse every command from a single UDP packet into (name, payload) pairs. */
    private fun parseAllCommands(packet: ByteArray): List<Pair<String, ByteArray>> {
        val out = mutableListOf<Pair<String, ByteArray>>()
        var offset = HEADER_SIZE
        while (offset + 8 <= packet.size) {
            val len = ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
            if (len < 8 || offset + len > packet.size) break
            val name = String(packet, offset + 4, 4, Charsets.US_ASCII)
            out.add(name to packet.copyOfRange(offset + 8, offset + len))
            offset += len
        }
        return out
    }

    // ── State parsers ─────────────────────────────────────────────────────────

    private fun parseAtemState(m: Map<String, List<ByteArray>>): AtemState {
        val (mode, fps) = when (m["VidM"]?.firstOrNull()?.getOrNull(0)?.toInt()?.and(0xFF)) {
            0, 2 -> "525i59.94 NTSC" to 30000.0 / 1001.0   // 29.97…
            1, 3 -> "625i50 PAL"     to 25.0
            4    -> "720p50"         to 50.0
            5    -> "720p59.94"      to 60000.0 / 1001.0    // 59.94…
            6    -> "1080i50"        to 50.0
            7    -> "1080i59.94"     to 60000.0 / 1001.0
            8    -> "1080p23.98"     to 24000.0 / 1001.0    // 23.976…
            9    -> "1080p24"        to 24.0
            10   -> "1080p25"        to 25.0
            11   -> "1080p29.97"     to 30000.0 / 1001.0
            12   -> "1080p50"        to 50.0
            13   -> "1080p59.94"     to 60000.0 / 1001.0
            14   -> "2160p23.98"     to 24000.0 / 1001.0
            15   -> "2160p24"        to 24.0
            16   -> "2160p25"        to 25.0
            17   -> "2160p29.97"     to 30000.0 / 1001.0
            else -> "Unknown"        to 30.0
        }
        return AtemState(fps, mode, parseStillSlots(m), parseClipSlots(m))
    }

    /**
     * MPSP (Media Pool Still Properties) payload layout:
     *   bytes 0-1:  index (uint16)
     *   byte  2:    flags (bit 0 = isUsed)
     *   byte  3:    padding
     *   bytes 4-19: hash (16 bytes)
     *   bytes 20+:  filename (null-terminated UTF-8, max 64 bytes)
     */
    private fun parseStillSlots(m: Map<String, List<ByteArray>>): List<AtemMediaSlot> =
        m["MPSP"]?.mapNotNull { p ->
            if (p.size < 4) return@mapNotNull null
            val idx  = ((p[0].toInt() and 0xFF) shl 8) or (p[1].toInt() and 0xFF)
            val used = (p[2].toInt() and 0x01) != 0
            val name = if (p.size > 20)
                String(p, 20, (p.size - 20).coerceAtMost(64), Charsets.UTF_8).trimEnd('\u0000')
            else ""
            AtemMediaSlot(idx, name, used)
        } ?: emptyList()

    /**
     * MPCP (Media Pool Clip Properties) payload layout:
     *   bytes 0-1:  index (uint16)
     *   byte  2:    isUsed (uint8, 0=no)
     *   byte  3:    padding
     *   bytes 4-67: name (64 bytes, null-padded UTF-8)
     */
    private fun parseClipSlots(m: Map<String, List<ByteArray>>): List<AtemMediaSlot> =
        m["MPCP"]?.mapNotNull { p ->
            if (p.size < 4) return@mapNotNull null
            val idx  = ((p[0].toInt() and 0xFF) shl 8) or (p[1].toInt() and 0xFF)
            val used = (p[2].toInt() and 0xFF) != 0
            val name = if (p.size > 4)
                String(p, 4, (p.size - 4).coerceAtMost(64), Charsets.UTF_8).trimEnd('\u0000')
            else ""
            AtemMediaSlot(idx, name, used)
        } ?: emptyList()

    // ── Payload builders ─────────────────────────────────────────────────────

    private fun writeU16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }

    /** LOCK payload (4 bytes): storeId (uint16), locked (uint8), padding. */
    private fun buildLockPayload(storeId: Int, locked: Boolean): ByteArray {
        val buf = ByteArray(4)
        writeU16(buf, 0, storeId)
        buf[2] = if (locked) 1 else 0
        return buf
    }

    /**
     * FTSD payload (16 bytes):
     *   bytes 0-1:  transferId (uint16)
     *   bytes 2-3:  storeId (uint16)
     *   bytes 4-5:  unknown (0)
     *   bytes 6-7:  slot / frame index (uint16)
     *   bytes 8-11: total data size (uint32, pre-RLE length)
     *   bytes 12-13: mode (uint16, 1 = write)
     */
    private fun buildUploadRequestPayload(transferId: Int, storeId: Int, frameIndex: Int, size: Int): ByteArray {
        val buf = ByteArray(16)
        writeU16(buf, 0, transferId)
        writeU16(buf, 2, storeId)
        writeU16(buf, 6, frameIndex)
        buf[8] = ((size shr 24) and 0xFF).toByte()
        buf[9] = ((size shr 16) and 0xFF).toByte()
        buf[10] = ((size shr 8) and 0xFF).toByte()
        buf[11] = (size and 0xFF).toByte()
        writeU16(buf, 12, 1)   // mode
        return buf
    }

    /**
     * FTFD payload (212 bytes):
     *   bytes 0-1:    transferId (uint16)
     *   bytes 2-65:   name (64 bytes, null-padded UTF-8)
     *   bytes 66-193: description (128 bytes, unused)
     *   bytes 194-209: MD5 hash of the encoded data (16 bytes)
     */
    private fun buildFileDescriptionPayload(transferId: Int, name: String?, md5: ByteArray): ByteArray {
        val buf = ByteArray(212)
        writeU16(buf, 0, transferId)
        if (!name.isNullOrEmpty()) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            System.arraycopy(nameBytes, 0, buf, 2, minOf(64, nameBytes.size))
        }
        System.arraycopy(md5, 0, buf, 194, minOf(16, md5.size))
        return buf
    }

    /** FTDa payload: transferId (uint16), chunk length (uint16), chunk data. */
    private fun buildDataChunkPayload(transferId: Int, data: ByteArray, offset: Int, length: Int): ByteArray {
        val buf = ByteArray(4 + length)
        writeU16(buf, 0, transferId)
        writeU16(buf, 2, length)
        System.arraycopy(data, offset, buf, 4, length)
        return buf
    }

    /**
     * SMPC payload (68 bytes): mask (uint8, 3 = name+frames), clip index (uint8),
     * name (44 bytes UTF-8 at offset 2), frame count (uint16 at offset 66).
     */
    private fun buildSetClipPayload(clipIndex: Int, name: String, frames: Int): ByteArray {
        val buf = ByteArray(68)
        buf[0] = 3
        buf[1] = clipIndex.toByte()
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        System.arraycopy(nameBytes, 0, buf, 2, minOf(44, nameBytes.size))
        writeU16(buf, 66, frames)
        return buf
    }

    // ── Pixel conversion ─────────────────────────────────────────────────────

    /**
     * Convert ARGB ints to the ATEM media pool's native 10-bit YUVA 4:2:2 format.
     *
     * Port of sofie-atem-connection's convertRGBAToYUV422: each pixel pair packs into
     * two big-endian 32-bit words — (A1, Cb, Y1) and (A2, Cr, Y2) — with 10-bit
     * components at broadcast levels (Y 64–940, CbCr 64–960, alpha scaled to 64–940).
     * BT.709 coefficients for >=720p, BT.601 below.
     */
    private fun argbToYuv422(width: Int, height: Int, pixels: IntArray): ByteArray {
        val kr = if (height >= 720) 0.2126 else 0.299
        val kb = if (height >= 720) 0.0722 else 0.114
        val kg = 1.0 - kr - kb
        val kri = 1.0 - kr
        val kbi = 1.0 - kb
        val yRange = 219.0
        val halfCbCrRange = 224.0 / 2
        val yOffset = (16 shl 8).toDouble()
        val cbCrOffset = (128 shl 8).toDouble()
        val krOKbi = kr / kbi * halfCbCrRange
        val kgOKbi = kg / kbi * halfCbCrRange
        val kbOKri = kb / kri * halfCbCrRange
        val kgOKri = kg / kri * halfCbCrRange

        fun genColor(rawA: Int, uv16: Double, y16: Double): Int {
            val a = ((rawA shl 2) * 219) / 255 + (16 shl 2)
            val y = (Math.round(y16) shr 6).toInt()
            val uv = (Math.round(uv16) shr 6).toInt()
            return (a shl 20) or (uv shl 10) or y
        }

        val out = ByteArray(width * height * 4)
        var o = 0
        var i = 0
        while (i < pixels.size) {
            val px1 = pixels[i]
            val px2 = pixels[i + 1]
            val a1 = (px1 ushr 24) and 0xFF
            val r1 = (px1 shr 16) and 0xFF
            val g1 = (px1 shr 8) and 0xFF
            val b1 = px1 and 0xFF
            val a2 = (px2 ushr 24) and 0xFF
            val r2 = (px2 shr 16) and 0xFF
            val g2 = (px2 shr 8) and 0xFF
            val b2 = px2 and 0xFF

            val y16a = yOffset + kr * yRange * r1 + kg * yRange * g1 + kb * yRange * b1
            val cb16 = cbCrOffset + (-krOKbi * r1 - kgOKbi * g1 + halfCbCrRange * b1)
            val y16b = yOffset + kr * yRange * r2 + kg * yRange * g2 + kb * yRange * b2
            val cr16 = cbCrOffset + (halfCbCrRange * r1 - kgOKri * g1 - kbOKri * b1)

            val word1 = genColor(a1, cb16, y16a)
            val word2 = genColor(a2, cr16, y16b)
            out[o]     = ((word1 ushr 24) and 0xFF).toByte()
            out[o + 1] = ((word1 shr 16) and 0xFF).toByte()
            out[o + 2] = ((word1 shr 8) and 0xFF).toByte()
            out[o + 3] = (word1 and 0xFF).toByte()
            out[o + 4] = ((word2 ushr 24) and 0xFF).toByte()
            out[o + 5] = ((word2 shr 16) and 0xFF).toByte()
            out[o + 6] = ((word2 shr 8) and 0xFF).toByte()
            out[o + 7] = (word2 and 0xFF).toByte()

            i += 2
            o += 8
        }
        return out
    }
}
