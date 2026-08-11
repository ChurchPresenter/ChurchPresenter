package converter

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/**
 * The "Holy Bible XML" dialect published by Beblia and mirrored at
 * `github.com/ChurchPresenter/Holy-Bible-XML-Format`.
 *
 * Five elements and nothing else — no inline markup, no notes, no mixed content:
 * ```xml
 * <bible translation="English KJV" status="Public Domain">
 *   <testament name="Old">
 *     <book number="1"><chapter number="1"><verse number="1">In the beginning…</verse>
 * ```
 *
 * Two properties of the format drive everything here:
 *
 * **Books are identified by a bare integer.** There is no name, abbreviation or `osisID` anywhere in
 * the file, so names come from [BookNames] keyed on the language — and for the great majority of
 * these translations [BookNames.LANGUAGE_LOOKUPS] has no entry, so the book list ends up in English
 * beside native verse text. That is a real limitation of the source data rather than of this parser:
 * the names are simply not in the file to be read. It matches what [XmlToSpbConverter]'s Zefania path
 * already does for its own unlisted languages.
 *
 * **The root's attribute names vary between files.** The title is spelled `translation`, `name` or
 * `language` depending on who contributed the file; the copyright is `status`, `info` or `version`;
 * the URL is `link` or `site`. Each is read as the first non-blank of its group. A caller that knows
 * better — the download browser, which has a catalogue entry in hand — passes the values in and they
 * win outright.
 *
 * Parsing is StAX rather than DOM because these files reach 21.6 MB and are parsed inside the running
 * app: a DOM of ~31,000 verse elements costs several hundred MB of transient heap, where a pull parse
 * costs a buffer. The dialect has no mixed content, so a flat state machine is a complete reader.
 */
internal object BebliaParser {

    /** Protestant canon; anything outside it is not a book number this format can mean. */
    private const val MIN_BOOK = 1
    private const val MAX_BOOK = 66

    /** How much of the parse's byte progress is reported at once — a whole file in ~100 steps. */
    private const val PROGRESS_STEP = 0.01f

    /** The three spellings each piece of root metadata appears under, in the order they are tried. */
    private val TITLE_ATTRIBUTES = listOf("translation", "name", "language")
    private val RIGHTS_ATTRIBUTES = listOf("status", "info", "version")
    private val SOURCE_ATTRIBUTES = listOf("link", "site")

    /**
     * Language names that appear in these files' titles, mapped to codes [BookNames.LANGUAGE_LOOKUPS]
     * knows.
     *
     * Only consulted when the caller supplies no language, which in practice means the standalone
     * converter GUI rather than the download browser. Russian precedes Ukrainian because that was the
     * order of the branches this replaced, and a handful of titles name both.
     */
    private val TITLE_LANGUAGES = listOf(
        "Russian" to "RUS",
        "Ukrainian" to "UKR",
        "English" to "ENG",
        "German" to "DEU",
        "French" to "FRA",
        "Spanish" to "SPA",
        "Portuguese" to "POR",
        "Italian" to "ITA",
        "Dutch" to "NLD",
        "Polish" to "POL",
        "Chinese" to "ZHO",
        "Korean" to "KOR",
        "Arabic" to "ARA",
        "Hebrew" to "HEB",
    )

    /**
     * A reader that will not fetch anything off the network.
     *
     * These files are downloaded, so a `<!DOCTYPE>` naming an external entity would otherwise be
     * resolved during the parse. Both flags are needed: refusing DTDs outright also disposes of
     * billion-laughs expansion.
     */
    private fun inputFactory(): XMLInputFactory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        setProperty(XMLInputFactory.IS_COALESCING, true)
    }

    /**
     * Whether [xmlFile]'s root is a `<bible>` element carrying one of the three title spellings.
     *
     * Reads only as far as the first start element, so choosing a parser never costs a whole-file
     * parse. A file that cannot be opened or is not XML at all answers `false` and is left to the
     * Zefania path to reject, which keeps the failure in one place.
     */
    fun looksLikeBeblia(xmlFile: File): Boolean = try {
        xmlFile.inputStream().buffered().use { input ->
            val reader = inputFactory().createXMLStreamReader(input)
            try {
                var result = false
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        result = reader.localName == "bible" && TITLE_ATTRIBUTES.any { !reader.attribute(it).isNullOrBlank() }
                        break
                    }
                }
                result
            } finally {
                reader.close()
            }
        }
    } catch (_: Exception) {
        false
    }

    /**
     * Reads [xmlFile] into a [ParsedBible].
     *
     * Every caller-supplied value wins over what the file says, and blanks fall back to the root
     * attributes — so the download browser's catalogue metadata is authoritative while the standalone
     * converter, which passes nothing, still recovers a title, a copyright and a source URL.
     *
     * @param language uppercase code; `null` falls back to reading the title. Unlike the
     *   implementation this replaces, an unrecognised title yields `null` rather than defaulting to
     *   Russian — a wrong language silently mis-numbers Psalms and picks wrong book names.
     * @param onProgress 0..1 by bytes consumed, so a 20 MB file reports movement rather than freezing.
     * @throws XMLStreamException the file is not well-formed XML.
     */
    fun parse(
        xmlFile: File,
        language: String? = null,
        name: String = "",
        rights: String = "",
        source: String = "",
        identifier: String = "",
        onProgress: (Float) -> Unit = {},
    ): ParsedBible {
        val totalBytes = xmlFile.length().coerceAtLeast(1)
        var reported = 0f

        var rootTitle = ""
        var rootRights = ""
        var rootSource = ""
        val books = mutableListOf<BibleBook>()

        // Resolved once the root has been read, because the title is what names the language when the
        // caller supplies none — and the language decides every book name below it.
        var resolvedLanguage: String? = null

        var bookNumber = 0
        var chapterNumber = 0
        var verseNumber = 0
        var chapters = mutableListOf<BibleChapter>()
        var verses = mutableListOf<BibleVerse>()
        val verseText = StringBuilder()
        var inVerse = false

        xmlFile.inputStream().buffered().use { raw ->
            val counting = CountingInputStream(raw)
            // Deliberately a stream, not a reader: StAX then honours the byte-order mark several of
            // these files carry, where handing it a Reader turns that BOM into a fatal
            // "content not allowed in prolog".
            val reader = inputFactory().createXMLStreamReader(counting)
            try {
                while (reader.hasNext()) {
                    when (reader.next()) {
                        XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                            "bible" -> {
                                rootTitle = reader.firstAttribute(TITLE_ATTRIBUTES)
                                rootRights = reader.firstAttribute(RIGHTS_ATTRIBUTES)
                                rootSource = reader.firstAttribute(SOURCE_ATTRIBUTES)
                                resolvedLanguage = language?.trim()?.uppercase()?.ifBlank { null }
                                    ?: languageFromTitle(name.ifBlank { rootTitle })
                            }
                            "book" -> {
                                bookNumber = reader.attribute("number")?.trim()?.toIntOrNull() ?: 0
                                chapters = mutableListOf()
                            }
                            "chapter" -> {
                                chapterNumber = reader.attribute("number")?.trim()?.toIntOrNull() ?: 0
                                verses = mutableListOf()
                            }
                            "verse" -> {
                                verseNumber = reader.attribute("number")?.trim()?.toIntOrNull() ?: 0
                                verseText.setLength(0)
                                inVerse = true
                            }
                        }

                        XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA ->
                            if (inVerse) verseText.append(reader.text)

                        XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
                            "verse" -> {
                                inVerse = false
                                verses.add(
                                    BibleVerse(
                                        verseNumber,
                                        XmlToSpbConverter.applyPatch(
                                            verseText.toString(), resolvedLanguage,
                                            bookNumber, chapterNumber, verseNumber
                                        )
                                    )
                                )
                            }
                            "chapter" -> chapters.add(BibleChapter(chapterNumber, verses))
                            // A book numbered outside the canon is dropped rather than failing the
                            // file, matching how a book with no number has always been treated.
                            "book" -> if (bookNumber in MIN_BOOK..MAX_BOOK) {
                                books.add(BibleBook(bookNumber, bookName(bookNumber, resolvedLanguage), chapters))
                            }
                        }
                    }

                    val fraction = (counting.count.toFloat() / totalBytes).coerceIn(0f, 1f)
                    if (fraction - reported >= PROGRESS_STEP) {
                        reported = fraction
                        onProgress(fraction)
                    }
                }
            } finally {
                reader.close()
            }
        }
        onProgress(1f)

        return ParsedBible(
            name = name.ifBlank { rootTitle }.ifBlank { "Unknown" },
            description = "",
            language = resolvedLanguage,
            books = books,
            title = name.ifBlank { rootTitle },
            identifier = identifier,
            rights = rights.ifBlank { rootRights },
            source = source.ifBlank { rootSource },
        )
    }

    private fun bookName(number: Int, language: String?): String =
        BookNames.LANGUAGE_LOOKUPS[language]?.get(number)
            ?: BookNames.ENGLISH[number]
            ?: "Book $number"

    internal fun languageFromTitle(title: String): String? =
        TITLE_LANGUAGES.firstOrNull { (name, _) -> title.contains(name, ignoreCase = true) }?.second

    private fun XMLStreamReader.attribute(name: String): String? = getAttributeValue(null, name)

    private fun XMLStreamReader.firstAttribute(names: List<String>): String =
        names.firstNotNullOfOrNull { attribute(it)?.trim()?.ifBlank { null } }.orEmpty()

    /** Counts bytes handed to the parser, which is the only progress signal a pull parse offers. */
    private class CountingInputStream(delegate: InputStream) : FilterInputStream(delegate) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) count += it }
    }
}
