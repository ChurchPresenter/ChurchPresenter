package org.churchpresenter.bible

/** One verse read straight out of a module, with the book named as that module names it. */
data class PreviewVerse(
    val bookName: String,
    val chapter: Int,
    val verseNumber: Int,
    val text: String,
) {
    /** "John 3:16", in whatever language the module is written in. */
    val reference: String get() = "$bookName $chapter:$verseNumber"
}

/** John 3:16 in the internal numbering every `.spb` is keyed by. */
private const val DEFAULT_BOOK = 43
private const val DEFAULT_CHAPTER = 3
private const val DEFAULT_VERSE = 16

private val VERSE_LINE = Regex("^B(\\d{3})C(\\d{3})V(\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(.*)")

private const val GROUP_CODE_BOOK = 1
private const val GROUP_DISPLAY_CHAPTER = 5
private const val GROUP_DISPLAY_VERSE = 6
private const val GROUP_TEXT = 7

/** What one scan of a module accumulates on its way to the verse it wants. */
private class PreviewScan {
    val bookNames = mutableMapOf<Int, String>()
    var first: PreviewVerse? = null
    var wanted: PreviewVerse? = null
}

/**
 * A single verse out of [resourcePath], for showing what a translation looks like.
 *
 * Deliberately **not** [Bible.loadFromSpb]. That parses the whole module into memory -- tens of
 * thousands of verses -- and the Bible settings tab wants one line from each of up to six
 * translations at once, purely to draw them in a preview. This reads forward and stops at the verse
 * it wants, so the cost is a partial scan of a text file rather than a full load.
 *
 * Returns [DEFAULT_BOOK] [DEFAULT_CHAPTER]:[DEFAULT_VERSE] where the module has it, and otherwise
 * the first verse in the file -- an Old-Testament-only module, or a single-book one, still has
 * something to show. Null only when nothing could be read at all, which is the caller's cue to fall
 * back to its own sample text.
 *
 * Never throws: a module that is missing, unreadable or not in this format is one translation whose
 * preview falls back, not a settings tab that fails to open. Whatever the scan reached before a
 * read failed is still returned, for the same reason [Bible.loadFromSpb] keeps a partial parse.
 */
fun readPreviewVerse(resourcePath: String): PreviewVerse? {
    val scan = PreviewScan()
    runCatching {
        openSpbReader(resourcePath).use { reader ->
            for (rawLine in reader.lineSequence()) {
                collectPreviewLine(rawLine.trimEnd('\r', '\n'), scan)
                if (scan.wanted != null) break
            }
        }
    }
    return scan.wanted ?: scan.first
}

/** Folds one line into [scan]: a book name, a candidate verse, or nothing at all. */
private fun collectPreviewLine(line: String, scan: PreviewScan) {
    if (line.isEmpty() || line.startsWith("##")) return
    // The header block names every book and comes before the verses, so by the time a verse line
    // is reached its own book already has a name to be given.
    val header = SPB_BOOK_HEADER_REGEX.matchEntire(line)
    if (header != null) {
        scan.bookNames[header.groupValues[1].toInt()] = header.groupValues[2].trim()
        return
    }
    val match = VERSE_LINE.matchEntire(line) ?: return
    val codeBook = match.groupValues[GROUP_CODE_BOOK].toInt()
    val verse = PreviewVerse(
        bookName = scan.bookNames[codeBook].orEmpty(),
        chapter = match.groupValues[GROUP_DISPLAY_CHAPTER].toInt(),
        verseNumber = match.groupValues[GROUP_DISPLAY_VERSE].toInt(),
        text = match.groupValues[GROUP_TEXT].trim(),
    )
    if (verse.text.isEmpty()) return
    if (scan.first == null) scan.first = verse
    if (codeBook == DEFAULT_BOOK && verse.chapter == DEFAULT_CHAPTER && verse.verseNumber == DEFAULT_VERSE) {
        scan.wanted = verse
    }
}
