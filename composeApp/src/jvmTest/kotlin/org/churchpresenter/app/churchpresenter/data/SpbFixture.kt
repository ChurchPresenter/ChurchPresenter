package org.churchpresenter.app.churchpresenter.data

import java.io.File

/**
 * Builds a tiny `.spb` Bible module on disk so [Bible] can be loaded in a test.
 *
 * `.spb` is BibleQuote's plain-text format, not a database:
 * ```
 * ##Title: <name>
 * <bookId> <Book Name> <chapterCount>      // one header line per book
 * -----
 * B001C001V001 1 1 1 <verse text>          // code ref, then DISPLAY book/chapter/verse, then text
 * ```
 * The code reference (`BxxxCyyyVzzz`) is the canonical/Hebrew numbering and the three bare numbers
 * are the module's own display numbering — they differ in real modules (e.g. Russian Psalms follow
 * the LXX), which is why cross-Bible lookups go through the code and the UI goes through the
 * display numbers. [spbFile] keeps them equal unless a test asks otherwise.
 */
object SpbFixture {

    data class Book(val id: Int, val name: String, val chapters: Int)

    /** One verse: display numbering, with an optional differing code reference. */
    data class Verse(
        val book: Int,
        val chapter: Int,
        val verse: Int,
        val text: String,
        val codeBook: Int = book,
        val codeChapter: Int = chapter,
        val codeVerse: Int = verse,
    )

    /** A three-book module with a handful of real verses — enough to exercise every accessor. */
    fun sampleContent(title: String = "Test Bible"): String = buildContent(
        title = title,
        books = listOf(
            Book(1, "Genesis", 2),
            Book(19, "Psalms", 2),
            Book(43, "John", 1),
        ),
        verses = listOf(
            Verse(1, 1, 1, "In the beginning God created the heaven and the earth."),
            Verse(1, 1, 2, "And the earth was without form, and void."),
            Verse(1, 1, 3, "And God said, Let there be light: and there was light."),
            Verse(1, 2, 1, "Thus the heavens and the earth were finished."),
            Verse(19, 23, 1, "The LORD is my shepherd; I shall not want."),
            Verse(19, 23, 2, "He maketh me to lie down in green pastures."),
            Verse(43, 3, 16, "For God so loved the world."),
            Verse(43, 3, 17, "For God sent not his Son to condemn the world."),
        ),
    )

    fun buildContent(title: String, books: List<Book>, verses: List<Verse>): String = buildString {
        appendLine("##Title: $title")
        appendLine("##Copyright: public domain")
        books.forEach { appendLine("${it.id} ${it.name} ${it.chapters}") }
        appendLine("-----")
        verses.forEach { v ->
            val code = "B%03dC%03dV%03d".format(v.codeBook, v.codeChapter, v.codeVerse)
            appendLine("$code ${v.book} ${v.chapter} ${v.verse} ${v.text}")
        }
    }

    /** Writes [content] into [dir] and returns the file. */
    fun spbFile(dir: File, name: String = "test.spb", content: String = sampleContent()): File =
        File(dir, name).also { it.writeText(content, Charsets.UTF_8) }

    /** A loaded [Bible] backed by a freshly written fixture. */
    fun loadedBible(dir: File, content: String = sampleContent()): Bible =
        Bible().also { it.loadFromSpb(spbFile(dir, content = content).absolutePath) }
}
