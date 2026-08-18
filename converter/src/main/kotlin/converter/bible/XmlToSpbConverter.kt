package converter.bible

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

data class BibleBook(
    val number: Int,
    val name: String,
    val chapters: List<BibleChapter>
)

data class BibleChapter(
    val number: Int,
    val verses: List<BibleVerse>
)

data class BibleVerse(
    val number: Int,
    val text: String
)

data class ParsedBible(
    val name: String,
    val description: String,
    val language: String?,
    val books: List<BibleBook>,
    /** Zefania `<INFORMATION>` metadata. Defaulted so existing callers are unaffected. */
    val title: String = "",
    val identifier: String = "",
    val rights: String = "",
    val source: String = ""
)

// Split into one small function per step, which is what keeps the readers below within the
// complexity and nesting limits. Splitting the object itself would scatter one file format across
// several files instead.
@Suppress("TooManyFunctions")
object XmlToSpbConverter {

    /** Longer than this and a psalm's first verse is content, whatever it opens with. */
    private const val MAX_SUPERSCRIPTION_LENGTH = 200

    /** How much text may follow a bracketed title before the verse counts as content. */
    private const val MAX_TITLE_REMAINDER = 40

    /** Longer than this and a chapter caption is a sentence about the book, not its name. */
    private const val MAX_CAPTION_NAME_LENGTH = 30

    // Languages that use LXX/Septuagint Psalm numbering (Orthodox traditions)
    private val LXX_PSALM_LANGUAGES = setOf(
        "RUS", "UKR", "BEL",           // East Slavic
        "SRP", "BUL", "MKD",           // South Slavic
        "RON", "RUM", "MOL",           // Romanian
        "KAT", "GEO",                  // Georgian
        "GRE", "GRC", "ELL",           // Greek
        "AMH", "ETH",                  // Ethiopian
        "COP",                         // Coptic
        "SYR", "ARC"                   // Syriac/Aramaic
    )

    /**
     * The psalms the two traditions number differently, LXX chapter to Hebrew chapter.
     *
     * Two are merged in the Septuagint and split in the Hebrew text (9 = 9+10, 113 = 114+115), and
     * two Hebrew psalms are split in the Septuagint (114 and 115 are both 116; 146 and 147 are both
     * 147). Each keeps the number of the Hebrew psalm it opens.
     */
    private val LXX_PSALM_EXCEPTIONS = mapOf(
        9 to 9, 113 to 114, 114 to 116, 115 to 116, 146 to 147, 147 to 147,
    )

    /** The runs where the Septuagint is exactly one behind the Hebrew numbering. */
    private val LXX_PSALMS_ONE_BEHIND = listOf(10..112, 116..145)

    /**
     * Maps LXX Psalm chapter number to Hebrew Psalm chapter number.
     * Used for the BXXXCXXXVXXX code so cross-referencing with Hebrew-numbered Bibles works.
     * Psalms 1-8 and 148 onwards are numbered alike and fall through unchanged.
     */
    private fun lxxToHebrewPsalm(lxxChapter: Int): Int = when {
        lxxChapter in LXX_PSALM_EXCEPTIONS -> LXX_PSALM_EXCEPTIONS.getValue(lxxChapter)
        LXX_PSALMS_ONE_BEHIND.any { lxxChapter in it } -> lxxChapter + 1
        else -> lxxChapter
    }

    /**
     * Detects if a psalm verse text is a standalone superscription (title only, no content).
     * Examples: "Начальнику хора. На струнных. Псалом Давида."
     *           "Псалом Давида, когда он бежал от Авессалома"
     * Counter-example: "«Псалом Давида.» Блажен муж, который не ходит..." (embedded title + content)
     */
    private fun isPsalmSuperscription(text: String): Boolean {
        val trimmed = text.trim()
        // Too long to be just a title
        if (trimmed.length > MAX_SUPERSCRIPTION_LENGTH) return false
        // Remove text inside «» brackets (title markers)
        val withoutBrackets = trimmed.replace(Regex("«[^»]*»\\.?"), "").trim()
        // If after removing bracketed title there's substantial content, it's embedded
        if (withoutBrackets.length > MAX_TITLE_REMAINDER) return false
        // Check for known superscription patterns
        val titlePatterns = listOf(
            "Псалом", "Молитва", "Начальнику", "Песнь", "Аллилуия",
            "Давида", "Асафа", "Кореевых", "Соломона", "Моисея", "Ефама", "Емана",
            "Psalm", "Prayer", "Song", "Maskil", "Miktam", "Shiggaion",
            "For the director", "Of David", "Of Asaph", "Of Solomon",
            "Псалом", "Пісня", "Молитва" // Ukrainian
        )
        val hasTitle = titlePatterns.any { trimmed.contains(it, ignoreCase = true) }
        // If it contains a title pattern and has no substantial content after removal, it's a superscription
        return hasTitle || withoutBrackets.isEmpty()
    }

    /**
     * Reads either dialect this converter understands, choosing between them by the file's root.
     *
     * The Beblia check comes first and costs only a read up to the first start element, so the
     * Zefania path's whole-file DOM parse is never paid to find out it was the wrong parser.
     */
    fun parse(xmlFile: File): ParsedBible {
        if (BebliaParser.looksLikeBeblia(xmlFile)) return BebliaParser.parse(xmlFile)

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(xmlFile)
        return parseZefania(doc.documentElement, xmlFile)
    }

    /**
     * Reads a Holy Bible XML file with catalogue metadata supplied by the caller.
     *
     * The download browser knows the translation's language, title and copyright from its catalogue,
     * where the file itself carries no language code at all and spells its other metadata three
     * different ways — so what the caller passes wins, and blanks fall back to the file. See
     * [BebliaParser] for the format and for why book names come out in English for most languages.
     */
    fun parseBeblia(
        xmlFile: File,
        language: String? = null,
        name: String = "",
        rights: String = "",
        source: String = "",
        identifier: String = "",
        onProgress: (Float) -> Unit = {},
    ): ParsedBible = BebliaParser.parse(xmlFile, language, name, rights, source, identifier, onProgress)

    private fun parseZefania(root: org.w3c.dom.Element, xmlFile: File): ParsedBible {
        var description = ""
        var language: String? = null
        var title = ""
        var identifier = ""
        var rights = ""
        var source = ""

        val infoNodes = root.getElementsByTagName("INFORMATION")
        if (infoNodes.length > 0) {
            val info = infoNodes.item(0)
            for (i in 0 until info.childNodes.length) {
                val child = info.childNodes.item(i)
                when (child.nodeName) {
                    "description" -> description = child.textContent ?: ""
                    "title" -> title = child.textContent?.trim() ?: ""
                    "identifier" -> identifier = child.textContent?.trim() ?: ""
                    "rights" -> rights = child.textContent?.trim() ?: ""
                    "source" -> source = child.textContent?.trim() ?: ""
                    "language" -> {
                        val xmlLang = (child.textContent ?: "").trim().uppercase()
                        val pathLower = xmlFile.absolutePath.lowercase()
                        language = if (xmlLang == "RUS" && ("ukrainian" in pathLower || "українська" in pathLower)) {
                            "UKR"
                        } else {
                            xmlLang
                        }
                    }
                }
            }
        }

        if (language.isNullOrBlank()) {
            val parts = xmlFile.absolutePath.replace("\\", "/").split("/")
            val parentFolder = parts.getOrNull(parts.size - 4)?.uppercase()
            language = when {
                parentFolder == "RUS" -> {
                    val pathLower = xmlFile.absolutePath.lowercase()
                    if ("ukrainian" in pathLower || "українська" in pathLower) "UKR" else "RUS"
                }
                parentFolder != null &&
                    (parentFolder in BookNames.LANGUAGE_LOOKUPS || parentFolder == "ENG") -> parentFolder
                else -> null
            }
        }

        // Some modules leave `biblename` empty but fill `<title>`; without this they end up
        // literally called "Unknown".
        val bibleName = root.getAttribute("biblename").ifBlank { title }.ifBlank { "Unknown" }

        val books = mutableListOf<BibleBook>()
        val bookNodes = root.getElementsByTagName("BIBLEBOOK")

        for (b in 0 until bookNodes.length) {
            val bookElem = bookNodes.item(b)
            val bookNum = bookElem.attributes.getNamedItem("bnumber")?.nodeValue?.toIntOrNull() ?: 0
            val bookName = getBookName(bookElem, bookNum, language)

            val chapters = mutableListOf<BibleChapter>()
            val chapterNodes = bookElem.childNodes

            for (c in 0 until chapterNodes.length) {
                val chapElem = chapterNodes.item(c)
                if (chapElem.nodeName != "CHAPTER") continue

                val chapNum = chapElem.attributes.getNamedItem("cnumber")?.nodeValue?.toIntOrNull() ?: 0
                val verses = mutableListOf<BibleVerse>()
                val verseNodes = chapElem.childNodes

                for (v in 0 until verseNodes.length) {
                    val versElem = verseNodes.item(v)
                    if (versElem.nodeName != "VERS") continue

                    val versNum = versElem.attributes.getNamedItem("vnumber")?.nodeValue?.toIntOrNull() ?: 0
                    val rawText = versElem.textContent ?: ""
                    val versText = applyPatch(rawText, language, bookNum, chapNum, versNum)
                    verses.add(BibleVerse(versNum, versText))
                }

                chapters.add(BibleChapter(chapNum, verses))
            }

            books.add(BibleBook(bookNum, bookName, chapters))
        }

        return ParsedBible(bibleName, description, language, books, title, identifier, rights, source)
    }

    fun convert(xmlFile: File, outputFile: File) = write(parse(xmlFile), outputFile)

    /**
     * Writes [bible] out as an `.spb` module.
     *
     * Split from [convert] so an already-parsed Bible can be written without re-reading the XML,
     * and so callers can show progress: [onProgress] reports 0..1 as books are written, which for
     * a full 66-book Bible is seconds rather than instant.
     */
    fun write(bible: ParsedBible, outputFile: File, onProgress: (Float) -> Unit = {}) {
        // Shared with the app's install-time naming rule so the header and the file name agree.
        val abbreviation = BibleCatalogNaming.abbreviation(bible.name)

        val rtl = if (BookNames.isRightToLeft(bible.language)) "1" else ""

        outputFile.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("##spDataVersion:\t1\n")
            w.write("##Title:\t${bible.name}\n")
            w.write("##Abbreviation:\t$abbreviation\n")
            w.write("##Information:\t${bible.description.oneLine()}\n")
            w.write("##RightToLeft:\t$rtl\n")
            // Attribution travels with the file, so it survives the user copying it elsewhere.
            if (bible.rights.isNotBlank()) w.write("##Copyright:\t${bible.rights.oneLine()}\n")
            if (bible.source.isNotBlank()) w.write("##Source:\t${bible.source.oneLine()}\n")

            for (book in bible.books) {
                w.write("${book.number}\t${book.name}\t${book.chapters.size}\n")
            }

            w.write("-----\n")

            val useLxxMapping = bible.language?.uppercase() in LXX_PSALM_LANGUAGES
            val psalmsBookNum = 19

            for ((index, book) in bible.books.withIndex()) {
                for (chapter in book.chapters) {
                    // For LXX Psalms, detect if verse 1 is a standalone superscription.
                    // If so, code it as V000 and offset subsequent verse numbers by -1.
                    val isLxxPsalm = useLxxMapping && book.number == psalmsBookNum
                    val hasStandaloneTitle = isLxxPsalm && chapter.verses.isNotEmpty()
                            && isPsalmSuperscription(chapter.verses.first().text)
                    val codeChapter = if (isLxxPsalm)
                        lxxToHebrewPsalm(chapter.number) else chapter.number

                    for (verse in chapter.verses) {
                        val codeVerse = if (hasStandaloneTitle) verse.number - 1 else verse.number
                        val verseId = "B%03dC%03dV%03d".format(book.number, codeChapter, codeVerse)
                        w.write("$verseId\t${book.number}\t${chapter.number}\t${verse.number}\t${verse.text}\n")
                    }
                }
                onProgress((index + 1).toFloat() / bible.books.size)
            }
        }
    }

    /**
     * Header values are tab-separated single lines, so a `<description>` or `<rights>` containing
     * newlines or tabs would corrupt the file structure.
     */
    private fun String.oneLine(): String =
        replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim()

    fun convertBatch(xmlFiles: List<File>, outputDir: File): List<Pair<File, File>> {
        outputDir.mkdirs()
        return xmlFiles.map { xmlFile ->
            val outputFile = File(outputDir, xmlFile.nameWithoutExtension + ".spb")
            convert(xmlFile, outputFile)
            xmlFile to outputFile
        }
    }

    /** Shared with [BebliaParser], which reads its verses without ever building a DOM node. */
    internal fun applyPatch(text: String, language: String?, bookNum: Int, chapNum: Int, versNum: Int): String {
        val patch = VersePatches.PATCHES[Triple(bookNum, chapNum, versNum)] ?: return text
        if (patch.language != null && patch.language != language?.uppercase()) return text
        if (patch.matchText != null) return if (text == patch.matchText) patch.correctedText else text
        if (patch.minimumPrefixLength > 0 && text.length < patch.minimumPrefixLength) return text
        return patch.correctedText
    }

    private fun getBookName(bookElem: org.w3c.dom.Node, bookNum: Int, language: String?): String {
        val canonical = BookNames.ENGLISH[bookNum] ?: "Book $bookNum"
        if (language == "ENG") declaredName(bookElem)?.let { return it }
        if (language != null && language in BookNames.LANGUAGE_LOOKUPS) {
            return BookNames.LANGUAGE_LOOKUPS[language]?.get(bookNum) ?: canonical
        }
        return captionName(bookElem) ?: canonical
    }

    /** The name an English module writes on the book itself, long form before short. */
    private fun declaredName(bookElem: org.w3c.dom.Node): String? =
        listOf("bname", "bsname")
            .firstNotNullOfOrNull { bookElem.attributes.getNamedItem(it)?.nodeValue?.takeIf { name -> name.isNotBlank() } }

    /**
     * The book's name as its first chapter's caption states it, for a module in a language with no
     * table of its own.
     *
     * A caption reads "1. Genesis", so what is wanted is what follows the number -- and only when
     * what follows is short enough to be a name rather than a sentence about the book.
     */
    private fun captionName(bookElem: org.w3c.dom.Node): String? {
        val firstChapter = bookElem.childNodes.elements().firstOrNull { it.nodeName == "CHAPTER" } ?: return null
        return firstChapter.childNodes.elements()
            .filter { it.nodeName == "CAPTION" }
            .mapNotNull { caption ->
                val text = caption.textContent?.trim().orEmpty()
                text.substringAfterLast(".").trim()
                    .takeIf { "." in text && it.isNotBlank() && it.length < MAX_CAPTION_NAME_LENGTH }
            }
            .firstOrNull()
    }

    /** A [org.w3c.dom.NodeList] as a sequence, which it is not by itself. */
    private fun org.w3c.dom.NodeList.elements(): Sequence<org.w3c.dom.Node> =
        (0 until length).asSequence().map { item(it) }
}
