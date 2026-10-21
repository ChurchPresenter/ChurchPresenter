package org.churchpresenter.songs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import org.churchpresenter.sharedui.composables.SongSectionKind
import org.churchpresenter.sharedui.composables.sectionKindOf
import org.churchpresenter.songchords.ChordTransposer
import org.churchpresenter.songchords.SecondVoice

/** The colours the lyrics box marks its lines in. */
internal data class LyricsInks(
    val verse: Color,
    val chorus: Color,
    val chord: Color,
    val divider: Color,
    /** The second voice's words and its `2>` marks. */
    val voice: Color = chord,
)

/** A `[...]` marker in a line, which is a chord when [ChordTransposer.isChord] says so. */
private val BRACKETED = Regex("\\[[^\\]]*\\]")

/** How many distinct lines are kept before the line cache starts over -- a few long songs' worth. */
private const val KEPT_LINES = 2_000

/**
 * The lyrics box's colouring: a verse or chorus header, a slide break or a directive, the chords
 * inside a line, and the second voice's words.
 *
 * The text field runs this on every recomposition and every keystroke, over the whole song. Each
 * line's colouring is kept, so a keystroke works out only the line it changed -- a chorus sung
 * four times is worked out once -- and an unchanged text is answered from the last result. A
 * line inside a `[voice: 2]` block is coloured as the second voice's, so it is kept apart from
 * the same line outside one.
 * It is confined to the thread the field composes on, like the field.
 */
internal class LyricsHighlighter(private val inks: LyricsInks) : VisualTransformation {

    private val lines = HashMap<Pair<String, Boolean>, AnnotatedString>()
    private var lastText: AnnotatedString? = null
    private var lastResult: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        if (text == lastText) lastResult?.let { return it }
        if (lines.size > KEPT_LINES) lines.clear()
        // Whether a `[voice: 2]` block is open: it ends at `[voice: 1]` or the next header.
        var inBlock = false
        val annotated = buildAnnotatedString {
            text.text.split("\n").forEachIndexed { i, line ->
                if (i > 0) append("\n")
                SecondVoice.voiceDirectiveOf(line)?.let { inBlock = it == SecondVoice.SECOND }
                if (ChordTransposer.isSectionHeader(line)) inBlock = false
                val voiced = inBlock
                append(lines.getOrPut(line to voiced) { highlight(line, voiced) })
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity).also {
            lastText = text
            lastResult = it
        }
    }

    /** One line, coloured for what it is — [inVoiceBlock] when a `[voice: 2]` block holds it. */
    internal fun highlight(line: String, inVoiceBlock: Boolean = false): AnnotatedString = buildAnnotatedString {
        if (ChordTransposer.isSlideBreak(line) || ChordTransposer.isBackgroundDirective(line) ||
            SecondVoice.isVoiceDirective(line)
        ) {
            // Neither a section nor a chord: a slide break is a rule drawn through the words and a
            // directive is configuration sitting among them. Each reads as what it is rather than
            // as a line someone will sing.
            pushStyle(SpanStyle(color = inks.divider, fontWeight = FontWeight.Bold))
            append(line)
            pop()
        } else if (ChordTransposer.isSectionHeader(line)) {
            val chorus = sectionKindOf(line.trim().trim('[', ']', '{', '}')) == SongSectionKind.CHORUS
            val ink = if (chorus) inks.chorus else inks.verse
            pushStyle(SpanStyle(color = ink, background = ink.copy(alpha = 0.16f), fontWeight = FontWeight.Bold))
            append(line)
            pop()
        } else if (inVoiceBlock) {
            withStyle(SpanStyle(color = inks.voice)) { appendChordHighlighted(line, inks.chord) }
        } else {
            appendVoiceHighlighted(line, inks.chord, inks.voice)
        }
    }
}

/**
 * Appends [line] with its second-voice markup shown for what it is: the `2>` marks and a run's
 * brackets bold, and the words they cover in [voiceInk]. The markup stays in the text — the field
 * edits what is written — so each character is drawn, only coloured.
 */
private fun AnnotatedString.Builder.appendVoiceHighlighted(line: String, chordInk: Color, voiceInk: Color) {
    if (!SecondVoice.hasMarkup(line)) {
        appendChordHighlighted(line, chordInk)
        return
    }
    val mark = SpanStyle(color = voiceInk, fontWeight = FontWeight.Bold, background = voiceInk.copy(alpha = 0.16f))
    val lead = line.length - line.trimStart().length
    val trimmed = line.trimStart()
    if (trimmed.startsWith(SecondVoice.MARK) && !trimmed.startsWith(SecondVoice.MARK + "(")) {
        append(line.take(lead))
        withStyle(mark) { append(SecondVoice.MARK) }
        withStyle(SpanStyle(color = voiceInk)) {
            appendChordHighlighted(line.drop(lead + SecondVoice.MARK.length), chordInk)
        }
        return
    }
    var cursor = 0
    while (cursor < line.length) {
        val open = line.indexOf(SecondVoice.MARK + "(", cursor)
        if (open < 0) break
        appendChordHighlighted(line.substring(cursor, open), chordInk)
        val inner = open + SecondVoice.MARK.length + 1
        val close = line.indexOf(')', inner).takeIf { it >= 0 } ?: line.length
        withStyle(mark) { append(line.substring(open, inner)) }
        withStyle(SpanStyle(color = voiceInk)) { appendChordHighlighted(line.substring(inner, close), chordInk) }
        if (close < line.length) withStyle(mark) { append(")") }
        cursor = close + 1
    }
    if (cursor < line.length) appendChordHighlighted(line.substring(cursor), chordInk)
}

/** Appends [line] with each `[chord]` marker tinted, leaving the words plain. */
private fun AnnotatedString.Builder.appendChordHighlighted(line: String, ink: Color) {
    var cursor = 0
    BRACKETED.findAll(line).forEach { match ->
        if (!ChordTransposer.isChord(match.value.substring(1, match.value.length - 1))) return@forEach
        if (match.range.first > cursor) append(line.substring(cursor, match.range.first))
        pushStyle(SpanStyle(color = ink, background = ink.copy(alpha = 0.14f), fontWeight = FontWeight.Bold))
        append(match.value)
        pop()
        cursor = match.range.last + 1
    }
    if (cursor < line.length) append(line.substring(cursor))
}
