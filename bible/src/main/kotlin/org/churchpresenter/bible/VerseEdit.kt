package org.churchpresenter.bible

/**
 * One verse as the person has corrected it, laid over a loaded module: [text] replaces the verse,
 * or puts it in where the file has none; null takes the verse out.
 *
 * In the module's own display numbering, so a correction finds its verse again on every load.
 */
data class VerseEdit(val book: Int, val chapter: Int, val verse: Int, val text: String?)

private val VERSE_CODE = Regex("^B(\\d{3})C(\\d{3})V(\\d{3})$")

/**
 * [verses] with [edits] laid over them, in reading order.
 *
 * A verse put in where the file has none takes its code from a verse of the same chapter, offset
 * by the same amount, so it lines up with the other translations as its neighbours do. One in a
 * chapter the module has no verse of is left out: there is nothing to number it by.
 */
internal fun overlaid(verses: List<BibleVerse>, edits: List<VerseEdit>): List<BibleVerse> {
    val byPlace = edits.associateBy { Triple(it.book, it.chapter, it.verse) }
    val out = verses.mapNotNull { v ->
        val edit = byPlace[Triple(v.book, v.chapter, v.verseNumber)] ?: return@mapNotNull v
        edit.text?.let { v.copy(verseText = it) }
    }.toMutableList()
    val present = verses.map { Triple(it.book, it.chapter, it.verseNumber) }.toSet()
    edits.filter { it.text != null && Triple(it.book, it.chapter, it.verse) !in present }
        .sortedBy { it.verse }
        .forEach { edit -> insertAdded(out, edit) }
    return out
}

private fun insertAdded(out: MutableList<BibleVerse>, edit: VerseEdit) {
    val chapter = out.withIndex().filter { it.value.book == edit.book && it.value.chapter == edit.chapter }
    if (chapter.isEmpty()) return
    val before = chapter.lastOrNull { it.value.verseNumber < edit.verse }
    val anchor = (before ?: chapter.first()).value
    val code = VERSE_CODE.matchEntire(anchor.verseId)?.groupValues ?: return
    val codeVerse = edit.verse + (code[REGEX_GROUP_THIRD].toInt() - anchor.verseNumber)
    val verse = BibleVerse(
        verseId = "B%sC%sV%03d".format(code[1], code[2], codeVerse),
        book = edit.book,
        chapter = edit.chapter,
        verseNumber = edit.verse,
        verseText = edit.text.orEmpty(),
    )
    out.add(before?.index?.plus(1) ?: chapter.first().index, verse)
}
