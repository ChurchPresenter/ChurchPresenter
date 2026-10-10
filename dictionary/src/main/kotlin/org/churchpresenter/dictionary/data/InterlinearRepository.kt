package org.churchpresenter.dictionary.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val VERSE_KEY_SCALE = 1000

/**
 * The interlinear Greek and Hebrew, indexed by Strong's number, book, chapter and verse. Each side is
 * read from [files] once, the first time it is asked for.
 */
class InterlinearRepository(private val files: DictionaryFiles = DictionaryFiles.Bundled) {
    private val json = Json { ignoreUnknownKeys = true }

    private val greekIndex  = HashMap<String, MutableList<InterlinearVerse>>()
    private val hebrewIndex = HashMap<String, MutableList<InterlinearVerse>>()

    // book → Strong's numbers
    private val greekBookIndex   = HashMap<Int, MutableSet<String>>()
    private val hebrewBookIndex  = HashMap<Int, MutableSet<String>>()

    // (bookId*1000+chapter) → Strong's numbers
    private val greekChapterIndex  = HashMap<Int, MutableSet<String>>()
    private val hebrewChapterIndex = HashMap<Int, MutableSet<String>>()

    // (bookId*1000+chapter) → verse numbers present
    private val greekChapterVerses  = HashMap<Int, MutableSet<Int>>()
    private val hebrewChapterVerses = HashMap<Int, MutableSet<Int>>()

    // ref string (BBBCCCVVV) → Strong's numbers
    private val greekVerseIndex  = HashMap<String, MutableSet<String>>()
    private val hebrewVerseIndex = HashMap<String, MutableSet<String>>()

    @Volatile private var greekLoaded  = false

    @Volatile private var hebrewLoaded = false

    @Volatile private var greekLoading  = false

    @Volatile private var hebrewLoading = false

    suspend fun ensureGreekLoaded() {
        if (greekLoaded || greekLoading) return
        greekLoading = true
        withContext(Dispatchers.IO) {
            val bytes = files.read(DictionaryFiles.INTERLINEAR_GREEK)
            val verses = json.decodeFromString<List<InterlinearVerse>>(bytes.decodeToString())
            for (verse in verses) {
                val chapterKey = verse.bookId * 1000 + verse.chapter
                greekChapterVerses.getOrPut(chapterKey) { mutableSetOf() }.add(verse.verseNumber)
                for (word in verse.words) {
                    greekIndex.getOrPut(word.strongsNumber) { mutableListOf() }.add(verse)
                    greekBookIndex.getOrPut(verse.bookId) { mutableSetOf() }.add(word.strongsNumber)
                    greekChapterIndex.getOrPut(chapterKey) { mutableSetOf() }.add(word.strongsNumber)
                    greekVerseIndex.getOrPut(verse.ref) { mutableSetOf() }.add(word.strongsNumber)
                }
            }
            greekLoaded = true
        }
    }

    suspend fun ensureHebrewLoaded() {
        if (hebrewLoaded || hebrewLoading) return
        hebrewLoading = true
        withContext(Dispatchers.IO) {
            val bytes = files.read(DictionaryFiles.INTERLINEAR_HEBREW)
            val verses = json.decodeFromString<List<InterlinearVerse>>(bytes.decodeToString())
            for (verse in verses) {
                val chapterKey = verse.bookId * 1000 + verse.chapter
                hebrewChapterVerses.getOrPut(chapterKey) { mutableSetOf() }.add(verse.verseNumber)
                for (word in verse.words) {
                    hebrewIndex.getOrPut(word.strongsNumber) { mutableListOf() }.add(verse)
                    hebrewBookIndex.getOrPut(verse.bookId) { mutableSetOf() }.add(word.strongsNumber)
                    hebrewChapterIndex.getOrPut(chapterKey) { mutableSetOf() }.add(word.strongsNumber)
                    hebrewVerseIndex.getOrPut(verse.ref) { mutableSetOf() }.add(word.strongsNumber)
                }
            }
            hebrewLoaded = true
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
        val index = if (number.startsWith("G")) greekIndex else hebrewIndex
        return index[number]?.toList() ?: emptyList()
    }

    fun getBooksWithGreekData(): List<Int> = greekBookIndex.keys.sorted()
    fun getBooksWithHebrewData(): List<Int> = hebrewBookIndex.keys.sorted()

    fun getChaptersForBook(bookId: Int): List<Int> {
        val index = if (greekBookIndex.containsKey(bookId)) greekChapterIndex else hebrewChapterIndex
        return index.keys
            .filter { it / VERSE_KEY_SCALE == bookId }
            .map { it % VERSE_KEY_SCALE }
            .sorted()
    }

    fun getVersesInChapter(bookId: Int, chapter: Int): List<Int> {
        val key = bookId * 1000 + chapter
        return (greekChapterVerses[key] ?: hebrewChapterVerses[key])?.sorted() ?: emptyList()
    }

    fun getStrongsForBookChapter(bookId: Int, chapter: Int?, verse: Int? = null): Set<String> {
        return when {
            chapter != null && verse != null -> {
                val ref = "%03d%03d%03d".format(bookId, chapter, verse)
                greekVerseIndex[ref] ?: hebrewVerseIndex[ref] ?: emptySet()
            }
            chapter != null -> {
                val key = bookId * 1000 + chapter
                greekChapterIndex[key] ?: hebrewChapterIndex[key] ?: emptySet()
            }
            else -> greekBookIndex[bookId] ?: hebrewBookIndex[bookId] ?: emptySet()
        }
    }
}
