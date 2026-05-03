package org.churchpresenter.app.churchpresenter.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

@Serializable
data class CachedSong(
    val song: SongItem,
    val lastModified: Long = 0L
)

@Serializable
data class SongCache(
    val storageDirectory: String,
    val songs: List<SongItem> = emptyList(),
    val cachedSongs: List<CachedSong> = emptyList()
)

class SongFileParser {

    fun parseSongFile(filePath: String, songbook: String = ""): SongItem? {
        try {
            val path = Paths.get(filePath)
            if (!Files.exists(path)) return null

            val content = Files.readString(path, StandardCharsets.UTF_8)
            return parseSongContent(content, filePath, songbook)
        } catch (_: Exception) {
            return null
        }
    }

    fun parseSongContent(content: String, filePath: String = "", songbook: String = ""): SongItem? {
        try {
            val lines = content.lines()

            // Parse YAML-like header between --- markers
            var author = ""
            var composer = ""
            var tune = ""
            var inHeader = false
            var headerEndIndex = 0

            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line == "---") {
                    if (!inHeader) {
                        inHeader = true
                        continue
                    } else {
                        headerEndIndex = i + 1
                        break
                    }
                }
                if (inHeader) {
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        val key = line.substring(0, colonIndex).trim().lowercase()
                        val value = line.substring(colonIndex + 1).trim()
                        when (key) {
                            "author" -> author = value
                            "composer" -> composer = value
                            "tune" -> tune = value
                        }
                    }
                }
            }

            // Parse body after header
            val bodyLines = if (headerEndIndex > 0) lines.subList(headerEndIndex, lines.size) else lines

            var primaryTitle = ""
            var secondaryTitle = ""
            val primaryLyrics = mutableListOf<String>()
            val secondaryLyrics = mutableListOf<String>()

            var currentSection: String? = null // null, "primary", "secondary"
            var currentTarget: MutableList<String>? = null

            for (line in bodyLines) {
                val trimmed = line.trim()

                when {
                    trimmed.equals("[Primary]", ignoreCase = true) -> {
                        currentSection = "primary"
                        currentTarget = primaryLyrics
                        continue
                    }
                    trimmed.equals("[Secondary]", ignoreCase = true) -> {
                        currentSection = "secondary"
                        currentTarget = secondaryLyrics
                        continue
                    }
                }

                if (currentSection != null && currentTarget != null) {
                    // Check for title line right after section tag
                    if (trimmed.startsWith("title:", ignoreCase = true)) {
                        val titleValue = trimmed.substring(6).trim()
                        if (currentSection == "primary") {
                            primaryTitle = titleValue
                        } else {
                            secondaryTitle = titleValue
                        }
                        continue
                    }

                    // Add lyric lines (including section headers like [Verse 1], empty lines, etc.)
                    currentTarget.add(line)
                }
            }

            // Trim leading/trailing blank lines from lyrics
            trimBlankLines(primaryLyrics)
            trimBlankLines(secondaryLyrics)

            // Extract song number from filename if present (e.g., "0001 - Title.song")
            val fileName = File(filePath).nameWithoutExtension
            val number = extractNumberFromFilename(fileName)

            // Use primary title; fall back to filename
            val title = primaryTitle.ifEmpty { fileName }

            return SongItem(
                number = number,
                title = title,
                songbook = songbook,
                tune = tune,
                author = author,
                composer = composer,
                lyrics = primaryLyrics,
                secondaryTitle = secondaryTitle,
                secondaryLyrics = secondaryLyrics,
                sourceFile = filePath
            )
        } catch (_: Exception) {
            return null
        }
    }

    fun writeSongFile(song: SongItem, filePath: String) {
        val sb = StringBuilder()

        // Write header if any metadata exists
        if (song.author.isNotEmpty() || song.composer.isNotEmpty() || song.tune.isNotEmpty()) {
            sb.appendLine("---")
            if (song.author.isNotEmpty()) sb.appendLine("author: ${song.author}")
            if (song.composer.isNotEmpty()) sb.appendLine("composer: ${song.composer}")
            if (song.tune.isNotEmpty()) sb.appendLine("tune: ${song.tune}")
            sb.appendLine("---")
            sb.appendLine()
        }

        // Write primary section
        sb.appendLine("[Primary]")
        sb.appendLine("title: ${song.title}")
        sb.appendLine()
        for (line in song.lyrics) {
            sb.appendLine(line)
        }

        // Write secondary section if present
        if (song.secondaryTitle.isNotEmpty() || song.secondaryLyrics.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("[Secondary]")
            if (song.secondaryTitle.isNotEmpty()) {
                sb.appendLine("title: ${song.secondaryTitle}")
            }
            sb.appendLine()
            for (line in song.secondaryLyrics) {
                sb.appendLine(line)
            }
        }

        val path = Paths.get(filePath)
        Files.createDirectories(path.parent)
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8)
    }

    private fun extractNumberFromFilename(fileName: String): String {
        // Match patterns like "0001 - Title" or "0001- Title" or "0001-Title"
        val match = Regex("""^(\d+)\s*-\s*""").find(fileName)
        return match?.groupValues?.get(1) ?: ""
    }

    private fun trimBlankLines(lines: MutableList<String>) {
        while (lines.isNotEmpty() && lines.first().isBlank()) {
            lines.removeFirst()
        }
        while (lines.isNotEmpty() && lines.last().isBlank()) {
            lines.removeLast()
        }
    }

    fun loadSongsFromDirectory(
        rootDirectory: String,
        cachedSongs: Map<String, CachedSong> = emptyMap()
    ): List<CachedSong> {
        val rootDir = File(rootDirectory)
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()

        val results = mutableListOf<CachedSong>()
        loadSongsRecursive(rootDir, rootDir, cachedSongs, results)
        return results
    }

    private fun loadSongsRecursive(
        currentDir: File,
        rootDir: File,
        cache: Map<String, CachedSong>,
        results: MutableList<CachedSong>
    ) {
        // Determine songbook from relative path (empty for root)
        val songbook = if (currentDir == rootDir) "" else
            currentDir.toRelativeString(rootDir).replace('\\', '/')

        // Load .song files in this directory
        val songFiles = currentDir.listFiles { file ->
            file.extension.lowercase() == Constants.EXTENSION_SONG
        } ?: emptyArray()
        for (songFile in songFiles.sortedBy { it.name }) {
            val path = songFile.absolutePath
            val lastMod = songFile.lastModified()
            val cached = cache[path]

            if (cached != null && cached.lastModified == lastMod) {
                // File unchanged — reuse cached parse result
                results.add(cached)
            } else {
                // File new or modified — parse it
                val song = parseSongFile(path, songbook)
                if (song != null) {
                    results.add(CachedSong(song = song, lastModified = lastMod))
                }
            }
        }

        // Recurse into subdirectories
        val subdirs = currentDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        for (subdir in subdirs.sortedBy { it.name }) {
            loadSongsRecursive(subdir, rootDir, cache, results)
        }
    }

    companion object {
        private val cacheJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        private val cacheFile = File(System.getProperty("user.home"), ".churchpresenter/song_cache.json")

        fun loadSongCache(storageDirectory: String): List<SongItem>? {
            try {
                if (!cacheFile.exists()) return null
                val cache = cacheJson.decodeFromString<SongCache>(cacheFile.readText(StandardCharsets.UTF_8))
                if (cache.storageDirectory != storageDirectory) return null
                // Prefer cachedSongs (with timestamps); fall back to legacy songs list
                return if (cache.cachedSongs.isNotEmpty()) cache.cachedSongs.map { it.song }
                else cache.songs.ifEmpty { null }
            } catch (_: Exception) {
                return null
            }
        }

        fun loadCachedSongMap(storageDirectory: String): Map<String, CachedSong> {
            try {
                if (!cacheFile.exists()) return emptyMap()
                val cache = cacheJson.decodeFromString<SongCache>(cacheFile.readText(StandardCharsets.UTF_8))
                if (cache.storageDirectory != storageDirectory) return emptyMap()
                return cache.cachedSongs.associateBy { it.song.sourceFile }
            } catch (_: Exception) {
                return emptyMap()
            }
        }

        fun saveSongCache(storageDirectory: String, cachedSongs: List<CachedSong>) {
            try {
                val cache = SongCache(
                    storageDirectory = storageDirectory,
                    songs = cachedSongs.map { it.song },
                    cachedSongs = cachedSongs
                )
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(cacheJson.encodeToString(cache), StandardCharsets.UTF_8)
            } catch (_: Exception) {
            }
        }
    }
}
