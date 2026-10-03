package org.churchpresenter.stt.presenter

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.churchpresenter.settings.CaptionReading

/**
 * The reading aids applied to one side's finished caption text, after its colours, highlights and
 * letter case: RSVP's cut down to the flash on screen, then bionic reading's bold word starts.
 *
 * [flashWords] is how many words the RSVP reveal's current flash holds, 0 when the caption is not
 * flashed -- a phrase flash varies in size, so the reveal reports it rather than this recomputing it.
 */
internal fun withReadingAids(text: AnnotatedString, flashWords: Int, reading: CaptionReading): AnnotatedString {
    val flash = if (flashWords > 0) rsvpFlash(text, flashWords) else text
    return if (reading.bionicReading) bionicEmphasis(flash) else flash
}

/**
 * The newest [words] words of [text] -- the RSVP flash the reveal has just stepped to -- on one line,
 * keeping the highlighting of the words it takes. See [lastWordsStart].
 */
internal fun rsvpFlash(text: AnnotatedString, words: Int): AnnotatedString {
    val flash = text.subSequence(lastWordsStart(text.text, words), text.length)
    val start = flash.text.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: return AnnotatedString("")
    val trimmed = flash.subSequence(start, flash.text.trimEnd().length)
    // One line: a line break the caption body put between two of the words becomes a space
    return AnnotatedString(trimmed.text.replace('\n', ' '), trimmed.spanStyles)
}

/** Words of this many letters or fewer have only their first letter bold. */
private const val BIONIC_SHORT_WORD = 3

/**
 * Where bionic reading bolds [text]: the first letter of a short word, and the first half -- rounded
 * up -- of a longer one. A word is a run of letters and digits; an apostrophe inside one ("don't")
 * keeps it one word, and punctuation around it is left regular.
 */
internal fun bionicPrefixes(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var i = 0
    while (i < text.length) {
        if (!text[i].isLetterOrDigit()) {
            i++
            continue
        }
        val start = i
        while (i < text.length && isInWord(text, i)) i++
        val letters = text.substring(start, i).count { it.isLetterOrDigit() }
        val bold = if (letters <= BIONIC_SHORT_WORD) 1 else (letters + 1) / 2
        ranges += start until start + bold
    }
    return ranges
}

/** Whether [text] at [i] continues a word: a letter or digit, or an apostrophe with a letter after it. */
private fun isInWord(text: String, i: Int): Boolean =
    text[i].isLetterOrDigit() || (text[i] == '\'' && text.getOrNull(i + 1)?.isLetter() == true)

/**
 * [text] with each word's start bold and the rest regular, whatever weight the caption was drawn in:
 * the contrast between the two is the whole effect, and a caption already bold would have none.
 * Its colours and highlights are kept.
 */
internal fun bionicEmphasis(text: AnnotatedString): AnnotatedString = buildAnnotatedString {
    append(text)
    addStyle(SpanStyle(fontWeight = FontWeight.Normal), 0, text.length)
    for (range in bionicPrefixes(text.text)) {
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), range.first, range.last + 1)
    }
}
