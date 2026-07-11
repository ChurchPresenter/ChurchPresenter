package presentation.engine.keynote

import io.airlift.compress.snappy.SnappyDecompressor
import java.io.ByteArrayOutputStream

/**
 * Reads the objects out of one `.iwa` file.
 *
 * Container format (see obriensp/iWorkFileFormat and keynote-parser's codec.py):
 *  - The file is a sequence of chunks: 1 header byte (0x00) + 3-byte little-endian length,
 *    followed by that many bytes of *raw* (unframed) snappy-compressed data.
 *  - The concatenated decompressed chunks form a stream of archives, each:
 *    varint(length of TSP.ArchiveInfo) + ArchiveInfo message + payload bytes, where payload is
 *    the concatenation of each MessageInfo's `length` bytes.
 *
 * TSP.ArchiveInfo: identifier=1, message_infos=2. TSP.MessageInfo: type=1, length=3
 * (protos/TSPArchiveMessages.proto).
 */
internal object IwaChunkReader {

    /** One archived object: [type] from the app's type registry, [payload] = first message bytes. */
    data class IwaObject(
        val identifier: Long,
        val type: Int,
        val payload: ByteArray
    )

    private const val ARCHIVE_INFO_IDENTIFIER = 1
    private const val ARCHIVE_INFO_MESSAGE_INFOS = 2
    private const val MESSAGE_INFO_TYPE = 1
    private const val MESSAGE_INFO_LENGTH = 3

    fun readObjects(iwaBytes: ByteArray): List<IwaObject> {
        val decompressed = decompressChunks(iwaBytes) ?: return emptyList()
        val objects = mutableListOf<IwaObject>()
        var pos = 0
        while (pos < decompressed.size) {
            val (infoLength, afterLen) = IwaMessage.readVarint(decompressed, pos) ?: break
            pos = afterLen
            val infoEnd = pos + infoLength.toInt()
            if (infoLength <= 0 || infoEnd > decompressed.size) break
            val info = IwaMessage.parse(decompressed, pos, infoLength.toInt()) ?: break
            pos = infoEnd
            val identifier = info.varint(ARCHIVE_INFO_IDENTIFIER) ?: 0L
            var firstType = 0
            var firstPayload = ByteArray(0)
            var isFirst = true
            for (messageInfo in info.messages(ARCHIVE_INFO_MESSAGE_INFOS)) {
                val length = (messageInfo.varint(MESSAGE_INFO_LENGTH) ?: 0L).toInt()
                if (length < 0 || pos + length > decompressed.size) return objects
                if (isFirst) {
                    firstType = (messageInfo.varint(MESSAGE_INFO_TYPE) ?: 0L).toInt()
                    firstPayload = decompressed.copyOfRange(pos, pos + length)
                    isFirst = false
                }
                pos += length
            }
            if (!isFirst) objects.add(IwaObject(identifier, firstType, firstPayload))
        }
        return objects
    }

    private fun decompressChunks(iwaBytes: ByteArray): ByteArray? {
        return try {
            val out = ByteArrayOutputStream(iwaBytes.size * 4)
            var pos = 0
            while (pos + 4 <= iwaBytes.size) {
                // Header: type byte (0x00 for snappy) + 3-byte LE length.
                val chunkType = iwaBytes[pos].toInt() and 0xFF
                val length = (iwaBytes[pos + 1].toInt() and 0xFF) or
                    ((iwaBytes[pos + 2].toInt() and 0xFF) shl 8) or
                    ((iwaBytes[pos + 3].toInt() and 0xFF) shl 16)
                pos += 4
                if (pos + length > iwaBytes.size) return null
                when (chunkType) {
                    0 -> {
                        val uncompressedLength = SnappyDecompressor.getUncompressedLength(iwaBytes, pos)
                        val buffer = ByteArray(uncompressedLength)
                        val written = SnappyDecompressor().decompress(iwaBytes, pos, length, buffer, 0, buffer.size)
                        out.write(buffer, 0, written)
                    }
                    // Some tooling writes uncompressed chunks with type 1.
                    1 -> out.write(iwaBytes, pos, length)
                    else -> return null
                }
                pos += length
            }
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}
