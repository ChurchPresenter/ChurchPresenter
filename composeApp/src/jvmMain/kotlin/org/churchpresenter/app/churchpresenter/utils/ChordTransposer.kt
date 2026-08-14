package org.churchpresenter.app.churchpresenter.utils

/**
 * One run of lyric text and the chord that lands on its first syllable.
 *
 * [chord] is empty for text that carries no chord — the leading run of a line that starts on a
 * bare word, for instance. Rendering stacks [chord] above [text] so the two stay aligned however
 * the line wraps.
 */
data class ChordSegment(val chord: String, val text: String)

/**
 * Chord parsing and transposition for songs written in the inline `[G]lyric` convention.
 *
 * Pure and stateless by construction: no Compose, no resources, no I/O. Everything that decides
 * what a chord *is*, what it becomes when moved, and how it should be spelled lives here so it can
 * be tested directly rather than through a dialog.
 *
 * Chords share `[...]` with section headers, which songs have always used. The two never collide in
 * practice — a header occupies a whole line by itself, a chord never does — so [isHeaderLine]
 * resolves the ambiguity positionally, and no existing song file has to change.
 */
object ChordTransposer {

    private val SHARP_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    /**
     * Pitch classes whose keys are conventionally written with flats. Transposing into E flat
     * should print A flat, not G sharp — the notes are the same, but only one of them is how a
     * musician reads it.
     */
    private val FLAT_KEYS = setOf(1, 3, 5, 6, 8, 10)

    private val PITCH = mapOf(
        "C" to 0, "B#" to 0, "C#" to 1, "DB" to 1, "D" to 2, "D#" to 3, "EB" to 3,
        "E" to 4, "FB" to 4, "F" to 5, "E#" to 5, "F#" to 6, "GB" to 6, "G" to 7,
        "G#" to 8, "AB" to 8, "A" to 9, "A#" to 10, "BB" to 10, "B" to 11, "CB" to 11,
    )

    /** A root note, optionally accidental — the part of a chord that moves when transposed. */
    private val ROOT = Regex("[A-G][#b]?")

    /**
     * What counts as a chord rather than a section name. Deliberately strict: `[Verse 1]` and
     * `[Bridge]` must not parse as chords, or a header would vanish into the lyric line.
     */
    private val CHORD = Regex(
        "^[A-G][#b]?" +                                  // root
            "(maj|min|dim|aug|sus|add|m|M)?" +           // quality
            "[0-9]*" +                                   // extension
            "(sus[24]|add[0-9]+|dim|aug)?" +             // trailing modifier
            "(/[A-G][#b]?)?$"                            // slash bass
    )

    /** Everything in brackets, chord or not — used to strip markers back out of a lyric line. */
    private val BRACKETED = Regex("\\[[^\\]]*\\]")

    /** True when [token] (the text between the brackets) names a chord. */
    fun isChord(token: String): Boolean = CHORD.matches(token.trim())

    /**
     * True when [line] is a section header — a whole line wrapped in `[]` or `{}`.
     *
     * Two bracketed lines are not headers. One holding a single chord, as an instrumental writes
     * `[Am]`. And one holding a run of them, as an intro writes `[Cm] [Bb] [Ab] [G]` — that line
     * also opens with `[` and closes with `]`, but what sits between them is further brackets, not
     * a name, so it is a chord line and has to be read as one.
     */
    fun isSectionHeader(line: String): Boolean {
        val t = line.trim()
        val bracketed = (t.startsWith("[") && t.endsWith("]")) || (t.startsWith("{") && t.endsWith("}"))
        if (!bracketed) return false
        val inner = t.substring(1, t.length - 1)
        if (inner.any { it in "[]{}" }) return false
        return !isChord(inner)
    }

    /** The pitch class of a root note, or null when it does not name one. */
    fun pitchOf(root: String): Int? = PITCH[root.uppercase()]

    /** Whether a key is written with flats — see [FLAT_KEYS]. */
    fun prefersFlats(key: String): Boolean = pitchOf(key)?.let { prefersFlats(it) } == true

    /** Whether the key on a pitch class is written with flats — see [FLAT_KEYS]. */
    fun prefersFlats(pitch: Int): Boolean = ((pitch % 12) + 12) % 12 in FLAT_KEYS

    /** Names a pitch class, spelling it with flats or sharps as [flats] asks. */
    fun nameOf(pitch: Int, flats: Boolean): String {
        val i = ((pitch % 12) + 12) % 12
        return if (flats) FLAT_NAMES[i] else SHARP_NAMES[i]
    }

    /**
     * Moves [chord] by [steps] semitones, spelling the result with flats when [flats] is set.
     *
     * Both halves of a slash chord move, and anything that is not a root note — the quality, the
     * extension — is carried through untouched. A token that is not a chord is returned as-is.
     */
    fun transposeChord(chord: String, steps: Int, flats: Boolean = false): String {
        if (!isChord(chord)) return chord
        if (steps == 0) return chord
        return ROOT.replace(chord) { m ->
            val pitch = pitchOf(m.value)
            if (pitch == null) m.value else nameOf(pitch + steps, flats)
        }
    }

    /** Removes every chord marker from [line], leaving the words alone. */
    fun stripChords(line: String): String =
        BRACKETED.replace(line) { m ->
            val inner = m.value.substring(1, m.value.length - 1)
            if (isChord(inner)) "" else m.value
        }

    /** True when [line] carries at least one chord marker. */
    fun hasChords(line: String): Boolean =
        BRACKETED.findAll(line).any { isChord(it.value.substring(1, it.value.length - 1)) }

    /** Every chord in [text], in the order written, transposed by [steps]. */
    fun chordsIn(text: String, steps: Int = 0, flats: Boolean = false): List<String> =
        BRACKETED.findAll(text)
            .map { it.value.substring(1, it.value.length - 1) }
            .filter { isChord(it) }
            .map { transposeChord(it, steps, flats) }
            .toList()

    /**
     * Splits [line] into chord-and-text runs for rendering.
     *
     * With [showChords] off the whole line comes back as one chord-free segment, so the same
     * renderer draws the plain-lyrics view without a second code path.
     */
    fun parseLine(line: String, steps: Int = 0, flats: Boolean = false, showChords: Boolean = true): List<ChordSegment> {
        if (!showChords || !hasChords(line)) {
            return listOf(ChordSegment("", if (showChords) line else stripChords(line)))
        }
        val segments = mutableListOf<ChordSegment>()
        var cursor = 0
        for (match in BRACKETED.findAll(line)) {
            val inner = match.value.substring(1, match.value.length - 1)
            if (!isChord(inner)) continue
            if (match.range.first > cursor) {
                segments.add(ChordSegment("", line.substring(cursor, match.range.first)))
            }
            cursor = match.range.last + 1
            // The chord owns the text up to the next chord marker, or the end of the line.
            val next = BRACKETED.findAll(line)
                .firstOrNull { it.range.first >= cursor && isChord(it.value.substring(1, it.value.length - 1)) }
            val end = next?.range?.first ?: line.length
            segments.add(ChordSegment(transposeChord(inner, steps, flats), line.substring(cursor, end)))
            cursor = end
        }
        if (cursor < line.length) segments.add(ChordSegment("", line.substring(cursor)))
        return segments
    }

    /**
     * The key a song is in, taken as the first chord it names. Falls back to C when it names none,
     * which only matters for the chord palette — a song with no chords never shows one.
     */
    fun detectKey(text: String): String {
        val first = chordsIn(text).firstOrNull() ?: return "C"
        return ROOT.find(first)?.value ?: "C"
    }

    /**
     * The seven chords built on the scale of [key] — the ones a song in that key almost always
     * draws from, offered as a palette to insert from.
     */
    fun diatonicChords(key: String): List<String> {
        val root = pitchOf(key) ?: return emptyList()
        val flats = root in FLAT_KEYS
        val degrees = listOf(0 to "", 2 to "m", 4 to "m", 5 to "", 7 to "", 9 to "m", 11 to "dim")
        return degrees.map { (interval, quality) -> nameOf(root + interval, flats) + quality }
    }
}
