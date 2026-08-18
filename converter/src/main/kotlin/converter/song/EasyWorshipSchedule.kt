package converter.song

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.Inflater

/**
 * EasyWorship `.ews` schedule files.
 *
 * A fixed-layout binary format: a header naming the version, then one record per schedule item at a
 * stride the header gives, then the items' content at offsets the records point into. The layout
 * below is Gerrit Meinders' specification, derived by inspection and published with lithium-ews;
 * the offsets are quoted rather than named because that is how the format is documented and how it
 * is checked against a hex dump.
 *
 * Three versions ship, differing only in where the header's own fields sit — everything after that
 * is common. Only content type 1 is a song; a schedule holds videos, images and scripture too, and
 * those are stepped over.
 *
 * Text content is deflate-compressed RTF. Strings are fixed-width and null-padded, written in the
 * machine's ANSI code page rather than anything self-describing, so cp1252 is assumed and the
 * decoder is told not to fail on a byte that is not in it.
 */
internal object EasyWorshipSchedule {

    private const val FILE_TYPE_LENGTH = 38
    private const val ENTRY_COUNT_TO_LENGTH = 4
    private const val HEADER_TO_FIRST_ENTRY = 6

    /** Where the entry count sits, by the version string the file opens with. */
    private val HEADER_POSITIONS = mapOf("  5" to 56, "  3" to 48, "1.6" to 40)

    // Offsets within one schedule entry.
    private const val ENTRY_TITLE = 0
    private const val ENTRY_TITLE_LENGTH = 50
    private const val ENTRY_AUTHOR = 307
    private const val ENTRY_AUTHOR_LENGTH = 50
    private const val ENTRY_COPYRIGHT = 358
    private const val ENTRY_COPYRIGHT_LENGTH = 100
    private const val ENTRY_ADMINISTRATOR = 459
    private const val ENTRY_ADMINISTRATOR_LENGTH = 50
    private const val ENTRY_CONTENT_POINTER = 800
    private const val ENTRY_CONTENT_TYPE = 820
    private const val ENTRY_SONG_NUMBER = 1410
    private const val ENTRY_SONG_NUMBER_LENGTH = 10

    private const val CONTENT_TYPE_SONG = 1

    /**
     * The content block's trailer: an Adler-32 checksum, a 4-byte marker, the inflated length and a
     * 2-byte tail — 14 bytes past the compressed run, of which the last 10 follow it.
     */
    private const val TRAILER_AFTER_CONTENT = 10
    private const val INFLATED_LENGTH_BEFORE_END = 6

    /** Smallest file that could hold a header and a single entry. */
    private const val MINIMUM_SIZE = 892

    private val ANSI: Charset = runCatching { Charset.forName("windows-1252") }.getOrElse { Charsets.ISO_8859_1 }

    fun parse(file: File): List<EasyWorshipSong> {
        val bytes = file.readBytes()
        require(bytes.size >= MINIMUM_SIZE) { "${file.name} is too small to be an EasyWorship schedule" }

        val fileType = String(bytes, 0, FILE_TYPE_LENGTH, ANSI)
        val version = fileType.takeLast(3)
        val headerPosition = HEADER_POSITIONS[version]
            ?: throw IllegalArgumentException("Unknown EasyWorship schedule version in ${file.name}")

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val entryCount = buffer.getInt(headerPosition)
        val entryLength = buffer.getShort(headerPosition + ENTRY_COUNT_TO_LENGTH).toInt()
        require(entryCount >= 0 && entryLength > 0) { "${file.name} has no readable schedule entries" }

        val songs = mutableListOf<EasyWorshipSong>()
        var entry = headerPosition + HEADER_TO_FIRST_ENTRY
        repeat(entryCount) {
            if (entry + entryLength > bytes.size) return@repeat
            if (buffer.getInt(entry + ENTRY_CONTENT_TYPE) == CONTENT_TYPE_SONG) {
                val pointer = buffer.getInt(entry + ENTRY_CONTENT_POINTER)
                val rtf = runCatching { inflateContent(buffer, bytes, pointer) }.getOrDefault("")
                songs.add(
                    EasyWorshipConverter.songOf(
                        title = string(bytes, entry + ENTRY_TITLE, ENTRY_TITLE_LENGTH),
                        author = string(bytes, entry + ENTRY_AUTHOR, ENTRY_AUTHOR_LENGTH),
                        copyright = copyrightOf(
                            string(bytes, entry + ENTRY_COPYRIGHT, ENTRY_COPYRIGHT_LENGTH),
                            string(bytes, entry + ENTRY_ADMINISTRATOR, ENTRY_ADMINISTRATOR_LENGTH),
                        ),
                        ccli = string(bytes, entry + ENTRY_SONG_NUMBER, ENTRY_SONG_NUMBER_LENGTH),
                        rtf = rtf,
                    )
                )
            }
            entry += entryLength
        }
        return songs
    }

    /** "© 1979 Author, administered by Publisher" — the two fields EasyWorship keeps apart. */
    private fun copyrightOf(copyright: String, administrator: String): String = when {
        administrator.isEmpty() -> copyright
        copyright.isEmpty() -> "Administered by $administrator"
        else -> "$copyright, administered by $administrator"
    }

    private fun inflateContent(buffer: ByteBuffer, bytes: ByteArray, pointer: Int): String {
        require(pointer in 0 until bytes.size - ENTRY_COUNT_TO_LENGTH) { "Content pointer out of range" }
        val length = buffer.getInt(pointer)
        val start = pointer + ENTRY_COUNT_TO_LENGTH
        val end = start + length - TRAILER_AFTER_CONTENT
        require(length > TRAILER_AFTER_CONTENT && end <= bytes.size) { "Content block out of range" }
        val inflatedLength = buffer.getInt(start + length - INFLATED_LENGTH_BEFORE_END)
        require(inflatedLength in 1..MAX_INFLATED_LENGTH) { "Implausible content length" }

        val inflater = Inflater()
        try {
            inflater.setInput(bytes, start, end - start)
            val output = ByteArray(inflatedLength)
            val produced = inflater.inflate(output)
            return String(output, 0, produced, ANSI)
        } finally {
            inflater.end()
        }
    }

    /** A fixed-width, null-padded string in the file's ANSI code page. */
    private fun string(bytes: ByteArray, offset: Int, length: Int): String {
        if (offset < 0 || offset + length > bytes.size) return ""
        val end = (offset until offset + length).firstOrNull { bytes[it] == 0.toByte() } ?: (offset + length)
        return String(bytes, offset, end - offset, ANSI).trim()
    }

    /** A guard against a corrupt length field asking for a gigabyte-sized array. */
    private const val MAX_INFLATED_LENGTH = 64 * 1024 * 1024
}
