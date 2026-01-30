package org.churchpresenter.app.churchpresenter.data

import androidx.compose.runtime.mutableStateListOf
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

        songsLogger.info("loadFromSps: start loading resourcePath='$resourcePath'")

        try {
            val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            val reader = if (inputStream != null) {
                songsLogger.info("loadFromSps: loaded resource from classpath: $resourcePath")
                inputStream.bufferedReader(StandardCharsets.UTF_8)
            } else {
                val path = Paths.get(resourcePath)
                if (Files.exists(path)) {
                    songsLogger.info("loadFromSps: loaded resource from filesystem: ${path.toAbsolutePath()}")
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
                        val category = parts[2]
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

        // Split by verse markers (@$)
        val verses = lyricsText.split("@\$")

        for (verse in verses) {
            if (verse.isBlank()) continue

            // Split lines by @%
            val lines = verse.split("@%")

            for (line in lines) {
                val cleanLine = line.trim()
                if (cleanLine.isNotEmpty()) {
                    lyrics.add(cleanLine)
                }
            }

            // Add empty line between verses
            if (lyrics.isNotEmpty() && !lyrics.last().isBlank()) {
                lyrics.add("")
            }
        }

        // Remove trailing empty line if exists
        if (lyrics.isNotEmpty() && lyrics.last().isBlank()) {
            lyrics.removeAt(lyrics.lastIndex)
        }

        return lyrics
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
                "Contains" -> song.title.contains(query, ignoreCase = true) ||
                            song.number.contains(query, ignoreCase = true)
                "Starts with" -> song.title.startsWith(query, ignoreCase = true) ||
                               song.number.startsWith(query, ignoreCase = true)
                "Exact match" -> song.title.equals(query, ignoreCase = true) ||
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