package converter.song

import converter.library.TextUtils

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager

data class SpsSong(
    val number: String,
    val title: String,
    val songbook: String = "",
    val tune: String = "",
    val author: String = "",
    val composer: String = "",
    val lyrics: List<String> = emptyList()
)

data class SpsParseResult(
    val songbookName: String,
    val songs: List<SpsSong>
)

data class SpsConversionResult(
    val songsConverted: Int,
    val songbookFolder: String,
    val errors: List<String>
)

object SpsToSongConverter {

    /** Enough bytes to hold the SQLite magic, which is how the two `.sps` flavours are told apart. */
    private const val SQLITE_MAGIC_LENGTH = 16

    /** Columns of a text-flavour row: `number#$#title#$#category#$#tune#$#words#$#music#$#lyrics`. */
    private const val TEXT_COLUMNS_BEFORE_LYRICS = 6
    private const val TEXT_COLUMN_TUNE = 3
    private const val TEXT_COLUMN_AUTHOR = 4
    private const val TEXT_COLUMN_COMPOSER = 5
    private const val TEXT_COLUMN_LYRICS = 6

    /** The same columns in the SQLite flavour, where JDBC counts from one. */
    private const val COLUMN_NUMBER = 1
    private const val COLUMN_TITLE = 2
    private const val COLUMN_TUNE = 4
    private const val COLUMN_AUTHOR = 5
    private const val COLUMN_COMPOSER = 6
    private const val COLUMN_SONG_TEXT = 7

    fun parse(spsFile: File): SpsParseResult {
        // Detect SQLite vs text format
        if (spsFile.length() >= SQLITE_MAGIC_LENGTH) {
            val header = ByteArray(SQLITE_MAGIC_LENGTH)
            spsFile.inputStream().use { it.read(header) }
            if (String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
                return parseSqlite(spsFile)
            }
        }
        return parseText(spsFile)
    }

    // As in the document converter: one song that will not write is reported and the rest of the
    // songbook still converts.
    @Suppress("TooGenericExceptionCaught")
    fun convert(spsFile: File, outputDirectory: File): SpsConversionResult {
        val errors = mutableListOf<String>()
        var songsConverted = 0

        val result = parse(spsFile)
        if (result.songs.isEmpty()) {
            return SpsConversionResult(0, "", listOf("No songs found in file"))
        }

        val songbookDir = File(outputDirectory, sanitizeName(result.songbookName))
        songbookDir.mkdirs()

        for (song in result.songs) {
            try {
                val paddedNumber = song.number.padStart(4, '0')
                val sanitizedTitle = sanitizeName(song.title)
                val fileName = "$paddedNumber - $sanitizedTitle.song"
                val filePath = File(songbookDir, fileName)

                writeSongFile(song, filePath)
                songsConverted++
            } catch (e: Exception) {
                errors.add("Error converting song ${song.number} - ${song.title}: ${e.message}")
            }
        }

        return SpsConversionResult(songsConverted, songbookDir.absolutePath, errors)
    }

    fun getTargetFolderName(spsFile: File): String {
        return try {
            val result = parse(spsFile)
            sanitizeName(result.songbookName)
        } catch (_: Exception) {
            spsFile.nameWithoutExtension
        }
    }

    // --- Text format parsing ---

    private fun parseText(spsFile: File): SpsParseResult {
        val fileBaseName = spsFile.nameWithoutExtension
        var songbookName = fileBaseName
        var headerLineCount = 0
        val songs = mutableListOf<SpsSong>()

        val reader = Files.newBufferedReader(spsFile.toPath(), StandardCharsets.UTF_8)
        reader.use { r ->
            r.forEachLine { rawLine ->
                val line = rawLine.trimEnd('\r', '\n')

                if (line.startsWith("##")) {
                    headerLineCount++
                    val headerContent = line.substring(2).trim()
                    if (headerLineCount == 2) {
                        songbookName = headerContent
                    }
                    return@forEachLine
                }

                if (line.isBlank()) return@forEachLine

                val parts = line.split("#\$#")
                if (parts.size >= TEXT_COLUMNS_BEFORE_LYRICS) {
                    val lyricsText = parts.getOrElse(TEXT_COLUMN_LYRICS) { "" }
                    val lyrics = parseLyrics(lyricsText)

                    songs.add(
                        SpsSong(
                            number = parts[0],
                            title = parts[1],
                            songbook = songbookName,
                            tune = parts[TEXT_COLUMN_TUNE],
                            author = parts[TEXT_COLUMN_AUTHOR],
                            composer = parts[TEXT_COLUMN_COMPOSER],
                            lyrics = lyrics
                        )
                    )
                }
            }
        }

        return SpsParseResult(songbookName, songs)
    }

    // --- SQLite format parsing ---

    private fun parseSqlite(spsFile: File): SpsParseResult {
        Class.forName("org.sqlite.JDBC")
        val conn: Connection = DriverManager.getConnection("jdbc:sqlite:${spsFile.absolutePath}")
        val songs = mutableListOf<SpsSong>()

        conn.use { c ->
            val songbookName = try {
                val stmt = c.createStatement()
                val rs = stmt.executeQuery("SELECT title FROM SongBook LIMIT 1")
                val name = if (rs.next()) rs.getString(1)?.ifEmpty { null } else null
                rs.close()
                stmt.close()
                name
            } catch (_: Exception) {
                null
            } ?: spsFile.nameWithoutExtension

            val stmt = c.createStatement()
            val rs = stmt.executeQuery(
                "SELECT number, title, category, tune, words, music, song_text FROM Songs ORDER BY number"
            )
            while (rs.next()) {
                val songText = rs.getString(COLUMN_SONG_TEXT) ?: ""
                val lyrics = parseSqliteLyrics(songText)
                songs.add(
                    SpsSong(
                        number = (rs.getString(COLUMN_NUMBER) ?: "").trim(),
                        title = (rs.getString(COLUMN_TITLE) ?: "").trim(),
                        songbook = songbookName,
                        tune = (rs.getString(COLUMN_TUNE) ?: "").trim(),
                        author = (rs.getString(COLUMN_AUTHOR) ?: "").trim(),
                        composer = (rs.getString(COLUMN_COMPOSER) ?: "").trim(),
                        lyrics = lyrics
                    )
                )
            }
            rs.close()
            stmt.close()

            return SpsParseResult(songbookName, songs)
        }
    }

    // --- Lyrics parsing ---

    private fun parseSqliteLyrics(songText: String): List<String> {
        if (songText.isBlank()) return emptyList()
        val sanitized = TextUtils.sanitizeLyricText(songText)
        val lines = sanitized.split("\n").map { wrapSectionHeader(it.trimEnd('\r')) }
        return lines.dropLastWhile { it.isBlank() }
    }

    private fun parseLyrics(lyricsText: String): List<String> {
        if (lyricsText.isBlank()) return emptyList()

        val sanitizedText = TextUtils.sanitizeLyricText(lyricsText)
        val lyrics = mutableListOf<String>()
        val sections = mutableListOf<LyricSection>()

        val verses = sanitizedText.split("@\$")

        // First pass: parse all sections
        for (verse in verses) {
            if (verse.isBlank()) continue

            val lines = verse.split("@%")
            val sectionLines = mutableListOf<String>()

            for (line in lines) {
                val cleanLine = line.trim()
                if (cleanLine.isNotEmpty()) {
                    sectionLines.add(cleanLine)
                }
            }

            if (sectionLines.isNotEmpty()) {
                sectionLines[0] = wrapSectionHeader(sectionLines[0])
                val firstLine = sectionLines[0]
                val type = when {
                    firstLine.startsWith("[") -> TYPE_VERSE
                    firstLine.startsWith("{") -> TYPE_CHORUS
                    else -> TYPE_OTHER
                }
                val section = LyricSection(type, sectionLines)
                sections.add(section)
            }
        }

        // Second pass: write each section once in order (no chorus repetition)
        for (i in sections.indices) {
            val section = sections[i]
            lyrics.addAll(section.lines)

            if (i < sections.size - 1) {
                lyrics.add("")
            }
        }

        if (lyrics.isNotEmpty() && lyrics.last().isBlank()) {
            lyrics.removeAt(lyrics.lastIndex)
        }

        return lyrics
    }

    private fun wrapSectionHeader(line: String): String {
        val t = line.trim()
        return when {
            t.matches(Regex("^(Припев|Chorus|Refrain).*", RegexOption.IGNORE_CASE)) -> "{$t}"
            t.matches(Regex("^(Куплет|Verse|Bridge).*", RegexOption.IGNORE_CASE)) -> "[$t]"
            else -> line
        }
    }

    // --- .song file writing ---

    private fun writeSongFile(song: SpsSong, file: File) {
        val sb = StringBuilder()

        if (song.author.isNotEmpty() || song.composer.isNotEmpty() || song.tune.isNotEmpty()) {
            sb.appendLine("---")
            if (song.author.isNotEmpty()) sb.appendLine("author: ${song.author}")
            if (song.composer.isNotEmpty()) sb.appendLine("composer: ${song.composer}")
            if (song.tune.isNotEmpty()) sb.appendLine("tune: ${song.tune}")
            sb.appendLine("---")
            sb.appendLine()
        }

        sb.appendLine("[Primary]")
        sb.appendLine("title: ${song.title}")
        sb.appendLine()
        for (line in song.lyrics) {
            sb.appendLine(line)
        }

        file.parentFile?.mkdirs()
        file.writeText(sb.toString(), StandardCharsets.UTF_8)
    }

    // --- Helpers ---

    private fun sanitizeName(name: String): String {
        return name
            .replace(Regex("""[/\\:*?"<>|]"""), " ")   // Windows-illegal chars
            .replace(Regex("""[\x00-\x1F\x7F]"""), "")  // control characters
            .replace(Regex("""[^\p{Print}\p{L}\p{M}\p{N}\p{P}\p{Z}]"""), " ") // non-printable
            .replace(Regex("""\s+"""), " ")              // collapse whitespace
            .trim()
    }

    private const val TYPE_VERSE = "verse"
    private const val TYPE_CHORUS = "chorus"
    private const val TYPE_OTHER = "other"

    private data class LyricSection(
        val type: String,
        val lines: List<String>
    )
}
