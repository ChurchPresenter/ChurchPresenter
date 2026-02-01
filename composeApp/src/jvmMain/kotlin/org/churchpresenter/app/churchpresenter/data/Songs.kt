package org.churchpresenter.app.churchpresenter.data

import androidx.compose.runtime.mutableStateListOf
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.logging.Level
import java.util.logging.Logger

private val songsLogger: Logger = Logger.getLogger("org.churchpresenter.Songs")


class Songs {
    private val songs = mutableStateListOf<SongItem>()

    fun loadFromSps(resourcePath: String) {
        songs.clear()
        try {
            val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            val reader = if (inputStream != null) {
                inputStream.bufferedReader(StandardCharsets.UTF_8)
            } else {
                val path = Paths.get(resourcePath)
                if (Files.exists(path)) {
                    Files.newBufferedReader(path, StandardCharsets.UTF_8)
                } else {
                    val msg = "loadFromSps: resource not found on classpath or filesystem: $resourcePath"
                    songsLogger.severe(msg)
                    throw IllegalArgumentException(msg)
                }
            }

            reader.use { r ->
                r.forEachLine { rawLine ->
                    val line = rawLine.trimEnd('\r', '\n')

                    // Skip header lines and empty lines
                    if (line.startsWith("##") || line.isBlank()) {
                        return@forEachLine
                    }

                    // Parse song entry
                    val parts = line.split("#\$#")
                    if (parts.size >= 6) {
                        val number = parts[0]
                        val title = parts[1]
                        // parts[2] is category (not currently used)
                        val key = parts[3]
                        val author = parts[4]
                        val composer = parts[5]
                        val lyricsText = if (parts.size > 6) parts[6] else ""

                        // Parse lyrics
                        val lyrics = parseLyrics(lyricsText)

                        songs.add(
                            SongItem(
                                number = number,
                                title = title,
                                songbook = "Песнь Во...",
                                tune = key,
                                author = author,
                                composer = composer,
                                lyrics = lyrics
                            )
                        )
                    }
                }
            }

            songsLogger.info("loadFromSps: parsed songs.size=${songs.size}")
        } catch (e: Exception) {
            songsLogger.log(Level.SEVERE, "loadFromSps failed for resourcePath=$resourcePath", e)
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
}