package org.churchpresenter.bibleeditor

/**
 * A verse numbering a Bible can be written in. Each is a table of verses per chapter for the 66
 * books, in `src/main/resources/versification/`: the KJV counted from eBible's public-domain KJV,
 * the others from CrossWire SWORD's versification data. In order of preference when two fit a book
 * equally well.
 */
enum class Numbering(internal val table: String) {
    KJV("kjv"),
    SYNODAL("synodal"),
    SYNODAL_PROTESTANT("synodalprot"),
    SEPTUAGINT("lxx"),
    HEBREW("leningrad"),
    VULGATE("vulg"),
    LUTHER("luther"),
    GERMAN("german"),
    NRSV("nrsv"),
    CATHOLIC("catholic"),
    ORTHODOX("orthodox"),
    SEGOND("segond"),
}

/** How many verses each chapter of each book has in one [Numbering]. */
internal class VerseTable(private val books: Map<Int, IntArray>) {

    fun chapterCount(book: Int): Int = books[book]?.size ?: 0

    fun verseCount(book: Int, chapter: Int): Int = books[book]?.getOrNull(chapter - 1) ?: 0

    companion object {
        /** Parses a table file: `#` comments, then one line per book, its number and then each chapter's count. */
        fun parse(text: String, fallback: VerseTable? = null): VerseTable {
            val read = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line -> line.split(' ').map(String::toInt) }
                .associate { numbers -> numbers.first() to numbers.drop(1).toIntArray() }
            return VerseTable(fallback?.books.orEmpty() + read)
        }
    }
}

/**
 * Every [Numbering]'s table, read from the resources on first use -- which is file I/O, so only
 * ever from the editor's `io` dispatcher. A table that leaves a book out (the Hebrew one has no
 * New Testament) takes that book from the KJV.
 */
internal object NumberingTables {

    const val BOOKS = 66

    val all: Map<Numbering, VerseTable> by lazy {
        val kjv = VerseTable.parse(read(Numbering.KJV))
        Numbering.entries.associateWith { if (it == Numbering.KJV) kjv else VerseTable.parse(read(it), kjv) }
    }

    private fun read(numbering: Numbering): String =
        checkNotNull(NumberingTables::class.java.getResource("/versification/${numbering.table}.txt")) {
            "No versification table for $numbering"
        }.readText()
}
