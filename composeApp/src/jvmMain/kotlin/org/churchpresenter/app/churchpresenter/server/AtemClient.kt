package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
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
 * Pixel format: ARGB, 4 bytes per pixel, big-endian byte order (A, R, G, B in memory).
 * Skia screenshots give BGRA — convert before passing to this client.
 *
 * Protocol reference: community-documented ATEM UDP protocol used by atem-connection
 * (https://github.com/nrkno/sofie-atem-connection). Tested against ATEM Mini family.
 * Other ATEM models may require minor adjustments.
 *
 * Packet header (12 bytes):
 *   byte 0   : (flags shl 3) or ((totalLength shr 8) and 0x07)
 *   byte 1   : totalLength and 0xFF
 *   bytes 2-3: sessionId (big-endian uint16) — assigned by ATEM on connect
 *   bytes 4-5: lastRemotePacketId (big-endian uint16) — ATEM's most recent packet ID, for ACK
 *   bytes 6-7: ackPacketId (big-endian uint16) — usually 0
 *   bytes 8-9: unknown (usually 0)
 *   bytes 10-11: localPacketId (big-endian uint16) — our own sequence number
 *
 * Flag bit values (before left-shift-3):
 *   0x01 = AckRequest — client asks ATEM to ACK this packet
 *   0x02 = Hello      — sent during connection handshake
 *   0x10 = Ack        — pure ACK from client to ATEM
 */
class AtemClient(val host: String, val port: Int = 9910) {

    companion object {
        private const val HEADER_SIZE = 12
        private const val FLAG_ACK_REQUEST = 0x01
        private const val FLAG_HELLO = 0x02
        private const val FLAG_ACK = 0x10
        private const val CHUNK_SIZE = 1396        // bytes of ARGB data per FTDA packet
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val CMD_TIMEOUT_MS = 8000
        private const val MAX_RECV_BUF = 65536
    }

    private var socket: DatagramSocket? = null
    private var sessionId: Int = 0
    private var lastRemotePacketId: Int = 0
    private val localPacketId = AtomicInteger(0)
    private val transferIdCounter = AtomicInteger(1)

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

        // Send Hello packet
        val helloPayload = ByteArray(8).also { it[0] = 0x01 }
        sendPacket(FLAG_HELLO, helloPayload, forcePktId = 1)

        // Receive ATEM's Hello response — extract session ID
        val resp = receivePacket() ?: throw Exception("No response from ATEM at $host:$port")
        sessionId = ((resp[2].toInt() and 0xFF) shl 8) or (resp[3].toInt() and 0xFF)
        lastRemotePacketId = ((resp[10].toInt() and 0xFF) shl 8) or (resp[11].toInt() and 0xFF)

        // ACK the ATEM's hello
        sendAck()

        // Collect and parse the ATEM state dump
        val stateMap = collectState(sock)
        lastKnownState = parseAtemState(stateMap)
    }

    /** Disconnect and release the socket. */
    fun disconnect() {
        socket?.close()
        socket = null
    }

    /**
     * Connect, read device state (video mode + media pool), disconnect.
     * Convenience wrapper — callers that only need state info don't have to manage
     * connect/disconnect themselves.
     */
    suspend fun queryState(): AtemState = withContext(Dispatchers.IO) {
        connect()
        val state = lastKnownState ?: AtemState(30.0, "Unknown", emptyList(), emptyList())
        disconnect()
        state
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
        val data = argbToBytes(argbPixels)
        val transferId = transferIdCounter.getAndIncrement()

        // Lock still store (storeId = 0)
        sendCommandAndWait("LKST", buildUInt16(0), "LKOB", timeout = CMD_TIMEOUT_MS.toLong())

        // Create file transfer descriptor
        sendCommandAndWait(
            "FTCD",
            buildFtcdPayload(transferId, storeId = 0, slotIndex = slot, size = data.size, name = name),
            expectedResponse = null
        )

        // Send data in chunks
        val totalChunks = (data.size + CHUNK_SIZE - 1) / CHUNK_SIZE
        for (chunkIdx in 0 until totalChunks) {
            val offset = chunkIdx * CHUNK_SIZE
            val len = minOf(CHUNK_SIZE, data.size - offset)
            sendCommandAndWait(
                "FTDA",
                buildFtdaPayload(transferId, data, offset, len),
                expectedResponse = null
            )
            onProgress((chunkIdx + 1).toFloat() / totalChunks)
        }

        // End transfer
        sendCommandAndWait("FTDE", buildFtdePayload(transferId), expectedResponse = null)

        // Unlock still store
        sendCommand("LKSU", buildUInt16(0))
    }

    /**
     * Upload an animated clip to the ATEM clip store.
     *
     * @param slot       0-based clip store slot index
     * @param frames     list of ARGB IntArrays, one per frame
     * @param width      frame width
     * @param height     frame height
     * @param fps        frames per second (informational — ATEM plays at its own rate)
     * @param name       clip name
     * @param onProgress called with 0f..1f
     */
    suspend fun uploadClip(
        slot: Int,
        frames: List<IntArray>,
        width: Int,
        height: Int,
        fps: Int,
        name: String,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        if (frames.isEmpty()) return@withContext

        // Lock clip store (storeId = 1)
        sendCommandAndWait("LKCP", buildUInt16(slot.toUShort()), "LKOB", timeout = CMD_TIMEOUT_MS.toLong())

        for ((frameIdx, argbPixels) in frames.withIndex()) {
            val data = argbToBytes(argbPixels)
            val transferId = transferIdCounter.getAndIncrement()

            sendCommandAndWait(
                "FTCD",
                buildFtcdPayload(
                    transferId,
                    storeId = 1,
                    slotIndex = frameIdx,  // frame index within the clip
                    size = data.size,
                    name = if (frameIdx == 0) name else ""
                ),
                expectedResponse = null
            )

            val totalChunks = (data.size + CHUNK_SIZE - 1) / CHUNK_SIZE
            for (chunkIdx in 0 until totalChunks) {
                val offset = chunkIdx * CHUNK_SIZE
                val len = minOf(CHUNK_SIZE, data.size - offset)
                sendCommandAndWait("FTDA", buildFtdaPayload(transferId, data, offset, len), null)
            }

            sendCommandAndWait("FTDE", buildFtdePayload(transferId), null)

            onProgress((frameIdx + 1).toFloat() / frames.size)
        }

        // Unlock clip store
        sendCommand("LKCU", buildUInt16(slot.toUShort()))
    }

    // ── Packet building ──────────────────────────────────────────────────────

    private fun sendPacket(flags: Int, payload: ByteArray = ByteArray(0), forcePktId: Int? = null) {
        val totalLen = HEADER_SIZE + payload.size
        val pkt = ByteArray(totalLen)
        pkt[0] = ((flags shl 3) or ((totalLen shr 8) and 0x07)).toByte()
        pkt[1] = (totalLen and 0xFF).toByte()
        pkt[2] = ((sessionId shr 8) and 0xFF).toByte()
        pkt[3] = (sessionId and 0xFF).toByte()
        pkt[4] = ((lastRemotePacketId shr 8) and 0xFF).toByte()
        pkt[5] = (lastRemotePacketId and 0xFF).toByte()
        // bytes 6-9 = 0
        val pktId = forcePktId ?: localPacketId.incrementAndGet()
        pkt[10] = ((pktId shr 8) and 0xFF).toByte()
        pkt[11] = (pktId and 0xFF).toByte()
        if (payload.isNotEmpty()) System.arraycopy(payload, 0, pkt, HEADER_SIZE, payload.size)
        val addr = InetAddress.getByName(host)
        socket?.send(DatagramPacket(pkt, pkt.size, addr, port))
    }

    private fun sendAck() = sendPacket(FLAG_ACK)

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

    private fun sendCommand(name: String, data: ByteArray) {
        sendPacket(FLAG_ACK_REQUEST, buildCommandBytes(name, data))
    }

    /** Send a command and optionally wait for a named response command from ATEM. */
    private fun sendCommandAndWait(
        name: String,
        data: ByteArray,
        expectedResponse: String?,
        timeout: Long = CMD_TIMEOUT_MS.toLong()
    ) {
        sendCommand(name, data)
        if (expectedResponse == null) {
            // Wait for ATEM's packet-level ACK (flags bit = Ack)
            waitForAck(timeout)
        } else {
            // Wait for a specific command response
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

    /** Receive packets until we get an ACK (flags=0x80 in byte 0) or timeout. */
    private fun waitForAck(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            socket?.soTimeout = (deadline - System.currentTimeMillis()).coerceAtLeast(1).toInt()
            val pkt = receivePacket() ?: break
            if (pkt.size < HEADER_SIZE) continue
            updateRemotePacketId(pkt)
            val flags = (pkt[0].toInt() and 0xFF) shr 3
            if (flags and FLAG_ACK != 0) return   // got ACK
            // ACK any incoming data packets
            if (flags and FLAG_ACK_REQUEST != 0) sendAck()
        }
    }

    /** Receive packets until we find a specific command name, ACKing everything else. */
    private fun waitForCommand(cmdName: String, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            socket?.soTimeout = (deadline - System.currentTimeMillis()).coerceAtLeast(1).toInt()
            val pkt = receivePacket() ?: break
            if (pkt.size < HEADER_SIZE) continue
            updateRemotePacketId(pkt)
            val flags = (pkt[0].toInt() and 0xFF) shr 3
            if (flags and FLAG_ACK_REQUEST != 0) sendAck()
            if (findCommand(pkt, cmdName) != null) return
        }
    }

    /**
     * Receive and ACK all ATEM state-dump packets, collecting every command by name.
     * Uses a 2-second deadline; exits early if no packet arrives within the remaining time.
     */
    private fun collectState(sock: DatagramSocket): Map<String, List<ByteArray>> {
        val result = mutableMapOf<String, MutableList<ByteArray>>()
        val deadline = System.currentTimeMillis() + 500
        while (System.currentTimeMillis() < deadline) {
            sock.soTimeout = (deadline - System.currentTimeMillis()).coerceAtLeast(1).toInt()
            val pkt = receivePacket() ?: break
            if (pkt.size < HEADER_SIZE) continue
            updateRemotePacketId(pkt)
            val flags = (pkt[0].toInt() and 0xFF) shr 3
            if (flags and FLAG_ACK_REQUEST != 0) sendAck()
            parseAllCommands(pkt).forEach { (name, payload) ->
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

    private fun updateRemotePacketId(pkt: ByteArray) {
        if (pkt.size < HEADER_SIZE) return
        val id = ((pkt[10].toInt() and 0xFF) shl 8) or (pkt[11].toInt() and 0xFF)
        if (id > 0) lastRemotePacketId = id
    }

    private fun findCommand(packet: ByteArray, name: String): ByteArray? {
        var offset = HEADER_SIZE
        while (offset + 8 <= packet.size) {
            val cmdLen = ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
            if (cmdLen < 8 || offset + cmdLen > packet.size) break
            val cmdName = String(packet, offset + 4, 4, Charsets.US_ASCII)
            if (cmdName == name) return packet.copyOfRange(offset + 8, offset + cmdLen)
            offset += cmdLen
        }
        return null
    }

    // ── Payload builders ─────────────────────────────────────────────────────

    private fun buildUInt16(value: Int): ByteArray {
        val b = ByteArray(2)
        b[0] = ((value shr 8) and 0xFF).toByte()
        b[1] = (value and 0xFF).toByte()
        return b
    }

    private fun buildUInt16(value: UShort): ByteArray = buildUInt16(value.toInt())

    private fun buildFtcdPayload(
        transferId: Int,
        storeId: Int,
        slotIndex: Int,
        size: Int,
        name: String
    ): ByteArray {
        // FTCD payload (92 bytes):
        // bytes 0-1:  transferId (uint16)
        // bytes 2-3:  storeId (uint16)
        // bytes 4-7:  slotIndex / frameIndex (uint32)
        // bytes 8-11: total data size (uint32)
        // bytes 12-27: hash (16 bytes, zeros = no integrity check)
        // bytes 28-91: name (64 bytes, null-padded UTF-8)
        val buf = ByteArray(92)
        buf[0] = ((transferId shr 8) and 0xFF).toByte()
        buf[1] = (transferId and 0xFF).toByte()
        buf[2] = ((storeId shr 8) and 0xFF).toByte()
        buf[3] = (storeId and 0xFF).toByte()
        buf[4] = ((slotIndex shr 24) and 0xFF).toByte()
        buf[5] = ((slotIndex shr 16) and 0xFF).toByte()
        buf[6] = ((slotIndex shr 8) and 0xFF).toByte()
        buf[7] = (slotIndex and 0xFF).toByte()
        buf[8] = ((size shr 24) and 0xFF).toByte()
        buf[9] = ((size shr 16) and 0xFF).toByte()
        buf[10] = ((size shr 8) and 0xFF).toByte()
        buf[11] = (size and 0xFF).toByte()
        // bytes 12-27: hash (all zeros)
        val nameBytes = name.toByteArray(Charsets.UTF_8).copyOf(64)
        System.arraycopy(nameBytes, 0, buf, 28, minOf(64, nameBytes.size))
        return buf
    }

    private fun buildFtdaPayload(
        transferId: Int,
        data: ByteArray,
        offset: Int,
        length: Int
    ): ByteArray {
        // FTDA payload:
        // bytes 0-1: transferId (uint16)
        // bytes 2-3: chunk length (uint16)
        // bytes 4+:  chunk data
        val buf = ByteArray(4 + length)
        buf[0] = ((transferId shr 8) and 0xFF).toByte()
        buf[1] = (transferId and 0xFF).toByte()
        buf[2] = ((length shr 8) and 0xFF).toByte()
        buf[3] = (length and 0xFF).toByte()
        System.arraycopy(data, offset, buf, 4, length)
        return buf
    }

    private fun buildFtdePayload(transferId: Int): ByteArray {
        // FTDE payload:
        // bytes 0-1: transferId (uint16)
        // bytes 2-3: status (0 = success)
        val buf = ByteArray(4)
        buf[0] = ((transferId shr 8) and 0xFF).toByte()
        buf[1] = (transferId and 0xFF).toByte()
        return buf
    }

    // ── Pixel conversion ─────────────────────────────────────────────────────

    /**
     * Convert ARGB IntArray (from SkiaLayer screenshot after BGRA→ARGB conversion)
     * to a byte array in ARGB byte order for the ATEM media protocol.
     *
     * The DeckLinkComposeOutput pixel array stores pixels as ARGB Int:
     *   int = (A shl 24) or (R shl 16) or (G shl 8) or B
     * ATEM wants bytes in order: A, R, G, B (big-endian ARGB).
     */
    private fun argbToBytes(pixels: IntArray): ByteArray {
        val bytes = ByteArray(pixels.size * 4)
        for (i in pixels.indices) {
            val px = pixels[i]
            bytes[i * 4 + 0] = ((px shr 24) and 0xFF).toByte()  // A
            bytes[i * 4 + 1] = ((px shr 16) and 0xFF).toByte()  // R
            bytes[i * 4 + 2] = ((px shr 8) and 0xFF).toByte()   // G
            bytes[i * 4 + 3] = (px and 0xFF).toByte()            // B
        }
        return bytes
    }
}
