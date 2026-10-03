package org.churchpresenter.bibletab

/**
 * A displayed verse line is stored as `"<number>. <text>"` — e.g. `"16. For God so loved the world"`.
 * These helpers pull the pieces back out in one place, so the `"N. text"` convention lives behind a
 * name instead of being re-parsed inline (with `substringBefore(". ")` / `substringAfter(". ")`) at a
 * dozen sites across the Bible tab and its view model. Behaviour is identical to those inline forms.
 */

/** The leading verse number, or `null` when the line doesn't start with an integer followed by `". "`. */
internal fun verseNumberOf(verseLine: String): Int? = verseLine.substringBefore(". ").toIntOrNull()

/** The verse text after `"N. "`; [fallback] (default: the whole line) is returned when there is no `". "`. */
internal fun verseTextOf(verseLine: String, fallback: String = verseLine): String =
    verseLine.substringAfter(". ", fallback)

/** Index of the first line whose verse number is in [liveVerseNumbers], or `-1` if none match. */
internal fun indexOfFirstLiveVerse(verses: List<String>, liveVerseNumbers: Set<Int>): Int =
    verses.indexOfFirst { verseNumberOf(it)?.let { n -> n in liveVerseNumbers } == true }

/**
 * The verse number to move to when the operator presses Up/Down in the live chapter panel: locates
 * [refVerse] in [verses] (or starts at the top if it isn't present), steps one line toward the start
 * ([moveUp]) or the end — clamped to the list bounds — and returns that line's number. Null when
 * [verses] is empty or the target line carries no number.
 */
internal fun nextLiveVerseNumber(verses: List<String>, refVerse: Int, moveUp: Boolean): Int? {
    if (verses.isEmpty()) return null
    val currentIdx = verses.indexOfFirst { verseNumberOf(it) == refVerse }.takeIf { it >= 0 } ?: 0
    val nextIdx = if (moveUp) (currentIdx - 1).coerceAtLeast(0)
                  else (currentIdx + 1).coerceAtMost(verses.size - 1)
    return verses.getOrNull(nextIdx)?.let { verseNumberOf(it) }
}

/**
 * The positions within [filteredVerses] of the currently multi-selected verses. Each selection in
 * [selectedRealIndices] is an index into the full [verses] list; its verse line is looked up in
 * [filteredVerses] and dropped when the active filter hides it. Null when nothing maps through — the
 * multi-select highlight is then off rather than an empty set.
 */
internal fun filteredSelectionIndices(
    selectedRealIndices: Collection<Int>,
    verses: List<String>,
    filteredVerses: List<String>,
): Set<Int>? =
    selectedRealIndices
        .mapNotNull { realIdx -> verses.getOrNull(realIdx)?.let { filteredVerses.indexOf(it).takeIf { i -> i >= 0 } } }
        .toSet()
        .takeIf { it.isNotEmpty() }

/** The reference label for a verse line — `"John 3:16"`, or `"John 3"` when the line carries no number. */
internal fun formatVerseReference(verseLine: String, bookName: String, chapter: Int): String {
    val verseNum = verseNumberOf(verseLine)
    return if (verseNum != null) "$bookName $chapter:$verseNum" else "$bookName $chapter"
}

/**
 * The `(start, end)` verse span shown for a passage, for CCLI/training logging. Parsed as the min and
 * max of the numbers in a range string like `"1-3"` or `"2,4,5"`; falls back to [fallbackVerse] as a
 * single verse when the range is blank or has no numbers. `end` is `null` for a single verse (when the
 * max doesn't exceed the min).
 */
internal fun verseSpan(verseRange: String, fallbackVerse: Int): Pair<Int, Int?> {
    val nums = verseRange
        .takeIf { it.isNotBlank() }
        ?.split(",", "-")
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.takeIf { it.isNotEmpty() }
        ?: listOf(fallbackVerse)
    val start = nums.min()
    val end = nums.max().takeIf { it > start }
    return start to end
}
