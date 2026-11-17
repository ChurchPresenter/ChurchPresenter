package org.churchpresenter.bibleeditor

import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.BibleVerse

/** A verse the reference Bible has and this one does not, placed in this Bible's own numbering. */
data class MissingVerse(val book: Int, val chapter: Int, val verse: Int)

private data class CodeVerse(val codeVerse: Int, val verse: BibleVerse)

/** Every verse of [bible], book by book, chapter by chapter. */
internal fun allVerses(bible: Bible): Sequence<BibleVerse> = sequence {
    for (index in 0 until bible.getBookCount()) {
        val bookId = bible.getBookId(index)
        for (chapter in 1..bible.getChapterCount(index)) yieldAll(bible.getChapterVerses(bookId, chapter))
    }
}

/**
 * The verses [reference] has that [target] lacks, matched by verse code (the `BxxxCxxxVxxx` id, which
 * is the same in every translation) and placed where [target]'s own numbering would put them.
 *
 * A chapter [target] has no verse of at all is left out: that is a missing chapter or a different
 * book order, not a verse to type in.
 */
fun findMissingVerses(target: Bible, reference: Bible): List<MissingVerse> {
    val byCodeChapter = HashMap<Pair<Int, Int>, MutableList<CodeVerse>>()
    val present = HashSet<Triple<Int, Int, Int>>()
    val displayTaken = HashSet<Triple<Int, Int, Int>>()
    for (v in allVerses(target)) {
        val code = target.parseVerseCode(v.verseId) ?: continue
        present += code
        displayTaken += Triple(v.book, v.chapter, v.verseNumber)
        byCodeChapter.getOrPut(code.first to code.second) { mutableListOf() } += CodeVerse(code.third, v)
    }
    return allVerses(reference)
        .mapNotNull { reference.parseVerseCode(it.verseId) }
        .filterNot { it in present }
        .mapNotNull { code -> placeOf(code, byCodeChapter[code.first to code.second]) }
        .filterNot { Triple(it.book, it.chapter, it.verse) in displayTaken }
        .distinct()
        .toList()
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
