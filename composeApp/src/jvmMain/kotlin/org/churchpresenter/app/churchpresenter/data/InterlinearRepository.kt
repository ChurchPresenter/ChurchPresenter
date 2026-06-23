package org.churchpresenter.app.churchpresenter.data

import churchpresenter.composeapp.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

class InterlinearRepository {
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

    @OptIn(ExperimentalResourceApi::class)
    suspend fun ensureGreekLoaded() {
        if (greekLoaded || greekLoading) return
        greekLoading = true
        withContext(Dispatchers.IO) {
            val bytes = Res.readBytes("files/dictionary/interlinear_g.json")
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

    @OptIn(ExperimentalResourceApi::class)
    suspend fun ensureHebrewLoaded() {
        if (hebrewLoaded || hebrewLoading) return
        hebrewLoading = true
        withContext(Dispatchers.IO) {
            val bytes = Res.readBytes("files/dictionary/interlinear_h.json")
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

    fun getVersesForEntry(number: String): List<InterlinearVerse> {
        val index = if (number.startsWith("G")) greekIndex else hebrewIndex
        return index[number] ?: emptyList()
    }

    fun getBooksWithGreekData(): List<Int>  = greekBookIndex.keys.sorted()
    fun getBooksWithHebrewData(): List<Int> = hebrewBookIndex.keys.sorted()

    fun getChaptersForBook(bookId: Int): List<Int> {
        val index = if (greekBookIndex.containsKey(bookId)) greekChapterIndex else hebrewChapterIndex
        return index.keys
            .filter { it / 1000 == bookId }
            .map { it % 1000 }
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
