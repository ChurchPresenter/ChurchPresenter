package org.churchpresenter.bibleeditor

import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.BibleVerse
import org.churchpresenter.bibleformats.KjvVersification

/**
 * A verse the KJV numbering has and this Bible does not, placed in this Bible's own numbering.
 *
 * [atChapterEnd] is one that would come after the chapter's last verse. Those are seldom missing:
 * a translation that joins two verses, or ends a chapter where the Hebrew text does (Leviticus 5-6,
 * Job 40-41, Daniel 3-4), comes out a verse or more short at the end with every word still there. A
 * gap inside a chapter -- Matthew 17 going 20, 22 -- is a verse that is not there.
 */
data class MissingVerse(val book: Int, val chapter: Int, val verse: Int, val atChapterEnd: Boolean = false)

private data class CodeVerse(val codeVerse: Int, val verse: BibleVerse)

/** Every verse of [bible], book by book, chapter by chapter. */
internal fun allVerses(bible: Bible): Sequence<BibleVerse> = sequence {
    for (index in 0 until bible.getBookCount()) {
        val bookId = bible.getBookId(index)
        for (chapter in 1..bible.getChapterCount(index)) yieldAll(bible.getChapterVerses(bookId, chapter))
    }
}

/**
 * The verses the KJV numbering has that [target] lacks, matched by verse code (the `BxxxCxxxVxxx` id,
 * which every translation is coded in) and placed where [target]'s own numbering would put them.
 *
 * Judged against [KjvVersification]'s table rather than against a KJV file, so it needs no KJV
 * installed and cannot inherit one's mistakes. A chapter [target] has no verse of at all is left
 * out: that is a missing chapter or a different book order, not a verse to type in.
 */
fun findMissingVerses(target: Bible): List<MissingVerse> {
    val byCodeChapter = HashMap<Pair<Int, Int>, MutableList<CodeVerse>>()
    val present = HashSet<Triple<Int, Int, Int>>()
    val displayTaken = HashSet<Triple<Int, Int, Int>>()
    for (v in allVerses(target)) {
        val code = target.parseVerseCode(v.verseId) ?: continue
        present += code
        displayTaken += Triple(v.book, v.chapter, v.verseNumber)
        byCodeChapter.getOrPut(code.first to code.second) { mutableListOf() } += CodeVerse(code.third, v)
    }
    val lastVerse = displayTaken.groupBy({ it.first to it.second }, { it.third }).mapValues { it.value.max() }
    return kjvVerseCodes()
        .filterNot { it in present }
        .mapNotNull { code -> placeOf(code, byCodeChapter[code.first to code.second]) }
        .filterNot { Triple(it.book, it.chapter, it.verse) in displayTaken }
        .map { it.copy(atChapterEnd = it.verse > (lastVerse[it.book to it.chapter] ?: 0)) }
        .distinct()
        .toList()
}

/** Every verse code in the KJV numbering, in reading order. */
private fun kjvVerseCodes(): Sequence<Triple<Int, Int, Int>> = sequence {
    for (book in 1..KjvVersification.BOOKS) {
        for (chapter in 1..KjvVersification.chapterCount(book)) {
            for (verse in 1..KjvVersification.verseCount(book, chapter)) yield(Triple(book, chapter, verse))
        }
    }
}

/** Where [code] would sit among [siblings], the verses this Bible has of the same code chapter. */
private fun placeOf(code: Triple<Int, Int, Int>, siblings: List<CodeVerse>?): MissingVerse? {
    // Never empty: a code chapter is only listed once a verse of it has been seen.
    if (siblings == null) return null
    val anchor = siblings.filter { it.codeVerse < code.third }.maxByOrNull { it.codeVerse }
        ?: siblings.minBy { it.codeVerse }
    val shown = code.third + (anchor.verse.verseNumber - anchor.codeVerse)
    return if (shown < 1) null else MissingVerse(anchor.verse.book, anchor.verse.chapter, shown)
}
