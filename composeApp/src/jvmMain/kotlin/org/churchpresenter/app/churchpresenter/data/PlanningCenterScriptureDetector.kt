package org.churchpresenter.app.churchpresenter.data

/**
 * Detects plain-text scripture references (e.g. "Psalm 23:1-6", one per line) inside Planning
 * Center plan item text and resolves them against a loaded [Bible] — used so a PCO-imported item
 * whose title/description is just a list of references becomes real local Bible verses instead of
 * an inert announcement. PCO itself never provides verse text (see the app's Planning Center
 * integration notes) — this only recognizes references and looks them up in the user's own
 * already-loaded primary Bible, respecting whatever language/translation that Bible is in. The
 * book name may be spelled out in full (matched against the loaded Bible's own book names) or a
 * common abbreviation (see [BibleBookAbbreviations]) — either resolves to whichever full name
 * the loaded Bible actually uses for that book.
 *
 * Reference matching is intentionally simple (one reference filling an entire line, or several
 * comma/semicolon-separated references on one line) rather than the fuzzy free-text matching used
 * by the live speech-detection engine (`ChurchPresenter-BLE`) — this is for cleanly-authored plan
 * text, not spoken audio transcripts.
 */
object PlanningCenterScriptureDetector {

    data class DetectedReference(
        val bookId: Int,
        val bookName: String,
        val chapter: Int,
        val verseStart: Int,
        val verseEnd: Int
    )

    /** Matches a whole line shaped like "<Book name> <chapter>:<verse>[-<verseEnd>]". */
    private val referenceLineRegex = Regex("""^(.*?)\s*(\d+)\s*[:.]\s*(\d+)(?:\s*-\s*(\d+))?\s*$""")

    suspend fun detectReferences(text: String, bible: Bible): List<DetectedReference> {
        if (text.isBlank()) return emptyList()
        val bookNamesById = (0 until bible.getBookCount()).mapNotNull { displayIndex ->
            val bookId = bible.getBookId(displayIndex)
            bible.getBookName(bookId)?.let { bookId to it }
        }
        return text.lines()
            .flatMap { it.split(",", ";") }
            .mapNotNull { rawSegment ->
                val line = rawSegment.trim()
                if (line.isEmpty()) return@mapNotNull null
                val match = referenceLineRegex.find(line) ?: return@mapNotNull null
                val bookText = match.groupValues[1].trim().trimEnd('.').trim()
                if (bookText.isBlank()) return@mapNotNull null
                val chapter = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val verseStart = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                val verseEnd = match.groupValues[4].toIntOrNull() ?: verseStart
                val (bookId, bookName) = bookNamesById.firstOrNull { (_, name) -> name.equals(bookText, ignoreCase = true) }
                    ?: BibleBookAbbreviations.resolveBookId(bookText)?.let { id -> bookNamesById.firstOrNull { (bid, _) -> bid == id } }
                    ?: return@mapNotNull null
                DetectedReference(bookId, bookName, chapter, verseStart, verseEnd)
            }
    }

    data class ResolvedVerses(
        val bookName: String,
        val bookId: Int,
        val chapter: Int,
        val verseNumber: Int,
        val verseText: String,
        val verseRange: String
    ) {
        /** Short human-readable reference for a picker UI, e.g. "Psalm 23:1-6" or "John 3:16". */
        val displayReference: String
            get() = if (verseRange.isNotEmpty()) "$bookName $chapter:$verseRange" else "$bookName $chapter:$verseNumber"
    }

    /** Looks up the actual verse text for a detected reference. Null if the chapter/verses don't exist. */
    fun resolveVerses(reference: DetectedReference, bible: Bible): ResolvedVerses? {
        val chapterVerses = bible.getChapterVerses(reference.bookId, reference.chapter)
        if (chapterVerses.isEmpty()) return null
        val selected = chapterVerses.filter { it.verseNumber in reference.verseStart..reference.verseEnd }
        if (selected.isEmpty()) return null
        val text = selected.sortedBy { it.verseNumber }.joinToString(" ") { it.verseText }
        val range = if (reference.verseEnd > reference.verseStart) "${reference.verseStart}-${reference.verseEnd}" else ""
        return ResolvedVerses(
            bookName = reference.bookName,
            bookId = reference.bookId,
            chapter = reference.chapter,
            verseNumber = reference.verseStart,
            verseText = text,
            verseRange = range
        )
    }
}
