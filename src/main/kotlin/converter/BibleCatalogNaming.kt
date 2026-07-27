package converter

import java.text.Normalizer

/**
 * Names an installed Bible module: `ENG_ACV.spb`, `CZE_CSP.spb`, `SWA.spb`.
 *
 * The stem is the identity the app persists as the user's selected Bible, so it is derived from the
 * archive's *file name* rather than from anything inside the XML — that way the browse list can
 * predict the installed name without downloading a thing, and a translation someone has already
 * chosen can never be renamed out from under them because a module's `<identifier>` was edited
 * upstream.
 *
 * [abbreviation] is shared with [XmlToSpbConverter], which writes the same value into the module's
 * `##Abbreviation:` header, so the header and the file name cannot drift apart.
 */
object BibleCatalogNaming {

    /** Used when a module carries no usable language code. */
    const val UNKNOWN_LANGUAGE = "UND"

    /** The `##Abbreviation:` rule: the initial of each word in the translation's name. */
    fun abbreviation(bibleName: String): String =
        bibleName.split(" ")
            .filter { it.isNotBlank() }
            .joinToString("") { it.first().toString() }

    /**
     * `("ENG", "ACV")` → `ENG_ACV`, `("THA", "KJVTHAI")` → `THA_KJVTHAI`, `("CZE", "ČSP")` → `CZE_CSP`.
     *
     * Two shapes in the archive would otherwise stutter, so the redundancy is dropped:
     * `("SWA", "SWA")` → `SWA` (the identifier is just the language), and
     * `("AFR", "AFR3353")` → `AFR_3353` (the identifier repeats the language as a prefix).
     */
    fun fileStem(language: String?, identifier: String?): String {
        val lang = slug(language).ifEmpty { UNKNOWN_LANGUAGE }
        val id = slug(identifier)
        return when {
            id.isEmpty() || id == lang -> lang
            id.startsWith(lang) && id.length > lang.length -> "${lang}_${id.removePrefix(lang)}"
            else -> "${lang}_$id"
        }
    }

    /**
     * Appends `_2`, `_3`… until the stem is free.
     *
     * Distinct modules can reduce to the same stem, and a browse list with one of them silently
     * missing is worse than one with a slightly ugly name.
     */
    fun deduplicate(stem: String, taken: Set<String>): String {
        if (stem !in taken) return stem
        var suffix = 2
        while ("${stem}_$suffix" in taken) suffix++
        return "${stem}_$suffix"
    }

    /**
     * Reduces to `A-Z0-9`, decomposing accents first so `Č` becomes `C` rather than being dropped —
     * without the NFD pass, the Czech identifier `ČSP` would slug to `SP`.
     */
    internal fun slug(value: String?): String =
        Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD)
            .uppercase()
            .filter { it in 'A'..'Z' || it in '0'..'9' }
}
