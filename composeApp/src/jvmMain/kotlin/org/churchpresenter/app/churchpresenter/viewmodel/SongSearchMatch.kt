package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.sharedui.utils.isChorusHeader
import org.churchpresenter.sharedui.utils.isHeaderLine
import org.churchpresenter.sharedui.utils.isSlideBreak
import org.churchpresenter.sharedui.utils.songBackgroundDirectiveOf
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTranslation
import org.churchpresenter.songchords.ChordTransposer
import org.churchpresenter.songchords.SongSectionWordGroup
import org.churchpresenter.songchords.SongSectionWords

private val WHITESPACE = Regex("\\s+")
private const val SNIPPET_BEFORE = 12
private const val SNIPPET_AFTER = 60
private const val ELLIPSIS = "…"

/** What part of a song a search matched, which decides the chip the results list draws. */
enum class SongMatchKind { TITLE, VERSE, CHORUS, OTHER_SECTION, LYRICS }

/**
 * Where a text search found a song: the part it matched, in which of the song's languages, and the
 * words around the match.
 *
 * @property sectionName the section's own name from the song file, brackets off ("Verse 1",
 *   "Приспів"); null for a title match or lyrics with no header above them.
 * @property languageIndex the position in [SongItem.translationList]; 0 is the song's own language.
 * @property languageLabel the name typed for that language, which may be blank.
 * @property snippet the text to show beside the chip; null when the match is the primary title, which
 *   the list already shows.
 */
data class SongSearchMatch(
    val kind: SongMatchKind,
    val sectionName: String?,
    val languageIndex: Int,
    val languageLabel: String,
    val snippet: String?,
)

/** One section's searchable text: its header (if any) and its lyric lines, chords off, on one line. */
internal data class SearchableSection(val header: String?, val text: String)

/** The lyrics of one language, split at its section headers, as the search reads them. */
internal fun searchableSections(lyrics: List<String>): List<SearchableSection> {
    val sections = mutableListOf<SearchableSection>()
    var header: String? = null
    val lines = mutableListOf<String>()
    fun flush() {
        val text = lines.joinToString(" ").replace(WHITESPACE, " ").trim()
        if (text.isNotEmpty()) sections += SearchableSection(header, text)
        lines.clear()
    }
    for (line in lyrics) {
        when {
            isHeaderLine(line) -> { flush(); header = line.trim() }
            isSlideBreak(line) || songBackgroundDirectiveOf(line) != null -> Unit
            else -> lines += ChordTransposer.stripChords(line)
        }
    }
    flush()
    return sections
}

/**
 * Where [query] matches [song], or null when it does not.
 *
 * Titles first -- the song's own, then each translation's -- and then each language's lyrics section
 * by section, in the order the song lists them; the first hit wins. A phrase that only matches
 * across the end of one section and the start of the next is still reported, as [SongMatchKind.LYRICS]
 * with no section name.
 *
 * [sectionsOf] supplies each language's [searchableSections]; the view model passes a cached one.
 */
internal fun findSongMatch(
    song: SongItem,
    query: String,
    sectionsOf: (languageIndex: Int, lyrics: List<String>) -> List<SearchableSection> =
        { _, lyrics -> searchableSections(lyrics) },
): SongSearchMatch? {
    val q = query.trim().replace(WHITESPACE, " ")
    if (q.isEmpty()) return null
    val languages = song.translationList()
    return titleMatch(languages, q)
        ?: languages.withIndex().firstNotNullOfOrNull { (index, language) ->
            lyricsMatch(index, language.label, sectionsOf(index, language.lyrics), q)
        }
}

/** The first language whose title holds [q]; the song's own title is not repeated as a snippet. */
private fun titleMatch(languages: List<SongTranslation>, q: String): SongSearchMatch? =
    languages.withIndex()
        .firstOrNull { (_, language) -> language.title.isNotBlank() && language.title.contains(q, ignoreCase = true) }
        ?.let { (index, language) ->
            SongSearchMatch(
                kind = SongMatchKind.TITLE,
                sectionName = null,
                languageIndex = index,
                languageLabel = language.label,
                snippet = if (index == 0) null else language.title,
            )
        }

/** The first of one language's [sections] holding [q], or the run of them a phrase spans. */
private fun lyricsMatch(index: Int, label: String, sections: List<SearchableSection>, q: String): SongSearchMatch? {
    val inSection = sections.firstNotNullOfOrNull { section ->
        section.text.indexOf(q, ignoreCase = true).takeIf { it >= 0 }?.let { at ->
            val (kind, name) = kindOf(section.header)
            SongSearchMatch(kind, name, index, label, snippetAround(section.text, at, q.length))
        }
    }
    if (inSection != null) return inSection
    val whole = sections.joinToString(" ") { it.text }
    val at = whole.indexOf(q, ignoreCase = true)
    if (at < 0) return null
    return SongSearchMatch(SongMatchKind.LYRICS, null, index, label, snippetAround(whole, at, q.length))
}

/** The chip a section's header earns, and the name to write on it. */
private fun kindOf(header: String?): Pair<SongMatchKind, String?> {
    if (header == null) return SongMatchKind.LYRICS to null
    val name = header.trim().trim('[', ']', '{', '}').trim()
    val kind = when {
        isChorusHeader(header) -> SongMatchKind.CHORUS
        else -> when (SongSectionWords.groupOf(name)) {
            SongSectionWordGroup.VERSE -> SongMatchKind.VERSE
            SongSectionWordGroup.CHORUS, SongSectionWordGroup.PRE_CHORUS -> SongMatchKind.CHORUS
            else -> SongMatchKind.OTHER_SECTION
        }
    }
    return kind to name.ifEmpty { null }
}

/**
 * A few words either side of the match at [at], cut at word boundaries, with an ellipsis wherever
 * [text] was trimmed.
 */
internal fun snippetAround(text: String, at: Int, length: Int): String {
    var start = (at - SNIPPET_BEFORE).coerceAtLeast(0)
    var end = (at + length + SNIPPET_AFTER).coerceAtMost(text.length)
    if (start > 0) {
        val space = text.indexOf(' ', start)
        if (space in start until at) start = space + 1
    }
    if (end < text.length) {
        val space = text.lastIndexOf(' ', end)
        if (space >= at + length) end = space
    }
    val body = text.substring(start, end).trim()
    return (if (start > 0) ELLIPSIS else "") + body + (if (end < text.length) ELLIPSIS else "")
}
