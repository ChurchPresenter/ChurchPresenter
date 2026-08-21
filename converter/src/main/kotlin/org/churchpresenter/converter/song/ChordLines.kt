package org.churchpresenter.converter.song

/**
 * Rows of chords written above the words, and what they become in the inline `[G]lyric` convention
 * ChurchPresenter stores songs in.
 *
 * The grammar is the app's own, repeated here rather than shared because this module builds
 * standalone and cannot see `ChordTransposer`. It is deliberately strict for the same reason it is
 * there: a section name must never parse as a chord, or a heading would vanish into a lyric.
 *
 * A row is turned into bare markers — `[Bb] [Bb/D] [Eb]` — rather than merged into the line beneath
 * it. Merging places each chord on the column it was written over, which is only right when the
 * source positioned them there; song packs mostly list a section's chords instead, so merging by
 * column lands them mid-word.
 */
internal object ChordLines {

    private val chord = Regex(
        "^[A-G][#b]?" + // root
            "(maj|min|dim|aug|sus|add|m|M)?" + // quality
            "[0-9]*" + // extension
            "(sus[24]|add[0-9]+|dim|aug)?" + // trailing modifier
            "(/[A-G][#b]?)?$", // slash bass
    )

    private val token = Regex("""\S+""")

    fun isChord(text: String): Boolean = chord.matches(text.trim())

    /**
     * True when [line] is a row of chords rather than words.
     *
     * A single bare letter is not enough on its own — `A` is as likely to be a lyric as a chord —
     * unless it is indented, which is what a chord positioned over a syllable always is.
     */
    fun isChordLine(line: String): Boolean {
        if (line.isBlank()) return false
        val tokens = token.findAll(line).map { it.value }.toList()
        if (!tokens.all { isChord(it) }) return false
        return tokens.size >= 2 || tokens.first().length >= 2 || line.first().isWhitespace()
    }

    /** `Bb Bb/D Eb` as `[Bb] [Bb/D] [Eb]`. */
    fun bracket(line: String): String = token.findAll(line).joinToString(" ") { "[${it.value}]" }
}
