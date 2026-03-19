package org.churchpresenter.app.churchpresenter.data

import androidx.compose.runtime.mutableStateListOf
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths


class Songs {
    private val songs = mutableStateListOf<SongItem>()

    fun loadFromSps(resourcePath: String) {
        songs.clear()
        loadFromSpsAppend(resourcePath)
    }

    fun loadFromSpsAppend(resourcePath: String) {
        try {
            // Detect SQLite format (Mac SongPresenter uses SQLite databases for .sps files)
            val spsFile = java.io.File(resourcePath)
            if (spsFile.exists() && spsFile.length() >= 16) {
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
                if (Files.exists(path)) {
                    Files.newBufferedReader(path, StandardCharsets.UTF_8)
                } else {
                    throw IllegalArgumentException("loadFromSpsAppend: resource not found on classpath or filesystem: $resourcePath")
                }
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
                    if (parts.size >= 6) {
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

                        songs.add(
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
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Load songs from a SQLite-format .sps file (Mac SongPresenter).
     */
    private fun loadFromSpsSqlite(file: java.io.File) {
        val conn = JdbcDatabase.openConnection(file.absolutePath)
        conn.use { c ->
            // Get songbook name from SongBook table
            val songbookName = try {
                val sbResult = JdbcDatabase.executeQuery(c, "SELECT title FROM SongBook LIMIT 1")
                sbResult.firstOrNull()?.getString(0)?.ifEmpty { null }
            } catch (_: Exception) { null } ?: file.nameWithoutExtension

            // Load all songs
            val result = JdbcDatabase.executeQuery(c,
                "SELECT number, title, category, tune, words, music, song_text FROM Songs ORDER BY number")
            for (row in result) {
                val songText = row.getString(6)
                val lyrics = parseSqliteLyrics(songText)
                songs.add(
                    SongItem(
                        number = row.getString(0).trim(),
                        title = row.getString(1).trim(),
                        songbook = songbookName,
                        tune = row.getString(3).trim(),
                        author = row.getString(4).trim(),
                        composer = row.getString(5).trim(),
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

    private fun wrapSectionHeader(line: String): String {
        val t = line.trim()
        return when {
            t.matches(Regex("^(Припев|Chorus|Refrain).*", RegexOption.IGNORE_CASE)) -> "{$t}"
            t.matches(Regex("^(Куплет|Verse|Bridge).*", RegexOption.IGNORE_CASE)) -> "[$t]"
            else -> line
        }
    }

    private data class LyricSection(
        val type: String, // "verse", "chorus", "other"
        val lines: List<String>
    )

    fun addSongs(newSongs: List<SongItem>) {
        songs.addAll(newSongs)
    }

    fun getSongs(): List<SongItem> {
        return songs.toList()
    }

    fun getSongCount(): Int {
        return songs.size
    }

    fun findSongs(query: String, filterType: String = "Contains"): List<SongItem> {
        if (query.isBlank()) return songs.toList()

        return songs.filter { song ->
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
        if (category == "All song categories") return songs.toList()

        // For now, return all songs since categories aren't clearly defined in the SPS format
        return songs.toList()
    }

    fun getSongsBySongbook(songbook: String): List<SongItem> {
        if (songbook == "All songbooks") return songs.toList()

        return songs.filter { it.songbook.contains(songbook, ignoreCase = true) }
    }

    fun updateSong(oldSong: SongItem, newSong: SongItem) {
        val index = songs.indexOfFirst { it.number == oldSong.number && it.songbook == oldSong.songbook }
        if (index >= 0) {
            songs[index] = newSong
        }
    }

    fun saveSongToFile(originalSong: SongItem, updatedSong: SongItem, storageDirectory: String): Boolean {
        try {
            if (storageDirectory.isEmpty()) {
                return false
            }

            // If the song has a sourceFile (.song format), save directly to that file
            if (updatedSong.sourceFile.isNotEmpty()) {
                val parser = SongFileParser()
                parser.writeSongFile(updatedSong, updatedSong.sourceFile)
                return true
            }
            if (originalSong.sourceFile.isNotEmpty()) {
                val parser = SongFileParser()
                parser.writeSongFile(updatedSong.copy(sourceFile = originalSong.sourceFile), originalSong.sourceFile)
                return true
            }

            val dir = java.io.File(storageDirectory)
            if (!dir.exists() || !dir.isDirectory) {
                return false
            }

            val spsFiles = dir.listFiles { file ->
                file.extension.lowercase() == Constants.EXTENSION_SPS
            } ?: emptyArray()

            for (file in spsFiles) {
                if (updateSongInFile(file.absolutePath, originalSong, updatedSong)) {
                    return true
                }
            }

            return false
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Update a song in a specific .sps file
     */
    private fun updateSongInFile(filePath: String, originalSong: SongItem, updatedSong: SongItem): Boolean {
        try {
            val path = Paths.get(filePath)
            if (!Files.exists(path)) {
                return false
            }

            val lines = Files.readAllLines(path, StandardCharsets.UTF_8).toMutableList()
            var songUpdated = false

            for (i in lines.indices) {
                val line = lines[i]

                if (line.startsWith("##") || line.isBlank()) continue

                val parts = line.split("#\$#")
                if (parts.size >= 6) {
                    val number = parts[0]
                    val title = parts[1]

                    if (number == originalSong.number && title == originalSong.title) {
                        val lyricsText = formatLyricsForSps(updatedSong.lyrics)
                        val categoryId = if (parts.size >= 3) parts[2] else ""

                        val updatedLine = "${updatedSong.number}#\$#${updatedSong.title}#\$#$categoryId#\$#${updatedSong.tune}#\$#${updatedSong.author}#\$#${updatedSong.composer}#\$#$lyricsText"

                        lines[i] = updatedLine
                        songUpdated = true
                        break
                    }
                }
            }

            if (songUpdated) {
                Files.write(path, lines, StandardCharsets.UTF_8)
                return true
            }

            return false
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Format lyrics list back to SPS format
     * Converts List<String> back to the @$ and @% delimited format
     */
    private fun formatLyricsForSps(lyrics: List<String>): String {
        if (lyrics.isEmpty()) return ""

        val result = StringBuilder()
        var currentSection = StringBuilder()

        for (line in lyrics) {
            val trimmedLine = line.trim()

            // Check if this is a section marker ([Куплет], {Припев}, etc.)
            if (trimmedLine.matches(Regex("^[\\[{](Куплет|Припев|Verse|Chorus|Refrain|Bridge).*[\\]}]$", RegexOption.IGNORE_CASE))) {
                // If we have accumulated lines, save them as a section
                if (currentSection.isNotEmpty()) {
                    if (result.isNotEmpty()) result.append("@\$")
                    result.append(currentSection)
                    currentSection = StringBuilder()
                }
                // Start new section with the marker (strip [] or {} wrapping for SPS format)
                val unwrapped = trimmedLine.removePrefix("[").removePrefix("{").removeSuffix("]").removeSuffix("}")
                currentSection.append(unwrapped)
            } else if (trimmedLine.isNotEmpty()) {
                // Add line to current section
                if (currentSection.isNotEmpty()) {
                    currentSection.append("@%")
                }
                currentSection.append(trimmedLine)
            }
        }

        // Add last section
        if (currentSection.isNotEmpty()) {
            if (result.isNotEmpty()) result.append("@\$")
            result.append(currentSection)
        }

        return result.toString()
    }
}

