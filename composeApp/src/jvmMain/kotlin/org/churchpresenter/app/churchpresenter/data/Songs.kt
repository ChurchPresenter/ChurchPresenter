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
                val firstLine = sectionLines[0]
                val section = LyricSection(
                    type = when {
                        firstLine.startsWith(Constants.VERSE_RUS) -> Constants.VERSE_RUS
                        firstLine.startsWith(Constants.CHORUS_RUS) -> Constants.CHORUS
                        else -> Constants.OTHER
                    },
                    lines = sectionLines
                )

                sections.add(section)

                // Store chorus for later use
                if (section.type == Constants.CHORUS) {
                    chorusSection = section
                }
            }
        }

        // Second pass: build final lyrics with chorus repeating after verses
        for (i in sections.indices) {
            val section = sections[i]

            // Skip the original chorus section - we'll add it after each verse instead
            if (section.type == Constants.CHORUS) {
                continue
            }

            // Add the current section (verse or other)
            lyrics.addAll(section.lines)

            // If this is a verse and we have a chorus, add the chorus after it
            if (section.type == "verse" && chorusSection != null) {
                lyrics.add("") // Empty line separator before chorus
                lyrics.addAll(chorusSection.lines)
            }

            // Add empty line after current section if there are more non-chorus sections coming
            val hasMoreSections = sections.subList(i + 1, sections.size).any { it.type != Constants.CHORUS }
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

    private data class LyricSection(
        val type: String, // "verse", "chorus", "other"
        val lines: List<String>
    )

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

            // Check if this is a section marker (Куплет, Припев, etc.)
            if (trimmedLine.matches(Regex("^(Куплет|Припев|Verse|Chorus|Bridge).*", RegexOption.IGNORE_CASE))) {
                // If we have accumulated lines, save them as a section
                if (currentSection.isNotEmpty()) {
                    if (result.isNotEmpty()) result.append("@\$")
                    result.append(currentSection)
                    currentSection = StringBuilder()
                }
                // Start new section with the marker
                currentSection.append(trimmedLine)
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

