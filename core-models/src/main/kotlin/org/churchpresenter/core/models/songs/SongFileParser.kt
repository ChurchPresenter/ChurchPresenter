package org.churchpresenter.core.models.songs

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

/** The offset between a `[Translation n]` tag's number and the extra-language slot it names. */
private const val FIRST_NUMBERED_TAG = 2

/** The three alternatives of [LANGUAGE_TAG] that say *which* language: primary, secondary, numbered. */
private const val TAG_KIND_GROUPS = 3

/** [lines] with its leading and trailing blank lines dropped, in place. */
private fun trimBlankLines(lines: MutableList<String>) {
    while (lines.isNotEmpty() && lines.first().isBlank()) {
        lines.removeFirst()
    }
    while (lines.isNotEmpty() && lines.last().isBlank()) {
        lines.removeLast()
    }
}

/** The extension every song in a library carries. */
const val SONG_EXTENSION = "song"

private val BACKGROUND_PREFIXES = listOf(SONG_BACKGROUND_PREFIX, SONG_LOWER_THIRD_BACKGROUND_PREFIX)

/**
 * A language tag opening a half of a `.song` body.
 *
 * `[Primary]`, `[Secondary]` and `[Translation 3]`..`[Translation N]`, each optionally carrying
 * a label after a colon — `[Translation 3: Ukrainian]`. Deliberately a closed set: a lyric
 * section header is written the same way (`[Verse 1]`, `[Chorus]`), so anything not matched
 * here has to stay a line of lyrics rather than open a language nobody asked for.
 */
private val LANGUAGE_TAG = Regex(
    """^\[\s*(?:(primary)|(secondary)|translation\s+(\d+))\s*(?::\s*([^\]]*))?\s*]$""",
    RegexOption.IGNORE_CASE,
)

/**
 * Which language a tag opens: `-1` for the primary, `0` for the secondary, `n` for the
 * `n + 2`th translation. `null` when the tag names a language this build does not carry, which
 * is dropped rather than folded into another one — silently merging a fifth language into the
 * fourth would corrupt both.
 */
private fun languageSlotOf(match: MatchResult): Int? {
    val (primary, secondary, numbered) = match.destructured.toList().take(TAG_KIND_GROUPS)
    return when {
        primary.isNotEmpty() -> -1
        secondary.isNotEmpty() -> 0
        // `[Translation 3]` is the second extra language, so the tag's number is two ahead of
        // the slot it names.
        else -> numbered.toIntOrNull()?.minus(FIRST_NUMBERED_TAG)
            ?.takeIf { it in 1 until MAX_SONG_EXTRA_TRANSLATIONS }
    }
}

/**
 * The tag that opens extra language [index] — the inverse of [LANGUAGE_TAG].
 *
 * Language 0 stays `[Secondary]` rather than becoming `[Translation 2]` so a file this build
 * writes is still read by one that only knows the two-language format.
 */
private fun languageTagFor(index: Int, label: String): String {
    val name = if (index == 0) "Secondary" else "Translation ${index + 2}"
    return if (label.isBlank()) name else "$name: $label"
}

/** One language's half of a .song body, filled a line at a time. */
private class BodyHalf(val label: String) {
    var title = ""
    val lines = mutableListOf<String>()
}

/** The language halves of a .song body, filled a line at a time. */
private class SongBody {
    private val primary = BodyHalf("")
    /** Sparse by slot, so a file that writes `[Translation 4]` and no `[Secondary]` keeps the
     *  gap rather than sliding the fourth language into the second one's place. */
    private val extras = sortedMapOf<Int, BodyHalf>()
    private var target: BodyHalf? = null

    val primaryTitle: String get() = primary.title
    val primaryLines: List<String> get() = primary.lines

    fun consume(line: String) {
        val tag = LANGUAGE_TAG.matchEntire(line.trim())
        if (tag != null) {
            val slot = languageSlotOf(tag)
            val label = tag.groupValues[4].trim()
            target = when {
                slot == null -> null
                slot < 0 -> primary
                else -> extras.getOrPut(slot) { BodyHalf(label) }
            }
            return
        }
        val half = target ?: return
        val trimmed = line.trim()
        // Title line right after the section tag
        if (trimmed.startsWith("title:", ignoreCase = true)) {
            half.title = trimmed.substring(TITLE_KEY_LENGTH).trim()
        } else {
            // Lyric lines (including section headers like [Verse 1], empty lines, etc.)
            half.lines.add(line)
        }
    }

    /** The extra languages, blank-trimmed, packed down to the ones that carry anything. */
    fun extraTranslations(): List<SongTranslation> = extras.values.mapNotNull { half ->
        trimBlankLines(half.lines)
        SongTranslation(label = half.label, title = half.title, lyrics = half.lines.toList())
            .takeUnless { it.isEmpty }
    }
}

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
        val known = setOf("author", "composer", "tune", "ccli") + BACKGROUND_PREFIXES.flatMap(::songBackgroundKeys)
        return headerBody.mapNotNull { raw ->
            val line = raw.trim()
            val colonIndex = line.indexOf(':')
            if (colonIndex <= 0) return@mapNotNull null
            val key = line.substring(0, colonIndex).trim().lowercase()
            if (key !in known) null else key to line.substring(colonIndex + 1).trim()
        }.toMap()
    }

    fun parseSongContent(content: String, filePath: String = "", songbook: String = ""): SongItem? {
        try {
            val lines = content.lines()

            // Parse YAML-like header between --- markers
            var author = ""
            var composer = ""
            var tune = ""
            var ccli = ""
            var background = SongBackground()
            var lowerThirdBackground = SongBackground()
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
                background = songBackgroundFrom(header, SONG_BACKGROUND_PREFIX)
                lowerThirdBackground = songBackgroundFrom(header, SONG_LOWER_THIRD_BACKGROUND_PREFIX)
            }

            // Parse body after header
            val bodyLines = if (headerEndIndex > 0) lines.subList(headerEndIndex, lines.size) else lines

            val body = SongBody()
            for (line in bodyLines) body.consume(line)
            val primaryTitle = body.primaryTitle
            val primaryLyrics = body.primaryLines.toMutableList()

            // Trim leading/trailing blank lines from lyrics
            trimBlankLines(primaryLyrics)
            val extraTranslations = body.extraTranslations()

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
                sourceFile = filePath,
                ccliNumber = ccli,
                background = background,
                lowerThirdBackground = lowerThirdBackground
            ).withTranslations(extraTranslations)
        } catch (_: Exception) {
            return null
        }
    }

    /** Writes [background] under [prefix], or nothing at all when the song inherits. */
    private fun appendBackground(sb: StringBuilder, prefix: String, background: SongBackground) {
        songBackgroundFields(background, prefix).forEach { (key, value) -> sb.appendLine("$key: $value") }
    }

    fun writeSongFile(song: SongItem, filePath: String) {
        val sb = StringBuilder()

        // Write header if any metadata exists
        val credits = listOf(song.author, song.composer, song.tune, song.ccliNumber)
        val hasBackground = song.background.isCustom || song.lowerThirdBackground.isCustom
        if (credits.any { it.isNotEmpty() } || hasBackground) {
            sb.appendLine("---")
            if (song.author.isNotEmpty()) sb.appendLine("author: ${song.author}")
            if (song.composer.isNotEmpty()) sb.appendLine("composer: ${song.composer}")
            if (song.tune.isNotEmpty()) sb.appendLine("tune: ${song.tune}")
            if (song.ccliNumber.isNotEmpty()) sb.appendLine("ccli: ${song.ccliNumber}")
            appendBackground(sb, SONG_BACKGROUND_PREFIX, song.background)
            appendBackground(sb, SONG_LOWER_THIRD_BACKGROUND_PREFIX, song.lowerThirdBackground)
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

        // Write each extra language, tagged by its position. Only the ones that carry something,
        // so a one- or two-language song still writes exactly the file it always did and a whole
        // library does not churn on the first save after upgrading.
        song.extraTranslations().forEachIndexed { index, translation ->
            if (translation.isEmpty) return@forEachIndexed
            sb.appendLine()
            sb.appendLine("[${languageTagFor(index, translation.label)}]")
            if (translation.title.isNotEmpty()) {
                sb.appendLine("title: ${translation.title}")
            }
            sb.appendLine()
            for (line in translation.lyrics) {
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
