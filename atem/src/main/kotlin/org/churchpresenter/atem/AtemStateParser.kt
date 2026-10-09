package org.churchpresenter.atem

// Reading the switcher's state dump: the commands in a packet, then video mode, keyers and the
// media pool.

private val VIDEO_MODES: Map<Int, Pair<String, Double>> = mapOf(
    0 to ("525i59.94 NTSC" to 30000.0 / 1001.0),
    2 to ("525i59.94 NTSC" to 30000.0 / 1001.0),
    1 to ("625i50 PAL" to 25.0),
    3 to ("625i50 PAL" to 25.0),
    4 to ("720p50" to 50.0),
    5 to ("720p59.94" to 60000.0 / 1001.0),
    6 to ("1080i50" to 50.0),
    7 to ("1080i59.94" to 60000.0 / 1001.0),
    8 to ("1080p23.98" to 24000.0 / 1001.0),
    9 to ("1080p24" to 24.0),
    10 to ("1080p25" to 25.0),
    11 to ("1080p29.97" to 30000.0 / 1001.0),
    12 to ("1080p50" to 50.0),
    13 to ("1080p59.94" to 60000.0 / 1001.0),
    14 to ("2160p23.98" to 24000.0 / 1001.0),
    15 to ("2160p24" to 24.0),
    16 to ("2160p25" to 25.0),
    17 to ("2160p29.97" to 30000.0 / 1001.0)
)

private const val OFFSET_TOP_DOWNSTREAM_KEYERS = 2

private const val OFFSET_MEC_KEYER_COUNT = 1

private const val MPFE_MIN_SIZE = 24

private const val OFFSET_MPFE_FRAME_INDEX = 2

private const val OFFSET_MPFE_IS_USED = 4

private const val OFFSET_MPFE_NAME_LEN = 23

private const val OFFSET_MPFE_NAME = 24

private const val OFFSET_MPCS_NAME = 2

private const val MPCS_NAME_END = 66

private const val MPSP_MIN_SIZE = 10

private const val OFFSET_MPSP_UNASSIGNED_FRAMES = 8

private const val OFFSET_MPL_CLIP_COUNT = 1

private const val DEFAULT_CLIP_BANK_COUNT = 4

/** Parse every command from a single UDP packet into (name, payload) pairs. */
internal fun AtemClient.parseAllCommands(packet: ByteArray): List<Pair<String, ByteArray>> {
    val out = mutableListOf<Pair<String, ByteArray>>()
    var offset = HEADER_SIZE
    while (offset + CMD_HEADER_SIZE <= packet.size) {
        val len = u16(packet, offset)
        if (len < CMD_HEADER_SIZE || offset + len > packet.size) break
        val name = String(packet, offset + CMD_NAME_OFFSET, CMD_NAME_SIZE, Charsets.US_ASCII)
        out.add(name to packet.copyOfRange(offset + CMD_HEADER_SIZE, offset + len))
        offset += len
    }
    return out
}

// ── State parsers ─────────────────────────────────────────────────────────

internal fun AtemClient.parseAtemState(m: Map<String, List<ByteArray>>): AtemState {
    val videoModeId = m["VidM"]?.firstOrNull()?.getOrNull(0)?.toInt()?.and(BYTE_MASK)
    val (mode, fps) = VIDEO_MODES[videoModeId] ?: (UNKNOWN_VIDEO_MODE to DEFAULT_FPS)
    val (clipMaxFrames, unassigned) = parseMediaPoolSettings(m)
    // _top topology byte 0 = number of M/E buses (program outputs); byte 2 = number of DSKs
    // (sofie-atem-connection TopologyCommand layout: ME, sources, downstreamKeyers, …)
    val topology = m["_top"]?.firstOrNull()
    val mixEffectCount = topology?.getOrNull(0)?.toInt()?.and(BYTE_MASK) ?: 0
    val downstreamKeyers = topology?.getOrNull(OFFSET_TOP_DOWNSTREAM_KEYERS)?.toInt()?.and(BYTE_MASK) ?: 0
    // _MeC: one per M/E — byte 0 = M/E index, byte 1 = upstream keyer count
    val keyersPerMe = if (mixEffectCount > 0) {
        val byMe = HashMap<Int, Int>()
        m["_MeC"]?.forEach { p ->
            if (p.size > OFFSET_MEC_KEYER_COUNT) {
                byMe[p[0].toInt() and BYTE_MASK] = p[OFFSET_MEC_KEYER_COUNT].toInt() and BYTE_MASK
            }
        }
        (0 until mixEffectCount).map { byMe[it] ?: 0 }
    } else emptyList()
    return AtemState(
        fps,
        mode,
        parseStillSlots(m),
        parseClipSlots(m),
        clipMaxFrames,
        unassigned,
        mixEffectCount,
        keyersPerMe,
        downstreamKeyers
    )
}

/**
 * MPfe (Media Pool Frame dEscription) payload layout - verified against hardware:
 *   byte  0:     media pool (0 = still store)
 *   bytes 2-3:   frame index (uint16)
 *   byte  4:     isUsed (uint8)
 *   bytes 5-20:  hash (16 bytes)
 *   byte  23:    name length (uint8)
 *   bytes 24+:   name (UTF-8)
 */
internal fun AtemClient.parseStillSlots(m: Map<String, List<ByteArray>>): List<AtemMediaSlot> =
    m["MPfe"]?.mapNotNull { p ->
        if (p.size < MPFE_MIN_SIZE || p[0].toInt() != 0) return@mapNotNull null   // still store only
        val idx  = u16(p, OFFSET_MPFE_FRAME_INDEX)
        val used = p[OFFSET_MPFE_IS_USED].toInt() == 1
        val nameLen = (p[OFFSET_MPFE_NAME_LEN].toInt() and BYTE_MASK).coerceAtMost(p.size - OFFSET_MPFE_NAME)
        val name = if (used && nameLen > 0) String(p, OFFSET_MPFE_NAME, nameLen, Charsets.UTF_8) else ""
        AtemMediaSlot(idx, name, used)
    }?.sortedBy { it.index } ?: emptyList()

/**
 * MPCS (Media Pool Clip deScription) payload layout - verified against hardware:
 *   byte  0:     clip bank index (uint8)
 *   byte  1:     isUsed (uint8)
 *   bytes 2-65:  name (null-terminated UTF-8; garbage when unused)
 *   bytes 66-67: current frame count (uint16)
 */
internal fun AtemClient.parseClipSlots(m: Map<String, List<ByteArray>>): List<AtemMediaSlot> =
    m["MPCS"]?.mapNotNull { p ->
        if (p.size < MPCS_MIN_SIZE) return@mapNotNull null
        val idx  = p[0].toInt() and BYTE_MASK
        val used = p[1].toInt() == 1
        val name = if (used) {
            val raw = p.copyOfRange(OFFSET_MPCS_NAME, MPCS_NAME_END)
            val end = raw.indexOfFirst { it.toInt() == 0 }.let { if (it < 0) raw.size else it }
            String(raw, 0, end, Charsets.UTF_8)
        } else ""
        AtemMediaSlot(idx, name, used)
    }?.sortedBy { it.index } ?: emptyList()

/**
 * MPSp (Media Pool Settings) payload layout - verified against hardware:
 *   bytes 0-7: max frames per clip bank (4 x uint16)
 *   bytes 8-9: unassigned frames (uint16)
 * Absent on pre-8.0 firmware - capacity stays unknown (empty list) in that case.
 */
internal fun AtemClient.parseMediaPoolSettings(m: Map<String, List<ByteArray>>): Pair<List<Int>, Int> {
    val p = m["MPSp"]?.firstOrNull() ?: return emptyList<Int>() to 0
    if (p.size < MPSP_MIN_SIZE) return emptyList<Int>() to 0
    val clipCount = m["_mpl"]?.firstOrNull()?.getOrNull(OFFSET_MPL_CLIP_COUNT)?.toInt()?.and(BYTE_MASK)
        ?: DEFAULT_CLIP_BANK_COUNT
    val maxFrames = (0 until minOf(DEFAULT_CLIP_BANK_COUNT, clipCount)).map { u16(p, it * U16_SIZE) }
    return maxFrames to u16(p, OFFSET_MPSP_UNASSIGNED_FRAMES)
}
