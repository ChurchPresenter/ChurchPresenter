package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import androidx.compose.runtime.mutableStateListOf
import org.churchpresenter.settings.utils.Constants
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

private const val MIN_SPS_FILE_BYTES = 16
private const val SPS_MIN_FIELDS = 6

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
                items.addAll(loadSpsSqlite(spsFile))
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
}
