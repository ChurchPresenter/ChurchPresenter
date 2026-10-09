package org.churchpresenter.atem

// The bytes on the wire: packet and command framing, acks across the packet-id wrap, and the
// payloads each upload command carries.

internal const val HEADER_SIZE = 12

internal const val MAX_PACKET_ID = 0x8000   // ATEM wraps packet ids at 15 bits

internal const val BYTE_MASK = 0xFF

internal const val BYTE_BITS = 8

private const val U16_MASK = 0xFFFF

private const val U16_BITS = 16

internal const val U16_SIZE = 2

internal const val CMD_HEADER_SIZE = 8

internal const val CMD_NAME_OFFSET = 4

internal const val CMD_NAME_SIZE = 4

internal const val FTDE_CODE_RETRY = 1

internal const val DEFAULT_FPS = 30.0

internal const val UNKNOWN_VIDEO_MODE = "Unknown"

internal const val MPCS_MIN_SIZE = 68

private const val OFFSET_FTSD_FRAME_INDEX = 6

private const val OFFSET_FTSD_SIZE = 8

private const val OFFSET_FTSD_MODE = 12

private const val FTSD_MODE_WRITE = 1

private const val OFFSET_FTFD_NAME = 2

private const val FTFD_NAME_MAX = 64

private const val OFFSET_FTFD_MD5 = 194

private const val MD5_SIZE = 16

private const val FTDA_HEADER_SIZE = 4

private const val OFFSET_FTDA_LENGTH = 2

private const val SMPC_MASK_NAME_AND_FRAMES = 3

private const val OFFSET_SMPC_NAME = 2

private const val SMPC_NAME_MAX = 44

private const val OFFSET_SMPC_FRAME_COUNT = 66

internal fun AtemClient.u16(b: ByteArray, offset: Int): Int =
    ((b[offset].toInt() and BYTE_MASK) shl BYTE_BITS) or (b[offset + 1].toInt() and BYTE_MASK)

internal fun AtemClient.buildCommandBytes(name: String, data: ByteArray): ByteArray {
    val cmdLen = CMD_HEADER_SIZE + data.size
    val cmd = ByteArray(cmdLen)
    cmd[0] = ((cmdLen shr BYTE_BITS) and BYTE_MASK).toByte()
    cmd[1] = (cmdLen and BYTE_MASK).toByte()
    // bytes 2-3 = 0 (unused)
    val nameBytes = name.toByteArray(Charsets.US_ASCII)
    System.arraycopy(nameBytes, 0, cmd, CMD_NAME_OFFSET, minOf(CMD_NAME_SIZE, nameBytes.size))
    System.arraycopy(data, 0, cmd, CMD_HEADER_SIZE, data.size)
    return cmd
}

/** Whether [packetId] is acknowledged by an ack for [ackId], allowing for 15-bit wrap. */
internal fun AtemClient.isCoveredByAck(ackId: Int, packetId: Int): Boolean {
    val tolerance = MAX_PACKET_ID / 2
    val shortlyBefore = packetId < ackId && packetId + tolerance > ackId
    val shortlyAfter = packetId > ackId && packetId < ackId + tolerance
    val beforeWrap = packetId > ackId + tolerance
    return packetId == ackId || ((shortlyBefore || beforeWrap) && !shortlyAfter)
}

// ── Payload builders ─────────────────────────────────────────────────────

internal fun AtemClient.writeU16(buf: ByteArray, offset: Int, value: Int) {
    buf[offset] = ((value shr BYTE_BITS) and BYTE_MASK).toByte()
    buf[offset + 1] = (value and BYTE_MASK).toByte()
}

internal fun AtemClient.writeU32(buf: ByteArray, offset: Int, value: Int) {
    writeU16(buf, offset, (value ushr U16_BITS) and U16_MASK)
    writeU16(buf, offset + U16_SIZE, value and U16_MASK)
}

/** LOCK payload (4 bytes): storeId (uint16), locked (uint8), padding. */
internal fun AtemClient.buildLockPayload(storeId: Int, locked: Boolean): ByteArray {
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
internal fun AtemClient.buildUploadRequestPayload(
    transferId: Int,
    storeId: Int,
    frameIndex: Int,
    size: Int,
): ByteArray {
    val buf = ByteArray(16)
    writeU16(buf, 0, transferId)
    writeU16(buf, 2, storeId)
    writeU16(buf, OFFSET_FTSD_FRAME_INDEX, frameIndex)
    writeU32(buf, OFFSET_FTSD_SIZE, size)
    writeU16(buf, OFFSET_FTSD_MODE, FTSD_MODE_WRITE)
    return buf
}

/**
 * FTFD payload (212 bytes):
 *   bytes 0-1:    transferId (uint16)
 *   bytes 2-65:   name (64 bytes, null-padded UTF-8)
 *   bytes 66-193: description (128 bytes, unused)
 *   bytes 194-209: MD5 hash of the encoded data (16 bytes)
 */
internal fun AtemClient.buildFileDescriptionPayload(transferId: Int, name: String?, md5: ByteArray): ByteArray {
    val buf = ByteArray(212)
    writeU16(buf, 0, transferId)
    if (!name.isNullOrEmpty()) {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        System.arraycopy(nameBytes, 0, buf, OFFSET_FTFD_NAME, minOf(FTFD_NAME_MAX, nameBytes.size))
    }
    System.arraycopy(md5, 0, buf, OFFSET_FTFD_MD5, minOf(MD5_SIZE, md5.size))
    return buf
}

/** FTDa payload: transferId (uint16), chunk length (uint16), chunk data. */
internal fun AtemClient.buildDataChunkPayload(transferId: Int, data: ByteArray, offset: Int, length: Int): ByteArray {
    val buf = ByteArray(FTDA_HEADER_SIZE + length)
    writeU16(buf, 0, transferId)
    writeU16(buf, OFFSET_FTDA_LENGTH, length)
    System.arraycopy(data, offset, buf, FTDA_HEADER_SIZE, length)
    return buf
}

/**
 * SMPC payload (68 bytes): mask (uint8, 3 = name+frames), clip index (uint8),
 * name (44 bytes UTF-8 at offset 2), frame count (uint16 at offset 66).
 */
internal fun AtemClient.buildSetClipPayload(clipIndex: Int, name: String, frames: Int): ByteArray {
    val buf = ByteArray(68)
    buf[0] = SMPC_MASK_NAME_AND_FRAMES.toByte()
    buf[1] = clipIndex.toByte()
    val nameBytes = name.toByteArray(Charsets.UTF_8)
    System.arraycopy(nameBytes, 0, buf, OFFSET_SMPC_NAME, minOf(SMPC_NAME_MAX, nameBytes.size))
    writeU16(buf, OFFSET_SMPC_FRAME_COUNT, frames)
    return buf
}
