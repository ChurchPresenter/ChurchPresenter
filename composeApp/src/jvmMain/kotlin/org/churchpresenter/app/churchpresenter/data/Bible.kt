package org.churchpresenter.app.churchpresenter.data

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import androidx.compose.runtime.mutableStateListOf

// Note: This conversion assumes you're using a Kotlin database library
// You may need to adapt the SQL queries based on your specific database framework
// (e.g., Room, Exposed, JDBC, etc.)

class Bible {
    private var bibleId: String = ""
    private val books = mutableStateListOf<BibleBook>()
    private val operatorBible = mutableStateListOf<BibleVerse>()
    val previewIdList = mutableListOf<String>()
    val verseList = mutableListOf<String>()

    private var conn: java.sql.Connection? = null


    private fun executeQuery(sql: String): DatabaseResult {
        val c = conn ?: throw IllegalStateException("Database connection not set")
        return JdbcDatabase.executeQuery(c, sql)
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
            val verseLineRegex = Regex("^(B(\\d{3})C(\\d{3})V(\\d{3}))\\s+\\d+\\s+\\d+\\s+\\d+\\s+(.*)")
            val bookHeaderRegex = Regex("^(\\d+)\\s+(.+?)\\s+(\\d+)$")
            val bookChapterMap = mutableMapOf<Int, MutableSet<Int>>()
            val parsedBookNames = mutableMapOf<Int, String>()

            var currentCode: String? = null
            val sb = StringBuilder()
            var headerParsed = false

            reader.use { r ->
                r.forEachLine { rawLine ->
                    val line = rawLine.trimEnd('\r', '\n')

                    // Skip metadata lines
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
                        val b = verseMatch.groupValues[2].toInt()
                        val ch = verseMatch.groupValues[3].toInt()
                        val vnum = verseMatch.groupValues[4].toInt()
                        val text = verseMatch.groupValues[5].trim()

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
                        currentCode = null
                        sb.setLength(0)
                        return@forEachLine
                    }

                    // Fallback for older SPB format
                    val m = codeRegex.matchEntire(line)
                    if (m != null) {
                        if (currentCode != null) {
                            val prev = codeRegex.matchEntire(currentCode!!)!!
                            val bPrev = prev.groupValues[1].toInt()
                            val chPrev = prev.groupValues[2].toInt()
                            val vnumPrev = prev.groupValues[3].toInt()
                            val textPrev = sb.toString().trim()
                            operatorBible.add(
                                BibleVerse(
                                    verseId = currentCode!!,
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
                if (currentCode != null) {
                    val prev = codeRegex.matchEntire(currentCode!!)!!
                    val b = prev.groupValues[1].toInt()
                    val ch = prev.groupValues[2].toInt()
                    val vnum = prev.groupValues[3].toInt()
                    val text = sb.toString().trim()
                    operatorBible.add(
                        BibleVerse(
                            verseId = currentCode!!,
                            book = b,
                            chapter = ch,
                            verseNumber = vnum,
                            verseText = text
                        )
                    )
                    bookChapterMap.getOrPut(b) { mutableSetOf() }.add(ch)
                }
            }

            // Build book list using parsed names from header, fallback to provided bookNames, then generic names
            val maxBook = if (bookChapterMap.isEmpty()) 0 else bookChapterMap.keys.maxOrNull() ?: 0
            for (b in 1..maxBook) {
                val chapterCount = bookChapterMap[b]?.maxOrNull() ?: 0
                val name = when {
                    parsedBookNames.containsKey(b) -> parsedBookNames[b]!!
                    bookNames.size >= b -> bookNames[b - 1]
                    else -> "Book $b"
                }
                books.add(BibleBook(book = name, bookId = b.toString(), chapterCount = chapterCount))
            }

        } catch (e: Exception) {
            throw e
        }
    }

    private fun retrieveBooks() {
        books.clear()
        val result = executeQuery("SELECT book_name, id, chapter_count FROM BibleBooks WHERE bible_id = $bibleId")

        result.forEach { row ->
            val book = BibleBook(
                book = row.getString(0).trim(),
                bookId = row.getString(1),
                chapterCount = row.getInt(2)
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

    fun getChapter(book: Int, chapter: Int): List<String> {
        var verseText: String
        var id: String
        var verseOld = 0

        previewIdList.clear()
        verseList.clear()

        operatorBible.filter { it.book == book && it.chapter == chapter }
            .forEach { bv ->
                val verse = bv.verseNumber

                if (verse == verseOld) {
                    verseText = "${verseList.last().substringAfter(". ")} ${bv.verseText}".trim()
                    id = "${previewIdList.last()},${bv.verseId}"
                    verseList.removeLast()
                    previewIdList.removeLast()
                } else {
                    verseText = bv.verseText
                    id = bv.verseId
                }

                verseList.add("$verse. $verseText")
                previewIdList.add(id)
                verseOld = verse
            }

        return verseList
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
                        Regex("\\b$word\\b", RegexOption.IGNORE_CASE).containsMatchIn(bv.verseText)
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
        val results = BibleSearch()

        books.firstOrNull { it.bookId == bv.book.toString() }?.let { bk ->
            results.book = bk.book
        }

        results.chapter = bv.chapter.toString()
        results.verse = bv.verseNumber.toString()
        results.verseText = "${results.book} ${results.chapter}:${results.verse} ${bv.verseText}"

        bsl.add(results)
    }

    fun getBookCount(): Int {
        return books.size
    }

    fun getChapterCount(bookIndex: Int): Int {
        // bookIndex is zero-based in the UI; our books list is 0-based
        val b = books.getOrNull(bookIndex) ?: return 0
        return b.chapterCount
    }

    // Get verse details for presenter screen
    fun getVerseDetails(book: Int, chapter: Int, verseNumber: Int): Triple<String, String, String>? {
        // Find the verse
        val bibleVerse = operatorBible.firstOrNull {
            it.book == book && it.chapter == chapter && it.verseNumber == verseNumber
        } ?: return null

        // Get book name
        val bookName = books.firstOrNull { it.bookId == book.toString() }?.book ?: "Book $book"

        return Triple(bookName, bibleVerse.verseText, bibleVerse.verseId)
    }

    // Diagnostic helper: number of parsed verses from SPB
    fun getVerseCount(): Int {
        return operatorBible.size
    }
}
