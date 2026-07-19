package org.churchpresenter.app.churchpresenter.server

import churchpresenter.composeapp.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.jetbrains.compose.resources.ExperimentalResourceApi

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

    private fun normalizeLang(lang: String?): String = if (lang?.lowercase() == "ru") "ru" else "en"

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
    suspend fun lookup(number: String, lang: String?): StrongsEntry? {
        val target = number.trim().uppercase()
        return all(lang).firstOrNull { it.number.uppercase() == target }
    }

    /**
     * Search entries by number, word, transliteration, definition or KJV usage.
     * [filter] is "all" | "hebrew" | "greek". Results are capped at [limit].
     * An empty [query] returns the first [limit] entries (optionally filtered).
     */
    suspend fun search(
        query: String,
        lang: String?,
        filter: String,
        limit: Int,
    ): List<StrongsEntry> {
        val all = all(lang)
        val byLang = when (filter.lowercase()) {
            "hebrew" -> all.filter { it.isHebrew }
            "greek" -> all.filter { it.isGreek }
            else -> all
        }
        val q = query.trim().lowercase()
        val matched = if (q.isEmpty()) {
            byLang
        } else {
            byLang.filter { e ->
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
        return matched.take(limit.coerceIn(1, 500))
    }
}
