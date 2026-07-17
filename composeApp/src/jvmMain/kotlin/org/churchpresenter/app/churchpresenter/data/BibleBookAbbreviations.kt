package org.churchpresenter.app.churchpresenter.data

import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_1
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_10
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_11
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_12
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_13
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_14
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_15
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_16
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_17
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_18
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_19
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_2
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_20
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_21
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_22
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_23
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_24
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_25
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_26
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_27
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_28
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_29
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_3
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_30
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_31
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_32
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_33
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_34
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_35
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_36
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_37
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_38
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_39
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_4
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_40
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_41
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_42
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_43
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_44
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_45
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_46
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_47
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_48
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_49
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_5
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_50
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_51
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_52
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_53
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_54
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_55
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_56
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_57
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_58
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_59
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_6
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_60
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_61
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_62
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_63
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_64
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_65
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_66
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_7
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_8
import churchpresenter.composeapp.generated.resources.bible_book_abbrev_9
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.util.Locale

/**
 * Common Bible book abbreviations, keyed by canonical book id (1 Genesis .. 66 Revelation — the
 * same numbering an .spb file's own header assigns, and what [Bible.getBookId] returns). Used by
 * [PlanningCenterScriptureDetector] to recognize a reference like "Gen 1:1" even though the
 * loaded Bible's own book name is spelled out in full ("Genesis") — PCO plan text is typically
 * abbreviated, regardless of which Bible is loaded locally.
 *
 * Backed by `bible_book_abbrev_1`..`bible_book_abbrev_66` in strings.xml (pipe-separated variants
 * per book) instead of a hardcoded table, so each locale's own translators can supply their own
 * abbreviations over time — English is the only populated locale today (see the "never touch
 * other locale files" rule in AGENT.md). Matching always also falls back to the English resource
 * regardless of the app's current language, since PCO plan text is frequently typed in English
 * abbreviations no matter the app's display language.
 */
object BibleBookAbbreviations {

    private val abbreviationResourceIds: List<StringResource> = listOf(
        Res.string.bible_book_abbrev_1, Res.string.bible_book_abbrev_2, Res.string.bible_book_abbrev_3,
        Res.string.bible_book_abbrev_4, Res.string.bible_book_abbrev_5, Res.string.bible_book_abbrev_6,
        Res.string.bible_book_abbrev_7, Res.string.bible_book_abbrev_8, Res.string.bible_book_abbrev_9,
        Res.string.bible_book_abbrev_10, Res.string.bible_book_abbrev_11, Res.string.bible_book_abbrev_12,
        Res.string.bible_book_abbrev_13, Res.string.bible_book_abbrev_14, Res.string.bible_book_abbrev_15,
        Res.string.bible_book_abbrev_16, Res.string.bible_book_abbrev_17, Res.string.bible_book_abbrev_18,
        Res.string.bible_book_abbrev_19, Res.string.bible_book_abbrev_20, Res.string.bible_book_abbrev_21,
        Res.string.bible_book_abbrev_22, Res.string.bible_book_abbrev_23, Res.string.bible_book_abbrev_24,
        Res.string.bible_book_abbrev_25, Res.string.bible_book_abbrev_26, Res.string.bible_book_abbrev_27,
        Res.string.bible_book_abbrev_28, Res.string.bible_book_abbrev_29, Res.string.bible_book_abbrev_30,
        Res.string.bible_book_abbrev_31, Res.string.bible_book_abbrev_32, Res.string.bible_book_abbrev_33,
        Res.string.bible_book_abbrev_34, Res.string.bible_book_abbrev_35, Res.string.bible_book_abbrev_36,
        Res.string.bible_book_abbrev_37, Res.string.bible_book_abbrev_38, Res.string.bible_book_abbrev_39,
        Res.string.bible_book_abbrev_40, Res.string.bible_book_abbrev_41, Res.string.bible_book_abbrev_42,
        Res.string.bible_book_abbrev_43, Res.string.bible_book_abbrev_44, Res.string.bible_book_abbrev_45,
        Res.string.bible_book_abbrev_46, Res.string.bible_book_abbrev_47, Res.string.bible_book_abbrev_48,
        Res.string.bible_book_abbrev_49, Res.string.bible_book_abbrev_50, Res.string.bible_book_abbrev_51,
        Res.string.bible_book_abbrev_52, Res.string.bible_book_abbrev_53, Res.string.bible_book_abbrev_54,
        Res.string.bible_book_abbrev_55, Res.string.bible_book_abbrev_56, Res.string.bible_book_abbrev_57,
        Res.string.bible_book_abbrev_58, Res.string.bible_book_abbrev_59, Res.string.bible_book_abbrev_60,
        Res.string.bible_book_abbrev_61, Res.string.bible_book_abbrev_62, Res.string.bible_book_abbrev_63,
        Res.string.bible_book_abbrev_64, Res.string.bible_book_abbrev_65, Res.string.bible_book_abbrev_66
    )

    @Volatile private var cachedEnglish: Map<Int, List<String>>? = null
    @Volatile private var cachedLocaleTag: String? = null
    @Volatile private var cachedForLocale: Map<Int, List<String>>? = null

    private fun parseVariants(raw: String): List<String> =
        raw.split("|").map { it.trim() }.filter { it.isNotBlank() }

    private suspend fun loadAbbreviations(locale: Locale): Map<Int, List<String>> =
        abbreviationResourceIds.mapIndexed { index, resource ->
            (index + 1) to parseVariants(getString(resource, locale))
        }.toMap()

    private suspend fun englishAbbreviations(): Map<Int, List<String>> {
        cachedEnglish?.let { return it }
        val map = loadAbbreviations(Locale.ENGLISH)
        cachedEnglish = map
        return map
    }

    private suspend fun currentLocaleAbbreviations(): Map<Int, List<String>> {
        val currentTag = Locale.getDefault().toLanguageTag()
        cachedForLocale?.takeIf { cachedLocaleTag == currentTag }?.let { return it }
        val map = loadAbbreviations(Locale.getDefault())
        cachedLocaleTag = currentTag
        cachedForLocale = map
        return map
    }

    private fun normalize(text: String): String =
        text.trim().trimEnd('.').lowercase().replace(Regex("\\s+"), " ")

    private fun findBookId(map: Map<Int, List<String>>, normalized: String, collapsed: String): Int? =
        map.entries.firstOrNull { (_, variants) ->
            variants.any {
                val n = normalize(it)
                n == normalized || n.replace(" ", "") == collapsed
            }
        }?.key

    /**
     * Resolves a book abbreviation (any case, optional trailing ".", with or without a space
     * after a numeral prefix, e.g. "1cor"/"1 cor") to its canonical book id (1-66). Checks the
     * app's current display language first, then falls back to English. Null if unrecognized in
     * either.
     */
    suspend fun resolveBookId(text: String): Int? {
        val normalized = normalize(text)
        val collapsed = normalized.replace(" ", "")
        findBookId(currentLocaleAbbreviations(), normalized, collapsed)?.let { return it }
        return findBookId(englishAbbreviations(), normalized, collapsed)
    }
}
