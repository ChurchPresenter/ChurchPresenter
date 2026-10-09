package org.churchpresenter.helper.intent

import org.churchpresenter.calendar.model.ParsedReference
import org.churchpresenter.calendar.model.parseReference

private const val BOOK = 1
private const val CHAPTER = 2
private const val FIRST_VERSE = 3
private const val LAST_VERSE = 4

private val MARKED_REFERENCE = Regex(
    """^\s*(\d?\s*\p{L}[\p{L}\p{M}.\s]*?)\s+(\d{1,3})(?::(\d{1,3})(?:\s*-\s*(\d{1,3}))?)?\s*$""",
)

/**
 * [parseReference], and a book name written with combining marks — "ยอห์น 3:16", "यूहन्ना 3:16" — which
 * it does not read.
 */
internal fun readReference(text: String): ParsedReference? = parseReference(text) ?: MARKED_REFERENCE.find(text)?.let {
    val groups = it.groupValues
    val firstVerse = groups[FIRST_VERSE].toIntOrNull() ?: 0
    ParsedReference(
        groups[BOOK].trim().replace(Regex("""\s+"""), " "),
        groups[CHAPTER].toInt(),
        firstVerse,
        maxOf(firstVerse, groups[LAST_VERSE].toIntOrNull() ?: firstVerse),
    )
}

/** A typed reference in the form [parseReference] reads: "john chapter 3 verse 16" → "john 3:16". */
internal fun asReference(text: String): String = text
    .replace(Regex("""\bchapter (\d+) verses? (\d+)"""), "$1:$2")
    .replace(Regex("""^(.*\p{L}) (\d{1,3}) (\d{1,3})$"""), "$1 $2:$3")
    .replace(Regex("""\s*:\s*"""), ":")
    .replace(Regex("""(\d) ?- ?(\d)"""), "$1-$2")
