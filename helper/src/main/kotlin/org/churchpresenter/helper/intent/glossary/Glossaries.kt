package org.churchpresenter.helper.intent.glossary

/**
 * Every language the app ships, besides English. A request is read in the app's language first,
 * then as typed, then in each of the others — the first reading a rule answers wins, so English
 * typed into a Russian app and Russian typed into an English one are both understood.
 */
internal object Glossaries {
    val ALL: List<Glossary> = listOf(
        RUSSIAN, UKRAINIAN, BELARUSIAN, KAZAKH, POLISH, CZECH, SLOVAK, CROATIAN, GERMAN, DUTCH, SWEDISH,
        NORWEGIAN, FINNISH, ESTONIAN, LATVIAN, SPANISH, PORTUGUESE, FRENCH, ROMANIAN, TURKISH, UZBEK,
        INDONESIAN, MALAY, TAGALOG, SWAHILI, ARABIC, PERSIAN, HINDI, NEPALI, TAMIL, THAI, LAO, JAPANESE,
        CHINESE, TAHITIAN,
    )

    fun readings(normalized: String, language: String): Sequence<String> = sequence {
        val own = ALL.firstOrNull { it.language == language }
        own?.toEnglish(normalized)?.takeIf { it != normalized }?.let { yield(it) }
        // Texting shorthand read as English, beside the text as typed: "u" is a word in Uzbek.
        expandShorthand(normalized).takeIf { it != normalized }?.let { yield(it) }
        yield(normalized)
        ALL.asSequence()
            .filter { it !== own }
            .map { it.toEnglish(normalized) }
            .filter { it != normalized }
            .forEach { yield(it) }
    }.distinct()
}

/** How people type to a chat: "what can u do", "pls", "thx". */
private val SHORTHAND = mapOf(
    "u" to "you", "ur" to "your", "pls" to "please", "plz" to "please", "thx" to "thanks", "wat" to "what",
)

/** [normalized] with each word of texting shorthand written out. */
internal fun expandShorthand(normalized: String): String =
    normalized.split(' ').joinToString(" ") { SHORTHAND[it] ?: it }
