package org.churchpresenter.bibleeditor

import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.BibleVerse

/**
 * A verse the Bible's [Numbering] has and this Bible does not, by the numbers the file shows.
 *
 * [atChapterEnd] is one that would come after the chapter's last verse. Those are seldom missing:
 * a translation that joins two verses, or ends a chapter where the Hebrew text does (Leviticus 5-6,
 * Job 40-41, Daniel 3-4), comes out a verse or more short at the end with every word still there. A
 * gap inside a chapter -- Matthew 17 going 20, 22 -- is a verse that is not there.
 */
data class MissingVerse(val book: Int, val chapter: Int, val verse: Int, val atChapterEnd: Boolean = false)

/** Every verse of [bible], book by book, chapter by chapter. */
internal fun allVerses(bible: Bible): Sequence<BibleVerse> = sequence {
    for (index in 0 until bible.getBookCount()) {
        val bookId = bible.getBookId(index)
        for (chapter in 1..bible.getChapterCount(index)) yieldAll(bible.getChapterVerses(bookId, chapter))
    }
}

/** What the missing-verse check found: the [Numbering] the Bible matched, and the verses it lacks in it. */
data class MissingCheck(val missing: List<MissingVerse>, val numbering: Numbering)

/**
 * The verses [target] lacks, by the book, chapter and verse numbers the file shows.
 *
 * Translations do not all number alike -- the Synodal joins and splits chapters the KJV does not,
 * the Septuagint and Vulgate count verses the Hebrew text has not -- so the Bible is matched to the
 * [Numbering] it fits best: the one with the fewest verses missing plus verses it has no place for,
 * over all its books. A book that fits another numbering with fewer issues -- a Synodal Bible
 * without the Greek additions to Daniel, one that breaks a few chapters where the Hebrew text does --
 * is checked against that one instead. The numbers the file shows are what is matched, never the
 * verse codes an importer wrote. A chapter [target] has no verse of at all is left out: that is a missing chapter
 * or a different book order, not a verse to type in.
 */
fun checkMissingVerses(target: Bible): MissingCheck {
    val books = allVerses(target)
        .filter { it.book in 1..NumberingTables.BOOKS && it.verseNumber > 0 }
        .groupBy({ it.book }, { it.chapter to it.verseNumber })
        .mapValues { it.value.toSet() }
    val tables = NumberingTables.all
    val numbering = Numbering.entries.minBy { candidate ->
        books.entries.sumOf { (book, verses) -> issues(book, verses, tables.getValue(candidate)) }
    }
    val missing = books.toSortedMap().flatMap { (book, verses) ->
        val fit = Numbering.entries.minBy { issues(book, verses, tables.getValue(it)) }
            .takeIf { issues(book, verses, tables.getValue(it)) < issues(book, verses, tables.getValue(numbering)) }
            ?: numbering
        missingIn(book, verses, tables.getValue(fit))
    }
    return MissingCheck(missing, numbering)
}

/** The verses [target] lacks; see [checkMissingVerses]. */
fun findMissingVerses(target: Bible): List<MissingVerse> = checkMissingVerses(target).missing

/** The verses [table] has in the chapters of [book] that [verses] reaches, and [verses] lacks. */
private fun missingIn(book: Int, verses: Set<Pair<Int, Int>>, table: VerseTable): List<MissingVerse> {
    val lastVerse = verses.groupBy({ it.first }, { it.second }).mapValues { it.value.max() }
    return lastVerse.toSortedMap().flatMap { (chapter, last) ->
        (1..table.verseCount(book, chapter))
            .filterNot { (chapter to it) in verses }
            .map { MissingVerse(book, chapter, it, atChapterEnd = it > last) }
    }
}

/**
 * How badly [verses] fit [table]: the verses [table] has in the chapters [verses] reaches and
 * [verses] lacks, plus the verses [verses] has that [table] has no place for.
 */
private fun issues(book: Int, verses: Set<Pair<Int, Int>>, table: VerseTable): Int {
    val chapters = verses.mapTo(HashSet()) { it.first }
    val expected = chapters.sumOf { table.verseCount(book, it) }
    val placed = verses.count { it.second <= table.verseCount(book, it.first) }
    return (expected - placed) + (verses.size - placed)
}
