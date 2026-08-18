package converter.song

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

data class OpenLpSong(
    val title: String,
    val author: String,
    val copyright: String,
    val ccli: String,
    val number: String,
    val verseOrder: List<String>,
    val sections: List<SongSection>,
)

/**
 * OpenLP's `songs.sqlite` library, read straight out of the database.
 *
 * Nothing here is derivable from the song table alone: the lyrics column holds OpenLP's *own* XML
 * (`<song version="1.0"><lyrics><verse type="v" label="1">`), the authors live behind the
 * `authors_songs` bridging table, and the song number moved out of `songs` and into
 * `songs_songbooks.entry` in OpenLP 2.4 — so both are looked up only when the table is actually
 * there, which is what keeps a 2.0-era library importing instead of failing on a missing table.
 *
 * `[---]` inside a verse is OpenLP's optional slide split. It is dropped rather than kept, since a
 * `.song` section is one block of lines and the marker would otherwise be sung.
 */
object OpenLpDatabaseConverter {

    /** Columns of the song query below, and of the author query that follows it. */
    private const val COLUMN_ID = 1
    private const val COLUMN_TITLE = 2
    private const val COLUMN_LYRICS = 3
    private const val COLUMN_VERSE_ORDER = 4
    private const val COLUMN_COPYRIGHT = 5
    private const val COLUMN_CCLI = 6
    private const val AUTHOR_DISPLAY_NAME = 2
    private const val AUTHOR_FIRST_NAME = 3
    private const val AUTHOR_LAST_NAME = 4

    private const val SQLITE_HEADER = "SQLite format 3"
    private val verseOrderSeparator = Regex("""\s+""")

    fun isDatabase(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.length) return false
        val header = ByteArray(SQLITE_HEADER.length)
        file.inputStream().use { it.read(header) }
        return String(header, Charsets.US_ASCII) == SQLITE_HEADER
    }

    fun parse(file: File): List<OpenLpSong> {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            val tables = tableNames(connection)
            require("songs" in tables) { "Not an OpenLP song database" }
            val authors = if ("authors_songs" in tables) authorsBySong(connection) else emptyMap()
            val numbers = if ("songs_songbooks" in tables) numbersBySong(connection) else emptyMap()
            return readSongs(connection, authors, numbers)
        }
    }

    fun convert(input: File, outputDir: File): SongConversionResult {
        val songs = parse(input)
        if (songs.isEmpty()) return SongConversionResult(emptyList(), listOf("No songs in ${input.name}"))
        val taken = mutableSetOf<String>()
        val written = songs.map { song ->
            val parsed = ParsedSong(song.title, song.author, song.copyright, sections = song.sections)
            SongOutput.write(outputDir, parsed, taken, song.number)
        }
        return SongConversionResult(written)
    }

    /** Splits OpenLP's stored lyrics XML into sections, ordered by `verse_order` when it has one. */
    internal fun sectionsOf(lyricsXml: String, verseOrder: String): List<SongSection> {
        if (lyricsXml.isBlank()) return emptyList()
        val verses = runCatching { parseXmlRoot(lyricsXml) }.getOrNull()?.descendants("verse").orEmpty()
        val collected = LinkedHashMap<String, List<String>>()
        for (verse in verses) {
            val key = verse.getAttribute("type").trim().ifBlank { "v" } + verse.getAttribute("label").trim()
            val lines = verse.textContent.replace("[---]", "\n").lines()
                .map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) collected[key] = collected.getOrDefault(key, emptyList()) + lines
        }
        val ordered = order(collected.keys.toList(), verseOrder.split(verseOrderSeparator))
        val labels = SectionLabel.tidy(ordered.map { SectionLabel.of(it) })
        return ordered.mapIndexed { index, key -> SongSection(labels[index], collected.getValue(key)) }
    }

    private fun order(keys: List<String>, wanted: List<String>): List<String> {
        val byName = keys.associateBy { it.lowercase() }
        val ordered = LinkedHashSet<String>()
        wanted.filter { it.isNotBlank() }.forEach { token -> byName[token.lowercase()]?.let { ordered.add(it) } }
        ordered.addAll(keys)
        return ordered.toList()
    }

    private fun readSongs(
        connection: Connection,
        authors: Map<Int, String>,
        numbers: Map<Int, String>,
    ): List<OpenLpSong> {
        val songs = mutableListOf<OpenLpSong>()
        query(connection, "SELECT id, title, lyrics, verse_order, copyright, ccli_number FROM songs ORDER BY id") {
            val id = it.getInt(COLUMN_ID)
            val verseOrder = it.getString(COLUMN_VERSE_ORDER).orEmpty()
            songs.add(
                OpenLpSong(
                    title = it.getString(COLUMN_TITLE).orEmpty().trim(),
                    author = authors[id].orEmpty(),
                    copyright = it.getString(COLUMN_COPYRIGHT).orEmpty().trim(),
                    ccli = it.getString(COLUMN_CCLI).orEmpty().trim(),
                    number = numbers[id].orEmpty(),
                    verseOrder = verseOrder.split(verseOrderSeparator).filter { token -> token.isNotBlank() },
                    sections = sectionsOf(it.getString(COLUMN_LYRICS).orEmpty(), verseOrder),
                )
            )
        }
        return songs
    }

    private fun authorsBySong(connection: Connection): Map<Int, String> {
        val names = LinkedHashMap<Int, MutableList<String>>()
        val sql = "SELECT authors_songs.song_id, authors.display_name, authors.first_name, authors.last_name " +
            "FROM authors_songs JOIN authors ON authors.id = authors_songs.author_id"
        query(connection, sql) {
            val display = it.getString(AUTHOR_DISPLAY_NAME).orEmpty().ifBlank {
                listOf(it.getString(AUTHOR_FIRST_NAME).orEmpty(), it.getString(AUTHOR_LAST_NAME).orEmpty())
                    .filter(String::isNotBlank).joinToString(" ")
            }
            if (display.isNotBlank()) names.getOrPut(it.getInt(COLUMN_ID)) { mutableListOf() }.add(display)
        }
        return names.mapValues { (_, list) -> list.joinToString(", ") }
    }

    private fun numbersBySong(connection: Connection): Map<Int, String> {
        val numbers = LinkedHashMap<Int, String>()
        query(connection, "SELECT song_id, entry FROM songs_songbooks") {
            val entry = it.getString(COLUMN_TITLE).orEmpty().trim()
            if (entry.isNotBlank()) numbers.putIfAbsent(it.getInt(COLUMN_ID), entry)
        }
        return numbers
    }

    private fun tableNames(connection: Connection): Set<String> {
        val names = mutableSetOf<String>()
        query(connection, "SELECT name FROM sqlite_master WHERE type = 'table'") {
            names.add(it.getString(1).orEmpty().lowercase())
        }
        return names
    }

    private fun query(connection: Connection, sql: String, row: (ResultSet) -> Unit) {
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { results ->
                while (results.next()) row(results)
            }
        }
    }
}
