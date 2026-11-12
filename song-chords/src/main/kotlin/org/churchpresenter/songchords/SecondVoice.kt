package org.churchpresenter.songchords

/** One run of a lyric line and the voice that sings it: 1, the main voice, 2 or 3. */
data class VoiceRun(val text: String, val voice: Int)

/**
 * The voice markup songs are written in, for parts only some of the people say or sing — the men
 * on one line and the women answering on the next, or a leader and two groups responding in a
 * liturgy — each drawn in a colour of its own. Voice 1 is the main voice; 2 and 3 are the others.
 *
 * Three ways to write it, all inside the lyrics, with `2` or `3` as the voice:
 * - `2>` at the start of a line puts the whole line in voice 2.
 * - `2>(…)` anywhere in a line puts just the words inside the brackets in voice 2.
 * - `[voice: 2]` on a line of its own puts every line after it in voice 2, until `[voice: 1]` or
 *   the next section. It is a directive, like `[background: …]`, not a section.
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

    /** The voices besides the main one that markup can name. */
    val VOICES: IntRange = SECOND..THIRD

    /** What opens a [voice] line, or a [voice] run when a bracket follows it: `2>`, `3>`. */
    fun markOf(voice: Int): String = "$voice>"

    private val VOICE_DIRECTIVE = Regex("^\\[\\s*voice\\s*:\\s*([^\\[\\]]*)]$", RegexOption.IGNORE_CASE)

    /** A `2>(` or `3>(` — where a run in another voice opens. */
    private val SPAN_OPEN = Regex("([$SECOND-$THIRD])>\\(")

    /**
     * The voice a `[voice: n]` line switches to — n for a voice in [VOICES], [MAIN] for anything
     * else — or null when [line] is not one.
     */
    fun voiceDirectiveOf(line: String): Int? {
        val match = VOICE_DIRECTIVE.matchEntire(line.trim()) ?: return null
        return match.groupValues[1].trim().toIntOrNull()?.takeIf { it in VOICES } ?: MAIN
    }

    /** True when [line] is a `[voice: n]` directive — see [voiceDirectiveOf]. */
    fun isVoiceDirective(line: String): Boolean = voiceDirectiveOf(line) != null

    /** True when [line] carries any voice markup. */
    fun hasMarkup(line: String): Boolean = wholeLineVoiceOf(line) != null || SPAN_OPEN.containsMatchIn(line)

    /**
     * [line] split into runs by voice, the markup gone. Runs of one voice are merged, and empty
     * runs dropped, so a line with no markup is one voice-1 run.
     *
     * An unclosed `2>(` runs to the end of the line, which is what someone typing it meant.
     */
    fun runs(line: String): List<VoiceRun> {
        val whole = wholeLineVoiceOf(line)
        if (whole != null) {
            val rest = line.trimStart().removePrefix(markOf(whole)).trimStart()
            // A `3>` line can still hold `2>(…)` runs: those keep their own voice, and the rest of
            // the line is the line's.
            val out = mutableListOf<VoiceRun>()
            runs(rest).forEach { addRun(out, it.text, if (it.voice == MAIN) whole else it.voice) }
            return out.ifEmpty { listOf(VoiceRun("", whole)) }
        }
        val out = mutableListOf<VoiceRun>()
        var cursor = 0
        while (cursor < line.length) {
            val open = SPAN_OPEN.find(line, cursor) ?: break
            addRun(out, line.substring(cursor, open.range.first), MAIN)
            val start = open.range.last + 1
            val close = closingBracket(line, start)
            addRun(out, line.substring(start, close), open.groupValues[1].toInt())
            cursor = (close + 1).coerceAtMost(line.length)
        }
        addRun(out, line.substring(cursor), MAIN)
        return out.ifEmpty { listOf(VoiceRun("", MAIN)) }
    }

    /** [line] with its voice markup taken out and nothing else changed — chords stay. */
    fun plain(line: String): String =
        if (!hasMarkup(line)) line else runs(line).joinToString("") { it.text }

    /**
     * True when no word of [line] is the main voice's: a `2>` or `3>` line, or one whose words all
     * sit inside `2>(…)` / `3>(…)` runs. A line with only some words in another voice is still a
     * main-voice line.
     */
    fun isAnswerLine(line: String): Boolean {
        if (!hasMarkup(line)) return false
        val sung = runs(line).filter { it.text.isNotBlank() }
        return sung.isNotEmpty() && sung.all { it.voice != MAIN }
    }

    /**
     * [line] written as a whole [voice] line — what a `[voice: n]` block makes of each line in it.
     * A line that is already all another voice's is left as it is.
     */
    fun asVoiceLine(line: String, voice: Int): String =
        if (isAnswerLine(line)) line else "${markOf(voice)}${plain(line)}"

    /**
     * The lines that show together in line mode, one range per press of Next. A main-voice line
     * and the answer lines straight after it — in voice 2, voice 3 or both — are one group; every
     * other line shows alone.
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

/**
 * The voice a whole-line mark — `2>` or `3>` not followed by a bracket — at the start of [line]
 * gives it, or null when it has none.
 */
fun wholeLineVoiceOf(line: String): Int? {
    val trimmed = line.trimStart()
    return SecondVoice.VOICES.firstOrNull { v ->
        val mark = SecondVoice.markOf(v)
        trimmed.startsWith(mark) && !trimmed.startsWith("$mark(")
    }
}

/** Appends [text] to [out] in [voice], merged into the last run when that is the same voice. */
private fun addRun(out: MutableList<VoiceRun>, text: String, voice: Int) {
    if (text.isEmpty()) return
    val last = out.lastOrNull()
    if (last != null && last.voice == voice) {
        out[out.lastIndex] = VoiceRun(last.text + text, voice)
    } else {
        out.add(VoiceRun(text, voice))
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
