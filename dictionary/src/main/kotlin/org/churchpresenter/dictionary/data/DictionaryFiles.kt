package org.churchpresenter.dictionary.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/**
 * Reads one of the dictionary's data files by name, such as [STRONGS_HEBREW] or [INTERLINEAR_GREEK].
 *
 * The app reads [Bundled], the files this module ships. A test hands its own reader in, so it gets
 * a small corpus it can see instead of the real 18 MB.
 */
fun interface DictionaryFiles {
    suspend fun read(name: String): ByteArray

    companion object {
        const val STRONGS_HEBREW = "strongs_h.json"
        const val STRONGS_GREEK = "strongs_g.json"
        const val STRONGS_HEBREW_RU = "strongs_h_ru.json"
        const val STRONGS_GREEK_RU = "strongs_g_ru.json"
        const val INTERLINEAR_GREEK = "interlinear_g.json"
        const val INTERLINEAR_HEBREW = "interlinear_h.json"

        /** The files under `resources/dictionary/` in this module, read off the classpath. */
        val Bundled = DictionaryFiles { name ->
            withContext(Dispatchers.IO) {
                DictionaryFiles::class.java.getResourceAsStream("/dictionary/$name")?.use { it.readBytes() }
                    ?: throw FileNotFoundException("dictionary/$name")
            }
        }

        /** The Hebrew and Greek Strong's files for [language]: `"ru"` for Russian, English otherwise. */
        fun strongsFor(language: String): Pair<String, String> =
            if (language == "ru") STRONGS_HEBREW_RU to STRONGS_GREEK_RU else STRONGS_HEBREW to STRONGS_GREEK
    }
}
