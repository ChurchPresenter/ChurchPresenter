package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.diagnostics.CrashReporter
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets

private const val MIN_SHORTENABLE_WORD_LENGTH = 3
private const val MAX_ACRONYM_WORDS = 4
private const val SHORT_WORD_MAX_LENGTH = 4
private const val SHORT_WORD_TRUNCATED_LENGTH = 3
private const val SHORT_TITLE_MAX_LENGTH = 5
private const val REGEX_GROUP_THIRD = 3
private const val VERSE_GROUP_NUMBER = 7
private const val VERSE_GROUP_TEXT = 8
private const val TITLE_PREFIX_LENGTH = 8
private const val CHAPTER_KEY_BOOK_SHIFT = 20
private const val CHAPTER_KEY_CHAPTER_MASK = 0xFFFFFL

data class ChapterResult(val previewIds: List<String>, val verses: List<String>)

/** A parenthesised aside in a module title: "King James Version (KJV)", "… (Public Domain)". */
private val PARENTHESISED_ASIDE = Regex("\\([^)]*\\)")

private val SPB_CODE_REGEX = Regex("^B(\\d{3})C(\\d{3})V(\\d{3})$")
private val SPB_VERSE_LINE_REGEX =
    Regex("^(B(\\d{3})C(\\d{3})V(\\d{3}))\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(.*)")
private val SPB_BOOK_HEADER_REGEX = Regex("^(\\d+)\\s+(.+?)\\s+(\\d+)$")

/**
 * Why a module could not be read, in whole or in part.
 *
 * A load never throws — every caller loads several modules at once and one bad file must not take
 * the others down with it — so this is how a failure travels back out. It is what the Bible tab
 * shows the operator and what [CrashReporter] has already recorded by the time it exists.
 *
 * @property resourcePath what was asked for — the absolute path, or a classpath resource name.
 * @property reason the exception's own message, kept verbatim; it is the only thing that says
 *   *why*, and it is what someone asking for help has to send in.
 * @property partial true when some of the module parsed before the failure. Whatever did parse is
 *   kept and shown as far as it goes, so a file truncated three books in still opens on those
 *   three books rather than on nothing.
 */
data class BibleLoadError(
    val resourcePath: String,
    val reason: String,
    val partial: Boolean,
) {
    /** The file's own name, which is what the operator recognises — not the whole path. */
    val fileName: String
        get() = resourcePath.substringAfterLast('/').substringAfterLast('\\')
}

class Bible {
    private var bibleAbbreviation: String = ""
    private var bibleTitle: String = ""
    private val books = mutableListOf<BibleBook>()
    private val operatorBible = mutableListOf<BibleVerse>()
    // Index: (bookId, chapterNum) -> ordered list of verses — built at load time for O(1) lookup
    private val chapterIndex = HashMap<Long, List<BibleVerse>>()
    // Maps code (BXXXCXXXVXXX) book/chapter to display book/chapter for cross-referencing
    private val codeToDisplayMap = HashMap<Long, Long>()
    // Index: full internal code id (BXXXCXXXVXXX) -> the verse, for exact cross-Bible lookups
    private val codeIndex = HashMap<String, BibleVerse>()

    /**
     * Why the last load failed, or null when it succeeded — see [BibleLoadError].
     *
     * Cleared at the start of every load, so it always describes the most recent one.
     */
    var loadError: BibleLoadError? = null
        private set

    /**
     * A short form of a book name, in whatever language the module names its books.
     *
     * Single words shorten to their first three or four characters ("Genesis" → "Gen", "Бытие" →
     * "Быт"). A name that leads with a numeral keeps it and shortens the word after it
     * ("1 Corinthians" → "1 Cor", "1 Коринфянам" → "1 Кор"), which is the form these books are
     * conventionally written in and is what makes them tellable apart at a glance. Anything else
     * multi-word takes the significant word — the first one longer than three characters, so
     * "Song of Solomon" → "Song" rather than "SoS".
     *
     * Initials remain the fallback for names with no word worth shortening.
     */
    internal fun generateAbbreviation(bookName: String): String {
        if (bookName.isBlank()) return ""

        val words = bookName.trim().split(Regex("\\s+"))
        return when {
            // Single word: take first 3-4 characters
            words.size == 1 -> shortenWord(words[0])
            // "1 Corinthians", "2 Samuel" — the numeral is the whole point of the name.
            words.size >= 2 && words[0].all { it.isDigit() } ->
                "${words[0]} ${shortenWord(words[1])}"
            else -> words.firstOrNull { it.length > MIN_SHORTENABLE_WORD_LENGTH }?.let(::shortenWord)
                ?: words.take(MAX_ACRONYM_WORDS).joinToString("") { it.first().uppercase() }
        }
    }

    /** One word shortened: kept whole at three characters or fewer, else its first three or four. */
    private fun shortenWord(word: String): String = when {
        word.length <= SHORT_WORD_MAX_LENGTH -> word
        else -> word.take(SHORT_WORD_TRUNCATED_LENGTH)
    }

    /**
     * Extract Bible version abbreviation from title or filename
     * Examples: "Russian Synodal Translation" -> "RST"
     *           "King James Version" -> "KJV"
     *           "King James Version (KJV)" -> "KJV"
     *           "ru_RST77.spb" -> "RST77"
     *
     * A parenthesised aside is dropped before the initials are taken, and each initial is the
     * word's first *letter or digit*. Without either step a bracket becomes an initial in its own
     * right — "King James Version (KJV)" abbreviated to "KJV(", which is what the operator then
     * saw beside every verse on screen.
     */
    private fun extractBibleAbbreviation(title: String?, filename: String): String {
        // First try to extract from title if available
        if (!title.isNullOrBlank()) {
            // A title that is nothing but an aside — "(KJV)" — still has to name itself, so fall
            // back to the title with its punctuation stripped rather than to the file name.
            val cleaned = title.replace(PARENTHESISED_ASIDE, " ").trim()
                .ifEmpty { title.filter { it.isLetterOrDigit() || it.isWhitespace() }.trim() }

            if (cleaned.isNotEmpty()) {
                val words = cleaned.split(Regex("\\s+"))

                // If title is short (like "RSV" or "KJV"), use it as-is — minus any punctuation
                // riding along with it, so "KJV." does not label every verse "KJV.".
                val loneWord = words.singleOrNull()?.filter { it.isLetterOrDigit() }
                if (loneWord != null && loneWord.isNotEmpty() && loneWord.length <= SHORT_TITLE_MAX_LENGTH) {
                    return loneWord
                }

                // Generate abbreviation from title words
                return words.mapNotNull { word ->
                    word.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
                }.take(MAX_ACRONYM_WORDS).joinToString("")
            }
        }

        // Fallback to filename without extension
        return filename.substringBeforeLast(".").substringAfterLast("/").substringAfterLast("\\")
    }

    /**
     * Fast path: reads ONLY the header section of an SPB file to populate book names.
     * Stops as soon as the separator line or first verse is encountered.
     * Call this first to show the book list immediately, then call loadFromSpb() for full data.
     */
    private fun openHeaderReader(resourcePath: String): java.io.BufferedReader {
        val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
        if (inputStream != null) return inputStream.bufferedReader(StandardCharsets.UTF_8)
        val path = Paths.get(resourcePath)
        if (!Files.exists(path)) {
            throw FileNotFoundException(
                "loadBooksOnly: module not found on classpath or filesystem: $resourcePath"
            )
        }
        return Files.newBufferedReader(path, StandardCharsets.UTF_8)
    }

    private fun collectBookHeader(
        line: String,
        headerOrder: MutableList<Int>,
        parsedBookNames: MutableMap<Int, String>,
        parsedChapterCounts: MutableMap<Int, Int>,
    ) {
        val m = SPB_BOOK_HEADER_REGEX.matchEntire(line) ?: return
        val bookId = m.groupValues[1].toInt()
        headerOrder.add(bookId)
        parsedBookNames[bookId] = m.groupValues[2].trim()
        parsedChapterCounts[bookId] = m.groupValues[REGEX_GROUP_THIRD].toInt()
    }

    fun loadBooksOnly(resourcePath: String) {
        books.clear()
        loadError = null
        val headerOrder = mutableListOf<Int>()
        val parsedBookNames = mutableMapOf<Int, String>()
        val parsedChapterCounts = mutableMapOf<Int, Int>()
        try {
            openHeaderReader(resourcePath).use { r ->
                r.lineSequence()
                    .map { it.trimEnd('\r', '\n') }
                    .takeWhile { !it.startsWith("-----") && !it.startsWith("B") }
                    .filter { it.isNotEmpty() && !it.startsWith("##") }
                    .forEach { line ->
                        collectBookHeader(line, headerOrder, parsedBookNames, parsedChapterCounts)
                    }
            }
        } catch (e: Exception) {
            recordLoadFailure(e, resourcePath, parsedAnything = headerOrder.isNotEmpty())
        }
        // Built from whatever the scan managed to read: on a header that fails partway through,
        // that is the books it got to, not nothing.
        for (b in headerOrder) {
            val name = parsedBookNames[b] ?: "Book $b"
            books.add(BibleBook(
                book = name,
                bookId = b.toString(),
                chapterCount = parsedChapterCounts[b] ?: 0,
                abbreviation = generateAbbreviation(name)
            ))
        }
    }

    /**
     * Records a failed load and reports it, once, in the one place both load paths funnel through.
     *
     * The exception is deliberately not rethrown. A load failure has to reach the operator as a
     * message beside the book list, not as an exception unwinding through a coroutine that is
     * loading several translations at once — see [BibleLoadError].
     */
    private fun recordLoadFailure(e: Exception, resourcePath: String, parsedAnything: Boolean) {
        loadError = BibleLoadError(
            resourcePath = resourcePath,
            reason = e.message?.takeIf { it.isNotBlank() } ?: e.toString(),
            partial = parsedAnything,
        )
        CrashReporter.reportException(e, "Loading Bible module $resourcePath")
    }

    // New: load from a BibleQuote .spb plain text module
    // resourcePath: either a classpath resource name (e.g. "ru_RST77.spb") or an absolute file path
    fun loadFromSpb(resourcePath: String, bookNames: List<String> = emptyList()) {
        operatorBible.clear()
        books.clear()
        loadError = null

        // Declared out here so a failure partway through still has whatever parsed to build from.
        val state = SpbParseState()

        try {
            openSpbReader(resourcePath).use { r ->
                r.forEachLine { rawLine -> parseSpbLine(rawLine.trimEnd('\r', '\n'), state) }
                // Flush last verse if using multiline format
                flushPendingVerse(state)
            }
        } catch (e: Exception) {
            recordLoadFailure(
                e, resourcePath,
                parsedAnything = operatorBible.isNotEmpty() || state.headerOrder.isNotEmpty(),
            )
        }

        // Outside the try: on a module that stops being readable partway through, the books and
        // verses that did parse are still indexed and still shown, as far as they go.
        buildBooksFrom(state, bookNames)

        // Store full title and abbreviation
        this.bibleTitle = state.bibleTitle
            ?: resourcePath.substringBeforeLast(".").substringAfterLast("/").substringAfterLast("\\")
        bibleAbbreviation = extractBibleAbbreviation(state.bibleTitle, resourcePath)

        // Build chapter index for O(1) lookup in getChapter()
        buildChapterIndex()
    }

    /** Everything one .spb scan accumulates, so a failure partway through still has it. */
    private class SpbParseState {
        val bookChapterMap = mutableMapOf<Int, MutableSet<Int>>()
        val headerOrder = mutableListOf<Int>()
        val parsedBookNames = mutableMapOf<Int, String>()
        var bibleTitle: String? = null
        var currentCode: String? = null
        val pendingText = StringBuilder()
        var headerParsed = false
    }

    private fun openSpbReader(resourcePath: String): java.io.BufferedReader {
        val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
        if (inputStream != null) return inputStream.bufferedReader(StandardCharsets.UTF_8)
        val path = Paths.get(resourcePath)
        require(Files.exists(path)) {
            "loadFromSpb: resource not found on classpath or filesystem: $resourcePath"
        }
        return Files.newBufferedReader(path, StandardCharsets.UTF_8)
    }

    private fun parseSpbLine(line: String, state: SpbParseState) {
        // Extract Bible title from ##Title: line
        if (line.startsWith("##Title:")) {
            state.bibleTitle = line.substring(TITLE_PREFIX_LENGTH).trim()
            return
        }
        // Skip other metadata lines
        if (line.startsWith("##")) return
        if (!state.headerParsed && line.isNotEmpty() && parseSpbHeaderLine(line, state)) return
        // Skip separator line
        if (line.startsWith("-----")) {
            state.headerParsed = true
            return
        }
        if (parseSpbVerseLine(line, state)) return
        parseSpbLegacyLine(line, state)
    }

    /** True once the line has been consumed as a book header or as the end of the header block. */
    private fun parseSpbHeaderLine(line: String, state: SpbParseState): Boolean {
        val headerMatch = SPB_BOOK_HEADER_REGEX.matchEntire(line)
        if (headerMatch != null) {
            val bookId = headerMatch.groupValues[1].toInt()
            state.headerOrder.add(bookId)
            state.parsedBookNames[bookId] = headerMatch.groupValues[2].trim()
            return true
        }
        // Check if we've reached the separator line (-----) or first verse
        if (line.startsWith("-----") || line.startsWith("B")) {
            state.headerParsed = true
            // A separator is done with here; a line starting with B is still a verse to process.
            return line.startsWith("-----")
        }
        return false
    }

    private fun parseSpbVerseLine(line: String, state: SpbParseState): Boolean {
        val verseMatch = SPB_VERSE_LINE_REGEX.matchEntire(line) ?: return false
        val code = verseMatch.groupValues[1]
        // Code numbers from BXXXCXXXVXXX (internal/Hebrew numbering)
        val codeBook = verseMatch.groupValues[2].toInt()
        val codeChapter = verseMatch.groupValues[3].toInt()
        // Display reference numbers (native numbering, e.g. LXX for Russian)
        val b = verseMatch.groupValues[5].toInt()
        val ch = verseMatch.groupValues[6].toInt()
        addVerse(
            state, code, b, ch,
            verseMatch.groupValues[VERSE_GROUP_NUMBER].toInt(),
            verseMatch.groupValues[VERSE_GROUP_TEXT].trim(),
        )
        // Map code reference to display reference for cross-Bible lookups
        codeToDisplayMap[chapterKey(codeBook, codeChapter)] = chapterKey(b, ch)
        state.currentCode = null
        state.pendingText.setLength(0)
        return true
    }

    /** Fallback for older SPB format: a bare code line followed by its text on the lines after it. */
    private fun parseSpbLegacyLine(line: String, state: SpbParseState) {
        if (SPB_CODE_REGEX.matchEntire(line) != null) {
            flushPendingVerse(state)
            state.currentCode = line
            state.pendingText.setLength(0)
        } else if (state.currentCode != null) {
            if (state.pendingText.isNotEmpty()) state.pendingText.append("\n")
            state.pendingText.append(line)
        }
    }

    private fun flushPendingVerse(state: SpbParseState) {
        val code = state.currentCode ?: return
        val prev = SPB_CODE_REGEX.matchEntire(code) ?: error("Invalid verse code: $code")
        addVerse(
            state, code,
            prev.groupValues[1].toInt(), prev.groupValues[2].toInt(), prev.groupValues[REGEX_GROUP_THIRD].toInt(),
            state.pendingText.toString().trim(),
        )
    }

    private fun addVerse(state: SpbParseState, code: String, book: Int, chapter: Int, verse: Int, text: String) {
        operatorBible.add(
            BibleVerse(
                verseId = code,
                book = book,
                chapter = chapter,
                verseNumber = verse,
                verseText = text
            )
        )
        state.bookChapterMap.getOrPut(book) { mutableSetOf() }.add(chapter)
    }

    /** Book list in header order first, then any book seen only in verse data. */
    private fun buildBooksFrom(state: SpbParseState, bookNames: List<String>) {
        val headerBookIds = state.headerOrder.toSet()
        val maxBook = state.bookChapterMap.keys.maxOrNull() ?: 0
        val fromVerses = (1..maxBook).filter { it !in headerBookIds && state.bookChapterMap.containsKey(it) }
        for (b in state.headerOrder + fromVerses) {
            val name = when {
                state.parsedBookNames.containsKey(b) && b in headerBookIds -> state.parsedBookNames.getValue(b)
                bookNames.size >= b -> bookNames[b - 1]
                else -> "Book $b"
            }
            books.add(BibleBook(
                book = name,
                bookId = b.toString(),
                chapterCount = state.bookChapterMap[b]?.maxOrNull() ?: 0,
                abbreviation = generateAbbreviation(name)
            ))
        }
    }

    /** Encodes (bookId, chapterNum) as a single Long key for the HashMap. */
    private fun chapterKey(
        book: Int,
        chapter: Int
    ): Long = book.toLong().shl(CHAPTER_KEY_BOOK_SHIFT) or chapter.toLong()

    private fun buildChapterIndex() {
        chapterIndex.clear()
        codeIndex.clear()
        // Group verses by (book, chapter) preserving their parsed order
        val grouped = LinkedHashMap<Long, MutableList<BibleVerse>>()
        for (verse in operatorBible) {
            grouped.getOrPut(chapterKey(verse.book, verse.chapter)) { mutableListOf() }.add(verse)
            // Index by the internal code id so a code reference resolves to this Bible's
            // own display numbering exactly, regardless of the code→display chapter map.
            codeIndex[verse.verseId] = verse
        }
        chapterIndex.putAll(grouped)
    }

    /** The module's books in header order, or empty when nothing has been loaded yet. */
    fun getBooks(): List<String> = books.map { it.book }

    fun getChapter(book: Int, chapter: Int): ChapterResult {
        val previewIds = mutableListOf<String>()
        val verseList = mutableListOf<String>()

        // O(1) lookup via pre-built index — no full scan needed
        val verses = chapterIndex[chapterKey(book, chapter)] ?: emptyList()

        var verseOld = 0
        for (bv in verses) {
            val verse = bv.verseNumber
            val verseText: String
            val id: String
            if (verse == verseOld) {
                verseText = "${verseList.last().substringAfter(". ")} ${bv.verseText}".trim()
                id = "${previewIds.last()},${bv.verseId}"
                verseList.removeLast()
                previewIds.removeLast()
            } else {
                verseText = bv.verseText
                id = bv.verseId
            }
            verseList.add("$verse. $verseText")
            previewIds.add(id)
            verseOld = verse
        }

        return ChapterResult(previewIds = previewIds.toList(), verses = verseList.toList())
    }

    fun searchBible(allWords: Boolean, searchExp: Regex): List<BibleSearch> {
        return searchBibleInternal(allWords, searchExp)
    }

    fun searchBible(allWords: Boolean, searchExp: Regex, book: Int): List<BibleSearch> {
        return searchBibleInternal(allWords, searchExp, book)
    }

    fun searchBible(allWords: Boolean, searchExp: Regex, book: Int, chapter: Int): List<BibleSearch> {
        return searchBibleInternal(allWords, searchExp, book, chapter)
    }

    private fun searchBibleInternal(
        allWords: Boolean,
        searchExp: Regex,
        book: Int? = null,
        chapter: Int? = null
    ): List<BibleSearch> = CrashReporter.trace("bible.search", "Search Bible") {
        val returnResults = mutableListOf<BibleSearch>()
        var sw = searchExp.pattern
        sw = sw.replace("\\b(", "").replace(")\\b", "")

        // Precompile per-word regexes outside the loop
        val wordRegexes = if (allWords) {
            sw.split("|").map { word ->
                Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
            }
        } else emptyList()

        operatorBible
            .filter { bv ->
                val matchesText = searchExp.containsMatchIn(bv.verseText)
                val matchesBook = book == null || bv.book == book
                val matchesChapter = chapter == null || bv.chapter == chapter
                matchesText && matchesBook && matchesChapter
            }
            .forEach { bv ->
                if (allWords) {
                    if (wordRegexes.all { it.containsMatchIn(bv.verseText) }) {
                        addSearchResult(bv, returnResults)
                    }
                } else {
                    addSearchResult(bv, returnResults)
                }
            }

        returnResults
    }

    private val bookIdToName: Map<String, String> get() =
        books.associate { it.bookId to it.book }

    private fun addSearchResult(bv: BibleVerse, bsl: MutableList<BibleSearch>) {
        val bookName = bookIdToName[bv.book.toString()] ?: ""
        val chapter = bv.chapter.toString()
        val verse = bv.verseNumber.toString()

        bsl.add(BibleSearch(
            book = bookName,
            chapter = chapter,
            verse = verse,
            verseText = "$bookName $chapter:$verse ${bv.verseText}"
        ))
    }

    fun getBookCount(): Int {
        return books.size
    }

    fun getChapterCount(bookIndex: Int): Int {
        // bookIndex is zero-based in the UI; our books list is 0-based
        val b = books.getOrNull(bookIndex)
        return b?.chapterCount ?: 0
    }

    /**
     * Returns the number of distinct verses for a given book+chapter.
     * O(1) via chapterIndex — safe to call from any thread.
     */
    fun getVerseCountForChapter(book: Int, chapter: Int): Int =
        chapterIndex[chapterKey(book, chapter)]?.size ?: 0

    // Get verse details for presenter screen
    fun getVerseDetails(book: Int, chapter: Int, verseNumber: Int): Triple<String, String, String>? {
        // O(1) lookup via chapterIndex, then find specific verse number
        val bibleVerse = chapterIndex[chapterKey(book, chapter)]
            ?.firstOrNull { it.verseNumber == verseNumber } ?: return null
        val bookName = books.firstOrNull { it.bookId == book.toString() }?.book ?: "Book $book"
        return Triple(bookName, bibleVerse.verseText, bibleVerse.verseId)
    }

    /**
     * Returns the raw list of [BibleVerse] objects for the given book+chapter.
     * O(1) via chapterIndex — safe to call from any thread and does NOT mutate any state.
     */
    fun getChapterVerses(book: Int, chapter: Int): List<BibleVerse> =
        chapterIndex[chapterKey(book, chapter)] ?: emptyList()

    /**
     * Returns the book name for the given 1-based book id, or null if not found.
     */
    fun getBookName(bookId: Int): String? = books.firstOrNull { it.bookId == bookId.toString() }?.book

    /**
     * Returns the short form of the book name for the given 1-based book id, or null if not found.
     *
     * In the module's own language, since it is derived from the name the module gives the book —
     * which is what a reference shown beside this module's verse text has to be written in.
     */
    fun getBookAbbreviation(bookId: Int): String? =
        books.firstOrNull { it.bookId == bookId.toString() }?.abbreviation?.takeIf { it.isNotBlank() }

    /**
     * Returns the SPB book ID for the given 0-based display index.
     */
    fun getBookId(displayIndex: Int): Int =
        books.getOrNull(displayIndex)?.bookId?.toIntOrNull() ?: (displayIndex + 1)

    /**
     * Returns the SPB book ID for the given book name (as returned by [getBookName]/[getCanonicalBooks]),
     * or null if not found. Used to compute a canonical code reference (see [getCodeReference]) from a
     * plain book name — e.g. when broadcasting a live verse over Instance Link, where only the name
     * crosses the wire, not this Bible's internal book ID.
     */
    fun getBookIdByName(name: String): Int? = books.firstOrNull { it.book == name }?.bookId?.toIntOrNull()

    /**
     * Returns the 0-based display index for the given canonical book ID, or -1 if not found.
     * Falls back to (bookId - 1) for Bibles where the internal numbering matches canonical order.
     */
    fun getDisplayIndexForBookId(bookId: Int): Int {
        val direct = books.indexOfFirst { it.bookId == bookId.toString() }
        return if (direct >= 0) direct else (bookId - 1)
    }

    /**
     * Extracts the internal code book/chapter/verse from a verseId like "B019C023V001".
     * Returns (book, chapter, verse) or null if the format doesn't match.
     */
    fun parseVerseCode(verseId: String): Triple<Int, Int, Int>? {
        val m = Regex("B(\\d{3})C(\\d{3})V(\\d{3})").matchEntire(verseId) ?: return null
        return Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[REGEX_GROUP_THIRD].toInt())
    }

    /**
     * Returns the internal code book/chapter/verse for a given display reference in this Bible.
     * Used to cross-reference between Bibles with different numbering systems.
     */
    fun getCodeReference(book: Int, chapter: Int, verseNumber: Int): Triple<Int, Int, Int>? {
        val verse = chapterIndex[chapterKey(book, chapter)]
            ?.firstOrNull { it.verseNumber == verseNumber } ?: return null
        return parseVerseCode(verse.verseId)
    }

    /**
     * Looks up a verse by its internal code reference (BXXXCXXXVXXX numbering),
     * translating to this Bible's display numbering first.
     * Returns: (bookName, verseText, verseId, displayChapter, displayVerse)
     */
    data class CodeLookupResult(
        val bookName: String, val verseText: String, val verseId: String,
        val displayChapter: Int, val displayVerse: Int
    )

    fun getVerseDetailsByCode(codeBook: Int, codeChapter: Int, codeVerse: Int): CodeLookupResult? {
        // Preferred: resolve the exact verse by its internal code id. This yields this Bible's
        // own display book/chapter/verse directly, so a translation with different numbering
        // (e.g. Synodal Psalm 135 vs KJV 136) always shows its native reference — never the
        // incoming code number.
        val codeId = "B%03dC%03dV%03d".format(codeBook, codeChapter, codeVerse)
        codeIndex[codeId]?.let { bv ->
            val bookName = books.firstOrNull { it.bookId == bv.book.toString() }?.book ?: "Book ${bv.book}"
            return CodeLookupResult(bookName, bv.verseText, bv.verseId, bv.chapter, bv.verseNumber)
        }

        // Fallback: map the code chapter to a display chapter, then look up the verse there.
        val displayKey = codeToDisplayMap[chapterKey(codeBook, codeChapter)]
        val displayBook: Int
        val displayChapter: Int
        if (displayKey != null) {
            displayBook = (displayKey shr CHAPTER_KEY_BOOK_SHIFT).toInt()
            displayChapter = (displayKey and CHAPTER_KEY_CHAPTER_MASK).toInt()
        } else {
            displayBook = codeBook
            displayChapter = codeChapter
        }
        val result = getVerseDetails(displayBook, displayChapter, codeVerse) ?: return null
        return CodeLookupResult(result.first, result.second, result.third, displayChapter, codeVerse)
    }

    // Diagnostic helper: number of parsed verses from SPB
    fun getVerseCount(): Int {
        return operatorBible.size
    }

    /**
     * Get Bible translation abbreviation (e.g., "RSV", "KJV")
     */
    fun getBibleAbbreviation(): String {
        return bibleAbbreviation
    }

    fun getBibleTitle(): String {
        return bibleTitle
    }

    companion object {
        /** Title and testament coverage of an SPB file, read without parsing any verse data. */
        data class TranslationSummary(val title: String?, val hasOldTestament: Boolean, val hasNewTestament: Boolean)

        // Canonical book numbering used throughout the .spb format: 1-39 = Old Testament,
        // 40-66 = New Testament (see the header book-list lines this reads).
        private val OLD_TESTAMENT_BOOK_IDS = 1..39
        private val NEW_TESTAMENT_BOOK_IDS = 40..66

        /**
         * How far in to keep looking for header content: the `##` block plus a full 66-book list,
         * with room to spare. The stop conditions below (`-----`, a verse line) are what normally
         * ends the scan; this is the backstop for a module that has neither, where without it a
         * "header scan" would read the whole multi-megabyte file.
         */
        const val HEADER_SCAN_LINE_LIMIT = 120

        /**
         * Far enough to pass the `##` block of any real module, and the limit the title-only callers
         * use. A title further in than this is not looked for -- scanning a folder of large modules
         * for one would stall the pickers that do it per file.
         */
        const val TITLE_SCAN_LINE_LIMIT = 10

        /**
         * Fast path like [loadBooksOnly]: reads only the header block of an SPB file -- its
         * `##Title:` line and which book IDs appear -- stopping at the first verse line, so a
         * caller can show a translation's title and OT/NT coverage without the full verse parse
         * [loadFromSpb] requires.
         *
         * [maxLines] bounds how far in it will look. A caller that only wants the title can pass
         * [TITLE_SCAN_LINE_LIMIT] and skip the book list entirely.
         */
        private class SummaryScan {
            var title: String? = null
            var hasOld = false
            var hasNew = false
        }

        private fun openSummaryReader(resourcePath: String): java.io.BufferedReader? {
            val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            if (inputStream != null) return inputStream.bufferedReader(StandardCharsets.UTF_8)
            val path = Paths.get(resourcePath)
            if (!Files.exists(path)) return null
            return Files.newBufferedReader(path, StandardCharsets.UTF_8)
        }

        private fun scanSummaryLine(line: String, scan: SummaryScan) {
            // The converter writes a TAB after the colon and hand-made modules often write a
            // space, so the separator is trimmed, not counted.
            if (line.startsWith("##Title:")) scan.title = line.removePrefix("##Title:").trim()
            if (line.startsWith("##") || line.isEmpty()) return
            when (SPB_BOOK_HEADER_REGEX.matchEntire(line)?.groupValues?.get(1)?.toIntOrNull()) {
                in OLD_TESTAMENT_BOOK_IDS -> scan.hasOld = true
                in NEW_TESTAMENT_BOOK_IDS -> scan.hasNew = true
                else -> Unit
            }
        }

        fun readTranslationSummary(
            resourcePath: String,
            maxLines: Int = HEADER_SCAN_LINE_LIMIT,
        ): TranslationSummary? {
            try {
                val reader = openSummaryReader(resourcePath) ?: return null
                val scan = SummaryScan()
                reader.use { r ->
                    r.lineSequence()
                        .take(maxLines)
                        .map { it.trimEnd('\r', '\n') }
                        .takeWhile { !it.startsWith("-----") && !it.startsWith("B") }
                        .forEach { line -> scanSummaryLine(line, scan) }
                }
                return TranslationSummary(scan.title, scan.hasOld, scan.hasNew)
            } catch (_: Exception) {
                return null
            }
        }
    }
}
