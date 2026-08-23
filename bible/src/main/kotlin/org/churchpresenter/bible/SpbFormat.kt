package org.churchpresenter.bible

import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

/**
 * The `.spb` format itself, as opposed to any Bible loaded from it.
 *
 * Everything here is a pure function of its arguments -- opening a reader, recognising a header
 * line, shortening a book name, keying a chapter. These were private members of [Bible] and are
 * out here because none of them touches a loaded module: they describe the file, not the Bible.
 */

/**
 * Folder depth searched for `.spb` files -- deep enough for language/publisher/edition nesting,
 * and bounded so a symlink cycle or a stray deep tree cannot hang the caller that is scanning.
 */
const val MAX_BIBLE_SCAN_DEPTH = 6

/** One word shortened: kept whole at three characters or fewer, else its first three or four. */
internal fun shortenWord(word: String): String = when {
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
internal fun extractBibleAbbreviation(title: String?, filename: String): String {
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
internal fun openHeaderReader(resourcePath: String): java.io.BufferedReader {
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


internal fun collectBookHeader(
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


internal fun openSpbReader(resourcePath: String): java.io.BufferedReader {
    val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
    if (inputStream != null) return inputStream.bufferedReader(StandardCharsets.UTF_8)
    val path = Paths.get(resourcePath)
    require(Files.exists(path)) {
        "loadFromSpb: resource not found on classpath or filesystem: $resourcePath"
    }
    return Files.newBufferedReader(path, StandardCharsets.UTF_8)
}


/** Encodes (bookId, chapterNum) as a single Long key for the HashMap. */
internal fun chapterKey(
    book: Int,
    chapter: Int
): Long = book.toLong().shl(CHAPTER_KEY_BOOK_SHIFT) or chapter.toLong()
