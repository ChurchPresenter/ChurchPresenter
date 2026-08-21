package converter.song

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
 * A real `.ews` schedule, damaged one field at a time.
 *
 * Every number in this format is a pointer or a length read straight out of the file, so a corrupt
 * one asks the reader to seek outside the file or to allocate whatever it says. Each guard below is
 * checked by breaking exactly the field it protects in a copy of a schedule that is otherwise
 * genuine — which is the only way to reach them, since a hand-built fixture would be proving the
 * test agrees with itself.
 *
 * A damaged *content block* is deliberately not fatal: the entry's title, author and copyright are
 * in the record itself, so the song still arrives, without its lyrics. Losing a service to one bad
 * pointer would be the worse outcome.
 */
class EasyWorshipScheduleEdgeCasesTest {

    private val temp: File = Files.createTempDirectory("ews-edges").toFile()

    /** Where the v5 header's entry count sits, and the entry stride two bytes past it. */
    private companion object {
        const val V5_HEADER = 56
        const val ENTRY_COUNT_TO_LENGTH = 4
        const val HEADER_TO_FIRST_ENTRY = 6
        const val ENTRY_CONTENT_POINTER = 800
        const val ENTRY_CONTENT_TYPE = 820
        const val ENTRY_COPYRIGHT = 358
        const val ENTRY_ADMINISTRATOR = 459
    }

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun sample(name: String): File =
        File(javaClass.classLoader.getResource("easyworship/$name")!!.toURI())

    /** A copy of the v5 sample with [patch] applied to its bytes. */
    private fun damaged(name: String, patch: (ByteArray, ByteBuffer) -> ByteArray): File {
        val bytes = sample("schedule-v5.ews").readBytes()
        val patched = patch(bytes, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN))
        return File(temp, name).apply { writeBytes(patched) }
    }

    private fun entryLength(bytes: ByteArray): Int =
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort(V5_HEADER + ENTRY_COUNT_TO_LENGTH).toInt()

    private fun firstEntry(): Int = V5_HEADER + HEADER_TO_FIRST_ENTRY

    // ── The header ────────────────────────────────────────────────────────────

    @Test
    fun `a schedule claiming a negative number of entries is refused`() {
        val file = damaged("negative-count.ews") { bytes, buffer ->
            buffer.putInt(V5_HEADER, -1); bytes
        }
        val error = assertFailsWith<IllegalArgumentException> { EasyWorshipSchedule.parse(file) }
        assertTrue(error.message!!.contains("readable schedule entries"), error.message!!)
    }

    @Test
    fun `a schedule whose entries have no length is refused`() {
        val file = damaged("zero-stride.ews") { bytes, buffer ->
            buffer.putShort(V5_HEADER + ENTRY_COUNT_TO_LENGTH, 0); bytes
        }
        assertFailsWith<IllegalArgumentException> { EasyWorshipSchedule.parse(file) }
    }

    @Test
    fun `entries the file is too short to hold are dropped, not read past the end`() {
        // The header still claims both songs; the file now holds only the first.
        val file = damaged("truncated-entries.ews") { bytes, _ ->
            bytes.copyOf(firstEntry() + entryLength(bytes) + 8)
        }
        assertEquals(listOf("Leeg"), EasyWorshipSchedule.parse(file).map { it.title })
    }

    @Test
    fun `a schedule of nothing but entries this reader has no use for yields no songs`() {
        // Content type 1 is a song; a service also holds videos, images and scripture.
        val file = damaged("no-songs.ews") { bytes, buffer ->
            val stride = entryLength(bytes)
            var entry = firstEntry()
            while (entry + stride <= bytes.size) {
                buffer.putInt(entry + ENTRY_CONTENT_TYPE, 3)
                entry += stride
            }
            bytes
        }
        assertTrue(EasyWorshipSchedule.parse(file).isEmpty())
    }

    // ── The content block ─────────────────────────────────────────────────────

    private fun assertSongsSurviveWithoutLyrics(file: File) {
        val songs = EasyWorshipSchedule.parse(file)
        assertEquals(listOf("Leeg", "Psalm 001"), songs.map { it.title })
        assertTrue(songs.all { it.sections.isEmpty() }, "expected no lyrics, got ${songs.map { it.sections.size }}")
    }

    @Test
    fun `a content pointer outside the file costs the lyrics, not the song`() {
        assertSongsSurviveWithoutLyrics(
            damaged("far-pointer.ews") { bytes, buffer ->
                val stride = entryLength(bytes)
                var entry = firstEntry()
                while (entry + stride <= bytes.size) {
                    buffer.putInt(entry + ENTRY_CONTENT_POINTER, bytes.size + 1000)
                    entry += stride
                }
                bytes
            }
        )
    }

    @Test
    fun `a negative content pointer costs the lyrics, not the song`() {
        assertSongsSurviveWithoutLyrics(
            damaged("negative-pointer.ews") { bytes, buffer ->
                val stride = entryLength(bytes)
                var entry = firstEntry()
                while (entry + stride <= bytes.size) {
                    buffer.putInt(entry + ENTRY_CONTENT_POINTER, -8)
                    entry += stride
                }
                bytes
            }
        )
    }

    @Test
    fun `a content block shorter than its own trailer costs the lyrics, not the song`() {
        assertSongsSurviveWithoutLyrics(
            damaged("short-block.ews") { bytes, buffer ->
                val stride = entryLength(bytes)
                var entry = firstEntry()
                while (entry + stride <= bytes.size) {
                    buffer.putInt(buffer.getInt(entry + ENTRY_CONTENT_POINTER), 4)
                    entry += stride
                }
                bytes
            }
        )
    }

    @Test
    fun `a content block running past the end of the file costs the lyrics, not the song`() {
        assertSongsSurviveWithoutLyrics(
            damaged("long-block.ews") { bytes, buffer ->
                val stride = entryLength(bytes)
                var entry = firstEntry()
                while (entry + stride <= bytes.size) {
                    buffer.putInt(buffer.getInt(entry + ENTRY_CONTENT_POINTER), bytes.size)
                    entry += stride
                }
                bytes
            }
        )
    }

    @Test
    fun `an implausible inflated length is refused rather than allocated`() {
        // The guard exists so a corrupt length cannot ask for a gigabyte-sized array.
        assertSongsSurviveWithoutLyrics(
            damaged("huge-inflated.ews") { bytes, buffer ->
                val stride = entryLength(bytes)
                var entry = firstEntry()
                while (entry + stride <= bytes.size) {
                    val pointer = buffer.getInt(entry + ENTRY_CONTENT_POINTER)
                    val length = buffer.getInt(pointer)
                    buffer.putInt(pointer + ENTRY_COUNT_TO_LENGTH + length - 6, Int.MAX_VALUE)
                    entry += stride
                }
                bytes
            }
        )
    }

    @Test
    fun `content that is not deflate data costs the lyrics, not the song`() {
        assertSongsSurviveWithoutLyrics(
            damaged("garbage-content.ews") { bytes, buffer ->
                val stride = entryLength(bytes)
                var entry = firstEntry()
                while (entry + stride <= bytes.size) {
                    val pointer = buffer.getInt(entry + ENTRY_CONTENT_POINTER)
                    val start = pointer + ENTRY_COUNT_TO_LENGTH
                    repeat(16) { bytes[start + it] = 0x7f }
                    entry += stride
                }
                bytes
            }
        )
    }

    // ── Copyright and administrator ───────────────────────────────────────────

    private fun writeField(bytes: ByteArray, at: Int, value: String, width: Int) {
        val encoded = value.toByteArray(Charsets.ISO_8859_1)
        encoded.copyInto(bytes, at, 0, minOf(encoded.size, width))
        for (i in encoded.size until width) bytes[at + i] = 0
    }

    private fun copyrightOf(name: String, copyright: String, administrator: String): String {
        val file = damaged(name) { bytes, _ ->
            val entry = firstEntry()
            writeField(bytes, entry + ENTRY_COPYRIGHT, copyright, 100)
            writeField(bytes, entry + ENTRY_ADMINISTRATOR, administrator, 50)
            bytes
        }
        return EasyWorshipSchedule.parse(file).first().copyright
    }

    @Test
    fun `an administrator is named alongside the copyright, or instead of it`() {
        assertEquals("© 1979 Author", copyrightOf("c1.ews", "© 1979 Author", ""))
        assertEquals("Administered by Publisher", copyrightOf("c2.ews", "", "Publisher"))
        assertEquals("© 1979 Author, administered by Publisher", copyrightOf("c3.ews", "© 1979 Author", "Publisher"))
        assertEquals("", copyrightOf("c4.ews", "", ""))
    }

    // ── Through the converter ─────────────────────────────────────────────────

    @Test
    fun `a schedule converts into one song file per song in it`() {
        val result = EasyWorshipConverter.convert(sample("schedule-v5.ews"), File(temp, "out"))

        assertTrue(result.errors.isEmpty(), result.errors.toString())
        assertEquals(listOf("Leeg.song", "Psalm 001.song"), result.outputFiles.map { it.name }.sorted())
    }
}
