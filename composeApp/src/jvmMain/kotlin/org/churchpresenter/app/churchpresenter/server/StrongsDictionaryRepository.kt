package org.churchpresenter.app.churchpresenter.server

import churchpresenter.composeapp.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.data.InterlinearRepository
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Wire model returned by the dictionary REST endpoints — the bundled [StrongsEntry]
 * enriched with the live occurrence count (from the interlinear index) and the
 * root Strong's number parsed from the definition.
 */
@Serializable
data class StrongsEntryDto(
    val number: String,
    val word: String,
    val transliteration: String,
    val pronunciation: String,
    val definition: String,
    val kjvUsage: String,
    val occurrences: Int,
    val root: String,
)

/** One verse in which a Strong's number appears, for the "Appears in" list. */
@Serializable
data class DictionaryVerseDto(
    val bookName: String,
    val chapter: Int,
    val verse: Int,
    /** Human reference, e.g. "Genesis 1:1". */
    val reference: String,
    /** English (translation) verse text; blank if the loaded Bible lacks it. */
    val text: String,
)

/** Response for `GET /api/dictionary/{number}/verses`. */
@Serializable
data class DictionaryVersesResponse(
    val number: String,
    /** Total number of verses the number appears in (the list itself is capped). */
    val total: Int,
    val verses: List<DictionaryVerseDto>,
)

/**
 * Loads the bundled Strong's dictionary JSON (`files/dictionary/strongs_h*.json`,
 * `strongs_g*.json`) and serves search / lookup over it for the companion REST API.
 *
 * Entries are loaded lazily on first request and cached per language. Mirrors the
 * loading logic in [org.churchpresenter.app.churchpresenter.viewmodel.DictionaryViewModel].
 */
object StrongsDictionaryRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, List<StrongsEntry>>()

    private val interlinear = InterlinearRepository()
    private val strongsRef = Regex("[HG]\\d{1,5}")

    private fun normalizeLang(lang: String?): String = if (lang?.lowercase() == "ru") "ru" else "en"

    /** Loads both interlinear indexes (once) so occurrence counts are available. */
    private suspend fun ensureInterlinear() {
        interlinear.ensureHebrewLoaded()
        interlinear.ensureGreekLoaded()
    }

    /** Total word-instance occurrences of a Strong's number across scripture. */
    private fun occurrencesOf(number: String): Int = interlinear.getVersesForEntry(number).size

    /** First Strong's reference in the definition other than the entry's own number, or "". */
    private fun rootOf(entry: StrongsEntry): String =
        strongsRef.findAll(entry.definition).map { it.value }.firstOrNull { it != entry.number } ?: ""

    private fun StrongsEntry.toDto(): StrongsEntryDto = StrongsEntryDto(
        number = number,
        word = word,
        transliteration = transliteration,
        pronunciation = pronunciation,
        definition = definition,
        kjvUsage = kjvUsage,
        occurrences = occurrencesOf(number),
        root = rootOf(this),
    )

    /** All entries (Hebrew + Greek) for the given language, loaded once and cached. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun all(lang: String?): List<StrongsEntry> {
        val key = normalizeLang(lang)
        cache[key]?.let { return it }
        return mutex.withLock {
            cache[key]?.let { return it }
            val hFile = if (key == "ru") "files/dictionary/strongs_h_ru.json" else "files/dictionary/strongs_h.json"
            val gFile = if (key == "ru") "files/dictionary/strongs_g_ru.json" else "files/dictionary/strongs_g.json"
            val loaded = withContext(Dispatchers.IO) {
                val h = json.decodeFromString<List<StrongsEntry>>(Res.readBytes(hFile).decodeToString())
                val g = json.decodeFromString<List<StrongsEntry>>(Res.readBytes(gFile).decodeToString())
                h + g
            }
            cache[key] = loaded
            loaded
        }
    }

    /** Look up a single entry by its Strong's number (e.g. "H430", "G26"). Case-insensitive. */
    suspend fun lookup(number: String, lang: String?): StrongsEntryDto? {
        ensureInterlinear()
        val target = number.trim().uppercase()
        return all(lang).firstOrNull { it.number.uppercase() == target }?.toDto()
    }

    /**
     * Search entries by number, word, transliteration, definition or KJV usage.
     * [filter] is "all" | "hebrew" | "greek". Results are capped at [limit].
     * An empty [query] returns the first [limit] entries (optionally filtered).
     *
     * When [book] is supplied, results are additionally restricted to the Strong's
     * numbers that occur within that Bible reference, narrowing progressively:
     * book only → whole book, +[chapter] → that chapter, +[verse] → a single verse.
     * [book]/[chapter]/[verse] use canonical KJV book numbering (Genesis=1 … Revelation=66),
     * which matches both the interlinear index and the `/api/bible` `book-id`.
     */
    suspend fun search(
        query: String,
        lang: String?,
        filter: String,
        limit: Int,
        book: Int? = null,
        chapter: Int? = null,
        verse: Int? = null,
    ): List<StrongsEntryDto> {
        ensureInterlinear()
        val all = all(lang)
        val byLang = when (filter.lowercase()) {
            "hebrew" -> all.filter { it.isHebrew }
            "greek" -> all.filter { it.isGreek }
            else -> all
        }
        val byRef = if (book != null) {
            val valid = interlinear.getStrongsForBookChapter(book, chapter, verse)
            byLang.filter { it.number in valid }
        } else byLang
        val q = query.trim().lowercase()
        val matched = if (q.isEmpty()) {
            byRef
        } else {
            byRef.filter { e ->
                e.number.lowercase() == q ||
                e.number.lowercase().contains(q) ||
                e.word.lowercase().contains(q) ||
                e.transliteration.lowercase().contains(q) ||
                e.definition.lowercase().contains(q) ||
                e.kjvUsage.lowercase().contains(q)
            }.sortedWith(
                // Exact number / word matches first, then by Strong's number order.
                compareByDescending<StrongsEntry> { it.number.lowercase() == q || it.word.lowercase() == q }
                    .thenBy { it.numericValue }
            )
        }
        return matched.take(limit.coerceIn(1, 500)).map { it.toDto() }
    }

    /**
     * Returns the verse references in which [number] appears, capped at [limit].
     * First element of the returned pair is the *total* occurrence count (before cap).
     *
     * When [book] is supplied, references within that reference scope are ordered
     * first (book-only → whole book, +[chapter] → that chapter, +[verse] → that
     * verse), so the verse the caller is filtering by leads the list. Canonical
     * order is preserved within each group. [book]/[chapter]/[verse] use canonical
     * KJV numbering (Genesis=1 … Revelation=66).
     */
    suspend fun versesFor(
        number: String,
        limit: Int,
        book: Int? = null,
        chapter: Int? = null,
        verse: Int? = null,
    ): Pair<Int, List<String>> {
        ensureInterlinear()
        // distinct(): a number occurring twice in one verse is added twice by the
        // index, but "Appears in" lists each verse once (and total counts verses).
        val refs = interlinear.getVersesForEntry(number.trim().uppercase()).map { it.ref }.distinct()
        val ordered = if (book != null) {
            val (inScope, rest) = refs.partition { ref ->
                ref.substring(0, 3).toInt() == book &&
                    (chapter == null || ref.substring(3, 6).toInt() == chapter) &&
                    (verse == null || ref.substring(6, 9).toInt() == verse)
            }
            inScope + rest
        } else refs
        return refs.size to ordered.take(limit.coerceIn(1, 200))
    }
}
