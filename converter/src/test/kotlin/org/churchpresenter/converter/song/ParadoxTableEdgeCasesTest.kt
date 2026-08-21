package org.churchpresenter.converter.song

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Paradox tables that are not the shape the happy path assumes: numeric columns, both kinds of
 * memo block, a non-Latin code page, and headers that describe something this reader must refuse.
 *
 * Like [ParadoxTableTest], every fixture is built here from one reading of the format, so these
 * pin the reader against its own understanding rather than against a table EasyWorship wrote. What
 * they are for is the arithmetic: a sign flip missed, a pointer read in the wrong endianness or a
 * packed-memo entry off by one all produce a song that is silently wrong rather than an error.
 */
class ParadoxTableEdgeCasesTest {

    private val temp: File = Files.createTempDirectory("paradox-edges").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private companion object {
        const val HEADER_SIZE = 0x800
        const val BLOCK_HEADER = 6
        const val TYPE_STRING = 1
        const val TYPE_INT16 = 3
        const val TYPE_INT32 = 4
        const val TYPE_LOGICAL = 9
        const val TYPE_MEMO = 0x0c
        const val TYPE_TIMESTAMP = 0x15
        const val MEMO_WIDTH = 13
    }

    private class Column(val name: String, val type: Int, val size: Int)

    /** What the reader itself computes: numbers are fixed width whatever the header's size byte says. */
    private fun widthOf(column: Column) = when (column.type) {
        TYPE_INT16 -> 2
        TYPE_INT32 -> 4
        TYPE_LOGICAL -> 1
        TYPE_TIMESTAMP -> 8
        else -> column.size
    }

    private class Memo(val text: String, val packed: Boolean = false, val subBlock: Int = 0)

    private class Builder {
        val bytes = mutableListOf<Byte>()
        val size get() = bytes.size
        fun byte(value: Int) = bytes.add(value.toByte())
        fun bytes(value: ByteArray) = bytes.addAll(value.toList())
        fun padTo(target: Int) { while (bytes.size < target) bytes.add(0) }
        fun toByteArray() = bytes.toByteArray()
    }

    /**
     * Writes `Songs.DB` and `Songs.MB` for [records], one value per column.
     *
     * A value is a String for a text column, an Int for a numeric one, or a [Memo] for the memo
     * column; anything else leaves the field's bytes zeroed.
     */
    private fun library(
        columns: List<Column>,
        records: List<List<Any?>>,
        codePage: Int = 1252,
        charset: Charset = Charsets.ISO_8859_1,
        headerSizeField: Int = HEADER_SIZE,
        blockSizeKb: Int = 1,
        recordSizeField: Int? = null,
        fieldCountField: Int? = null,
        firstBlock: Int = 1,
        folderName: String = "Data",
        writeMemoFile: Boolean = true,
    ): File {
        val recordSize = columns.sumOf { widthOf(it) }
        val memo = Builder()
        val rows = Builder()

        for (record in records) {
            columns.forEachIndexed { index, column ->
                when (val value = record.getOrNull(index)) {
                    is String -> {
                        val encoded = value.toByteArray(charset).take(column.size)
                        rows.bytes(encoded.toByteArray())
                        repeat(widthOf(column) - encoded.size) { rows.byte(0) }
                    }
                    is Int -> {
                        // Paradox flips the high bit so a byte-wise sort orders numbers correctly.
                        val flipped = ByteBuffer.allocate(widthOf(column)).order(ByteOrder.BIG_ENDIAN)
                        if (widthOf(column) == 2) flipped.putShort((value xor 0x8000).toShort())
                        else flipped.putInt((value.toLong() xor 0x80000000L).toInt())
                        rows.bytes(flipped.array())
                    }
                    is Memo -> rows.bytes(writeMemo(memo, value, charset))
                    else -> repeat(widthOf(column)) { rows.byte(0) }
                }
            }
        }

        val folder = File(temp, folderName).apply { mkdirs() }
        if (writeMemoFile) File(folder, "Songs.MB").writeBytes(memo.toByteArray())

        val header = ByteArray(HEADER_SIZE)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(0, (recordSizeField ?: recordSize).toShort())
        buffer.putShort(2, headerSizeField.toShort())
        header[5] = blockSizeKb.toByte()
        buffer.putShort(14, firstBlock.toShort())
        buffer.putShort(33, (fieldCountField ?: columns.size).toShort())
        buffer.putShort(106, codePage.toShort())
        columns.forEachIndexed { index, column ->
            header[120 + index * 2] = column.type.toByte()
            header[120 + index * 2 + 1] = column.size.toByte()
        }
        var at = 120 + columns.size * 2 + 4 + columns.size * 4 + 261
        for (column in columns) {
            val name = column.name.toByteArray(Charsets.ISO_8859_1)
            name.copyInto(header, at)
            at += name.size + 1
        }

        val block = ByteArray(1024 * blockSizeKb)
        val blockHeader = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)
        blockHeader.putShort(0, 0)
        blockHeader.putShort(4, ((records.size - 1) * recordSize).toShort())
        rows.toByteArray().copyInto(block, BLOCK_HEADER)

        File(folder, "Songs.DB").writeBytes(header + block)
        return folder
    }

    /** Appends [value] to the `.MB` and returns the pointer field that addresses it. */
    private fun writeMemo(memo: Builder, value: Memo, charset: Charset): ByteArray {
        val body = value.text.toByteArray(charset)
        // The pointer's low byte is the sub-block index, so a block has to start 256-byte aligned.
        memo.padTo((memo.size + 0xff) and 0xff.inv())
        val blockStart = memo.size
        if (value.packed) {
            memo.byte(3)
            // The entry table holds each memo's start as a count of 16-byte units from the block.
            val bodyStart = 64
            memo.padTo(blockStart + 12 + 5 * value.subBlock)
            memo.byte(bodyStart / 16)
            memo.padTo(blockStart + bodyStart)
            memo.bytes(body)
        } else {
            memo.byte(2)
            memo.padTo(blockStart + 9)
            memo.bytes(body)
        }

        val field = ByteBuffer.allocate(MEMO_WIDTH).order(ByteOrder.LITTLE_ENDIAN)
        field.position(MEMO_WIDTH - 10)
        field.putInt(blockStart + if (value.packed) value.subBlock else 0).putInt(body.size)
        return field.array()
    }

    private val words = Column("Words", TYPE_MEMO, MEMO_WIDTH)
    private val title = Column("Title", TYPE_STRING, 40)

    /** The one-song fixture most of these start from: a title, and its lyrics in a large memo. */
    private fun hymn(folderName: String, firstBlock: Int = 1, writeMemoFile: Boolean = true): File =
        library(
            listOf(title, words),
            listOf(listOf("Hymn", Memo("Verse 1\n\nline"))),
            firstBlock = firstBlock,
            folderName = folderName,
            writeMemoFile = writeMemoFile,
        )

    // ── Headers this reader must refuse ───────────────────────────────────────

    @Test
    fun `a table that is not there is reported by name`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ParadoxTable.parseSongs(File(temp, "Missing.DB"))
        }
        assertTrue(error.message!!.contains("Missing.DB"), error.message!!)
    }

    @Test
    fun `a header describing an impossible table is refused`() {
        val columns = listOf(title, words)
        val record = listOf(listOf("A", Memo("l")))
        val cases = mapOf(
            "wrong header size" to library(columns, record, headerSizeField = 0x400, folderName = "h1"),
            "no fields" to library(columns, record, fieldCountField = 0, folderName = "h2"),
            "no record length" to library(columns, record, recordSizeField = 0, folderName = "h3"),
            "block size out of range" to library(columns, record, blockSizeKb = 5, folderName = "h4"),
        )
        cases.forEach { (why, folder) ->
            assertFailsWith<IllegalArgumentException>(why) { ParadoxTable.parseSongs(File(folder, "Songs.DB")) }
        }
    }

    @Test
    fun `a block number pointing past the end of the file ends the walk`() {
        val folder = hymn("far", firstBlock = 99)
        assertTrue(ParadoxTable.parseSongs(File(folder, "Songs.DB")).isEmpty())
    }

    @Test
    fun `a record running past the end of the file is dropped rather than read`() {
        val folder = hymn("short")
        val file = File(folder, "Songs.DB")
        // Cut the block so the record the header still claims is only half present.
        file.writeBytes(file.readBytes().copyOf(HEADER_SIZE + BLOCK_HEADER + 10))
        assertTrue(ParadoxTable.parseSongs(file).isEmpty())
    }

    @Test
    fun `a record with neither a title nor lyrics is not a song`() {
        val folder = library(listOf(title, words), listOf(listOf("", Memo(""))), folderName = "blank")
        assertTrue(ParadoxTable.parseSongs(File(folder, "Songs.DB")).isEmpty())
    }

    // ── Numeric columns ───────────────────────────────────────────────────────

    @Test
    fun `a song number held as a 16-bit field is un-flipped back to what it was`() {
        val columns = listOf(title, Column("Song Number", TYPE_INT16, 2), words)
        val folder = library(columns, listOf(listOf("Hymn", 1234, Memo("Verse 1\n\nline"))), folderName = "int16")

        assertEquals("1234", ParadoxTable.parseSongs(File(folder, "Songs.DB")).single().ccli)
    }

    @Test
    fun `a song number held as a 32-bit field is un-flipped too`() {
        val columns = listOf(title, Column("Song Number", TYPE_INT32, 4), words)
        val folder = library(columns, listOf(listOf("Hymn", 7654321, Memo("Verse 1\n\nline"))), folderName = "int32")

        assertEquals("7654321", ParadoxTable.parseSongs(File(folder, "Songs.DB")).single().ccli)
    }

    @Test
    fun `columns of a type this reader has no use for are stepped over, not read`() {
        // A logical and a timestamp are fixed-width whatever the header's size byte says, so
        // getting either wrong shifts every field after it.
        val columns = listOf(
            Column("Reference", TYPE_LOGICAL, 1),
            Column("Modified", TYPE_TIMESTAMP, 8),
            title,
            words,
        )
        val folder = library(
            columns,
            listOf(listOf(null, null, "Hymn", Memo("Verse 1\n\nline"))),
            folderName = "othertypes",
        )

        assertEquals("Hymn", ParadoxTable.parseSongs(File(folder, "Songs.DB")).single().title)
    }

    // ── Copyright and administrator ───────────────────────────────────────────

    private fun copyrightOf(copyright: String, administrator: String, folderName: String): String {
        val columns = listOf(
            title,
            Column("Copyright", TYPE_STRING, 40),
            Column("Administrator", TYPE_STRING, 40),
            words,
        )
        val folder = library(
            columns,
            listOf(listOf("Hymn", copyright, administrator, Memo("Verse 1\n\nline"))),
            folderName = folderName,
        )
        return ParadoxTable.parseSongs(File(folder, "Songs.DB")).single().copyright
    }

    @Test
    fun `an administrator is named alongside the copyright, or instead of it`() {
        assertEquals("Public Domain", copyrightOf("Public Domain", "", "c1"))
        assertEquals("Administered by CCLI", copyrightOf("", "CCLI", "c2"))
        assertEquals("1984 Word Music, administered by CCLI", copyrightOf("1984 Word Music", "CCLI", "c3"))
        assertEquals("", copyrightOf("", "", "c4"))
    }

    // ── Memo blocks ───────────────────────────────────────────────────────────

    @Test
    fun `a small memo packed in with others is found through the sub-block table`() {
        val folder = library(
            listOf(title, words),
            listOf(listOf("Hymn", Memo("Verse 1\n\npacked lyric", packed = true, subBlock = 3))),
            folderName = "packed",
        )
        val song = ParadoxTable.parseSongs(File(folder, "Songs.DB")).single()
        assertEquals(listOf("packed lyric"), song.sections.single().lines)
    }

    @Test
    fun `a memo of no length reads as no lyrics`() {
        val folder = library(listOf(title, words), listOf(listOf("Hymn", Memo(""))), folderName = "zerolen")
        assertTrue(ParadoxTable.parseSongs(File(folder, "Songs.DB")).single().sections.isEmpty())
    }

    @Test
    fun `a memo pointer into a block of an unknown kind reads as no lyrics`() {
        val folder = hymn("badblock")
        val memoFile = File(folder, "Songs.MB")
        val bytes = memoFile.readBytes()
        bytes[0] = 9                                   // neither a large nor a packed block
        memoFile.writeBytes(bytes)

        assertTrue(ParadoxTable.parseSongs(File(folder, "Songs.DB")).single().sections.isEmpty())
    }

    @Test
    fun `a memo pointer past the end of the memo file reads as no lyrics`() {
        val folder = hymn("farmemo")
        val memoFile = File(folder, "Songs.MB")
        memoFile.writeBytes(memoFile.readBytes().copyOf(4))

        assertTrue(ParadoxTable.parseSongs(File(folder, "Songs.DB")).single().sections.isEmpty())
    }

    @Test
    fun `a memo field too small to hold a pointer reads as no lyrics`() {
        val folder = library(
            listOf(title, Column("Words", TYPE_MEMO, 4)),
            listOf(listOf("Hymn", null)),
            folderName = "tinymemo",
        )
        assertTrue(ParadoxTable.parseSongs(File(folder, "Songs.DB")).single().sections.isEmpty())
    }

    @Test
    fun `a table with no memo file beside it is reported rather than read empty`() {
        val folder = hymn("nomemo", writeMemoFile = false)
        val error = assertFailsWith<IllegalArgumentException> { ParadoxTable.parseSongs(File(folder, "Songs.DB")) }
        assertTrue(error.message!!.contains("Songs.MB"), error.message!!)
    }

    // ── Code pages ────────────────────────────────────────────────────────────

    @Test
    fun `a library written in a Cyrillic code page is decoded in it`() {
        val cyrillic = Charset.forName("windows-1251")
        val folder = library(
            listOf(title, words),
            listOf(listOf("Слава", Memo("Куплет 1\n\nСлава Богу"))),
            codePage = 855,
            charset = cyrillic,
            folderName = "cyrillic",
        )
        val song = ParadoxTable.parseSongs(File(folder, "Songs.DB")).single()
        assertEquals("Слава", song.title)
        assertEquals(listOf("Слава Богу"), song.sections.single().lines)
    }

    @Test
    fun `a code page nobody recognises falls back to Latin rather than failing`() {
        val folder = library(
            listOf(title, words),
            listOf(listOf("Hymn", Memo("Verse 1\n\nline"))),
            codePage = 65001,
            folderName = "oddcp",
        )
        assertEquals("Hymn", ParadoxTable.parseSongs(File(folder, "Songs.DB")).single().title)
    }

    // ── Where the memo file is looked for ─────────────────────────────────────

    @Test
    fun `the memo file is matched however it is capitalised`() {
        val folder = hymn("case")
        File(folder, "Songs.MB").renameTo(File(folder, "songs.mb"))

        assertEquals("Hymn", ParadoxTable.parseSongs(File(folder, "Songs.DB")).single().title)
    }

    @Test
    fun `a memo file belonging to another table is not used`() {
        val folder = hymn("othertable")
        File(folder, "Songs.MB").renameTo(File(folder, "Media.MB"))

        assertFailsWith<IllegalArgumentException> { ParadoxTable.parseSongs(File(folder, "Songs.DB")) }
    }
}
