package converter.song

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * The Paradox table that EasyWorship 2007 and 2009 keep their song library in.
 *
 * `Songs.DB` is a Paradox 7 table and `Songs.MB` its memo file. Neither is optional: the table
 * holds fixed-width fields only, so a song's lyrics — unbounded — are not in it. What the `Words`
 * column actually stores is a *pointer* into the `.MB`, and following that pointer is most of the
 * work here.
 *
 * The layout, and the two ways a memo can be stored:
 *
 *  - The table header names each field's type and width. Records are packed at a fixed stride into
 *    1–4 KB blocks, chained through a "next block" field, and the record count is per block — there
 *    is no total anywhere, so the chain is walked to find out how many songs there are.
 *  - Numbers are stored with their **high bit flipped**, so that a plain byte-wise sort orders them
 *    correctly. They have to be flipped back or every number comes out astronomically wrong.
 *  - A memo pointer's low byte is a *sub-block index* and the rest is the offset of the block. A
 *    type-2 block holds one large memo starting at a fixed offset; a type-3 block packs up to 64
 *    small ones, and the sub-block index selects an entry in its table of 16-byte-aligned starts.
 */
internal object ParadoxTable {

    private const val HEADER_SIZE = 0x800
    private const val BLOCK_UNIT = 1024
    private const val CODE_PAGE_OFFSET = 106
    private const val FIELD_INFO_OFFSET = 120
    private const val FIELD_NAME_PADDING = 261

    private const val TYPE_STRING = 1
    private const val TYPE_INT16 = 3
    private const val TYPE_INT32 = 4
    private const val TYPE_LOGICAL = 9
    private const val TYPE_MEMO = 0x0c
    private const val TYPE_BLOB = 0x0d
    private const val TYPE_TIMESTAMP = 0x15

    private const val INT16_SIGN_FLIP = 0x8000
    private const val INT32_SIGN_FLIP = 0x80000000L

    /** A memo pointer's trailer: the block reference and the memo's length, in its last 10 bytes. */
    private const val MEMO_POINTER_FROM_END = 10
    private const val MEMO_SUB_BLOCK_MASK = 0xff
    private const val MEMO_LARGE_BLOCK = 2
    private const val MEMO_PACKED_BLOCK = 3
    private const val MEMO_LARGE_HEADER = 8
    private const val MEMO_PACKED_TABLE = 12
    private const val MEMO_PACKED_ENTRY = 5
    private const val MEMO_PACKED_MAX = 63
    private const val MEMO_ALIGNMENT = 16

    /**
     * Code pages EasyWorship wrote non-English libraries in. The mapping for everything but 852 was
     * inferred rather than observed — see OpenLP's importer, which carries the same caveat.
     */
    private val CODE_PAGES = mapOf(
        852 to "windows-1250", 737 to "windows-1253", 775 to "windows-1257", 855 to "windows-1251",
        857 to "windows-1254", 866 to "windows-1251", 869 to "windows-1253", 862 to "windows-1255",
        874 to "windows-874",
    )

    private data class Field(val name: String, val type: Int, val size: Int, val offset: Int)

    fun parseSongs(songsFile: File): List<EasyWorshipSong> {
        require(songsFile.isFile) { "${songsFile.name} does not exist" }
        val memoFile = memoFileFor(songsFile)
            ?: throw IllegalArgumentException("Songs.MB must sit beside ${songsFile.name}")
        val table = songsFile.readBytes()
        require(table.size >= HEADER_SIZE) { "${songsFile.name} is not a Paradox table" }

        val buffer = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)
        // Fixed offsets in the table header: record size, header size, a pad, the block size in
        // kilobytes, eight more pad bytes, the first block's number, seventeen more, the field count.
        val recordSize = buffer.getShort(0).toInt()
        val headerSize = buffer.getShort(2).toInt()
        val blockSize = table[5].toInt()
        val firstBlock = buffer.getShort(14).toInt()
        val fieldCount = buffer.getShort(33).toInt()
        require(headerSize == HEADER_SIZE && blockSize in 1..4 && recordSize > 0 && fieldCount > 0) {
            "${songsFile.name} is not a valid EasyWorship database"
        }

        val charset = charsetFor(buffer.getShort(CODE_PAGE_OFFSET).toInt())
        val fields = readFields(table, buffer, fieldCount)
        val memo = memoFile.readBytes()

        val songs = mutableListOf<EasyWorshipSong>()
        var block = firstBlock
        val visited = mutableSetOf<Int>()
        while (block != 0 && visited.add(block)) {
            val blockStart = headerSize + (block - 1) * BLOCK_UNIT * blockSize
            if (blockStart < 0 || blockStart + HEADER_OF_BLOCK > table.size) break
            block = buffer.getShort(blockStart).toInt()
            // The field is the offset of the last record's end, not a count; it divides out.
            val usedBytes = buffer.getShort(blockStart + 4).toInt()
            val records = (usedBytes + recordSize) / recordSize
            for (index in 0 until records) {
                val record = blockStart + HEADER_OF_BLOCK + index * recordSize
                if (record + recordSize > table.size) break
                songs.add(songAt(table, record, fields, charset, memo))
            }
        }
        return songs.filter { it.title.isNotEmpty() || it.sections.isNotEmpty() }
    }

    private const val HEADER_OF_BLOCK = 6

    private fun memoFileFor(songsFile: File): File? {
        val base = songsFile.nameWithoutExtension
        return songsFile.parentFile?.listFiles()
            ?.firstOrNull { it.isFile && it.nameWithoutExtension.equals(base, true) && it.extension.equals("MB", true) }
    }

    /**
     * The field descriptors, then the names, then each field's offset into a record.
     *
     * The header stores a type/size pair per field, then a run of pointers and a fixed pad, then the
     * names as null-terminated strings. The record offsets are the running sum of the widths, which
     * is why the two have to be read in one pass.
     */
    private fun readFields(table: ByteArray, buffer: ByteBuffer, fieldCount: Int): List<Field> {
        val namesStart = FIELD_INFO_OFFSET + fieldCount * 2 + 4 + fieldCount * 4 + FIELD_NAME_PADDING
        val names = ArrayList<String>(fieldCount)
        var cursor = namesStart
        repeat(fieldCount) {
            val end = (cursor until table.size).firstOrNull { table[it] == 0.toByte() } ?: table.size
            names.add(String(table, cursor, end - cursor, Charsets.ISO_8859_1))
            cursor = end + 1
        }

        var offset = 0
        return List(fieldCount) { index ->
            val type = table[FIELD_INFO_OFFSET + index * 2].toInt() and 0xff
            val size = table[FIELD_INFO_OFFSET + index * 2 + 1].toInt() and 0xff
            val width = widthOf(type, size)
            Field(names.getOrElse(index) { "" }, type, size, offset).also { offset += width }
        }.also { _ -> buffer.rewind() }
    }

    /** The bytes a field of [type] occupies in a record — fixed for numbers, [size] for the rest. */
    private fun widthOf(type: Int, size: Int): Int = when (type) {
        TYPE_INT16 -> Short.SIZE_BYTES
        TYPE_INT32 -> Int.SIZE_BYTES
        TYPE_LOGICAL -> 1
        TYPE_TIMESTAMP -> Long.SIZE_BYTES
        else -> size
    }

    private fun songAt(
        table: ByteArray,
        record: Int,
        fields: List<Field>,
        charset: Charset,
        memo: ByteArray,
    ): EasyWorshipSong {
        fun text(name: String): String {
            val field = fields.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return ""
            return when (field.type) {
                TYPE_STRING -> string(table, record + field.offset, field.size, charset)
                TYPE_INT16 -> (readShort(table, record + field.offset) xor INT16_SIGN_FLIP).toString()
                TYPE_INT32 -> (readInt(table, record + field.offset) xor INT32_SIGN_FLIP).toString()
                TYPE_MEMO, TYPE_BLOB -> readMemo(table, record + field.offset, field.size, memo, charset)
                else -> ""
            }
        }

        val copyright = text("Copyright")
        val administrator = text("Administrator")
        return EasyWorshipConverter.songOf(
            title = text("Title"),
            author = text("Author"),
            copyright = when {
                administrator.isEmpty() -> copyright
                copyright.isEmpty() -> "Administered by $administrator"
                else -> "$copyright, administered by $administrator"
            },
            ccli = text("Song Number"),
            rtf = text("Words"),
        )
    }

    /** Follows a memo pointer into the `.MB` file and returns what it addresses. */
    private fun readMemo(table: ByteArray, offset: Int, size: Int, memo: ByteArray, charset: Charset): String {
        if (offset + size > table.size || size < MEMO_POINTER_FROM_END) return ""
        val pointerAt = offset + size - MEMO_POINTER_FROM_END
        // Little-endian, unlike the record's own numeric fields — the two disagree within the same
        // file, so reading the pointer the way the numbers are read sends it to a random offset.
        val reference = readIntLittleEndian(table, pointerAt)
        val length = readIntLittleEndian(table, pointerAt + Int.SIZE_BYTES).toInt()
        if (length <= 0) return ""

        val subBlock = (reference and MEMO_SUB_BLOCK_MASK.toLong()).toInt()
        val blockStart = (reference and MEMO_SUB_BLOCK_MASK.toLong().inv()).toInt()
        if (blockStart < 0 || blockStart >= memo.size) return ""

        val start = when (memo[blockStart].toInt()) {
            MEMO_LARGE_BLOCK -> blockStart + 1 + MEMO_LARGE_HEADER
            MEMO_PACKED_BLOCK -> {
                if (subBlock > MEMO_PACKED_MAX) return ""
                val entry = blockStart + 1 + MEMO_PACKED_TABLE - 1 + MEMO_PACKED_ENTRY * subBlock
                if (entry >= memo.size) return ""
                blockStart + (memo[entry].toInt() and 0xff) * MEMO_ALIGNMENT
            }
            else -> return ""
        }
        if (start < 0 || start >= memo.size) return ""
        return String(memo, start, length.coerceAtMost(memo.size - start), charset)
    }

    private fun string(table: ByteArray, offset: Int, size: Int, charset: Charset): String {
        if (offset < 0 || offset + size > table.size) return ""
        val end = (offset until offset + size).firstOrNull { table[it] == 0.toByte() } ?: (offset + size)
        return String(table, offset, end - offset, charset).trim()
    }

    /** Paradox stores its numbers big-endian, unlike its header fields. */
    private fun readShort(table: ByteArray, offset: Int): Int =
        if (offset + Short.SIZE_BYTES > table.size) 0
        else ByteBuffer.wrap(table, offset, Short.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xffff

    private fun readInt(table: ByteArray, offset: Int): Long =
        if (offset + Int.SIZE_BYTES > table.size) 0
        else ByteBuffer.wrap(table, offset, Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffffffffL

    private fun readIntLittleEndian(table: ByteArray, offset: Int): Long =
        if (offset + Int.SIZE_BYTES > table.size) 0
        else ByteBuffer.wrap(table, offset, Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL

    private fun charsetFor(codePage: Int): Charset {
        val name = CODE_PAGES[codePage] ?: "windows-1252"
        return runCatching { Charset.forName(name) }.getOrElse { Charsets.ISO_8859_1 }
    }
}
