package org.churchpresenter.songchords

/** One run of a lyric line and the voice that sings it: 1, the main voice, or 2. */
data class VoiceRun(val text: String, val voice: Int)

/**
 * The second-voice markup songs are written in, for a part only some of the people sing — the men
 * on one line and the women answering on the next, drawn in a colour of its own.
 *
 * Three ways to write it, all inside the lyrics:
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

    /** What opens a voice-2 line, or a voice-2 run when a bracket follows it. */
    const val MARK = "2>"

    private const val SPAN_OPEN = "$MARK("

    /** The voice a [voiceDirectiveOf] line hands back for any value that is not `2`. */
    const val MAIN = 1

    /** The second voice. */
    const val SECOND = 2

    private val VOICE_DIRECTIVE = Regex("^\\[\\s*voice\\s*:\\s*([^\\[\\]]*)]$", RegexOption.IGNORE_CASE)

    /**
     * The voice a `[voice: n]` line switches to — [SECOND] for `2`, [MAIN] for anything else — or
     * null when [line] is not one.
     */
    fun voiceDirectiveOf(line: String): Int? {
        val match = VOICE_DIRECTIVE.matchEntire(line.trim()) ?: return null
        return if (match.groupValues[1].trim() == SECOND.toString()) SECOND else MAIN
    }

    /** True when [line] is a `[voice: n]` directive — see [voiceDirectiveOf]. */
    fun isVoiceDirective(line: String): Boolean = voiceDirectiveOf(line) != null

    /** True when [line] carries any voice markup. */
    fun hasMarkup(line: String): Boolean = line.trimStart().startsWith(MARK) || SPAN_OPEN in line

    /**
     * [line] split into runs by voice, the markup gone. Runs of one voice are merged, and empty
     * runs dropped, so a line with no markup is one voice-1 run.
     *
     * An unclosed `2>(` runs to the end of the line, which is what someone typing it meant.
     */
    fun runs(line: String): List<VoiceRun> {
        val trimmed = line.trimStart()
        if (trimmed.startsWith(MARK) && !trimmed.startsWith(SPAN_OPEN)) {
            val rest = trimmed.removePrefix(MARK).trimStart()
            // A `2>` line can still hold `2>(…)` runs; they are voice 2 already, so only their
            // brackets have to go.
            return listOf(VoiceRun(runs(rest).joinToString("") { it.text }, SECOND))
        }
        val out = mutableListOf<VoiceRun>()
        var cursor = 0
        while (cursor < line.length) {
            val open = line.indexOf(SPAN_OPEN, cursor)
            if (open < 0) break
            add(out, line.substring(cursor, open), MAIN)
            val start = open + SPAN_OPEN.length
            val close = closingBracket(line, start)
            add(out, line.substring(start, close), SECOND)
            cursor = (close + 1).coerceAtMost(line.length)
        }
        add(out, line.substring(cursor), MAIN)
        return out.ifEmpty { listOf(VoiceRun("", MAIN)) }
    }

    /** [line] with its voice markup taken out and nothing else changed — chords stay. */
    fun plain(line: String): String =
        if (!hasMarkup(line)) line else runs(line).joinToString("") { it.text }

    /**
     * True when every word of [line] is voice 2: a `2>` line, or one whose words all sit inside
     * `2>(…)`. A line with only some words in voice 2 is still a main-voice line.
     */
    fun isSecondVoiceLine(line: String): Boolean {
        if (!hasMarkup(line)) return false
        val sung = runs(line).filter { it.text.isNotBlank() }
        return sung.isNotEmpty() && sung.all { it.voice == SECOND }
    }

    /** [line] written as a whole voice-2 line — what a `[voice: 2]` block makes of each line in it. */
    fun asSecondVoiceLine(line: String): String =
        if (isSecondVoiceLine(line)) line else "$MARK${plain(line)}"

    /**
     * The lines that show together in line mode, one range per press of Next. A main-voice line
     * and the voice-2 line straight after it — the answer to it — are one pair; every other line
     * shows alone.
     */
    fun lineGroups(lines: List<String>): List<IntRange> {
        val groups = mutableListOf<IntRange>()
        var i = 0
        while (i < lines.size) {
            val answered = i + 1 < lines.size &&
                !isSecondVoiceLine(lines[i]) && isSecondVoiceLine(lines[i + 1])
            val end = if (answered) i + 1 else i
            groups.add(i..end)
            i = end + 1
        }
        return groups
    }

    /** The group [index] falls in — see [lineGroups] — or [index] alone when it is out of range. */
    fun lineGroupAt(lines: List<String>, index: Int): IntRange =
        lineGroups(lines).firstOrNull { index in it } ?: index..index

    private fun add(out: MutableList<VoiceRun>, text: String, voice: Int) {
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
}
