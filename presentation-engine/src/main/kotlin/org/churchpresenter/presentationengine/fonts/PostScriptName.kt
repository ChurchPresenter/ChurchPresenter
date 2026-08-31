package org.churchpresenter.presentationengine.fonts

/**
 * Peels a PostScript font name back towards the family name AWT knows it by.
 *
 * Keynote stores the typeface as a PostScript name (`Arial-BoldMT`, `HelveticaNeue-Bold`,
 * `TimesNewRomanPS-BoldMT`), never as the AWT family, so a plain family lookup misses on
 * essentially every real deck. That matters beyond the glyph shapes: the substitute is a
 * different width, which silently re-wraps slides — `Arial-BoldMT` at 38pt bold measures 922pt as
 * Arial and 987pt as Open Sans, one extra line inside a 952.8pt text box.
 */
internal object PostScriptName {

    /** PostScript names put the style after a hyphen: `Arial-BoldMT`, `Avenir-BookOblique`. */
    private const val STYLE_SEPARATOR = '-'

    /** Foundry tags the family half may carry, longest first so `PS` cannot win over `Pro`. */
    private val FOUNDRY_SUFFIXES = listOf("Std", "Pro", "MT", "PS")

    /** Words a split family half may trail in and still mean the same family (`Avenir Book`). */
    private val STYLE_WORDS = setOf(
        "regular", "roman", "book", "normal", "bold", "semibold", "demibold", "medium", "light",
        "extralight", "ultralight", "thin", "black", "heavy", "italic", "oblique", "condensed",
        "narrow", "expanded", "ps", "mt", "std", "pro"
    )

    private val BOLD_WORDS = setOf("bold", "semibold", "demibold", "black", "heavy")
    private val ITALIC_WORDS = setOf("italic", "oblique")

    /** `aB` → `a B` (lower running into upper), and `ABc` → `A Bc` (acronym running into a word). */
    private val CAMEL_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")
    private val ACRONYM_BOUNDARY = Regex("(?<=[A-Z])(?=[A-Z][a-z])")

    /**
     * The name itself, then progressively family-shaped forms of it, most specific first:
     * `TimesNewRomanPS-BoldMT` yields `TimesNewRomanPS-BoldMT`, `TimesNewRomanPS`, `TimesNewRoman`,
     * `Times New Roman Ps`, `Times New Roman`, `Times`.
     */
    fun candidates(requested: String): List<String> {
        val raw = requested.trim()
        if (raw.isEmpty()) return emptyList()
        val base = raw.substringBefore(STYLE_SEPARATOR).trim()
        val withoutFoundry = FOUNDRY_SUFFIXES
            .firstOrNull { base.length > it.length && base.endsWith(it, ignoreCase = true) }
            ?.let { base.dropLast(it.length) }
            ?: base
        return listOf(raw, base, withoutFoundry, splitCamelCase(base), splitCamelCase(withoutFoundry))
            .flatMap { listOf(it, dropStyleWords(it)) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /** Weight and slant read out of a style suffix (`-BoldItalic`, `-Oblique`); bare names say nothing. */
    fun styleOf(requested: String): Pair<Boolean, Boolean> {
        val suffix = requested.trim().substringAfter(STYLE_SEPARATOR, "")
        if (suffix.isEmpty()) return false to false
        val words = splitCamelCase(suffix).split(' ', STYLE_SEPARATOR).map { it.lowercase() }
        return words.any { it in BOLD_WORDS } to words.any { it in ITALIC_WORDS }
    }

    /** `HelveticaNeue` → `Helvetica Neue`; `TimesNewRoman` → `Times New Roman`. */
    private fun splitCamelCase(name: String): String =
        name.replace(CAMEL_BOUNDARY, " ").replace(ACRONYM_BOUNDARY, " ")

    /** `Avenir Book` → `Avenir`; a name that is *only* style words is left alone. */
    private fun dropStyleWords(name: String): String {
        val words = name.split(' ').filter { it.isNotEmpty() }
        val kept = words.dropLastWhile { it.lowercase() in STYLE_WORDS }
        return if (kept.isEmpty()) name else kept.joinToString(" ")
    }
}
