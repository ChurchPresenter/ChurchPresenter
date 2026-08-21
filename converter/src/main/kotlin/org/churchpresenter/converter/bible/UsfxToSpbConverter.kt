package org.churchpresenter.converter.bible

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Reads USFX — the XML scripture format published by eBible.org — into the same [ParsedBible] the
 * Zefania path produces, so both sources share one writer.
 *
 * USFX is *milestone*-based rather than nested: `<v id="1"/>` and `<c id="1"/>` are empty markers
 * and the verse text is whatever follows until the next marker, mixed in with paragraph, poetry and
 * word-level tags. So this walks the document in order and accumulates text, rather than reading a
 * tree of verse elements the way [XmlToSpbConverter] can.
 *
 * Book names come from the `BookNames.xml` that ships alongside, which carries them **in the
 * translation's own language** — meaning eBible modules need none of the curated
 * [BookNames.LANGUAGE_LOOKUPS] fallbacks that the Zefania archive's unreliable `bname` attributes
 * force on us.
 *
 * A verse marker with no text is dropped rather than written out empty. That is not a parse
 * failure: translations working from the earliest manuscripts deliberately publish the disputed
 * verses — Matthew 17:21, Mark 9:44, Acts 8:37 and the rest — as a marker with the text replaced by
 * a footnote, and the NET Bible does this for seventeen of them. Writing those out would put blank
 * slides on the screen; leaving them out matches what the translation itself intends.
 */
object UsfxToSpbConverter {

    /** USFM book codes for the 66-book canon, in order. */
    private val BOOK_NUMBERS: Map<String, Int> = listOf(
        "GEN", "EXO", "LEV", "NUM", "DEU", "JOS", "JDG", "RUT", "1SA", "2SA", "1KI", "2KI",
        "1CH", "2CH", "EZR", "NEH", "EST", "JOB", "PSA", "PRO", "ECC", "SNG", "ISA", "JER",
        "LAM", "EZK", "DAN", "HOS", "JOL", "AMO", "OBA", "JON", "MIC", "NAM", "HAB", "ZEP",
        "HAG", "ZEC", "MAL",
        "MAT", "MRK", "LUK", "JHN", "ACT", "ROM", "1CO", "2CO", "GAL", "EPH", "PHP", "COL",
        "1TH", "2TH", "1TI", "2TI", "TIT", "PHM", "HEB", "JAS", "1PE", "2PE", "1JN", "2JN",
        "3JN", "JUD", "REV"
    ).withIndex().associate { (index, code) -> code to index + 1 }

    /**
     * Elements whose contents are not scripture text.
     *
     * Footnotes and cross-references sit *inside* the verse they annotate, so without dropping them
     * wholesale their text would be spliced into the middle of the verse on screen. Headings and
     * front matter are editorial rather than part of any verse.
     */
    private val SKIPPED_ELEMENTS = setOf(
        "f", "fe", "x", "ef", "ex",           // footnotes and cross references
        "s", "r", "sp", "ms", "mr",           // headings, parallel refs, speaker labels
        "toc", "h", "id", "ide", "rem",       // front matter
        "fig", "cl", "cp", "va", "vp"         // figures and alternate numbering
    )
    // `d` — the descriptor marker — is deliberately absent: it carries Psalm superscriptions and
    // prophetic-oracle titles, which are scripture rather than editorial, and in some translations
    // it wraps the verse marker itself (Zechariah 12:1 in the Berean Standard Bible).

    fun parse(
        usfxFile: File,
        bookNamesFile: File?,
        name: String,
        language: String?,
        rights: String = "",
        source: String = ""
    ): ParsedBible {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val doc = factory.newDocumentBuilder().parse(usfxFile)

        val names = bookNamesFile?.takeIf { it.isFile }?.let { parseBookNames(it) } ?: emptyMap()
        val books = mutableListOf<BibleBook>()
        val collector = Collector(names, language)
        walk(doc.documentElement, collector)
        collector.finish(books)

        return ParsedBible(
            name = name,
            description = rights,
            language = language,
            books = books,
            title = name,
            rights = rights,
            source = source
        )
    }

    /** `<book code="GEN" abbr="Genesis" short="Genesis" long="Genesis"/>` → `GEN` to `Genesis`. */
    internal fun parseBookNames(file: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val doc = factory.newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("book")
        return (0 until nodes.length)
            .mapNotNull { nodes.item(it) as? Element }
            .mapNotNull { bookName(it) }
            .toMap()
    }

    /** One `<book>` entry as its code and the name to show, or null when it names nothing. */
    private fun bookName(element: Element): Pair<String, String>? {
        val code = element.getAttribute("code").trim().uppercase()
        // `short` is what a book list wants; `abbr` and `long` are the fallbacks.
        val label = listOf("short", "abbr", "long")
            .map { element.getAttribute(it).trim() }
            .firstOrNull { it.isNotEmpty() }
        return if (code.isEmpty() || label.isNullOrEmpty()) null else code to label
    }

    internal fun bookNumberFor(code: String): Int? = BOOK_NUMBERS[code.trim().uppercase()]

    private fun containsVerseMarker(element: Element): Boolean =
        element.getElementsByTagName("v").length > 0

    private fun walk(node: Node, collector: Collector) {
        when (node.nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> collector.text(node.textContent)
            Node.ELEMENT_NODE -> {
                val element = node as Element
                val tag = element.nodeName.lowercase()
                // Dropping an annotation is a cosmetic loss; dropping a verse is a missing verse on
                // screen with nothing to indicate it. So an element that would otherwise be skipped
                // is walked anyway when a verse marker is nested inside it.
                if (tag in SKIPPED_ELEMENTS && !containsVerseMarker(element)) return
                when (tag) {
                    "book" -> collector.startBook(element.getAttribute("id"))
                    "c" -> collector.startChapter(element.getAttribute("id"))
                    "v" -> collector.startVerse(element.getAttribute("id"))
                    "ve" -> collector.endVerse()
                }
                var child = element.firstChild
                while (child != null) {
                    walk(child, collector)
                    child = child.nextSibling
                }
            }
        }
    }

    /** Accumulates the running text into books/chapters/verses as the milestones go past. */
    private class Collector(
        private val bookNames: Map<String, String>,
        private val language: String?
    ) {
        private var bookNumber: Int? = null
        private var bookName: String = ""
        private var chapterNumber: Int = 0
        private var verseNumber: Int? = null
        private val text = StringBuilder()

        private val chapters = linkedMapOf<Int, MutableList<BibleVerse>>()
        private val books = mutableListOf<BibleBook>()

        fun startBook(code: String) {
            flushBook()
            bookNumber = bookNumberFor(code)
            // Prefer the translation's own name; fall back to a curated table, then English.
            bookName = bookNames[code.trim().uppercase()]
                ?: bookNumber?.let { number ->
                    BookNames.LANGUAGE_LOOKUPS[language?.uppercase()]?.get(number)
                        ?: BookNames.ENGLISH[number]
                }
                ?: code
            chapterNumber = 0
        }

        fun startChapter(id: String) {
            flushVerse()
            chapterNumber = id.trim().toIntOrNull() ?: (chapterNumber + 1)
        }

        fun startVerse(id: String) {
            flushVerse()
            // Ranges like `17-18` are stored under their first number.
            verseNumber = id.trim().substringBefore('-').toIntOrNull()
        }

        fun endVerse() = flushVerse()

        fun text(value: String) {
            if (verseNumber != null) text.append(value)
        }

        private fun flushVerse() {
            val verse = verseNumber
            val body = text.toString().normalizeSpace()
            text.setLength(0)
            verseNumber = null
            if (verse == null || body.isEmpty() || bookNumber == null) return
            chapters.getOrPut(chapterNumber) { mutableListOf() }.add(BibleVerse(verse, body))
        }

        private fun flushBook() {
            flushVerse()
            val number = bookNumber ?: return
            if (chapters.isNotEmpty()) {
                books.add(
                    BibleBook(
                        number = number,
                        name = bookName,
                        chapters = chapters.map { (chapter, verses) -> BibleChapter(chapter, verses) }
                    )
                )
            }
            chapters.clear()
            bookNumber = null
        }

        fun finish(into: MutableList<BibleBook>) {
            flushBook()
            into.addAll(books.sortedBy { it.number })
        }
    }
}

/** USFX is line-wrapped, so verse text arrives full of newlines that mean nothing. */
private fun String.normalizeSpace(): String =
    replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
        .replace(Regex(" +"), " ")
        .trim()
