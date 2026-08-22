package org.churchpresenter.dictionary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Where the bundled JSON sits on the classpath, inside this module's own jar. */
private const val BUNDLED_DIR = "/dictionary"

/**
 * Reads one of the module's bundled JSON files.
 *
 * A missing file is a broken build rather than a runtime condition to recover from — the six files
 * are committed beside this code and packaged with it — so it throws instead of answering empty,
 * which would look to the tab like a dictionary with nothing in it.
 */
internal fun readBundledDictionaryFile(name: String): ByteArray =
    StrongsCatalog::class.java.getResourceAsStream("$BUNDLED_DIR/$name")?.use { it.readBytes() }
        ?: error("bundled dictionary file is missing from the classpath: $name")

/** The two halves of one language's dictionary, as loaded. */
data class StrongsEntries(
    val hebrew: List<StrongsEntry>,
    val greek: List<StrongsEntry>,
) {
    /** Both halves in one list, Hebrew first — H numbers precede G numbers in Strong's. */
    val all: List<StrongsEntry> get() = hebrew + greek
}

/**
 * The bundled Strong's dictionary, in English or Russian.
 *
 * Nothing is cached here: the two callers want different things from a load — the tab sorts each
 * half by number and holds the result as UI state, the REST layer caches both halves flat per
 * language — so the shared piece is reading and parsing the files, and each caller keeps what it
 * made of them.
 *
 * @param loader reads one bundled file by name. Defaulted to the packaged resource and replaced in
 * tests with a lambda over a handful of entries — an injected function rather than a mutable field,
 * so a test needs no teardown and cannot leak its fixture into whatever runs next in the same JVM.
 */
class StrongsCatalog(
    private val loader: (String) -> ByteArray = ::readBundledDictionaryFile,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Loads both halves for [language]; anything but `"ru"` is English. */
    suspend fun load(language: String?): StrongsEntries {
        val key = normalizeLanguage(language)
        return withContext(Dispatchers.IO) {
            StrongsEntries(hebrew = parse(hebrewFile(key)), greek = parse(greekFile(key)))
        }
    }

    private fun parse(name: String): List<StrongsEntry> =
        json.decodeFromString(ListSerializer(StrongsEntry.serializer()), loader(name).decodeToString())

    companion object {
        const val ENGLISH = "en"
        const val RUSSIAN = "ru"

        // The four bundled files. Public so a fixture can answer for them by name.
        const val HEBREW_FILE = "strongs_h.json"
        const val HEBREW_FILE_RU = "strongs_h_ru.json"
        const val GREEK_FILE = "strongs_g.json"
        const val GREEK_FILE_RU = "strongs_g_ru.json"

        /** The dictionary ships in two languages, so every other value means English. */
        fun normalizeLanguage(language: String?): String =
            if (language?.lowercase() == RUSSIAN) RUSSIAN else ENGLISH

        private fun hebrewFile(key: String) = if (key == RUSSIAN) HEBREW_FILE_RU else HEBREW_FILE

        private fun greekFile(key: String) = if (key == RUSSIAN) GREEK_FILE_RU else GREEK_FILE
    }
}
