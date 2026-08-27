package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import org.churchpresenter.settings.utils.Constants

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
