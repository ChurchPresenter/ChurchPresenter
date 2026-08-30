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

/** One verse to go looking for, in the internal numbering every `.spb` is keyed by. */
data class VerseTarget(val book: Int, val chapter: Int, val verse: Int)

/** John 3:16 in the internal numbering every `.spb` is keyed by. */
private const val DEFAULT_BOOK = 43
private const val DEFAULT_CHAPTER = 3
private const val DEFAULT_VERSE = 16

/** The verse [readPreviewVerse] goes looking for when it is not told otherwise. */
val DEFAULT_PREVIEW_TARGET = VerseTarget(DEFAULT_BOOK, DEFAULT_CHAPTER, DEFAULT_VERSE)

private val VERSE_LINE = Regex("^B(\\d{3})C(\\d{3})V(\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(.*)")

private const val GROUP_CODE_BOOK = 1
private const val GROUP_CODE_CHAPTER = 2
private const val GROUP_CODE_VERSE = 3
private const val GROUP_DISPLAY_CHAPTER = 5
private const val GROUP_DISPLAY_VERSE = 6
private const val GROUP_TEXT = 7

/** What one scan of a module accumulates on its way to the verses it wants. */
private class PreviewScan(val targets: Set<VerseTarget>) {
    val bookNames = mutableMapOf<Int, String>()
    var first: PreviewVerse? = null
    val found = mutableMapOf<VerseTarget, PreviewVerse>()

    /** True once nothing is left to look for, which is what lets the scan stop early. */
    val complete: Boolean get() = found.size == targets.size
}

/**
 * A single verse out of [resourcePath], for showing what a translation looks like.
 *
 * Returns [DEFAULT_PREVIEW_TARGET] where the module has it, and otherwise the first verse in the
 * file. Null only when nothing could be read at all, which is the caller's cue to fall back to its
 * own sample text.
 */
fun readPreviewVerse(resourcePath: String): PreviewVerse? =
    readPreviewVerses(resourcePath, listOf(DEFAULT_PREVIEW_TARGET))[DEFAULT_PREVIEW_TARGET]

/**
 * Several verses out of [resourcePath] in **one** forward scan, keyed by the target that found them.
 *
 * Deliberately **not** [Bible.loadFromSpb]. That parses the whole module into memory -- tens of
 * thousands of verses -- and the Bible settings tab wants a few lines from each of up to six
 * translations at once, purely to draw them in a preview. This reads forward and stops as soon as
 * every target is in hand, so the cost is a partial scan of a text file rather than a full load.
 * One scan for all of them and not one per target, because the file is read in book order and the
 * targets are known up front: three separate calls would walk the same prefix three times.
 *
 * A target the module does not carry is simply absent from the result. If *none* of them is found
 * the first verse in the file stands in for all of them -- an Old-Testament-only module, or a
 * single-book one, still has something to show, and showing the same line three times is a truer
 * report of that module than showing nothing.
 *
 * Never throws: a module that is missing, unreadable or not in this format is one translation whose
 * preview falls back, not a settings tab that fails to open. Whatever the scan reached before a
 * read failed is still returned, for the same reason [Bible.loadFromSpb] keeps a partial parse.
 */
fun readPreviewVerses(resourcePath: String, targets: List<VerseTarget>): Map<VerseTarget, PreviewVerse> {
    if (targets.isEmpty()) return emptyMap()
    val scan = PreviewScan(targets.toSet())
    runCatching {
        openSpbReader(resourcePath).use { reader ->
            for (rawLine in reader.lineSequence()) {
                collectPreviewLine(rawLine.trimEnd('\r', '\n'), scan)
                if (scan.complete) break
            }
        }
    }
    if (scan.found.isNotEmpty()) return scan.found
    val fallback = scan.first ?: return emptyMap()
    return targets.associateWith { fallback }
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
    // Matched on the internal code and not on the displayed numbers: the code is what a target is
    // written in, and the two differ wherever a module renumbers (the Psalms, most obviously).
    val target = VerseTarget(
        book = codeBook,
        chapter = match.groupValues[GROUP_CODE_CHAPTER].toInt(),
        verse = match.groupValues[GROUP_CODE_VERSE].toInt(),
    )
    if (target in scan.targets) scan.found[target] = verse
}
