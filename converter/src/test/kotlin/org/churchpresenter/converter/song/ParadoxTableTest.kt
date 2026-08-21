package org.churchpresenter.converter.song

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * EasyWorship 2007/2009 libraries, in Paradox table format.
 *
 * No sample is published anywhere, so the fixture is built here to the layout the reader expects.
 * That is worth stating plainly: it exercises the block chain, the field descriptors and the memo
 * indirection mechanically, but because both sides come from one reading of the format it cannot
 * confirm that a table written by EasyWorship itself is laid out this way. Only a real `Songs.DB`
 * can settle that, and none was available.
 *
 * The two things worth pinning are the ones that were wrong first time round and are invisible
 * without a fixture: the memo pointer is little-endian while the record's own numbers are
 * big-endian, and the large-memo body begins nine bytes into its block, not ten.
 */
class ParadoxTableTest {

    private val temp: File = Files.createTempDirectory("paradox-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private companion object {
        const val HEADER_SIZE = 0x800
        const val TYPE_STRING = 1
        const val TYPE_MEMO = 0x0c
        const val TITLE_WIDTH = 40
        const val AUTHOR_WIDTH = 30
        const val MEMO_WIDTH = 13
    }

    private class Column(val name: String, val type: Int, val width: Int)

    private val columns = listOf(
        Column("Title", TYPE_STRING, TITLE_WIDTH),
        Column("Author", TYPE_STRING, AUTHOR_WIDTH),
        Column("Words", TYPE_MEMO, MEMO_WIDTH),
    )

    /**
     * Writes a `Songs.DB` and the `Songs.MB` its `Words` column points into, one record per song.
     *
     * Memos go into the `.MB` as type-2 blocks — one memo to a block, its body nine bytes in — and
     * the record's memo field holds the block offset and the memo's length as little-endian
     * integers in its last ten bytes.
     */
    /** Returns the data folder holding the pair. */
    private fun library(songs: List<Triple<String, String, String>>): File {
        val recordSize = columns.sumOf { it.width }
        val memo = ByteArrayBuilder()
        val records = ByteArrayBuilder()

        for ((title, author, words) in songs) {
            records.fixedString(title, TITLE_WIDTH)
            records.fixedString(author, AUTHOR_WIDTH)

            val body = words.toByteArray(Charsets.ISO_8859_1)
            // Blocks are 16-byte aligned, and the low byte of a pointer is the sub-block index, so
            // a type-2 block has to start on a 256-byte boundary for that byte to read as zero.
            memo.padTo((memo.size + 0xff) and 0xff.inv())
            val blockStart = memo.size
            memo.byte(2)                       // block type: one large memo
            memo.padTo(blockStart + 9)
            memo.bytes(body)

            val field = ByteBuffer.allocate(MEMO_WIDTH).order(ByteOrder.LITTLE_ENDIAN)
            field.position(MEMO_WIDTH - 10)
            field.putInt(blockStart).putInt(body.size)
            records.bytes(field.array())
        }

        val folder = File(temp, "Data").apply { mkdirs() }
        File(folder, "Songs.MB").writeBytes(memo.toByteArray())

        val header = ByteArray(HEADER_SIZE)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(0, recordSize.toShort())
        buffer.putShort(2, HEADER_SIZE.toShort())
        header[5] = 1                                   // block size, in kilobytes
        buffer.putShort(14, 1)                          // first block
        buffer.putShort(33, columns.size.toShort())
        buffer.putShort(106, 1252)                      // code page
        columns.forEachIndexed { index, column ->
            header[120 + index * 2] = column.type.toByte()
            header[120 + index * 2 + 1] = column.width.toByte()
        }
        var at = 120 + columns.size * 2 + 4 + columns.size * 4 + 261
        for (column in columns) {
            val name = column.name.toByteArray(Charsets.ISO_8859_1)
            name.copyInto(header, at)
            at += name.size + 1                         // names are null-terminated
        }

        val block = ByteArray(1024)
        val blockHeader = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)
        blockHeader.putShort(0, 0)                                          // no block after this
        blockHeader.putShort(4, ((songs.size - 1) * recordSize).toShort())  // last record's offset
        records.toByteArray().copyInto(block, 6)

        File(folder, "Songs.DB").writeBytes(header + block)
        return folder
    }

    /** A growable byte buffer, so the fixture reads as the layout it is writing. */
    private class ByteArrayBuilder {
        private val bytes = mutableListOf<Byte>()
        val size get() = bytes.size
        fun byte(value: Int) = bytes.add(value.toByte())
        fun bytes(value: ByteArray) = bytes.addAll(value.toList())
        fun padTo(target: Int) { while (bytes.size < target) bytes.add(0) }
        fun fixedString(value: String, width: Int) {
            val encoded = value.toByteArray(Charsets.ISO_8859_1).take(width)
            bytes.addAll(encoded)
            repeat(width - encoded.size) { bytes.add(0) }
        }
        fun toByteArray() = bytes.toByteArray()
    }

    @Test
    fun `a table is read record by record, following each memo into the MB file`() {
        val folder = library(
            listOf(
                Triple("Amazing Grace", "John Newton", "Verse 1\n\nAmazing grace how sweet the sound"),
                Triple("Be Thou My Vision", "Dallan Forgaill", "Verse 1\n\nBe Thou my vision"),
            )
        )

        val songs = ParadoxTable.parseSongs(File(folder, "Songs.DB"))

        assertEquals(listOf("Amazing Grace", "Be Thou My Vision"), songs.map { it.title })
        assertEquals(listOf("John Newton", "Dallan Forgaill"), songs.map { it.author })
        assertEquals(listOf("Amazing grace how sweet the sound"), songs.first().sections.single().lines)
        assertEquals("Verse 1", songs.first().sections.single().label)
    }

    @Test
    fun `the whole library reads through the converter's own entry point`() {
        val folder = library(listOf(Triple("Hymn", "Anon", "Verse 1\n\nline one")))

        assertEquals(listOf("Hymn"), EasyWorshipConverter.parse(File(folder, "Songs.DB")).map { it.title })
    }

    @Test
    fun `a table without its memo file is reported rather than read with empty lyrics`() {
        val folder = library(listOf(Triple("Hymn", "Anon", "line")))
        File(folder, "Songs.MB").delete()

        val failure = assertFailsWith<IllegalArgumentException> {
            ParadoxTable.parseSongs(File(folder, "Songs.DB"))
        }
        assertTrue(failure.message!!.contains("Songs.MB"), failure.message!!)
    }

    @Test
    fun `a file that is not a Paradox table is rejected`() {
        val folder = File(temp, "junk").apply { mkdirs() }
        File(folder, "Songs.MB").writeBytes(ByteArray(16))
        val notATable = File(folder, "Songs.DB").apply { writeBytes(ByteArray(HEADER_SIZE + 1024)) }

        assertFailsWith<IllegalArgumentException> { ParadoxTable.parseSongs(notATable) }
    }
}
