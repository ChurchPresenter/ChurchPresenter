package org.churchpresenter.app.churchpresenter.utils

/**
 * Turns a chords-over-lyrics sheet — the layout every chord site publishes, where a line of chords
 * is positioned by spaces over the line it belongs to — into the inline `[G]lyric` form the editor
 * writes.
 *
 * Pure and stateless: no Compose, no clipboard, no I/O. The editor decides when to run this; all
 * this decides is what the text becomes.
 *
 * The column a chord starts on is the character it lands on, which is exactly how the sheet was
 * written, so the conversion is arithmetic rather than guesswork. Nothing is inferred beyond that:
 * a section repeated later without its own chord line — which sheets do constantly, expecting the
 * player to reuse the first one — is left as plain lyrics rather than having chords copied onto it,
 * because a repeat whose arrangement differs would then be silently wrong.
 */
object ChordSheetImporter {

    private val URL = Regex("^\\s*https?://\\S+\\s*$")

    /** Splits a line into its whitespace-separated tokens with the column each one starts on. */
    private fun anchors(line: String): List<Pair<Int, String>> =
        Regex("\\S+").findAll(line).map { it.range.first to it.value }.toList()

    /**
     * True when [line] is a row of chords rather than words.
     *
     * A single bare letter is not enough on its own — `A` is as likely to be a lyric as a chord —
     * unless it is indented, which is what a positioned chord always is.
     */
    fun isChordLine(line: String): Boolean {
        if (line.isBlank()) return false
        val tokens = anchors(line).map { it.second }
        if (tokens.isEmpty() || !tokens.all { ChordTransposer.isChord(it) }) return false
        return tokens.size >= 2 || tokens.first().length >= 2 || line.first().isWhitespace()
    }

    /**
     * The marker a section heading becomes, or null when the line is not a heading.
     *
     * Canonicalised to English on purpose — [isChorusHeader] keys the chorus off braces, and
     * everything downstream reads the marker rather than the language it was written in. The words
     * themselves come from [SongSectionWords], shared with the parser and the preview's colouring.
     */
    fun sectionMarkerOf(line: String, verseCounter: () -> Int): String? {
        val t = line.trim()
        if (t.isEmpty()) return null
        // Already in the app's own form — take it as it stands.
        if (ChordTransposer.isSectionHeader(t)) return t
        val group = SongSectionWords.looseGroupOf(t) ?: return null
        val name = SongSectionWords.CANONICAL.getValue(group)
        if (group == SongSectionWordGroup.CHORUS) return "{$name}"
        if (group != SongSectionWordGroup.VERSE) return "[$name]"
        val number = Regex("\\d+").find(t)?.value?.toIntOrNull() ?: verseCounter()
        return "[$name $number]"
    }

    /**
     * Puts each chord from [chordLine] into [lyricLine] at the column it was written over.
     *
     * Chords hanging past the end of the words — a turnaround written after the last syllable —
     * land at the end rather than being dropped.
     */
    fun merge(chordLine: String, lyricLine: String): String {
        val lyric = lyricLine.trimEnd()
        val out = StringBuilder()
        var cursor = 0
        anchors(chordLine).forEach { (column, chord) ->
            val at = column.coerceIn(cursor, lyric.length)
            out.append(lyric, cursor, at)
            out.append('[').append(chord).append(']')
            cursor = at
        }
        if (cursor < lyric.length) out.append(lyric, cursor, lyric.length)
        return out.toString()
    }

    /** True when [text] carries at least one positioned chord line, and so is worth converting. */
    fun looksLikeChordSheet(text: String): Boolean = text.lines().any { isChordLine(it) }

    /**
     * Converts a whole sheet.
     *
     * A chord line takes the next line of words as its own. A chord line with nothing under it —
     * an intro, or a turnaround between verses — becomes a line of bare markers, which is what it
     * is. Source URLs are dropped.
     */
    fun convert(text: String): String {
        val lines = text.lines()
        val out = mutableListOf<String>()
        var verses = 0
        var index = 0

        while (index < lines.size) {
            val line = lines[index]

            if (URL.matches(line)) {
                index++
                continue
            }

            val marker = sectionMarkerOf(line) { verses + 1 }
            if (marker != null) {
                if (marker.startsWith("[Verse")) verses++
                if (out.isNotEmpty() && out.last().isNotBlank()) out.add("")
                out.add(marker)
                index++
                continue
            }

            if (isChordLine(line)) {
                val next = lines.getOrNull(index + 1)
                val isLyricLine = next != null && next.isNotBlank() && !isChordLine(next)
            if (isLyricLine && sectionMarkerOf(next) { 0 } == null) {
                    out.add(merge(line, next))
                    index += 2
                } else {
                    out.add(anchors(line).joinToString(" ") { "[${it.second}]" })
                    index++
                }
                continue
            }

            out.add(line.trimEnd())
            index++
        }

        // Collapse the runs of blank lines the section breaks can leave behind.
        return out
            .filterIndexed { i, l -> l.isNotBlank() || (i > 0 && out[i - 1].isNotBlank()) }
            .joinToString("\n")
            .trim()
    }
}
