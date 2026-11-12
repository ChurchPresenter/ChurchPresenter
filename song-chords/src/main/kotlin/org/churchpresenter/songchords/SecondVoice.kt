package org.churchpresenter.songchords

/**
 * One run of a lyric line and who says it: 1, the main voice, 2 or 3 — or [SecondVoice.CUSTOM],
 * a colour of its own, written as the hex [color].
 */
data class VoiceRun(val text: String, val voice: Int, val color: String = "") {
    /** The voice or colour this run is in. */
    val key: VoiceKey get() = VoiceKey(voice, color)
}

/**
 * What a mark names: a voice — [SecondVoice.MAIN], [SecondVoice.SECOND], [SecondVoice.THIRD] — or,
 * with [SecondVoice.CUSTOM], a colour of its own written as `#RRGGBB` / `#AARRGGBB` in [color].
 */
data class VoiceKey(val voice: Int, val color: String = "") {
    /** What opens a line or a run in this key: `2>`, `3>`, `#E91E63>`. */
    val mark: String get() = if (voice == SecondVoice.CUSTOM) "$color>" else "$voice>"

    companion object {
        /** The main voice: no mark at all. */
        val MAIN = VoiceKey(SecondVoice.MAIN)

        /** A colour of its own, written as [hex]. */
        fun color(hex: String) = VoiceKey(SecondVoice.CUSTOM, hex)
    }
}

/**
 * The voice markup songs are written in, for parts only some of the people say or sing — the men
 * on one line and the women answering on the next, or a leader and two groups responding in a
 * liturgy — each drawn in a colour of its own. Voice 1 is the main voice; 2 and 3 are the others,
 * coloured as the profile or the song says. A hex colour in a voice's place colours the words
 * that colour, for as many parts as a service needs — a wedding's two voices and its leader.
 *
 * Three ways to write it, all inside the lyrics, with `2`, `3` or a colour such as `#E91E63`:
 * - `2>` at the start of a line puts the whole line in voice 2.
 * - `2>(…)` anywhere in a line puts just the words inside the brackets in voice 2.
 * - `[voice: 2]` on a line of its own puts every line after it in voice 2, until `[voice: 1]` or
 *   the next section; `[color: #E91E63]` does the same in a colour, until `[color]`, `[voice: 1]`
 *   or the next section. Both are directives, like `[background: …]`, not sections.
 *
 * Chords pass through untouched: `2>([G]now I see)` is voice-2 words with a chord on them, and
 * stripping the chords and stripping the voice markup can happen in either order.
 *
 * Pure, like [ChordTransposer]: no model, no Compose, no I/O.
 */
object SecondVoice {

    /** What opens a voice-2 line, or a voice-2 run when a bracket follows it — see [markOf]. */
    const val MARK = "2>"

    /** The main voice, and what a [voiceDirectiveOf] line hands back for any value not in [VOICES]. */
    const val MAIN = 1

    /** The second voice. */
    const val SECOND = 2

    /** The third voice. */
    const val THIRD = 3

    /** Not a voice: words in a colour of their own, the colour carried with them as hex. */
    const val CUSTOM = 0

    /** The voices besides the main one that markup can name. */
    val VOICES: IntRange = SECOND..THIRD

    /** What opens a [voice] line, or a [voice] run when a bracket follows it: `2>`, `3>`. */
    fun markOf(voice: Int): String = "$voice>"

    private val VOICE_DIRECTIVE = Regex("^\\[\\s*voice\\s*:\\s*([^\\[\\]]*)]$", RegexOption.IGNORE_CASE)

    /**
     * The voice a `[voice: n]` line switches to — n for a voice in [VOICES], [MAIN] for anything
     * else — or null when [line] is not one. `[color: …]` lines are read by [blockKeyOf].
     */
    fun voiceDirectiveOf(line: String): Int? {
        val match = VOICE_DIRECTIVE.matchEntire(line.trim()) ?: return null
        return match.groupValues[1].trim().toIntOrNull()?.takeIf { it in VOICES } ?: MAIN
    }

    /** True when [line] is a `[voice: n]` or `[color: …]` directive — see [blockKeyOf]. */
    fun isVoiceDirective(line: String): Boolean =
        voiceDirectiveOf(line) != null || COLOR_DIRECTIVE.matches(line.trim())

    /** True when [line] carries any voice markup. */
    fun hasMarkup(line: String): Boolean = wholeLineKeyOf(line) != null || VOICE_RUN_OPEN.containsMatchIn(line)

    /**
     * [line] split into runs by voice, the markup gone. Runs of one voice are merged, and empty
     * runs dropped, so a line with no markup is one voice-1 run.
     *
     * An unclosed `2>(` runs to the end of the line, which is what someone typing it meant.
     */
    fun runs(line: String): List<VoiceRun> {
        val whole = VOICE_LINE_MARK.find(line)
        if (whole != null) {
            val key = keyOfMark(whole.groupValues[1])
            val rest = line.substring(whole.range.last + 1).trimStart()
            // A `3>` line can still hold `2>(…)` runs: those keep their own voice, and the rest of
            // the line is the line's.
            val out = mutableListOf<VoiceRun>()
            runs(rest).forEach { addRun(out, it.text, if (it.voice == MAIN) key else it.key) }
            return out.ifEmpty { listOf(VoiceRun("", key.voice, key.color)) }
        }
        val out = mutableListOf<VoiceRun>()
        var cursor = 0
        while (cursor < line.length) {
            val open = VOICE_RUN_OPEN.find(line, cursor) ?: break
            addRun(out, line.substring(cursor, open.range.first), VoiceKey.MAIN)
            val start = open.range.last + 1
            val close = closingBracket(line, start)
            addRun(out, line.substring(start, close), keyOfMark(open.groupValues[1]))
            cursor = (close + 1).coerceAtMost(line.length)
        }
        addRun(out, line.substring(cursor), VoiceKey.MAIN)
        return out.ifEmpty { listOf(VoiceRun("", MAIN)) }
    }

    /** [line] with its voice markup taken out and nothing else changed — chords stay. */
    fun plain(line: String): String =
        if (!hasMarkup(line)) line else runs(line).joinToString("") { it.text }

    /**
     * True when no word of [line] is the main voice's: a `2>`, `3>` or coloured line, or one whose
     * words all sit inside runs like those. A line with only some words marked is still a
     * main-voice line.
     */
    fun isAnswerLine(line: String): Boolean {
        if (!hasMarkup(line)) return false
        val sung = runs(line).filter { it.text.isNotBlank() }
        return sung.isNotEmpty() && sung.all { it.voice != MAIN }
    }

    /**
     * [line] written as a whole [key] line — what a `[voice: n]` or `[color: …]` block makes of
     * each line in it. A line that is already all another voice's is left as it is.
     */
    fun asVoiceLine(line: String, key: VoiceKey): String =
        if (isAnswerLine(line)) line else "${key.mark}${plain(line)}"

    /**
     * The lines that show together in line mode, one range per press of Next. A main-voice line
     * and the answer lines straight after it — in voice 2, voice 3, a colour, or several — are one
     * group; every other line shows alone.
     */
    fun lineGroups(lines: List<String>): List<IntRange> {
        val groups = mutableListOf<IntRange>()
        var i = 0
        while (i < lines.size) {
            var end = i
            if (!isAnswerLine(lines[i])) {
                while (end + 1 < lines.size && isAnswerLine(lines[end + 1])) end++
            }
            groups.add(i..end)
            i = end + 1
        }
        return groups
    }

    /** The group [index] falls in — see [lineGroups] — or [index] alone when it is out of range. */
    fun lineGroupAt(lines: List<String>, index: Int): IntRange =
        lineGroups(lines).firstOrNull { index in it } ?: index..index
}

/** A colour as a mark writes it: `#RRGGBB` or `#AARRGGBB`. */
private const val HEX = "#(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6})"

/** What a mark names before its `>`: a voice's number or a colour. */
private const val MARK_NAME = "([${SecondVoice.SECOND}-${SecondVoice.THIRD}]|$HEX)"

/**
 * A whole-line mark at the start of a line — `2>`, `3>` or `#E91E63>`, not followed by a bracket.
 * Group 1 is what it names; read it with [keyOfMark].
 */
val VOICE_LINE_MARK = Regex("^\\s*$MARK_NAME>(?!\\()")

/** Where a run opens — `2>(`, `3>(` or `#E91E63>(`. Group 1 is what it names; read it with [keyOfMark]. */
val VOICE_RUN_OPEN = Regex("$MARK_NAME>\\(")

private val HEX_COLOR = Regex("^$HEX$")

private val COLOR_DIRECTIVE = Regex("^\\[\\s*colou?r\\s*(?::\\s*([^\\[\\]]*))?]$", RegexOption.IGNORE_CASE)

/** The key a mark's name — group 1 of [VOICE_LINE_MARK] or [VOICE_RUN_OPEN] — stands for. */
fun keyOfMark(name: String): VoiceKey =
    if (name.startsWith("#")) VoiceKey.color(name) else VoiceKey(name.toInt())

/** The key a whole-line mark at the start of [line] gives it, or null when it has none. */
fun wholeLineKeyOf(line: String): VoiceKey? = VOICE_LINE_MARK.find(line)?.let { keyOfMark(it.groupValues[1]) }

/**
 * The key a block directive switches to — a voice for `[voice: n]`, a colour for
 * `[color: #E91E63]`, and the main voice for `[voice: 1]`, `[color]` or a colour that is not hex —
 * or null when [line] is not one.
 */
fun blockKeyOf(line: String): VoiceKey? {
    SecondVoice.voiceDirectiveOf(line)?.let { return VoiceKey(it) }
    val match = COLOR_DIRECTIVE.matchEntire(line.trim()) ?: return null
    val hex = match.groupValues[1].trim()
    return if (HEX_COLOR.matches(hex)) VoiceKey.color(hex) else VoiceKey.MAIN
}

/** Appends [text] to [out] in [key], merged into the last run when that is in the same key. */
private fun addRun(out: MutableList<VoiceRun>, text: String, key: VoiceKey) {
    if (text.isEmpty()) return
    val last = out.lastOrNull()
    if (last != null && last.key == key) {
        out[out.lastIndex] = last.copy(text = last.text + text)
    } else {
        out.add(VoiceRun(text, key.voice, key.color))
    }
}

/** Where the bracket opened just before [start] closes, counting the ones nested inside it. */
private fun closingBracket(line: String, start: Int): Int {
    var depth = 1
    for (i in start until line.length) {
        when (line[i]) {
            '(' -> depth++
            ')' -> if (--depth == 0) return i
        }
    }
    return line.length
}
