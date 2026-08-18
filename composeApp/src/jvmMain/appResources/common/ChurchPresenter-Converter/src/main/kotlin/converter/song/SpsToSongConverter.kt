package converter.song

import converter.library.TextUtils

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
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

    fun parse(spsFile: File): SpsParseResult {
        // Detect SQLite vs text format
        if (spsFile.length() >= 16) {
            val header = ByteArray(16)
            spsFile.inputStream().use { it.read(header) }
            if (String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
                return parseSqlite(spsFile)
            }
        }
        return parseText(spsFile)
    }

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
                if (parts.size >= 6) {
                    val lyricsText = if (parts.size > 6) parts[6] else ""
                    val lyrics = parseLyrics(lyricsText)

                    songs.add(
                        SpsSong(
                            number = parts[0],
                            title = parts[1],
                            songbook = songbookName,
                            tune = parts[3],
                            author = parts[4],
                            composer = parts[5],
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
                val songText = rs.getString(7) ?: ""
                val lyrics = parseSqliteLyrics(songText)
                songs.add(
                    SpsSong(
                        number = (rs.getString(1) ?: "").trim(),
                        title = (rs.getString(2) ?: "").trim(),
                        songbook = songbookName,
                        tune = (rs.getString(4) ?: "").trim(),
                        author = (rs.getString(5) ?: "").trim(),
                        composer = (rs.getString(6) ?: "").trim(),
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
