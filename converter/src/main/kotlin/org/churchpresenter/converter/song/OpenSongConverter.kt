package org.churchpresenter.converter.song

import java.io.File

data class OpenSongSong(
    val title: String,
    val author: String,
    val copyright: String,
    val ccli: String,
    val hymnNumber: String,
    val verseOrder: List<String>,
    val sections: List<SongSection>,
)

/**
 * OpenSong song files: XML metadata wrapped around a plain-text `<lyrics>` body.
 *
 * The body is the whole format, and four of its conventions decide whether an import is lyrics or
 * rubbish:
 *  - `[V1]`, `[C]`, `[B]` on their own line open a section; the word may be spelled out.
 *  - a line starting with `.` is a chord line and a line starting with `;` is a comment — both are
 *    dropped, and keeping either turns chord symbols into sung words.
 *  - lyric lines carry one leading space. Files written by hand often omit it, so it is stripped
 *    when present rather than required.
 *  - inside an unnumbered `[V]` block a leading **digit** says which verse the line belongs to, so
 *    one block holds every verse of the song. That digit is only read as a verse number when the
 *    marker itself has none — otherwise `[V1]` followed by "3 crosses stood there" would lose its
 *    first word.
 *
 * OpenSong files usually have no extension at all, which is why the format accepts extensionless
 * files in the picker.
 */
object OpenSongConverter {

    private val underscoreRuns = Regex("_{2,}")
    private val chordPadding = Regex(" {2,}")
    private val markerLine = Regex("""^\[([^]]*)]""")

    fun parse(file: File): OpenSongSong {
        val root = xmlRootOf(file, "song")
        val presentation = root.childText("presentation")
            .split(Regex("""\s+""")).filter { it.isNotBlank() }
        return OpenSongSong(
            title = root.childText("title"),
            author = root.childText("author"),
            copyright = root.childText("copyright"),
            ccli = root.childText("ccli"),
            hymnNumber = root.childText("hymn_number"),
            verseOrder = presentation,
            sections = sectionsOf(root.childElement("lyrics")?.textContent.orEmpty(), presentation),
        )
    }

    fun convert(input: File, outputFile: File) {
        val song = parse(input)
        outputFile.writeText(MarkdownToSongConverter.buildSongContent(asParsed(song, input)), Charsets.UTF_8)
    }

    internal fun asParsed(song: OpenSongSong, input: File): ParsedSong = ParsedSong(
        title = song.title.ifBlank { input.nameWithoutExtension },
        author = song.author,
        copyright = song.copyright,
        sections = song.sections,
    )

    /** Splits a `<lyrics>` body into sections, ordered by `<presentation>` when it has one. */
    internal fun sectionsOf(body: String, presentation: List<String>): List<SongSection> {
        val collected = collect(body)
        val ordered = orderKeys(collected.keys.toList(), presentation)
        val labels = SectionLabel.tidy(ordered.map { SectionLabel.of(it) })
        return ordered.mapIndexed { index, key -> SongSection(labels[index], collected.getValue(key)) }
    }

    private fun collect(body: String): LinkedHashMap<String, MutableList<String>> {
        val collected = LinkedHashMap<String, MutableList<String>>()
        var marker = "V1"
        for (raw in body.lines()) {
            val trimmed = raw.trim()
            val opened = markerLine.find(trimmed)?.groupValues?.get(1)?.trim()
            when {
                opened != null -> marker = opened.ifEmpty { marker }
                isNoise(trimmed) -> Unit
                else -> {
                    val (key, text) = lyricLine(raw, marker)
                    if (text.isNotBlank()) collected.getOrPut(key) { mutableListOf() }.add(text)
                }
            }
        }
        return collected
    }

    /** Which section the line belongs to, and the line with OpenSong's own markup taken off. */
    private fun lyricLine(raw: String, marker: String): Pair<String, String> {
        val first = raw.firstOrNull()
        val markerIsNumbered = marker.any { it.isDigit() }
        return if (first != null && first.isDigit() && !markerIsNumbered) {
            "$marker$first" to clean(raw.substring(1))
        } else {
            marker to clean(raw)
        }
    }

    /**
     * A lyric line with OpenSong's own markup taken off.
     *
     * The runs of `_` and the runs of spaces are both there to line syllables up under the chord
     * line above them — real files read `A______ma________zing grace! How   sweet the  sound!` —
     * so a line kept verbatim reaches the screen with the chord grid still in it.
     */
    private fun clean(text: String): String = text
        .removePrefix(" ")
        .replace(underscoreRuns, "")
        .replace("|", "")
        .replace(chordPadding, " ")
        .trimEnd()

    private fun isNoise(trimmed: String): Boolean =
        trimmed.isEmpty() ||
            trimmed.startsWith(';') ||
            trimmed.startsWith('.') ||
            trimmed.startsWith("---") ||
            trimmed.startsWith("-!!") ||
            trimmed.all { it == '|' }

    private fun orderKeys(keys: List<String>, presentation: List<String>): List<String> {
        if (presentation.isEmpty()) return keys
        val byName = keys.associateBy { it.uppercase() }
        val ordered = LinkedHashSet<String>()
        presentation.forEach { token -> byName[token.uppercase()]?.let { ordered.add(it) } }
        ordered.addAll(keys)
        return ordered.toList()
    }
}
