package org.churchpresenter.songs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import org.churchpresenter.sharedui.composables.SongSectionKind
import org.churchpresenter.sharedui.composables.sectionKindOf
import org.churchpresenter.songchords.ChordTransposer

/** The colours the lyrics box marks its lines in. */
internal data class LyricsInks(val verse: Color, val chorus: Color, val chord: Color, val divider: Color)

/** A `[...]` marker in a line, which is a chord when [ChordTransposer.isChord] says so. */
private val BRACKETED = Regex("\\[[^\\]]*\\]")

/** How many distinct lines are kept before the line cache starts over -- a few long songs' worth. */
private const val KEPT_LINES = 2_000

/**
 * The lyrics box's colouring: a verse or chorus header, a slide break or background directive, and
 * the chords inside a line.
 *
 * The text field runs this on every recomposition and every keystroke, over the whole song. Each
 * line's colouring is kept, so a keystroke works out only the line it changed -- a chorus sung
 * four times is worked out once -- and an unchanged text is answered from the last result.
 * It is confined to the thread the field composes on, like the field.
 */
internal class LyricsHighlighter(private val inks: LyricsInks) : VisualTransformation {

    private val lines = HashMap<String, AnnotatedString>()
    private var lastText: AnnotatedString? = null
    private var lastResult: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        if (text == lastText) lastResult?.let { return it }
        if (lines.size > KEPT_LINES) lines.clear()
        val annotated = buildAnnotatedString {
            text.text.split("\n").forEachIndexed { i, line ->
                if (i > 0) append("\n")
                append(lines.getOrPut(line) { highlight(line) })
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity).also {
            lastText = text
            lastResult = it
        }
    }

    /** One line, coloured for what it is. */
    internal fun highlight(line: String): AnnotatedString = buildAnnotatedString {
        if (ChordTransposer.isSlideBreak(line) || ChordTransposer.isBackgroundDirective(line)) {
            // Neither a section nor a chord: a slide break is a rule drawn through the words and a
            // background directive is configuration sitting among them. Both read as what they are
            // rather than as a line someone will sing.
            pushStyle(SpanStyle(color = inks.divider, fontWeight = FontWeight.Bold))
            append(line)
            pop()
        } else if (ChordTransposer.isSectionHeader(line)) {
            val chorus = sectionKindOf(line.trim().trim('[', ']', '{', '}')) == SongSectionKind.CHORUS
            val ink = if (chorus) inks.chorus else inks.verse
            pushStyle(SpanStyle(color = ink, background = ink.copy(alpha = 0.16f), fontWeight = FontWeight.Bold))
            append(line)
            pop()
        } else {
            appendChordHighlighted(line, inks.chord)
        }
    }
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
