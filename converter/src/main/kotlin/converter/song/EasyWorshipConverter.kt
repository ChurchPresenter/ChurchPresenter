package converter.song

import converter.library.RtfText
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.sql.Connection
import java.sql.ResultSet
import java.sql.DriverManager
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

data class EasyWorshipSong(
    val title: String,
    val author: String,
    val copyright: String,
    val ccli: String,
    val sections: List<SongSection>,
)

/**
 * EasyWorship libraries and schedules, in all four of the shapes the product has shipped.
 *
 * "EasyWorship format" names four unrelated files, and a church upgrading across a decade will have
 * more than one of them:
 *
 *  - **EasyWorship 6/7 library** — `Songs.db` beside `SongWords.db`, both ordinary SQLite despite
 *    the extension. The lyrics live in the *second* file, so pointing at `Songs.db` alone finds
 *    every title and no words; both are opened together and a missing sibling is reported rather
 *    than silently producing empty songs.
 *  - **`.ewsx` schedule** — a zip holding one `main.db`. See [readZipEntryIgnoringChecksum] for why
 *    it cannot be read with `ZipFile`.
 *  - **`.ews` schedule** — a binary file of fixed-size records; see [EasyWorshipSchedule].
 *  - **EasyWorship 2007/2009 library** — `Songs.DB` with a `Songs.MB` memo file, in Paradox table
 *    format; see [ParadoxTable].
 *
 * In every one of them the lyrics are RTF and the sections are separated by a blank line, with the
 * section's name as the first line of its block.
 */
// Split into one small function per step, which is what keeps the readers below within the
// complexity and nesting limits. Splitting the object itself would scatter one file format across
// several files instead.
@Suppress("TooManyFunctions")
object EasyWorshipConverter {

    private const val SQLITE_HEADER = "SQLite format 3"

    /**
     * Column positions in the two song queries below, which select the same five values in the same
     * order out of tables that name them differently.
     */
    private const val COLUMN_ID = 1
    private const val COLUMN_TITLE = 2
    private const val COLUMN_AUTHOR = 3
    private const val COLUMN_COPYRIGHT = 4
    private const val COLUMN_NUMBER = 5

    /** One row of [SERVICE_SONG_QUERY], read before its slides are fetched by [id]. */
    private data class ServiceSong(
        val id: String,
        val title: String,
        val author: String,
        val copyright: String,
        val number: String,
    )
    private const val SONGS_DATABASE = "Songs.db"
    private const val WORDS_DATABASE = "SongWords.db"
    private const val SERVICE_DATABASE = "main.db"

    /** `presentation_type` 6 is a song; `element_type` 6 with `element_style_type` 4 is its text. */
    private const val SERVICE_SONG_QUERY =
        "SELECT rowid, title, author, copyright, reference_number FROM presentation " +
            "WHERE presentation_type = 6 ORDER BY rowid"
    private const val SERVICE_SLIDE_QUERY =
        "SELECT rt.rtf FROM element AS e " +
            "JOIN slide AS s ON e.slide_id = s.rowid " +
            "JOIN resource_text AS rt ON rt.resource_id = e.foreground_resource_id " +
            "WHERE e.element_type = 6 AND e.element_style_type = 4 AND s.presentation_id = ? " +
            "ORDER BY s.order_index"

    fun parse(input: File): List<EasyWorshipSong> = when {
        input.isDirectory -> parseLibrary(libraryFile(input))
        input.extension.equals("ewsx", ignoreCase = true) -> parseService(input)
        input.extension.equals("ews", ignoreCase = true) -> EasyWorshipSchedule.parse(input)
        isSqlite(input) -> parseLibrary(input)
        else -> ParadoxTable.parseSongs(input)
    }

    fun convert(input: File, outputDir: File): SongConversionResult {
        val songs = runCatching { parse(input) }.getOrElse { error ->
            return SongConversionResult(emptyList(), listOf("${input.name}: ${error.message}"))
        }
        if (songs.isEmpty()) return SongConversionResult(emptyList(), listOf("No songs in ${input.name}"))
        val taken = mutableSetOf<String>()
        val written = songs.map { song ->
            val parsed = ParsedSong(song.title, song.author, song.copyright, sections = song.sections)
            SongOutput.write(outputDir, parsed, taken)
        }
        return SongConversionResult(written)
    }

    fun isSqlite(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.length) return false
        val header = ByteArray(SQLITE_HEADER.length)
        file.inputStream().use { it.read(header) }
        return String(header, Charsets.US_ASCII) == SQLITE_HEADER
    }

    /** The `Songs.db`/`Songs.DB` inside a chosen data directory, whichever case it is written in. */
    private fun libraryFile(directory: File): File =
        directory.walkTopDown().firstOrNull { it.isFile && it.name.equals(SONGS_DATABASE, ignoreCase = true) }
            ?: throw IllegalArgumentException("No $SONGS_DATABASE in ${directory.name}")

    /** Runs [sql] on [database] and hands each row to [read], closing everything on the way out. */
    private fun <T> readRows(database: File, sql: String, read: (ResultSet) -> T): List<T> =
        connect(database).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    buildList { while (rows.next()) add(read(rows)) }
                }
            }
        }

    // --- EasyWorship 6/7 library ---

    private fun parseLibrary(songsDatabase: File): List<EasyWorshipSong> {
        val wordsDatabase = songsDatabase.resolveSibling(WORDS_DATABASE).takeIf { it.isFile }
            ?: songsDatabase.parentFile?.listFiles()
                ?.firstOrNull { it.name.equals(WORDS_DATABASE, ignoreCase = true) }
            ?: throw IllegalArgumentException("$WORDS_DATABASE must sit beside ${songsDatabase.name}")

        val words = readRows(wordsDatabase, "SELECT song_id, words FROM word") {
            it.getInt(COLUMN_ID) to it.getString(COLUMN_TITLE).orEmpty()
        }.toMap()

        val query = "SELECT rowid, title, author, copyright, vendor_id FROM song ORDER BY rowid"
        return readRows(songsDatabase, query) { rows ->
            songOf(
                title = rows.getString(COLUMN_TITLE).orEmpty(),
                author = rows.getString(COLUMN_AUTHOR).orEmpty(),
                copyright = rows.getString(COLUMN_COPYRIGHT).orEmpty(),
                ccli = rows.getString(COLUMN_NUMBER).orEmpty(),
                rtf = words[rows.getInt(COLUMN_ID)].orEmpty(),
            )
        }
    }

    // --- .ewsx schedule ---

    private fun parseService(file: File): List<EasyWorshipSong> {
        val database = readZipEntryIgnoringChecksum(file, SERVICE_DATABASE)
            ?: throw IllegalArgumentException("No $SERVICE_DATABASE in ${file.name}")
        val temporary = Files.createTempFile("easyworship-service", ".db").toFile()
        try {
            temporary.writeBytes(database)
            return connect(temporary).use { connection ->
                serviceSongs(connection).map { row ->
                    // Already one block per slide, so the RTF is stitched with blank lines here
                    // and split back apart the same way the other flavours are.
                    songOf(
                        row.title, row.author, row.copyright, row.number,
                        slidesOf(connection, row.id), alreadyPlainText = true,
                    )
                }
            }
        } finally {
            temporary.delete()
        }
    }

    /** Every song cue in the service, in the order it runs. */
    private fun serviceSongs(connection: Connection): List<ServiceSong> =
        connection.createStatement().use { statement ->
            statement.executeQuery(SERVICE_SONG_QUERY).use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            ServiceSong(
                                id = result.getString(COLUMN_ID).orEmpty(),
                                title = result.getString(COLUMN_TITLE).orEmpty(),
                                author = result.getString(COLUMN_AUTHOR).orEmpty(),
                                copyright = result.getString(COLUMN_COPYRIGHT).orEmpty(),
                                number = result.getString(COLUMN_NUMBER).orEmpty(),
                            )
                        )
                    }
                }
            }
        }

    /** One song's slides as plain text, blank-line separated. */
    private fun slidesOf(connection: Connection, songId: String): String =
        connection.prepareStatement(SERVICE_SLIDE_QUERY).use { statement ->
            statement.setString(1, songId)
            statement.executeQuery().use { result ->
                buildList { while (result.next()) add(RtfText.toPlainText(result.getString(1).orEmpty())) }
            }
        }.joinToString("\n\n")

    /**
     * The bytes of [name] from [file], accepting a wrong CRC.
     *
     * EasyWorship writes a checksum for `main.db` that does not match what it stored, so `ZipFile`
     * refuses the entry outright and the schedule cannot be read at all. `ZipInputStream` only
     * verifies once it reaches the end of the entry, by which point every byte has already been
     * handed over — so the failure is caught there and the data kept.
     */
    internal fun readZipEntryIgnoringChecksum(file: File, name: String): ByteArray? {
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (namesEntry(entry.name, name)) return drain(zip)
            }
        }
    }

    /** Whether a zip entry's path names [wanted], in a folder or at the root. */
    private fun namesEntry(path: String, wanted: String): Boolean =
        path.equals(wanted, ignoreCase = true) ||
            path.substringAfterLast('/').equals(wanted, ignoreCase = true)

    /**
     * Everything left in [zip]'s current entry.
     *
     * The CRC is verified when the entry ends, which is after the last byte has been handed over —
     * so a checksum failure at that point is ignored and what was read is kept.
     */
    private fun drain(zip: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
        } catch (_: ZipException) {
            // The mismatched checksum EasyWorship writes; the bytes above are already read.
        }
        return out.toByteArray()
    }

    // --- Shared ---

    internal fun songOf(
        title: String,
        author: String,
        copyright: String,
        ccli: String,
        rtf: String,
        alreadyPlainText: Boolean = false,
    ): EasyWorshipSong {
        val text = if (alreadyPlainText) rtf else RtfText.toPlainText(rtf)
        return EasyWorshipSong(
            title = title.trim(),
            // EasyWorship keeps several authors in one field, separated by whichever of these the
            // person typing happened to use.
            author = author.split('/', ';').joinToString(", ") { it.trim() }.trim(',', ' '),
            copyright = copyright.trim(),
            ccli = ccli.trim(),
            sections = LyricBlocks.split(text),
        )
    }

    private fun connect(file: File): Connection {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
    }
}
