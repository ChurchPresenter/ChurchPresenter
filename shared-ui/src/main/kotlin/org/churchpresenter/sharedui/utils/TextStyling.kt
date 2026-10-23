package org.churchpresenter.sharedui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.songchords.SecondVoice

/**
 * The three per-element typography settings that cannot simply be handed to a `TextStyle`:
 * the case transform, the strikethrough that has to combine with underline, and the word spacing
 * Compose has no property for.
 *
 * Shared by the Bible and the song presenters, which offer the same four controls per element. All
 * three are applied as the text is drawn -- nothing here writes back, so turning a transform off
 * restores the scripture or the lyric exactly as it is stored.
 */

/** Case as it goes on screen. An unknown value is left alone, which is what an older file stores. */
fun applyTextTransform(text: String, transform: String): String = when (transform) {
    Constants.TEXT_TRANSFORM_UPPERCASE -> text.uppercase()
    Constants.TEXT_TRANSFORM_LOWERCASE -> text.lowercase()
    Constants.TEXT_TRANSFORM_CAPITALIZE -> capitalizeWords(text)
    else -> text
}

/**
 * The first letter of every word raised, the rest left as it was written.
 *
 * CSS `text-transform: capitalize` is the model, and leaving the tail alone is the part that
 * matters for scripture: lowercasing it would turn "LORD" -- which many translations set that way
 * deliberately, for the divine name -- into "Lord".
 */
private fun capitalizeWords(text: String): String {
    val out = StringBuilder(text.length)
    var atWordStart = true
    text.forEach { char ->
        out.append(if (atWordStart) char.uppercaseChar() else char)
        atWordStart = !char.isLetter() && char != '\''
    }
    return out.toString()
}

/** Underline and strikethrough compose, so neither can be expressed as the other's absence. */
fun combinedTextDecoration(underline: Boolean, strikethrough: Boolean): TextDecoration = when {
    underline && strikethrough -> TextDecoration.combine(
        listOf(TextDecoration.Underline, TextDecoration.LineThrough),
    )
    underline -> TextDecoration.Underline
    strikethrough -> TextDecoration.LineThrough
    else -> TextDecoration.None
}

/**
 * Letter or word spacing as a fraction of the em, from the points-at-the-configured-size the
 * settings store.
 *
 * Kept relative rather than absolute so it survives every scaling the presenter does to it: the
 * output's own resolution scale, and the auto-fit that shrinks a long passage to its band. An
 * absolute `sp` tracking would stay put while the type around it shrank, and a stack squeezed to
 * half size would draw with double the intended spacing.
 */
fun spacingEm(spacing: Int, fontSize: Int): Float =
    if (fontSize <= 0) 0f else spacing.toFloat() / fontSize

/**
 * The string to draw: [raw] under [transform], with [wordSpacingEm] added at each word break.
 *
 * Compose's `TextStyle` has no word spacing, so the space characters are widened instead -- each one
 * carries a span whose tracking is the paragraph's own [letterSpacingEm] plus the extra. A span's
 * `letterSpacing` *replaces* the style's rather than adding to it, which is why [letterSpacingEm]
 * has to be passed in here too rather than left to the `TextStyle` alone.
 *
 * With no word spacing asked for there is nothing to annotate, and the plain string is returned.
 */
fun styledDisplayText(
    raw: String,
    transform: String,
    letterSpacingEm: Float,
    wordSpacingEm: Float,
): AnnotatedString {
    val text = applyTextTransform(raw, transform)
    if (wordSpacingEm == 0f) return AnnotatedString(text)
    val spaceTracking = letterSpacingEm + wordSpacingEm
    return buildAnnotatedString {
        append(text)
        var index = 0
        while (index < text.length) {
            if (text[index] == ' ') {
                addStyle(SpanStyle(letterSpacing = spaceTracking.em), index, index + 1)
            }
            index++
        }
    }
}

/**
 * A lyric line as drawn: its voice markup (`2>`, `2>(…)`) taken out, cased by [transform], and the
 * ranges of [text] the second voice sings, for its colour to go on.
 *
 * The line is cased whole rather than run by run, so a capitalised run mid-line is not mistaken for
 * the start of a word; when casing changes the length (`ß` → `SS`) the ranges no longer line up, and
 * the line is drawn in one colour rather than coloured in the wrong places.
 */
data class VoicedLine(val text: String, val secondVoiceRanges: List<IntRange>)

/** [line] as a [VoicedLine] under [transform]. */
fun voicedLine(line: String, transform: String): VoicedLine {
    val runs = SecondVoice.runs(line)
    val plain = runs.joinToString("") { it.text }
    val cased = applyTextTransform(plain, transform)
    if (cased.length != plain.length) return VoicedLine(cased, emptyList())
    val ranges = mutableListOf<IntRange>()
    var at = 0
    runs.forEach { run ->
        if (run.voice == SecondVoice.SECOND && run.text.isNotEmpty()) ranges += at until at + run.text.length
        at += run.text.length
    }
    return VoicedLine(cased, ranges)
}

/**
 * [styledDisplayText] for a lyric line that may carry voice markup: the markup taken out, and the
 * second voice's words drawn in [secondVoiceColor]. Null draws every word in the paragraph's own
 * colour — what a fit measures, and what a key output draws.
 */
fun voicedDisplayText(
    raw: String,
    transform: String,
    letterSpacingEm: Float,
    wordSpacingEm: Float,
    secondVoiceColor: Color?,
): AnnotatedString {
    if (!SecondVoice.hasMarkup(raw)) return styledDisplayText(raw, transform, letterSpacingEm, wordSpacingEm)
    // The markup comes off uncased; styledDisplayText cases the line.
    val voiced = voicedLine(raw, Constants.TEXT_TRANSFORM_NONE)
    val styled = styledDisplayText(voiced.text, transform, letterSpacingEm, wordSpacingEm)
    if (secondVoiceColor == null || styled.length != voiced.text.length) return styled
    return buildAnnotatedString {
        append(styled)
        voiced.secondVoiceRanges.forEach { addStyle(SpanStyle(color = secondVoiceColor), it.first, it.last + 1) }
    }
}

/** [voicedDisplayText] for several lines drawn as one block, each line's markup read on its own. */
fun voicedDisplayText(
    lines: List<String>,
    transform: String,
    letterSpacingEm: Float,
    wordSpacingEm: Float,
    secondVoiceColor: Color?,
): AnnotatedString = buildAnnotatedString {
    lines.forEachIndexed { i, line ->
        if (i > 0) append('\n')
        append(voicedDisplayText(line, transform, letterSpacingEm, wordSpacingEm, secondVoiceColor))
    }
}
