package org.churchpresenter.bibleformats.catalog

/**
 * What a language is called, in English and in itself.
 *
 * [native] is blank wherever it would only repeat [english], since a label showing both would then
 * say the same word twice.
 */
internal data class LanguageNaming(val english: String, val native: String = "")

/**
 * Uppercase language code to language name, for the download browser's language filter.
 *
 * eBible's own catalogue already publishes both spellings for every one of the ~1,240 codes it
 * carries, keyed by the same uppercase three-letter codes the Zefania archive names its folders
 * with — so it doubles as the lookup for both tabs, and there is no thousand-row table here to keep
 * current.
 *
 * [UNLISTED] covers only the gap: the codes the Zefania archive uses that eBible has no row for at
 * all. That is partly the ISO 639-2/B bibliographic spellings (`CZE`, `GER`, `FRE`, `DUT`, `CHI`)
 * that eBible writes the 639-3 way, and partly languages eBible simply does not publish — Afrikaans,
 * Bulgarian, Norwegian, Swahili among them. Bounded by the archive's folder list rather than by ISO
 * 639 as a whole, which is what keeps it at two dozen-odd entries instead of several thousand.
 */
internal object BibleLanguageNames {

    /**
     * Every Zefania language folder with no matching row in the eBible catalogue.
     *
     * Measured against both published catalogues: of the archive's 63 language folders, these 29 are
     * absent from eBible entirely. A folder added later for a language eBible also lacks will show
     * its bare code until it is added here — visible, but harmless.
     *
     * An autonym is filled in only where it is both settled and renderable; the exceptions each say
     * why below, because "no autonym" here is a decision rather than an oversight.
     */
    private val UNLISTED = mapOf(
        // The autonym is the English name, so there is nothing to add.
        "AFR" to LanguageNaming("Afrikaans"),
        "ALB" to LanguageNaming("Albanian", "Shqip"),
        "ARA" to LanguageNaming("Arabic", "العربية"),
        "BAQ" to LanguageNaming("Basque", "Euskara"),
        "BUL" to LanguageNaming("Bulgarian", "български"),
        "CHI" to LanguageNaming("Chinese", "中文"),
        // Old Cyrillic, with characters common desktop fonts cover patchily, and an orthography
        // that varies by recension.
        "CHU" to LanguageNaming("Church Slavonic"),
        "CZE" to LanguageNaming("Czech", "Čeština"),
        // Esperanto calls itself Esperanto.
        "ESP" to LanguageNaming("Esperanto"),
        "FRE" to LanguageNaming("French", "Français"),
        // "Gaelic" is itself ambiguous between Irish and Scottish, and GLA already covers Scottish,
        // so an autonym here would silently resolve that ambiguity one way.
        "GAE" to LanguageNaming("Gaelic"),
        "GER" to LanguageNaming("German", "Deutsch"),
        "GLA" to LanguageNaming("Scottish Gaelic", "Gàidhlig"),
        // Written in the Gothic script, which almost no desktop font carries — it would be tofu.
        "GOT" to LanguageNaming("Gothic"),
        "GRE" to LanguageNaming("Greek", "Ελληνικά"),
        // No single settled written autonym; "Patwa" is contested and is not what the modules use.
        "JAM" to LanguageNaming("Jamaican Creole"),
        "KAB" to LanguageNaming("Kabyle", "Taqbaylit"),
        "LAV" to LanguageNaming("Latvian", "Latviešu"),
        "MAO" to LanguageNaming("Maori", "Māori"),
        "NDS" to LanguageNaming("Low German", "Plattdüütsch"),
        "NL" to LanguageNaming("Dutch", "Nederlands"),
        "NOR" to LanguageNaming("Norwegian", "Norsk"),
        "RUM" to LanguageNaming("Romanian", "Română"),
        "SCR" to LanguageNaming("Croatian", "Hrvatski"),
        // A dialectal Arabic spelling this cannot verify.
        "SHU" to LanguageNaming("Chadian Arabic"),
        "SWA" to LanguageNaming("Swahili", "Kiswahili"),
        // Syriac script, with the same font-coverage problem as Gothic.
        "SYR" to LanguageNaming("Syriac"),
        // Not a language.
        "UND" to LanguageNaming("Unknown"),
        // No settled written autonym.
        "XKL" to LanguageNaming("Kenyang"),
    )

    /**
     * Merges a catalogue's own names over [UNLISTED].
     *
     * The catalogue wins outright wherever it has a row: it is published data and this map is a
     * snapshot. Replacement is per entry rather than per field — the two key sets are disjoint in
     * practice, since the whole point of [UNLISTED] is the codes eBible does not carry.
     */
    internal fun resolve(catalogue: Map<String, LanguageNaming>): Map<String, LanguageNaming> =
        UNLISTED + catalogue

    /**
     * The lookup as it stands, using whatever eBible data is already cached.
     *
     * With no eBible catalogue fetched yet this still returns [UNLISTED], so a cold first visit to
     * the Zefania tab names most of what it lists rather than nothing.
     */
    suspend fun table(): Map<String, LanguageNaming> = resolve(EBibleSource.cachedLanguageNames())
}
