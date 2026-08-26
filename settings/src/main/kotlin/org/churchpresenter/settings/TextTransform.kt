package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import java.util.Locale

/**
 * The typography a presenter applies to text that is already laid out: how it is re-cased, and how
 * far its letters and words are pushed apart.
 *
 * Held apart from the font itself because these three travel together and are set together, and
 * because every profile that has one has all three — a lower third's letter spacing is no more
 * related to a full screen's than its font size is.
 */

/** [text] re-cased for [transform], which is one of `Constants.TEXT_TRANSFORM_*`. */
fun applyTextTransform(text: String, transform: String): String = when (transform) {
    Constants.TEXT_TRANSFORM_UPPERCASE -> text.uppercase(Locale.getDefault())
    Constants.TEXT_TRANSFORM_LOWERCASE -> text.lowercase(Locale.getDefault())
    Constants.TEXT_TRANSFORM_CAPITALIZE -> capitalizeWords(text)
    else -> text
}

/**
 * Every word's first letter raised, the rest of it lowered.
 *
 * Splits on whitespace and keeps it, so the run of spaces a chord sheet uses to place a chord over
 * the right syllable survives — collapsing them would move the chords.
 */
private fun capitalizeWords(text: String): String {
    val out = StringBuilder(text.length)
    var atWordStart = true
    for (ch in text) {
        when {
            ch.isWhitespace() -> {
                atWordStart = true
                out.append(ch)
            }
            atWordStart -> {
                atWordStart = false
                out.append(ch.uppercaseChar())
            }
            else -> out.append(ch.lowercaseChar())
        }
    }
    return out.toString()
}

/** The transforms a settings picker offers, in the order it offers them. */
fun textTransformOptions(): List<String> = listOf(
    Constants.TEXT_TRANSFORM_NONE,
    Constants.TEXT_TRANSFORM_UPPERCASE,
    Constants.TEXT_TRANSFORM_LOWERCASE,
    Constants.TEXT_TRANSFORM_CAPITALIZE,
)
