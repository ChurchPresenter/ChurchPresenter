package org.churchpresenter.dictionary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private const val VERSE_KEY_SCALE = 1000

/** The bundled file each testament's index is built from. Public so a fixture can stand in for it. */
const val GREEK_INTERLINEAR_FILE = "interlinear_g.json"
const val HEBREW_INTERLINEAR_FILE = "interlinear_h.json"

/**
 * One testament's lookup tables, filled once from its bundled file.
 *
 * Greek and Hebrew are loaded independently — the Hebrew file is twice the size of the Greek one
 * and a Greek-only lookup must not pay for it — so each keeps its own tables and its own flags.
 */
private class TestamentIndex {
    /** Strong's number → the verses it appears in. */
    val versesByNumber = HashMap<String, MutableList<InterlinearVerse>>()

    /** book → the Strong's numbers occurring in it. */
    val numbersByBook = HashMap<Int, MutableSet<String>>()

    /** [chapterKey] → the Strong's numbers occurring in it. */
    val numbersByChapter = HashMap<Int, MutableSet<String>>()

    /** [chapterKey] → the verse numbers present. */
    val versesByChapter = HashMap<Int, MutableSet<Int>>()

    /** packed `BBBCCCVVV` reference → the Strong's numbers occurring in it. */
    val numbersByVerse = HashMap<String, MutableSet<String>>()

    @Volatile var loaded = false
    @Volatile var loading = false

    fun add(verse: InterlinearVerse) {
        val chapter = chapterKey(verse.bookId, verse.chapter)
        versesByChapter.getOrPut(chapter) { mutableSetOf() }.add(verse.verseNumber)
        for (word in verse.words) {
            versesByNumber.getOrPut(word.strongsNumber) { mutableListOf() }.add(verse)
            numbersByBook.getOrPut(verse.bookId) { mutableSetOf() }.add(word.strongsNumber)
            numbersByChapter.getOrPut(chapter) { mutableSetOf() }.add(word.strongsNumber)
            numbersByVerse.getOrPut(verse.ref) { mutableSetOf() }.add(word.strongsNumber)
        }
    }
}

/** Book and chapter packed into one int, so the chapter tables key on a single value. */
private fun chapterKey(bookId: Int, chapter: Int) = bookId * VERSE_KEY_SCALE + chapter

/**
 * The index behind the dictionary tab's "where does this word appear?" panel: every occurrence of
 * every Strong's number in the original-language text, keyed by number, book, chapter and verse.
 *
 * The two bundled files are 4 MB of Greek and 8 MB of Hebrew, and most sessions never open the
 * panel that needs them, so nothing is read until something asks and each testament is read at
 * most once.
 *
 * @param loader reads one bundled file by name. Defaulted to the packaged resource and replaced in
 * tests with a lambda over a handful of verses — the real files are far too large for a test, and
 * an injected function leaks no fixture into whatever runs next in the same JVM.
 */
class InterlinearRepository(
    private val loader: (String) -> ByteArray = ::readBundledDictionaryFile,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val greek = TestamentIndex()
    private val hebrew = TestamentIndex()

    suspend fun ensureGreekLoaded() = ensureLoaded(greek, GREEK_INTERLINEAR_FILE)

    suspend fun ensureHebrewLoaded() = ensureLoaded(hebrew, HEBREW_INTERLINEAR_FILE)

    private suspend fun ensureLoaded(index: TestamentIndex, file: String) {
        if (index.loaded || index.loading) return
        index.loading = true
        withContext(Dispatchers.IO) {
            val bytes = loader(file)
            val verses = json.decodeFromString(
                ListSerializer(InterlinearVerse.serializer()), bytes.decodeToString(),
            )
            for (verse in verses) index.add(verse)
            index.loaded = true
        }
    }

    /**
     * The verses for one Strong's number, **as a copy**.
     *
     * The index holds `MutableList`s that loading appends to, so returning one directly handed the
     * caller a live view of a list this class goes on mutating. `DictionaryViewModel` keeps it in
     * `interlinearVerses`, and `cardAvailableBooks` iterates it during composition — so a load
     * finishing while the Dictionary tab recomposed threw `ConcurrentModificationException` out of
     * the composition and took the tab down. Seen on CI 2026-08-07.
     *
     * A copy is cheap next to the load that produced it, and it is what every caller already
     * assumed it was getting from a `List` return type.
     */
    fun getVersesForEntry(number: String): List<InterlinearVerse> {
        val index = if (number.startsWith("G")) greek else hebrew
        return index.versesByNumber[number]?.toList() ?: emptyList()
    }

    fun getBooksWithGreekData(): List<Int> = greek.numbersByBook.keys.sorted()

    fun getBooksWithHebrewData(): List<Int> = hebrew.numbersByBook.keys.sorted()

    fun getChaptersForBook(bookId: Int): List<Int> {
        val index = if (greek.numbersByBook.containsKey(bookId)) greek else hebrew
        return index.numbersByChapter.keys
            .filter { it / VERSE_KEY_SCALE == bookId }
            .map { it % VERSE_KEY_SCALE }
            .sorted()
    }

    fun getVersesInChapter(bookId: Int, chapter: Int): List<Int> {
        val key = chapterKey(bookId, chapter)
        return (greek.versesByChapter[key] ?: hebrew.versesByChapter[key])?.sorted() ?: emptyList()
    }

    fun getStrongsForBookChapter(bookId: Int, chapter: Int?, verse: Int? = null): Set<String> {
        return when {
            chapter != null && verse != null -> {
                val ref = "%03d%03d%03d".format(bookId, chapter, verse)
                greek.numbersByVerse[ref] ?: hebrew.numbersByVerse[ref] ?: emptySet()
            }
            chapter != null -> {
                val key = chapterKey(bookId, chapter)
                greek.numbersByChapter[key] ?: hebrew.numbersByChapter[key] ?: emptySet()
            }
            else -> greek.numbersByBook[bookId] ?: hebrew.numbersByBook[bookId] ?: emptySet()
        }
    }
}
