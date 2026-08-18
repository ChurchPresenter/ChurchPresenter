package org.churchpresenter.app.churchpresenter.data

import core.models.songs.SongFileParser
import core.models.songs.SongItem
import androidx.compose.runtime.mutableStateListOf
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.SongSectionWords
import org.churchpresenter.app.churchpresenter.utils.isHeaderLine
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

private const val SPS_FIELD_SEPARATOR = "#\$#"
private const val MIN_SPS_FILE_BYTES = 16
private const val SPS_MIN_FIELDS = 6
private const val SQLITE_COL_TUNE = 3
private const val SQLITE_COL_AUTHOR = 4
private const val SQLITE_COL_COMPOSER = 5


class Songs {
    private val items = mutableStateListOf<SongItem>()

    fun loadFromSps(resourcePath: String) {
        items.clear()
        loadFromSpsAppend(resourcePath)
    }

    fun loadFromSpsAppend(resourcePath: String) {
        // Detect SQLite format (Mac SongPresenter uses SQLite databases for .sps files)
        val spsFile = java.io.File(resourcePath)
        if (spsFile.exists() && spsFile.length() >= MIN_SPS_FILE_BYTES) {
            val header = ByteArray(16)
            spsFile.inputStream().use { it.read(header) }
            if (String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
                loadFromSpsSqlite(spsFile)
                return
            }
        }

        // Extract database name from the file path (without extension) as fallback
        val fileBaseName = resourcePath.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')

        val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
        val reader = if (inputStream != null) {
            inputStream.bufferedReader(StandardCharsets.UTF_8)
        } else {
            val path = Paths.get(resourcePath)
            require(Files.exists(path)) {
                "loadFromSpsAppend: resource not found on classpath or filesystem: $resourcePath"
            }
            Files.newBufferedReader(path, StandardCharsets.UTF_8)
        }

        var databaseName = fileBaseName // Default to filename
        val categoryToSongbookMap = mutableMapOf<String, String>()
        var headerLineCount = 0 // Track which header line we're on

        reader.use { r ->
            r.forEachLine { rawLine ->
                val line = rawLine.trimEnd('\r', '\n')

                // Parse header lines for songbook mappings
                if (line.startsWith("##")) {
                    headerLineCount++
                    val headerContent = line.substring(2).trim()

                    // The second header line contains the actual songbook name
                    if (headerLineCount == 2) {
                        databaseName = headerContent
                    }
                    return@forEachLine
                }

                // Skip empty lines
                if (line.isBlank()) {
                    return@forEachLine
                }

                // Parse song entry
                val parts = line.split("#\$#")
                if (parts.size >= SPS_MIN_FIELDS) {
                    val number = parts[0]
                    val title = parts[1]
                    val categoryId = parts[2].trim() // This is the category/songbook ID
                    val key = parts[3]
                    val author = parts[4]
                    val composer = parts[5]
                    val lyricsText = if (parts.size > 6) parts[6] else ""

                    // Map category ID to actual songbook name, or use database name as fallback
                    val songbookName = categoryToSongbookMap[categoryId] ?: databaseName

                    // Parse lyrics
                    val lyrics = parseLyrics(lyricsText)

                    items.add(
                        SongItem(
                            number = number,
                            title = title,
                            songbook = songbookName,
                            tune = key,
                            author = author,
                            composer = composer,
                            lyrics = lyrics
                        )
                    )
                }
            }
        }
    }

    /**
     * Load items from a SQLite-format .sps file (Mac SongPresenter).
     */
    private fun loadFromSpsSqlite(file: java.io.File) {
        val conn = JdbcDatabase.openConnection(file.absolutePath)
        conn.use { c ->
            // Get songbook name from SongBook table
            val songbookName = try {
                val sbResult = JdbcDatabase.executeQuery(c, "SELECT title FROM SongBook LIMIT 1")
                sbResult.firstOrNull()?.getString(0)?.ifEmpty { null }
            } catch (_: Exception) { null } ?: file.nameWithoutExtension

            // Load all items
            val result = JdbcDatabase.executeQuery(c,
                "SELECT number, title, category, tune, words, music, song_text FROM Songs ORDER BY number")
            for (row in result) {
                val songText = row.getString(6)
                val lyrics = parseSqliteLyrics(songText)
                items.add(
                    SongItem(
                        number = row.getString(0).trim(),
                        title = row.getString(1).trim(),
                        songbook = songbookName,
                        tune = row.getString(SQLITE_COL_TUNE).trim(),
                        author = row.getString(SQLITE_COL_AUTHOR).trim(),
                        composer = row.getString(SQLITE_COL_COMPOSER).trim(),
                        lyrics = lyrics
                    )
                )
            }
        }
    }

    /**
     * Parse lyrics from SQLite song_text format.
     * Uses newlines to separate lines and blank lines to separate sections.
     * Section headers like "Куплет 1", "Припев" appear on their own lines.
     */
    private fun parseSqliteLyrics(songText: String): List<String> {
        if (songText.isBlank()) return emptyList()
        // The song_text uses plain newlines — just split and return as-is
        // Section headers and empty line separators are already in the correct format
        val lines = songText.split("\n").map { wrapSectionHeader(it.trimEnd('\r')) }
        // Remove trailing empty lines
        val trimmed = lines.dropLastWhile { it.isBlank() }
        return trimmed
    }

    private fun parseLyrics(lyricsText: String): List<String> {
        if (lyricsText.isBlank()) return emptyList()

        val lyrics = mutableListOf<String>()
        val sections = mutableListOf<LyricSection>()
        var chorusSection: LyricSection? = null

        // Split by verse markers (@$)
        val verses = lyricsText.split("@\$")

        // First pass: parse all sections
        for (verse in verses) {
            if (verse.isBlank()) continue

            // Split lines by @%
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
                val section = LyricSection(
                    type = when {
                        firstLine.startsWith("[") -> Constants.SECTION_TYPE_VERSE
                        firstLine.startsWith("{") -> Constants.SECTION_TYPE_CHORUS
                        else -> Constants.OTHER
                    },
                    lines = sectionLines
                )

                sections.add(section)

                // Store chorus for later use
                if (section.type == Constants.SECTION_TYPE_CHORUS) {
                    chorusSection = section
                }
            }
        }

        // Second pass: build final lyrics with chorus repeating after verses
        for (i in sections.indices) {
            val section = sections[i]

            // Skip the original chorus section - we'll add it after each verse instead
            if (section.type == Constants.SECTION_TYPE_CHORUS) {
                continue
            }

            // Add the current section (verse or other)
            lyrics.addAll(section.lines)

            // If this is a verse and we have a chorus, add the chorus after it
            if (section.type == Constants.SECTION_TYPE_VERSE && chorusSection != null) {
                lyrics.add("") // Empty line separator before chorus
                lyrics.addAll(chorusSection.lines)
            }

            // Add empty line after current section if there are more non-chorus sections coming
            val hasMoreSections = sections.subList(i + 1, sections.size).any { it.type != Constants.SECTION_TYPE_CHORUS }
            if (hasMoreSections) {
                lyrics.add("") // Empty line separator after section
            }
        }

        // Remove trailing empty line if exists
        if (lyrics.isNotEmpty() && lyrics.last().isBlank()) {
            lyrics.removeAt(lyrics.lastIndex)
        }

        return lyrics
    }

    /**
     * Brackets a bare header line — SQLite and SPS write "Куплет 1" or "Zwrotka 1" plain, while the
     * rest of the app reads `[]`/`{}`. Recognised in every language at once; see [SongSectionWords].
     */
    private fun wrapSectionHeader(line: String): String {
        val t = line.trim()
        return when {
            SongSectionWords.isChorus(t) -> "{$t}"
            SongSectionWords.isKnownSection(t) -> "[$t]"
            else -> line
        }
    }

    private data class LyricSection(
        val type: String, // "verse", "chorus", "other"
        val lines: List<String>
    )

    fun addSongs(newSongs: List<SongItem>) {
        items.addAll(newSongs)
    }

    fun getSongs(): List<SongItem> {
        return items.toList()
    }

    fun getSongCount(): Int {
        return items.size
    }

    fun findSongs(query: String, filterType: String = "Contains"): List<SongItem> {
        if (query.isBlank()) return items.toList()

        return items.filter { song ->
            when (filterType) {
                Constants.CONTAINS -> song.title.contains(query, ignoreCase = true) ||
                            song.number.contains(query, ignoreCase = true)
                Constants.STARTS_WITH -> song.title.startsWith(query, ignoreCase = true) ||
                               song.number.startsWith(query, ignoreCase = true)
                Constants.EXACT_MATCH -> song.title.equals(query, ignoreCase = true) ||
                               song.number.equals(query, ignoreCase = true)
                else -> song.title.contains(query, ignoreCase = true)
            }
        }
    }

    fun getSongsByCategory(category: String): List<SongItem> {
        if (category == "All song categories") return items.toList()

        // For now, return all items since categories aren't clearly defined in the SPS format
        return items.toList()
    }

    fun getSongsBySongbook(songbook: String): List<SongItem> {
        if (songbook == "All songbooks") return items.toList()

        return items.filter { it.songbook.contains(songbook, ignoreCase = true) }
    }

    fun updateSong(oldSong: SongItem, newSong: SongItem) {
        val index = items.indexOfFirst { it.number == oldSong.number && it.songbook == oldSong.songbook }
        if (index >= 0) {
            items[index] = newSong
        }
    }

    fun saveSongToFile(originalSong: SongItem, updatedSong: SongItem, storageDirectory: String): Boolean {
        if (storageDirectory.isEmpty()) return false
        val sourceFile = updatedSong.sourceFile.ifEmpty { originalSong.sourceFile }
        return try {
            if (sourceFile.isNotEmpty()) {
                SongFileParser().writeSongFile(updatedSong.copy(sourceFile = sourceFile), sourceFile)
                true
            } else {
                val dir = java.io.File(storageDirectory)
                val spsFiles = dir.listFiles { file ->
                    file.extension.lowercase() == Constants.EXTENSION_SPS
                } ?: emptyArray()
                spsFiles.any { updateSongInFile(it.absolutePath, originalSong, updatedSong) }
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Update a song in a specific .sps file */
    private fun updateSongInFile(filePath: String, originalSong: SongItem, updatedSong: SongItem): Boolean = try {
        val path = Paths.get(filePath)
        val lines = Files.readAllLines(path, StandardCharsets.UTF_8).toMutableList()
        val index = lines.indexOfFirst { isSpsLineFor(it, originalSong) }
        if (index >= 0) {
            lines[index] = spsLineFor(lines[index], updatedSong)
            Files.write(path, lines, StandardCharsets.UTF_8)
        }
        index >= 0
    } catch (_: Exception) {
        false
    }

    private fun isSpsLineFor(line: String, song: SongItem): Boolean {
        if (line.startsWith("##") || line.isBlank()) return false
        val parts = line.split(SPS_FIELD_SEPARATOR)
        return parts.size >= SPS_MIN_FIELDS && parts[0] == song.number && parts[1] == song.title
    }

    private fun spsLineFor(existingLine: String, song: SongItem): String {
        val parts = existingLine.split(SPS_FIELD_SEPARATOR)
        val categoryId = parts.getOrElse(2) { "" }
        val lyricsText = formatLyricsForSps(song.lyrics)
        return listOf(
            song.number, song.title, categoryId, song.tune, song.author, song.composer, lyricsText
        ).joinToString(SPS_FIELD_SEPARATOR)
    }

    /**
     * Format lyrics list back to SPS format
     * Converts List<String> back to the @$ and @% delimited format
     */
    internal fun formatLyricsForSps(lyrics: List<String>): String {
        if (lyrics.isEmpty()) return ""

        val result = StringBuilder()
        var currentSection = StringBuilder()

        for (line in lyrics) {
            val trimmedLine = line.trim()

            // A section marker is any bracketed line — [Куплет], {Припев}, [Zwrotka 1], and equally
            // a name this app has no word for. Matching on a word list here instead would write
            // every unrecognised header back out as a lyric line, brackets and all.
            if (isHeaderLine(trimmedLine)) {
                result.appendSection(currentSection)
                currentSection = StringBuilder()
                // Start new section with the marker (strip [] or {} wrapping for SPS format)
                currentSection.append(
                    trimmedLine.removePrefix("[").removePrefix("{").removeSuffix("]").removeSuffix("}")
                )
            } else if (trimmedLine.isNotEmpty()) {
                if (currentSection.isNotEmpty()) currentSection.append("@%")
                currentSection.append(trimmedLine)
            }
        }

        result.appendSection(currentSection)
        return result.toString()
    }

    private fun StringBuilder.appendSection(section: StringBuilder) {
        if (section.isEmpty()) return
        if (isNotEmpty()) append("@\$")
        append(section)
    }
}

