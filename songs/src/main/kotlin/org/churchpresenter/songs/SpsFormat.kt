package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.isHeaderLine
import org.churchpresenter.songchords.SongSectionWords
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

private const val SPS_FIELD_SEPARATOR = "#\$#"
private const val SPS_MIN_FIELDS = 6
private const val SQLITE_COL_TUNE = 3
private const val SQLITE_COL_AUTHOR = 4
private const val SQLITE_COL_COMPOSER = 5

/**
 * Load items from a SQLite-format .sps file (Mac SongPresenter).
 */
internal fun loadSpsSqlite(file: java.io.File): List<SongItem> {
    val items = mutableListOf<SongItem>()
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
    return items
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

internal fun parseLyrics(lyricsText: String): List<String> {
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
        val hasMoreSections = sections.subList(i + 1, sections.size)
            .any { it.type != Constants.SECTION_TYPE_CHORUS }
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

/** Update a song in a specific .sps file */
internal fun updateSongInFile(filePath: String, originalSong: SongItem, updatedSong: SongItem): Boolean = try {
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
    val lyricsText = spsLyricsText(song.lyrics)
    return listOf(
        song.number, song.title, categoryId, song.tune, song.author, song.composer, lyricsText
    ).joinToString(SPS_FIELD_SEPARATOR)
}

/**
 * Format lyrics list back to SPS format
 * Converts List<String> back to the @$ and @% delimited format
 */
internal fun Songs.formatLyricsForSps(lyrics: List<String>): String = spsLyricsText(lyrics)

/** [lyrics] in the `.sps` field format: sections joined by `@$`, lines within one by `@%`. */
private fun spsLyricsText(lyrics: List<String>): String {
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
