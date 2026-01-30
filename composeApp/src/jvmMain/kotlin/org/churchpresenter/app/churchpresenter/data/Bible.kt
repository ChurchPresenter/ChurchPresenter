package org.churchpresenter.app.churchpresenter.data

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import java.util.logging.Level
import java.util.logging.Logger
import androidx.compose.runtime.mutableStateListOf

private val bibleLogger: Logger = Logger.getLogger("org.churchpresenter.Bible")

// Note: This conversion assumes you're using a Kotlin database library
// You may need to adapt the SQL queries based on your specific database framework
// (e.g., Room, Exposed, JDBC, etc.)


data class BibleBook(
    var book: String = "",
    var bookId: String = "",
    var chapterCount: Int = 0
)

data class BibleVerse(
    var verseId: String = "",
    var book: Int = 0,
    var chapter: Int = 0,
    var verseNumber: Int = 0,
    var verseText: String = ""
)

data class BibleSearch(
    var book: String = "",
    var chapter: String = "",
    var verse: String = "",
    var verseText: String = ""
)

data class Verse(
    var primaryText: String = "",
    var primaryCaption: String = "",
    var secondaryText: String = "",
    var secondaryCaption: String = "",
    var trinaryText: String = "",
    var trinaryCaption: String = ""
)

data class BibleSettings(
    var useAbbriviation: Boolean = false
)

data class BibleVersionSettings(
    var primaryBible: String = "",
    var secondaryBible: String = "none",
    var trinaryBible: String = "none"
)

class Bible {
    private var bibleId: String = ""
    private val books = mutableStateListOf<BibleBook>()
    private val operatorBible = mutableStateListOf<BibleVerse>()
    val previewIdList = mutableListOf<String>()
    val verseList = mutableListOf<String>()
    val currentIdList = mutableListOf<String>()

    private var conn: java.sql.Connection? = null

    fun setConnection(connection: java.sql.Connection) {
        conn = connection
    }

    private fun executeQuery(sql: String): DatabaseResult {
        val c = conn ?: throw IllegalStateException("Database connection not set")
        return JdbcDatabase.executeQuery(c, sql)
    }

    // New: load from a BibleQuote .spb plain text module
    // resourcePath: either a classpath resource name (e.g. "ru_RST77.spb") or an absolute file path
    fun loadFromSpb(resourcePath: String, bookNames: List<String> = emptyList()) {
        operatorBible.clear()
        books.clear()

        bibleLogger.info("loadFromSpb: start loading resourcePath='$resourcePath'")

        try {
            val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            val reader = if (inputStream != null) {
                bibleLogger.info("loadFromSpb: loaded resource from classpath: $resourcePath")
                inputStream.bufferedReader(StandardCharsets.UTF_8)
            } else {
                val path = Paths.get(resourcePath)
                if (Files.exists(path)) {
                    bibleLogger.info("loadFromSpb: loaded resource from filesystem: ${path.toAbsolutePath()}")
                    Files.newBufferedReader(path, StandardCharsets.UTF_8)
                } else {
                    val msg = "loadFromSpb: resource not found on classpath or filesystem: $resourcePath"
                    bibleLogger.severe(msg)
                    throw IllegalArgumentException(msg)
                }
            }

            val codeRegex = Regex("^B(\\d{3})C(\\d{3})V(\\d{3})$")
            // Matches lines like: B001C001V001    1       1       1       Verse text...
            val verseLineRegex = Regex("^(B(\\d{3})C(\\d{3})V(\\d{3}))\\s+\\d+\\s+\\d+\\s+\\d+\\s+(.*)")
            val bookChapterMap = mutableMapOf<Int, MutableSet<Int>>()

            var currentCode: String? = null
            val sb = StringBuilder()

            reader.use { r ->
                r.forEachLine { rawLine ->
                    val line = rawLine.trimEnd('\r', '\n')

                    // Fast path: verse and text are on the same line (common in SPB)
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
                        // reset any multiline buffer state
                        currentCode = null
                        sb.setLength(0)
                        return@forEachLine
                    }

                    // Fallback: some modules split code line and text lines; handle older format
                    val m = codeRegex.matchEntire(line)
                    if (m != null) {
                        // flush previous verse (if any)
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
                    } else {
                        if (currentCode != null) {
                            if (sb.isNotEmpty()) sb.append("\n")
                            sb.append(line)
                        }
                    }
                }
                // flush last if multiline format was used
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

            // Build book list using provided bookNames if available, otherwise default names like "Book 1"
            val maxBook = if (bookChapterMap.isEmpty()) 0 else bookChapterMap.keys.maxOrNull() ?: 0
            for (b in 1..maxBook) {
                val chapterCount = bookChapterMap[b]?.maxOrNull() ?: 0
                val name = if (bookNames.size >= b) bookNames[b - 1] else "Book $b"
                books.add(BibleBook(book = name, bookId = b.toString(), chapterCount = chapterCount))
            }

            bibleLogger.info("loadFromSpb: parsed operatorBible.size=${operatorBible.size}, books.size=${books.size}")
        } catch (e: Exception) {
            bibleLogger.log(Level.SEVERE, "loadFromSpb failed for resourcePath=$resourcePath", e)
            throw e
        }
    }

    fun setBiblesId(id: String) {
        bibleId = id
        retrieveBooks()
    }

    fun getBibleName(): String {
        if (bibleId.isEmpty()) return ""

        val result = executeQuery("SELECT bible_name FROM BibleVersions WHERE id = $bibleId")
        return result.firstOrNull()?.getString(0)?.trim() ?: ""
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

    fun getBookName(id: Int): String {
        return books.firstOrNull { it.bookId.toInt() == id }?.book ?: ""
    }

    fun getVerseRef(vId: String): Triple<String, Int, Int> {
        var verseId = vId
        if (verseId.contains(",")) {
            verseId = verseId.split(",").first()
        }

        var bookResult = ""
        var chapterResult = 0
        var verseResult = 0

        operatorBible.firstOrNull { it.verseId == verseId }?.let { bv ->
            bookResult = bv.book.toString()
            chapterResult = bv.chapter
            verseResult = bv.verseNumber
        }

        books.firstOrNull { it.bookId == bookResult }?.let { bk ->
            bookResult = bk.book
        }

        return Triple(bookResult, chapterResult, verseResult)
    }

    fun getVerseNumberLast(vId: String): Int {
        var verseId = vId
        if (verseId.contains(",")) {
            verseId = verseId.split(",").last()
        }

        return operatorBible.firstOrNull { it.verseId == verseId }?.verseNumber ?: 0
    }

    fun getCurrentBookRow(book: String): Int {
        return books.indexOfFirst { it.book == book }
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

    fun getCurrentVerseAndCaption(
        currentRows: List<Int>,
        sets: BibleSettings,
        bv: BibleVersionSettings
    ): Verse {
        val verseId = currentRows.joinToString(",") { currentIdList[it] }
        val verse = Verse()

        // Get primary verse
        val (primaryText, primaryCaption) = getVerseAndCaption(
            verseId,
            bv.primaryBible,
            sets.useAbbriviation
        )
        verse.primaryText = primaryText
        verse.primaryCaption = primaryCaption

        // Get secondary verse
        if (bv.primaryBible != bv.secondaryBible && bv.secondaryBible != "none") {
            val (secondaryText, secondaryCaption) = getVerseAndCaption(
                verseId,
                bv.secondaryBible,
                sets.useAbbriviation
            )
            verse.secondaryText = secondaryText
            verse.secondaryCaption = secondaryCaption
        }

        // Get trinary verse
        if (bv.trinaryBible != bv.primaryBible &&
            bv.trinaryBible != bv.secondaryBible &&
            bv.trinaryBible != "none") {
            val (trinaryText, trinaryCaption) = getVerseAndCaption(
                verseId,
                bv.trinaryBible,
                sets.useAbbriviation
            )
            verse.trinaryText = trinaryText
            verse.trinaryCaption = trinaryCaption
        }

        return verse
    }

    private fun getVerseAndCaption(
        verId: String,
        bibId: String,
        useAbbr: Boolean
    ): Pair<String, String> {
        var verse = ""
        var caption = ""
        var verseShow = ""
        var verseN: String
        var verseNOld = ""
        var verseNFirst = ""
        var chapter = ""
        var book = ""

        if (verId.contains(",")) {
            val ids = verId.split(",")
            val modifiedVerId = verId.replace(",", "' OR verse_id = '")
            val result = executeQuery(
                "SELECT book,chapter,verse,verse_text FROM BibleVerse WHERE " +
                        "( verse_id = '$modifiedVerId' ) AND bible_id = $bibId"
            )

            result.forEach { row ->
                book = row.getString(0)
                chapter = row.getString(1)
                verseN = row.getString(2)
                verse = row.getString(3).trim()

                if (verseNFirst.isEmpty()) {
                    verseNFirst = verseN
                }

                if (verseN == verseNOld) {
                    if (verseN == verseNFirst) {
                        val endIndex = verseShow.indexOf(")") + 1
                        verseShow = verseShow.substring(endIndex)
                        caption = " $chapter:$verseN"
                    } else {
                        caption = " $chapter:$verseNFirst-$verseN"
                    }
                    verseShow += " $verse"
                } else {
                    caption = " $chapter:$verseNFirst-$verseN"
                    verse = " ($verseN) $verse"
                    verseShow += verse
                    if (!verseShow.startsWith(" (")) {
                        verseShow = " ($verseNFirst) $verseShow"
                    }
                }
                verseNOld = verseN
            }
            verse = verseShow.trim()
        } else {
            val result = executeQuery(
                "SELECT book,chapter,verse,verse_text FROM BibleVerse WHERE " +
                        "verse_id = '$verId' AND bible_id = $bibId"
            )

            result.firstOrNull()?.let { row ->
                verse = row.getString(3).trim()
                book = row.getString(0)
                caption = " ${row.getString(1)}:${row.getString(2)}"
            }
        }

        // Add book name to caption
        val bookResult = executeQuery(
            "SELECT book_name FROM BibleBooks WHERE id = $book AND bible_id = $bibId"
        )
        bookResult.firstOrNull()?.let { row ->
            caption = row.getString(0) + caption
        }

        // Add bible abbreviation if needed
        if (useAbbr) {
            val abbrResult = executeQuery(
                "SELECT abbreviation FROM BibleVersions WHERE id = $bibId"
            )
            abbrResult.firstOrNull()?.let { row ->
                val abbr = row.getString(0).trim()
                if (abbr.isNotEmpty()) {
                    caption = "$caption ($abbr)"
                }
            }
        }

        return Pair(verse.trim(), caption.trim())
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

    fun loadOperatorBible() {
        operatorBible.clear()
        val result = executeQuery(
            "SELECT verse_id, book, chapter, verse, verse_text FROM BibleVerse WHERE bible_id = '$bibleId'"
        )

        result.forEach { row ->
            val bv = BibleVerse(
                verseId = row.getString(0).trim(),
                book = row.getInt(1),
                chapter = row.getInt(2),
                verseNumber = row.getInt(3),
                verseText = row.getString(4).trim()
            )
            operatorBible.add(bv)
        }
    }

    fun getBookCount(): Int {
        return books.size
    }

    fun getChapterCount(bookIndex: Int): Int {
        // bookIndex is zero-based in the UI; our books list is 0-based
        val b = books.getOrNull(bookIndex) ?: return 0
        return b.chapterCount
    }

    // Diagnostic helper: number of parsed verses from SPB
    fun getVerseCount(): Int {
        return operatorBible.size
    }
}

// Placeholder interface for database results
// You'll need to implement this based on your database framework
interface DatabaseResult : Iterable<DatabaseRow> {
    fun firstOrNull(): DatabaseRow?
}

interface DatabaseRow {
    fun getString(index: Int): String
    fun getInt(index: Int): Int
}