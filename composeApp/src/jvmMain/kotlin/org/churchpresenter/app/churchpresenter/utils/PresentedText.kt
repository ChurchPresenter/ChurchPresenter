package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import org.churchpresenter.settings.applyTextTransform

/**
 * Turning stored text into the text a presenter draws: re-cased, and with its letters and words
 * pushed as far apart as the style profile asks.
 *
 * Spacing is stored in hundredths of an em rather than pixels so that it tracks the font size —
 * including the size auto-fit lands on, which is not known until the text has been measured.
 */

/** Letter spacing as a text unit, from hundredths of an em. */
fun letterSpacingEm(hundredths: Int): TextUnit = (hundredths / HUNDREDTHS).em

/**
 * [text] re-cased for [transform], with its spaces widened by [wordSpacing] hundredths of an em.
 *
 * Compose has no word-spacing property, so the extra width goes onto the space characters
 * themselves as letter spacing — in a laid-out line the space *is* the word gap, and widening it is
 * what a word-spacing setting means. Runs of spaces are treated one by one, so the padding a chord
 * sheet uses to sit a chord over the right syllable widens with everything else instead of drifting.
 *
 * Returns a plain [AnnotatedString] with no spans when there is no word spacing to apply, which is
 * the ordinary case.
 */
fun presentedText(text: String, transform: String, wordSpacing: Int): AnnotatedString {
    val cased = applyTextTransform(text, transform)
    if (wordSpacing == 0) return AnnotatedString(cased)
    val extra = SpanStyle(letterSpacing = letterSpacingEm(wordSpacing))
    return buildAnnotatedString {
        append(cased)
        cased.forEachIndexed { index, ch ->
            if (ch == ' ') addStyle(extra, index, index + 1)
        }
    }
}

private const val HUNDREDTHS = 100f
