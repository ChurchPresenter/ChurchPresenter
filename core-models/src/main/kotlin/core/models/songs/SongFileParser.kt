package core.models.songs

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

@Serializable
data class CachedSong(
    val song: SongItem,
    val lastModified: Long = 0L,
    /**
     * File size, checked alongside [lastModified] to decide whether a cached parse is still good.
     *
     * Timestamp alone is not enough: `File.lastModified()` has millisecond resolution at best and a
     * whole second on some filesystems, so a song edited within the same tick as the cache entry
     * looks unchanged and the edit is silently dropped on the next load. Size catches that for any
     * edit that changes the length, which is nearly all of them. Defaults to 0 so caches written by
     * an older build still deserialize — those entries simply miss once and get re-parsed.
     */
    val fileSize: Long = 0L,
)

@Serializable
data class SongCache(
    val storageDirectory: String,
    val songs: List<SongItem> = emptyList(),
    val cachedSongs: List<CachedSong> = emptyList()
)

private const val TITLE_KEY_LENGTH = 6

/** The extension every song in a library carries. */
const val SONG_EXTENSION = "song"

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

    /** The `key: value` lines of a .song header, lowercased keys, unknown keys kept out. */
    private fun parseHeaderFields(headerBody: List<String>): Map<String, String> {
        val known = setOf("author", "composer", "tune", "ccli")
        return headerBody.mapNotNull { raw ->
            val line = raw.trim()
            val colonIndex = line.indexOf(':')
            if (colonIndex <= 0) return@mapNotNull null
            val key = line.substring(0, colonIndex).trim().lowercase()
            if (key !in known) null else key to line.substring(colonIndex + 1).trim()
        }.toMap()
    }

    /** The [Primary]/[Secondary] halves of a .song body, filled a line at a time. */
    private class SongBody(
        private val primaryLyrics: MutableList<String>,
        private val secondaryLyrics: MutableList<String>,
    ) {
        var primaryTitle = ""
        var secondaryTitle = ""
        private var section: String? = null // null, "primary", "secondary"
        private var target: MutableList<String>? = null

        fun consume(line: String) {
            val trimmed = line.trim()
            when {
                trimmed.equals("[Primary]", ignoreCase = true) -> {
                    section = "primary"
                    target = primaryLyrics
                }
                trimmed.equals("[Secondary]", ignoreCase = true) -> {
                    section = "secondary"
                    target = secondaryLyrics
                }
                section == null || target == null -> Unit
                // Title line right after the section tag
                trimmed.startsWith("title:", ignoreCase = true) -> {
                    val titleValue = trimmed.substring(TITLE_KEY_LENGTH).trim()
                    if (section == "primary") primaryTitle = titleValue else secondaryTitle = titleValue
                }
                // Lyric lines (including section headers like [Verse 1], empty lines, etc.)
                else -> target?.add(line)
            }
        }
    }

    fun parseSongContent(content: String, filePath: String = "", songbook: String = ""): SongItem? {
        try {
            val lines = content.lines()

            // Parse YAML-like header between --- markers
            var author = ""
            var composer = ""
            var tune = ""
            var ccli = ""
            val headerStart = lines.indexOfFirst { it.trim() == "---" }
            val headerClose = if (headerStart < 0) -1 else
                lines.subList(headerStart + 1, lines.size).indexOfFirst { it.trim() == "---" }
                    .takeIf { it >= 0 }?.plus(headerStart + 1) ?: -1
            val headerEndIndex = if (headerClose < 0) 0 else headerClose + 1

            if (headerStart >= 0) {
                val headerBody = lines.subList(headerStart + 1, if (headerClose < 0) lines.size else headerClose)
                val header = parseHeaderFields(headerBody)
                author = header["author"].orEmpty()
                composer = header["composer"].orEmpty()
                tune = header["tune"].orEmpty()
                ccli = header["ccli"].orEmpty()
            }

            // Parse body after header
            val bodyLines = if (headerEndIndex > 0) lines.subList(headerEndIndex, lines.size) else lines

            var primaryTitle = ""
            var secondaryTitle = ""
            val primaryLyrics = mutableListOf<String>()
            val secondaryLyrics = mutableListOf<String>()

            val body = SongBody(primaryLyrics, secondaryLyrics)
            for (line in bodyLines) body.consume(line)
            primaryTitle = body.primaryTitle
            secondaryTitle = body.secondaryTitle

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
                sourceFile = filePath,
                ccliNumber = ccli
            )
        } catch (_: Exception) {
            return null
        }
    }

    fun writeSongFile(song: SongItem, filePath: String) {
        val sb = StringBuilder()

        // Write header if any metadata exists
        val hasCredits = song.author.isNotEmpty() || song.composer.isNotEmpty()
        if (hasCredits || song.tune.isNotEmpty() || song.ccliNumber.isNotEmpty()) {
            sb.appendLine("---")
            if (song.author.isNotEmpty()) sb.appendLine("author: ${song.author}")
            if (song.composer.isNotEmpty()) sb.appendLine("composer: ${song.composer}")
            if (song.tune.isNotEmpty()) sb.appendLine("tune: ${song.tune}")
            if (song.ccliNumber.isNotEmpty()) sb.appendLine("ccli: ${song.ccliNumber}")
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
            file.extension.equals(SONG_EXTENSION, ignoreCase = true)
        } ?: emptyArray()
        for (songFile in songFiles.sortedBy { it.name }) {
            val path = songFile.absolutePath
            val lastMod = songFile.lastModified()
            val size = songFile.length()
            val cached = cache[path]

            if (cached != null && cached.lastModified == lastMod && cached.fileSize == size) {
                // File unchanged — reuse cached parse result
                results.add(cached)
            } else {
                // File new or modified — parse it
                val song = parseSongFile(path, songbook)
                if (song != null) {
                    results.add(CachedSong(song = song, lastModified = lastMod, fileSize = size))
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

        private fun readCacheFor(storageDirectory: String): SongCache? = try {
            cacheJson.decodeFromString<SongCache>(cacheFile.readText(StandardCharsets.UTF_8))
                .takeIf { it.storageDirectory == storageDirectory }
        } catch (_: Exception) {
            null
        }

        fun loadSongCache(storageDirectory: String): List<SongItem>? {
            val cache = readCacheFor(storageDirectory) ?: return null
            // Prefer cachedSongs (with timestamps); fall back to legacy songs list
            return if (cache.cachedSongs.isNotEmpty()) cache.cachedSongs.map { it.song }
            else cache.songs.ifEmpty { null }
        }

        fun loadCachedSongMap(storageDirectory: String): Map<String, CachedSong> =
            readCacheFor(storageDirectory)?.cachedSongs?.associateBy { it.song.sourceFile } ?: emptyMap()

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
