package org.churchpresenter.atem

import org.churchpresenter.diagnostics.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

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
// TooManyFunctions: the byte builders and parsers live in AtemWire.kt, AtemStateParser.kt and
// AtemTransferProtocol.kt; what is left is the commands and the reliable-delivery layer under them,
// which share the socket and the in-flight packets and so stay one class. A wire protocol has as
// many functions as it has commands.
@Suppress("TooManyFunctions")
class AtemClient(
    val host: String,
    val port: Int = 9910,

    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val commandTimeoutMs: Long = CMD_TIMEOUT_MS.toLong(),
    private val keepAliveIntervalMs: Long = KEEPALIVE_INTERVAL_MS,
    private val silenceTimeoutMs: Long = CONNECT_TIMEOUT_MS.toLong(),
) {

    companion object {
        private const val BITS_PER_BYTE = 8
        private const val MACRO_RUN: Byte = 0
        private const val FLAG_ACK_REQUEST = 0x01
        private const val FLAG_HELLO = 0x02
        private const val FLAG_RETRANSMIT_REQUEST = 0x08
        private const val FLAG_ACK = 0x10
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val CMD_TIMEOUT_MS = 8000
        private const val MAX_RECV_BUF = 65536
        private const val MAX_TRANSFER_RETRIES = 40   // ATEM sends "retry" while busy, e.g. clearing the clip pool
        private const val RETRY_BACKOFF_MS = 250L
        private const val MAX_IN_FLIGHT = 2048
        private const val KEEPALIVE_INTERVAL_MS = 1500L   // drain+ack cadence; 3x margin under the 5s timeout
        private const val DRAIN_SOTIMEOUT_MS = 30         // short read window when draining the queue
        private const val TEMP_SESSION_ID = 0x53AB        // client's placeholder until the ATEM assigns the real one
        private const val SESSION_WAIT_MS = 1500L         // how long to wait for the real session id post-handshake
        private const val HELLO_RESEND_MS = 500           // resend an unanswered hello this often

        private const val FLAGS_SHIFT = 3
        private const val PACKET_LEN_HIGH_MASK = 0x07

        private const val OFFSET_SESSION_ID = 2
        private const val OFFSET_ACK_PACKET_ID = 4
        private const val OFFSET_RETRANSMIT_FROM = 6
        private const val OFFSET_PACKET_ID = 10

        private const val FTCD_MIN_SIZE = 10
        private const val OFFSET_FTCD_CHUNK_SIZE = 6
        private const val OFFSET_FTCD_CHUNK_COUNT = 8
        private const val CHUNK_SIZE_ALIGNMENT = 8

        private const val OFFSET_FTDE_CODE = 2

        private const val STATE_DUMP_TIMEOUT_MS = 2000
        private const val STATE_DUMP_IDLE_MS = 300

        private const val OFFSET_MPCS_FRAME_COUNT = 66

        private const val CMPC_PAYLOAD_SIZE = 4

        /** Client hello packet, verbatim from sofie-atem-connection (COMMAND_CONNECT_HELLO). */
        private val CONNECT_HELLO = byteArrayOf(
            0x10, 0x14, 0x53, 0xAB.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x3A, 0x00, 0x00,
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )

        /** Commands worth buffering when received while waiting for something else. */
        // MPCS is included so post-upload clip-store state updates (used to detect when the ATEM
        // has finished ingesting a clip) aren't dropped by the keepalive drain loop.
        private val INTERESTING_COMMANDS = setOf("FTCD", "FTDC", "FTDE", "FTUA", "LKOB", "LKST", "MPCS")

        /**
         * Cut an upstream keyer using a fresh short-lived connection. Used as the fallback
         * when the shared upload connection is busy, so a key cut never waits behind an upload.
         */
        suspend fun cutUpstreamKeyer(host: String, port: Int, mixEffect: Int, keyer: Int, onAir: Boolean) =
            withContext(Dispatchers.IO) {
                val c = AtemClient(host, port)
                try {
                    c.connect(collectState = false)
                    c.setUpstreamKeyerOnAir(mixEffect, keyer, onAir)
                } finally {
                    c.disconnect()
                }
            }

        /**
         * Cut the key [target] names using a fresh short-lived connection — the DSK-aware
         * counterpart of [cutUpstreamKeyer].
         */
        suspend fun cutKey(host: String, port: Int, target: AtemKey, onAir: Boolean) =
            withContext(Dispatchers.IO) {
                val c = AtemClient(host, port)
                try {
                    c.connect(collectState = false)
                    c.setKeyOnAir(target, onAir)
                } finally {
                    c.disconnect()
                }
            }

        /**
         * Lightweight reachability probe: one hello packet, true if anything answers.
         * The half-open session is never ACKed — the ATEM expires it on its own.
         * Cheap enough to poll (single ~20-byte UDP round-trip).
         */
        suspend fun isReachable(host: String, port: Int = 9910, timeoutMs: Int = 2000): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    DatagramSocket().use { sock ->
                        sock.soTimeout = timeoutMs
                        val addr = InetAddress.getByName(host)
                        sock.send(DatagramPacket(CONNECT_HELLO, CONNECT_HELLO.size, addr, port))
                        val buf = ByteArray(MAX_RECV_BUF)
                        sock.receive(DatagramPacket(buf, buf.size))
                        true
                    }
                } catch (_: Exception) {
                    false
                }
            }
    }

    private var socket: DatagramSocket? = null
    private val address: InetAddress by lazy { InetAddress.getByName(host) }
    private var sessionId: Int = 0
    private var lastReceivedPacketId: Int = 0
    private var nextSendPacketId: Int = 1
    private var helloReceived = false
    private val transferIdCounter = AtomicInteger(1)

    // Keepalive: a persistent connection runs a background drain+ack loop so the ATEM
    // never drops the idle session. opMutex serialises that loop against the synchronous
    // send/wait operations so they never touch the socket concurrently.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val opMutex = Mutex()
    private var keepAliveJob: Job? = null
    @Volatile private var lastReceivedAt: Long = 0L

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
     *
     * @param collectState drain and parse the ~2s state dump into [lastKnownState].
     *   Pass false for fast control connections (e.g. DSK switching) that only need
     *   the handshake — those complete in tens of milliseconds.
     * @param keepAlive keep the session alive with a background drain+ack loop. Use for a
     *   long-lived reused connection (AtemConnectionManager); leave false for one-shot use.
     */
    suspend fun connect(collectState: Boolean = true, keepAlive: Boolean = false) = withContext(Dispatchers.IO) {
        val sock = DatagramSocket()
        sock.soTimeout = connectTimeoutMs
        socket = sock

        var handshakeComplete = false
        try {
            sessionId = TEMP_SESSION_ID   // temporary client session id, replaced by the ATEM's
            lastReceivedPacketId = 0
            nextSendPacketId = 1
            helloReceived = false
            inFlight.clear()
            pendingCommands.clear()

            sendRaw(CONNECT_HELLO)

            // Wait for the hello response (receiveAndProcess ACKs it and flips the flag), sending the
            // hello again every HELLO_RESEND_MS until it comes. It is one UDP datagram: sent once, a
            // single loss -- a busy network, a full receive buffer on a loaded machine -- turned a
            // switcher that was there all along into "No response" after the whole connect timeout.
            sock.soTimeout = minOf(HELLO_RESEND_MS, connectTimeoutMs)
            val deadline = System.currentTimeMillis() + connectTimeoutMs
            while (!helloReceived) {
                if (System.currentTimeMillis() >= deadline) {
                    throw AtemProtocolException("No response from ATEM at $host:$port")
                }
                if (receiveAndProcess() == null && !helloReceived) sendRaw(CONNECT_HELLO)
            }
            sock.soTimeout = connectTimeoutMs

            // The hello response still carries our placeholder session id — the ATEM only
            // sends the REAL session id in the packets right after the handshake. We must
            // capture it before sending any command, or the ATEM ignores them (8s timeout).
            if (collectState) {
                val stateMap = collectState(sock)   // 2s drain also captures the real session
                lastKnownState = parseAtemState(stateMap)
            } else {
                awaitRealSession(SESSION_WAIT_MS)
            }

            if (keepAlive) startKeepAlive()
            handshakeComplete = true
        } finally {
            // Not `catch (e: Exception) { disconnect(); throw e }`, which named the exception only
            // to rethrow it untouched and covered nothing outside Exception. Any exit from the
            // handshake other than a completed one leaves a half-open socket, so the teardown runs
            // for all of them — cancellation and Error included — and the cause travels on
            // unchanged.
            if (!handshakeComplete) disconnect()
        }
    }

    /** True while the socket is open (keepalive nulls it when the ATEM goes silent). */
    fun isAlive(): Boolean = socket != null

    /** Packets sent but not yet acknowledged by the ATEM — the reliable layer's backlog. */
    internal fun inFlightCount(): Int = inFlight.size

    /**
     * Background loop that keeps a persistent session alive: every [KEEPALIVE_INTERVAL_MS]
     * it drains and ACKs whatever the ATEM has sent (the ATEM drops a client that stops
     * acking for ~5s). If the ATEM goes silent past [CONNECT_TIMEOUT_MS] the socket is
     * torn down so the next AtemConnectionManager.use() reconnects fresh.
     */
    private fun startKeepAlive() {
        lastReceivedAt = System.currentTimeMillis()
        keepAliveJob = scope.launch {
            while (isActive) {
                delay(keepAliveIntervalMs)
                opMutex.withLock {
                    if (socket == null) return@withLock
                    runCatching { drainAndAck() }
                    if (System.currentTimeMillis() - lastReceivedAt > silenceTimeoutMs) {
                        closeSocketOnly()
                    }
                }
                if (socket == null) break
            }
        }
    }

    /**
     * Read post-handshake packets until the ATEM's real session id arrives (it is NOT in the
     * hello response — that echoes our placeholder). Without this, commands sent on a
     * collectState=false connection go out with the wrong session id and are ignored.
     */
    private fun awaitRealSession(timeoutMs: Long) {
        val sock = socket ?: return
        val prev = sock.soTimeout
        sock.soTimeout = DRAIN_SOTIMEOUT_MS
        try {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (sessionId == TEMP_SESSION_ID && System.currentTimeMillis() < deadline) {
                receiveAndProcess()   // null on a read-window timeout — keep trying until deadline
            }
        } finally {
            runCatching { socket?.soTimeout = prev }
        }
    }

    /** Read and ACK every queued packet, then return (does not buffer command responses). */
    private fun drainAndAck() {
        val sock = socket ?: return
        val prev = sock.soTimeout
        sock.soTimeout = DRAIN_SOTIMEOUT_MS
        try {
            while (receiveAndProcess() != null) { /* ack + advance only */ }
        } finally {
            runCatching { socket?.soTimeout = prev }
        }
    }

    /** Close the socket without tearing down the keepalive scope (used by the liveness check). */
    private fun closeSocketOnly() {
        socket?.close()
        socket = null
        inFlight.clear()
        pendingCommands.clear()
    }

    /**
     * Cut an upstream keyer on or off air (CKOn — hard cut, no transition).
     * @param mixEffect 0-based M/E index
     * @param keyer     0-based upstream keyer index on that M/E
     */
    suspend fun setUpstreamKeyerOnAir(mixEffect: Int, keyer: Int, onAir: Boolean) = withContext(Dispatchers.IO) {
        opMutex.withLock {
            sendCommandAndWait(
                "CKOn",
                byteArrayOf(mixEffect.toByte(), keyer.toByte(), if (onAir) 1 else 0, 0),
                expectedResponse = null
            )
        }
    }

    /**
     * Cut a downstream keyer on or off air (CDsL — hard cut, no transition).
     * Downstream keyers are global (not per-M/E).
     * @param keyer 0-based downstream keyer index (DSK 1 = 0)
     */
    suspend fun setDownstreamKeyerOnAir(keyer: Int, onAir: Boolean) = withContext(Dispatchers.IO) {
        opMutex.withLock {
            sendCommandAndWait(
                "CDsL",
                byteArrayOf(keyer.toByte(), if (onAir) 1 else 0, 0, 0),
                expectedResponse = null
            )
        }
    }

    /**
     * Run the macro in slot [index] (MAct, action 0 -- run), 0-based as the switcher counts them.
     * A slot with no macro in it does nothing.
     */
    suspend fun runMacro(index: Int) = withContext(Dispatchers.IO) {
        opMutex.withLock {
            sendCommandAndWait(
                "MAct",
                byteArrayOf((index shr BITS_PER_BYTE).toByte(), index.toByte(), MACRO_RUN, 0),
                expectedResponse = null
            )
        }
    }

    /** Cut the key [target] names on or off air, downstream or upstream as it says. */
    suspend fun setKeyOnAir(target: AtemKey, onAir: Boolean) {
        if (target.useDsk) setDownstreamKeyerOnAir(target.keyer, onAir)
        else setUpstreamKeyerOnAir(target.mixEffect, target.keyer, onAir)
    }

    /** Disconnect and release the socket and the keepalive loop. The client is not reused after this. */
    fun disconnect() {
        keepAliveJob?.cancel()
        keepAliveJob = null
        scope.cancel()
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
            lastKnownState ?: AtemState(DEFAULT_FPS, UNKNOWN_VIDEO_MODE, emptyList(), emptyList())
        } finally {
            disconnect()
        }
    }

    /**
     * Upload one encoded frame (see [AtemFrameEncoder.encodeFrame]) to the still store.
     *
     * @param slot       0-based still store slot index
     * @param frame      RLE-encoded YUVA frame
     * @param name       display name for the still (max 64 chars)
     * @param onProgress called with 0f..1f as upload progresses
     */
    suspend fun uploadStillEncoded(
        slot: Int,
        frame: EncodedFrame,
        name: String,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        require(frame.data.isNotEmpty()) { "Nothing to upload — frame rendering produced no pixels" }
        val knownStills = lastKnownState?.stillSlots
        if (!knownStills.isNullOrEmpty()) {
            // 1-based in messages to match ATEM Software Control's numbering
            require(knownStills.any { it.index == slot }) {
                val available = knownStills.maxOf { it.index } + 1
                "Still slot ${slot + 1} does not exist on this ATEM (available: 1–$available)"
            }
        }

        opMutex.withLock {
            // Lock still store (storeId 0)
            sendCommandAndWait("LOCK", buildLockPayload(0, locked = true), "LKOB", timeout = commandTimeoutMs)
            try {
                CrashReporter.trace("atem.upload", "ATEM upload still") {
                    performTransfer(storeId = 0, frameIndex = slot, frame = frame, name = name, onProgress = onProgress)
                }
            } finally {
                // Unlock even if the transfer failed midway; best-effort so a dead socket
                // here can't mask the original failure
                runCatching { sendCommand("LOCK", buildLockPayload(0, locked = false)) }
            }
        }
    }

    /**
     * Upload an animated clip to the ATEM clip store, one frame at a time.
     *
     * Frames are pulled lazily through [nextFrame] so only a single frame is ever in
     * memory — a raw 1080p frame is ~8 MB, so buffering a whole clip would exhaust the heap.
     *
     * @param slot       0-based clip store slot index
     * @param frameCount total number of frames in the clip
     * @param name       clip name
     * @param nextFrame  returns the encoded frame for an index; called in order 0..frameCount-1
     * @param onProgress called with 0f..1f
     */
    suspend fun uploadClipEncoded(
        slot: Int,
        frameCount: Int,
        name: String,
        nextFrame: suspend (Int) -> EncodedFrame,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        require(frameCount > 0) { "Nothing to upload — clip rendering produced no frames" }
        // Locking a nonexistent store is silently ignored by the ATEM (LKOB never comes),
        // so validate the slot against the state dump up front for a clear error
        val knownClips = lastKnownState?.clipSlots
        if (!knownClips.isNullOrEmpty()) {
            // 1-based in messages to match ATEM Software Control's numbering
            require(knownClips.any { it.index == slot }) {
                "Clip slot ${slot + 1} does not exist on this ATEM (available: 1–${knownClips.maxOf { it.index } + 1})"
            }
        }
        val storeId = slot + 1   // clip stores are 1-based; store 0 is the still pool

        opMutex.withLock {
            // Drop any buffered clip-store state from before this upload so a later readiness wait
            // (awaitClipReady) only reacts to MPCS updates produced by this upload.
            pendingCommands.removeAll { it.first == "MPCS" }
            sendCommandAndWait(
                "LOCK",
                buildLockPayload(storeId, locked = true),
                "LKOB",
                timeout = commandTimeoutMs
            )
            try {
                // Clear the clip slot before uploading new frames
                sendCommandAndWait(
                    "CMPC",
                    ByteArray(CMPC_PAYLOAD_SIZE).also { it[0] = slot.toByte() },
                    expectedResponse = null
                )

                for (frameIdx in 0 until frameCount) {
                    val frame = nextFrame(frameIdx)
                    performTransfer(storeId = storeId, frameIndex = frameIdx, frame = frame, name = null) { p ->
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
    }

    /**
     * After a clip upload, wait until the ATEM reports the clip bank fully ingested. The clip-store
     * state command MPCS carries the bank's `isUsed` flag and current frame count (bytes 66-67);
     * the clip is ready once `isUsed && currentFrames >= [expectedFrames]`. Some firmware reports the
     * count climbing during ingest (→ smooth progress), others only once on completion — both work.
     *
     * @return true when the clip is reported ready; false on timeout (caller should proceed
     *   best-effort, i.e. key on anyway). [onProgress] receives 0f..1f as the frame count climbs.
     */
    suspend fun awaitClipReady(
        slot: Int,
        expectedFrames: Int,
        timeoutMs: Long = 15_000,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (expectedFrames <= 0) return@withContext true
        opMutex.withLock {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1)
                val payload = try {
                    waitForAnyCommand(setOf("MPCS"), remaining).second
                } catch (_: Exception) {
                    return@withLock false   // no MPCS within the timeout
                }
                if (payload.size >= MPCS_MIN_SIZE && (payload[0].toInt() and BYTE_MASK) == slot) {
                    val used = payload[1].toInt() == 1
                    val frames = u16(payload, OFFSET_MPCS_FRAME_COUNT)
                    onProgress((frames.toFloat() / expectedFrames).coerceIn(0f, 1f))
                    if (used && frames >= expectedFrames) return@withLock true
                }
            }
            false
        }
    }

    /**
     * Runs one FTSD upload transfer, honoring the ATEM's FTCD flow-control grants.
     * Sends the FTFD description on the first grant; finishes when FTDC arrives.
     * FTDE code 1 means the ATEM wants the transfer restarted (e.g. it was busy).
     *
     * FTSD's size field is the PRE-RLE length; the FTDa chunks carry the encoded bytes,
     * and a chunk must never end in the middle of an RLE block (header/count/pattern).
     */
    private fun performTransfer(
        storeId: Int,
        frameIndex: Int,
        frame: EncodedFrame,
        name: String?,
        onProgress: (Float) -> Unit
    ) {
        val data = frame.data
        val dataBuf = java.nio.ByteBuffer.wrap(data)
        val hash = MessageDigest.getInstance("MD5").digest(data)
        val transferId = transferIdCounter.getAndIncrement()
        val transfer = Transfer(transferId, data, dataBuf, onProgress)
        var bytesSent = 0
        var descriptionSent = false
        var retries = 0

        sendCommand("FTSD", buildUploadRequestPayload(transferId, storeId, frameIndex, frame.rawLen))

        while (true) {
            val (cmd, payload) = waitForAnyCommand(setOf("FTCD", "FTDC", "FTDE"), commandTimeoutMs)
            // Ignore messages that belong to other transfers
            val cmdTransferId = if (payload.size < 2) null
                else ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
            if (cmdTransferId != transferId) continue

            when (cmd) {
                "FTCD" -> {
                    if (!descriptionSent) {
                        sendCommand("FTFD", buildFileDescriptionPayload(transferId, name, hash))
                        descriptionSent = true
                    }
                    bytesSent = sendGrantedChunks(payload, transfer, bytesSent)
                }
                "FTDC" -> return
                "FTDE" -> {
                    val code = payload.getOrNull(OFFSET_FTDE_CODE)?.toInt()?.and(BYTE_MASK) ?: -1
                    if (code != FTDE_CODE_RETRY || retries >= MAX_TRANSFER_RETRIES) {
                        throw transferRejected(code, name, frameIndex, retries)
                    }
                    // Code 1 = "retry": the ATEM is busy (e.g. still clearing the clip pool after
                    // CMPC). Back off briefly, then restart the same transfer.
                    retries++
                    bytesSent = 0
                    descriptionSent = false
                    Thread.sleep(RETRY_BACKOFF_MS)
                    sendCommand("FTSD", buildUploadRequestPayload(transferId, storeId, frameIndex, frame.rawLen))
                }
            }
        }
    }

    /**
     * One in-progress media-pool transfer: what is being sent, and to which transfer id.
     *
     * These four were separate parameters of [sendGrantedChunks] alongside the grant itself, which
     * put it over detekt's six-parameter limit. They are not independent — [dataBuf] wraps [data],
     * and both are fixed for the whole transfer while only the grant changes per FTCD.
     */
    private class Transfer(
        val transferId: Int,
        val data: ByteArray,
        val dataBuf: java.nio.ByteBuffer,
        val onProgress: (Float) -> Unit,
    )

    /**
     * Sends as much of the transfer as this FTCD grant allows, and returns the new total sent.
     *
     * ATEM grants chunkCount chunks of chunkSize bytes (rounded down to 8).
     */
    private fun sendGrantedChunks(payload: ByteArray, transfer: Transfer, alreadySent: Int): Int {
        val chunkSize = if (payload.size < FTCD_MIN_SIZE) 0
            else (u16(payload, OFFSET_FTCD_CHUNK_SIZE) / CHUNK_SIZE_ALIGNMENT) * CHUNK_SIZE_ALIGNMENT
        val chunkCount = if (payload.size < FTCD_MIN_SIZE) 0 else u16(payload, OFFSET_FTCD_CHUNK_COUNT)
        val data = transfer.data
        var bytesSent = alreadySent
        var sent = 0
        while (chunkSize > 0 && sent < chunkCount && bytesSent < data.size) {
            val len = chunkLengthAt(transfer.dataBuf, data.size, bytesSent, chunkSize)
            sendCommand("FTDa", buildDataChunkPayload(transfer.transferId, data, bytesSent, len))
            bytesSent += len
            sent++
        }
        if (chunkSize > 0) transfer.onProgress(bytesSent.toFloat() / data.size)
        return bytesSent
    }

    // ── Packet building ──────────────────────────────────────────────────────

    /**
     * Write one packet to the switcher.
     *
     * Fails loudly on a closed socket rather than dropping the packet. This used to be
     * `socket?.send(...)`, which silently discarded every command sent after a disconnect —
     * the caller then waited out its full 8s command timeout and reported "ATEM did not respond
     * with LKOB", blaming the device for what is entirely a local lifecycle error. The
     * best-effort unlock in the upload paths already wraps its send in `runCatching`, so a
     * genuinely dead socket there still cannot mask the original failure.
     */
    private fun sendRaw(bytes: ByteArray) {
        val sock = socket ?: error(
            "ATEM connection to $host:$port is closed — connect() first (or the keepalive dropped it)"
        )
        sock.send(DatagramPacket(bytes, bytes.size, address, port))
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
        pkt[0] = ((FLAG_ACK_REQUEST shl FLAGS_SHIFT) or ((totalLen shr BYTE_BITS) and PACKET_LEN_HIGH_MASK)).toByte()
        pkt[1] = (totalLen and BYTE_MASK).toByte()
        writeU16(pkt, OFFSET_SESSION_ID, sessionId)
        writeU16(pkt, OFFSET_PACKET_ID, pktId)
        System.arraycopy(payload, 0, pkt, HEADER_SIZE, payload.size)
        inFlight[pktId] = pkt
        while (inFlight.size > MAX_IN_FLIGHT) inFlight.remove(inFlight.keys.first())
        sendRaw(pkt)
        return pktId
    }

    /** Pure ACK packet: AckReply flag, acked packet id at bytes 4-5, packet id 0. */
    private fun sendAck(ackId: Int) {
        val pkt = ByteArray(HEADER_SIZE)
        pkt[0] = (FLAG_ACK shl FLAGS_SHIFT).toByte()
        pkt[1] = HEADER_SIZE.toByte()
        writeU16(pkt, OFFSET_SESSION_ID, sessionId)
        writeU16(pkt, OFFSET_ACK_PACKET_ID, ackId)
        sendRaw(pkt)
    }

    /** Send a command and wait for either its packet-level ACK or a named response command. */
    private fun sendCommandAndWait(
        name: String,
        data: ByteArray,
        expectedResponse: String?,
        timeout: Long = commandTimeoutMs
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
        lastReceivedAt = System.currentTimeMillis()   // liveness signal for the keepalive loop
        if (pkt.size < HEADER_SIZE) return emptyList()
        val flags = (pkt[0].toInt() and BYTE_MASK) shr FLAGS_SHIFT
        // A second hello reply -- the switcher answering a resent hello after it had already
        // answered the first. Taken for real it would put the placeholder session id back and
        // restart the packet numbering in the middle of the state dump, so it changes nothing.
        val repeatedHello = flags and FLAG_HELLO != 0 && helloReceived
        // The ATEM assigns the real session id after the handshake — track it always
        if (!repeatedHello) sessionId = u16(pkt, OFFSET_SESSION_ID)
        val remoteId = u16(pkt, OFFSET_PACKET_ID)

        if (flags and FLAG_HELLO != 0) {
            if (!repeatedHello) {
                helloReceived = true
                lastReceivedPacketId = remoteId
                sendAck(remoteId)
            }
            return emptyList()
        }

        if (flags and FLAG_RETRANSMIT_REQUEST != 0) {
            retransmitFrom(u16(pkt, OFFSET_RETRANSMIT_FROM) % MAX_PACKET_ID)
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
            val ackId = u16(pkt, OFFSET_ACK_PACKET_ID)
            inFlight.keys.removeAll { isCoveredByAck(ackId, it) }
        }

        return commands
    }

    /** The packet ids still awaiting an ACK, oldest first — the buffer [retransmitFrom] resends from. */
    internal fun inFlightIds(): List<Int> = inFlight.keys.toList()

    /**
     * Resend buffered in-flight packets starting from [fromId] (ATEM retransmit request).
     *
     * `internal` rather than private so a test can drive it directly. Reaching it through
     * [FakeAtemSwitcher] would mean the fake emitting a retransmit request, and there is no capture
     * of one — writing those bytes by reading this file is what the fake's doc comment forbids.
     */
    internal fun retransmitFrom(fromId: Int) {
        if (!inFlight.containsKey(fromId)) {
            throw AtemProtocolException("ATEM requested retransmit of packet $fromId, which is no longer buffered")
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
            if (remaining <= 0) throw AtemProtocolException("ATEM did not acknowledge within ${timeoutMs}ms")
            socket?.soTimeout = remaining.coerceAtLeast(1).toInt()
            val commands = receiveAndProcess()
                ?: throw AtemProtocolException("ATEM did not acknowledge within ${timeoutMs}ms")
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
        throw AtemProtocolException("ATEM did not respond with ${names.joinToString("/")} within ${timeoutMs}ms")
    }

    /**
     * Receive and ACK all ATEM state-dump packets, collecting every command by name.
     * Uses a 2-second overall deadline; exits early once the device goes idle
     * (no packet for 300 ms), so a fast dump doesn't wait out the full deadline.
     */
    private fun collectState(sock: DatagramSocket): Map<String, List<ByteArray>> {
        val result = mutableMapOf<String, MutableList<ByteArray>>()
        val deadline = System.currentTimeMillis() + STATE_DUMP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            sock.soTimeout = (deadline - System.currentTimeMillis())
                .coerceAtLeast(1).toInt().coerceAtMost(STATE_DUMP_IDLE_MS)
            val commands = receiveAndProcess() ?: break
            commands.forEach { (name, payload) ->
                result.getOrPut(name) { mutableListOf() }.add(payload)
            }
        }
        return result
    }

}
