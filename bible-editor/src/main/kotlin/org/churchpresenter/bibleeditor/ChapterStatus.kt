package org.churchpresenter.bibleeditor

/** What a chapter of the open book shows in the chapter picker. */
enum class ChapterStatus { COMPLETE, MISSING, RESTORED }

/** Whether [chapter] of the open book still lacks a verse, had every one typed back in, or never lacked any. */
fun BibleEditorState.chapterStatus(chapter: Int): ChapterStatus {
    val here = missingHere.orEmpty().filter { it.book == bookId && it.chapter == chapter }
    return when {
        here.isEmpty() -> ChapterStatus.COMPLETE
        here.any { !isRestored(it) } -> ChapterStatus.MISSING
        else -> ChapterStatus.RESTORED
    }
}

/** Whether any chapter of the open book still lacks a verse. */
val BibleEditorState.bookHasMissing: Boolean get() = openMissing.any { it.book == bookId }

/**
 * The first verse still missing after the open chapter, in reading order, going round to the start
 * of the Bible when there is none after it.
 */
fun BibleEditorState.nextMissing(): MissingVerse? {
    val loaded = bible ?: return null
    val order = compareBy<MissingVerse>({ loaded.getDisplayIndexForBookId(it.book) }, { it.chapter }, { it.verse })
    val open = openMissing.sortedWith(order)
    val here = MissingVerse(bookId, chapter, Int.MAX_VALUE)
    return open.firstOrNull { order.compare(it, here) > 0 } ?: open.firstOrNull()
}

/** Opens the chapter of the next missing verse and lights the verse's place up. */
fun BibleEditorState.goToNextMissing() {
    val key = selected?.key ?: return
    val next = nextMissing() ?: return
    goTo(key, next.book, next.chapter, next.verse)
}
