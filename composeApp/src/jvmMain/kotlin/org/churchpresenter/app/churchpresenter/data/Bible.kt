package org.churchpresenter.app.churchpresenter.data

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets

// Note: This conversion assumes you're using a Kotlin database library
// You may need to adapt the SQL queries based on your specific database framework
// (e.g., Room, Exposed, JDBC, etc.)

data class ChapterResult(val previewIds: List<String>, val verses: List<String>)

class Bible {
    private var bibleId: String = ""
    private var bibleAbbreviation: String = ""
    private var bibleTitle: String = ""
    private val books = mutableListOf<BibleBook>()
    private val operatorBible = mutableListOf<BibleVerse>()
    // Index: (bookId, chapterNum) -> ordered list of verses — built at load time for O(1) lookup
    private val chapterIndex = HashMap<Long, List<BibleVerse>>()
    // Maps code (BXXXCXXXVXXX) book/chapter to display book/chapter for cross-referencing
    private val codeToDisplayMap = HashMap<Long, Long>()

    private var conn: java.sql.Connection? = null


    private fun executeQuery(sql: String): DatabaseResult {
        val c = conn ?: throw IllegalStateException("Database connection not set")
        return JdbcDatabase.executeQuery(c, sql)
    }

    private fun executeQueryParameterized(sql: String, params: List<Any?>): DatabaseResult {
        val c = conn ?: throw IllegalStateException("Database connection not set")
        return JdbcDatabase.executeQueryParameterized(c, sql, params)
    }

    /**
     * Generate abbreviation for a book name
     * Takes first 3 letters or first letter of each word for compound names
     */
    private fun generateAbbreviation(bookName: String): String {
        if (bookName.isBlank()) return ""

        val words = bookName.trim().split(Regex("\\s+"))
        return when {
            // Single word: take first 3-4 characters
            words.size == 1 -> {
                val word = words[0]
                when {
                    word.length <= 3 -> word
                    word.length == 4 -> word.take(4)
                    else -> word.take(3)
                }
            }
            // Multiple words: take first letter of each word (up to 4 letters)
            else -> {
                words.take(4).map { it.first().uppercase() }.joinToString("")
            }
        }
    }

    /**
     * Extract Bible version abbreviation from title or filename
     * Examples: "Russian Synodal Translation" -> "RST"
     *           "King James Version" -> "KJV"
     *           "ru_RST77.spb" -> "RST77"
     */
    private fun extractBibleAbbreviation(title: String?, filename: String): String {
        // First try to extract from title if available
        if (!title.isNullOrBlank()) {
            // Look for common patterns: "Version", "Translation", etc.
            val words = title.trim().split(Regex("\\s+"))

            // If title is short (like "RSV" or "KJV"), use it as-is
            if (words.size == 1 && words[0].length <= 5) {
                return words[0]
            }

            // Generate abbreviation from title words
            return words.take(4).map { it.first().uppercase() }.joinToString("")
        }

        // Fallback to filename without extension
        return filename.substringBeforeLast(".").substringAfterLast("/").substringAfterLast("\\")
    }

    /**
     * Fast path: reads ONLY the header section of an SPB file to populate book names.
     * Stops as soon as the separator line or first verse is encountered.
     * Call this first to show the book list immediately, then call loadFromSpb() for full data.
     */
    fun loadBooksOnly(resourcePath: String) {
        books.clear()
        try {
            val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            val reader = if (inputStream != null) {
                inputStream.bufferedReader(StandardCharsets.UTF_8)
            } else {
                val path = Paths.get(resourcePath)
                if (!Files.exists(path)) return
                Files.newBufferedReader(path, StandardCharsets.UTF_8)
            }
            val bookHeaderRegex = Regex("^(\\d+)\\s+(.+?)\\s+(\\d+)$")
            val headerOrder = mutableListOf<Int>()
            val parsedBookNames = mutableMapOf<Int, String>()
            val parsedChapterCounts = mutableMapOf<Int, Int>()
            reader.use { r ->
                for (rawLine in r.lineSequence()) {
                    val line = rawLine.trimEnd('\r', '\n')
                    if (line.startsWith("##")) continue
                    if (line.startsWith("-----") || line.startsWith("B")) break
                    if (line.isNotEmpty()) {
                        val m = bookHeaderRegex.matchEntire(line)
                        if (m != null) {
                            val bookId = m.groupValues[1].toInt()
                            headerOrder.add(bookId)
                            parsedBookNames[bookId] = m.groupValues[2].trim()
                            parsedChapterCounts[bookId] = m.groupValues[3].toInt()
                        }
                    }
                }
            }
            if (headerOrder.isNotEmpty()) {
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
        } catch (_: Exception) {}
    }

    // New: load from a BibleQuote .spb plain text module
    // resourcePath: either a classpath resource name (e.g. "ru_RST77.spb") or an absolute file path
    fun loadFromSpb(resourcePath: String, bookNames: List<String> = emptyList()) {
        operatorBible.clear()
        books.clear()


        try {
            val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            val reader = if (inputStream != null) {
                inputStream.bufferedReader(StandardCharsets.UTF_8)
            } else {
                val path = Paths.get(resourcePath)
                if (Files.exists(path)) {
                    Files.newBufferedReader(path, StandardCharsets.UTF_8)
                } else {
                    val msg = "loadFromSpb: resource not found on classpath or filesystem: $resourcePath"
                    throw IllegalArgumentException(msg)
                }
            }

            val codeRegex = Regex("^B(\\d{3})C(\\d{3})V(\\d{3})$")
            val verseLineRegex = Regex("^(B(\\d{3})C(\\d{3})V(\\d{3}))\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(.*)")
            val bookHeaderRegex = Regex("^(\\d+)\\s+(.+?)\\s+(\\d+)$")
            val bookChapterMap = mutableMapOf<Int, MutableSet<Int>>()
            val headerOrder = mutableListOf<Int>()
            val parsedBookNames = mutableMapOf<Int, String>()
            var bibleTitle: String? = null

            var currentCode: String? = null
            val sb = StringBuilder()
            var headerParsed = false

            reader.use { r ->
                r.forEachLine { rawLine ->
                    val line = rawLine.trimEnd('\r', '\n')

                    // Extract Bible title from ##Title: line
                    if (line.startsWith("##Title:")) {
                        bibleTitle = line.substring(8).trim()
                        return@forEachLine
                    }

                    // Skip other metadata lines
                    if (line.startsWith("##")) {
                        return@forEachLine
                    }

                    // Parse book header lines (before verse data starts)
                    if (!headerParsed && line.isNotEmpty()) {
                        val headerMatch = bookHeaderRegex.matchEntire(line)
                        if (headerMatch != null) {
                            val bookId = headerMatch.groupValues[1].toInt()
                            val bookName = headerMatch.groupValues[2].trim()
                            val chapterCount = headerMatch.groupValues[3].toInt()
                            headerOrder.add(bookId)
                            parsedBookNames[bookId] = bookName
                            return@forEachLine
                        }

                        // Check if we've reached the separator line (-----) or first verse
                        if (line.startsWith("-----") || line.startsWith("B")) {
                            headerParsed = true
                            if (line.startsWith("-----")) {
                                return@forEachLine
                            }
                            // Continue processing this line as a verse if it starts with B
                        }
                    }

                    // Skip separator line
                    if (line.startsWith("-----")) {
                        headerParsed = true
                        return@forEachLine
                    }

                    // Process verse data (same as before)
                    val verseMatch = verseLineRegex.matchEntire(line)
                    if (verseMatch != null) {
                        val code = verseMatch.groupValues[1]
                        // Code numbers from BXXXCXXXVXXX (internal/Hebrew numbering)
                        val codeBook = verseMatch.groupValues[2].toInt()
                        val codeChapter = verseMatch.groupValues[3].toInt()
                        // Display reference numbers (native numbering, e.g. LXX for Russian)
                        val b = verseMatch.groupValues[5].toInt()
                        val ch = verseMatch.groupValues[6].toInt()
                        val vnum = verseMatch.groupValues[7].toInt()
                        val text = verseMatch.groupValues[8].trim()

                        operatorBible.add(
                            BibleVerse(
                                verseId = code,
                                book = b,
                                chapter = ch,
                                verseNumber = vnum,
                                verseText = text
                            )
                        )
                        bookChapterMap.getOrPut(b) { mutableSetOf() }.add(ch)
                        // Map code reference to display reference for cross-Bible lookups
                        codeToDisplayMap[chapterKey(codeBook, codeChapter)] = chapterKey(b, ch)
                        currentCode = null
                        sb.setLength(0)
                        return@forEachLine
                    }

                    // Fallback for older SPB format
                    val m = codeRegex.matchEntire(line)
                    if (m != null) {
                        val code = currentCode
                        if (code != null) {
                            val prev = codeRegex.matchEntire(code) ?: error("Invalid verse code: $code")
                            val bPrev = prev.groupValues[1].toInt()
                            val chPrev = prev.groupValues[2].toInt()
                            val vnumPrev = prev.groupValues[3].toInt()
                            val textPrev = sb.toString().trim()
                            operatorBible.add(
                                BibleVerse(
                                    verseId = code,
                                    book = bPrev,
                                    chapter = chPrev,
                                    verseNumber = vnumPrev,
                                    verseText = textPrev
                                )
                            )
                            bookChapterMap.getOrPut(bPrev) { mutableSetOf() }.add(chPrev)
                        }
                        currentCode = line
                        sb.setLength(0)
                    } else if (currentCode != null) {
                        if (sb.isNotEmpty()) sb.append("\n")
                        sb.append(line)
                    }
                }

                // Flush last verse if using multiline format
                val lastCode = currentCode
                if (lastCode != null) {
                    val prev = codeRegex.matchEntire(lastCode) ?: error("Invalid verse code: $lastCode")
                    val b = prev.groupValues[1].toInt()
                    val ch = prev.groupValues[2].toInt()
                    val vnum = prev.groupValues[3].toInt()
                    val text = sb.toString().trim()
                    operatorBible.add(
                        BibleVerse(
                            verseId = lastCode,
                            book = b,
                            chapter = ch,
                            verseNumber = vnum,
                            verseText = text
                        )
                    )
                    bookChapterMap.getOrPut(b) { mutableSetOf() }.add(ch)
                }
            }

            // Build book list preserving header order from the SPB file
            val maxBook = if (bookChapterMap.isEmpty()) 0 else bookChapterMap.keys.maxOrNull() ?: 0
            val headerBookIds = headerOrder.toSet()
            // First: books in header order
            for (b in headerOrder) {
                val chapterCount = bookChapterMap[b]?.maxOrNull() ?: 0
                val name = when {
                    parsedBookNames.containsKey(b) -> parsedBookNames.getValue(b)
                    bookNames.size >= b -> bookNames[b - 1]
                    else -> "Book $b"
                }
                books.add(BibleBook(
                    book = name,
                    bookId = b.toString(),
                    chapterCount = chapterCount,
                    abbreviation = generateAbbreviation(name)
                ))
            }
            // Then: any books found in verse data but missing from header
            for (b in 1..maxBook) {
                if (b in headerBookIds) continue
                if (!bookChapterMap.containsKey(b)) continue
                val chapterCount = bookChapterMap[b]?.maxOrNull() ?: 0
                val name = when {
                    bookNames.size >= b -> bookNames[b - 1]
                    else -> "Book $b"
                }
                books.add(BibleBook(
                    book = name,
                    bookId = b.toString(),
                    chapterCount = chapterCount,
                    abbreviation = generateAbbreviation(name)
                ))
            }

            // Store full title and abbreviation
            this.bibleTitle = bibleTitle ?: resourcePath.substringBeforeLast(".").substringAfterLast("/").substringAfterLast("\\")
            bibleAbbreviation = extractBibleAbbreviation(bibleTitle, resourcePath)

            // Build chapter index for O(1) lookup in getChapter()
            buildChapterIndex()

        } catch (e: Exception) {
            throw e
        }
    }

    /** Encodes (bookId, chapterNum) as a single Long key for the HashMap. */
    private fun chapterKey(book: Int, chapter: Int): Long = book.toLong().shl(20) or chapter.toLong()

    private fun buildChapterIndex() {
        chapterIndex.clear()
        // Group verses by (book, chapter) preserving their parsed order
        val grouped = LinkedHashMap<Long, MutableList<BibleVerse>>()
        for (verse in operatorBible) {
            grouped.getOrPut(chapterKey(verse.book, verse.chapter)) { mutableListOf() }.add(verse)
        }
        chapterIndex.putAll(grouped)
    }

    private fun retrieveBooks() {
        books.clear()
        val result = executeQueryParameterized("SELECT book_name, id, chapter_count FROM BibleBooks WHERE bible_id = ?", listOf(bibleId))

        result.forEach { row ->
            val bookName = row.getString(0).trim()
            val book = BibleBook(
                book = bookName,
                bookId = row.getString(1),
                chapterCount = row.getInt(2),
                abbreviation = generateAbbreviation(bookName)
            )
            books.add(book)
        }
    }

    fun getBooks(): List<String> {
        // If we already have books parsed from an SPB load, return them.
        if (books.isNotEmpty()) return books.map { it.book }

        // If no books parsed and a JDBC connection is available, load from DB.
        if (conn != null) {
            retrieveBooks()
            return books.map { it.book }
        }

        // No data available: return empty list (caller should handle it)
        return emptyList()
    }

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
    ): List<BibleSearch> {
        val returnResults = mutableListOf<BibleSearch>()
        var sw = searchExp.pattern
        sw = sw.replace("\\b(", "").replace(")\\b", "")

        operatorBible
            .filter { bv ->
                val matchesText = searchExp.containsMatchIn(bv.verseText)
                val matchesBook = book == null || bv.book == book
                val matchesChapter = chapter == null || bv.chapter == chapter
                matchesText && matchesBook && matchesChapter
            }
            .forEach { bv ->
                if (allWords) {
                    val words = sw.split("|")
                    val hasAll = words.all { word ->
                        Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(bv.verseText)
                    }
                    if (hasAll) {
                        addSearchResult(bv, returnResults)
                    }
                } else {
                    addSearchResult(bv, returnResults)
                }
            }

        return returnResults
    }

    private fun addSearchResult(bv: BibleVerse, bsl: MutableList<BibleSearch>) {
        val bookName = books.firstOrNull { it.bookId == bv.book.toString() }?.book ?: ""
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
     * Returns the SPB book ID for the given 0-based display index.
     */
    fun getBookId(displayIndex: Int): Int =
        books.getOrNull(displayIndex)?.bookId?.toIntOrNull() ?: (displayIndex + 1)

    /**
     * Extracts the internal code book/chapter/verse from a verseId like "B019C023V001".
     * Returns (book, chapter, verse) or null if the format doesn't match.
     */
    fun parseVerseCode(verseId: String): Triple<Int, Int, Int>? {
        val m = Regex("B(\\d{3})C(\\d{3})V(\\d{3})").matchEntire(verseId) ?: return null
        return Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
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
        val displayKey = codeToDisplayMap[chapterKey(codeBook, codeChapter)]
        val displayBook: Int
        val displayChapter: Int
        if (displayKey != null) {
            displayBook = (displayKey shr 20).toInt()
            displayChapter = (displayKey and 0xFFFFF).toInt()
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
}
