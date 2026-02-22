package org.churchpresenter.app.churchpresenter.data

import churchpresenter.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import java.util.Locale

object BibleBookNames {
    private val bookResourceIds = listOf(
        Res.string.bible_book_1, Res.string.bible_book_2, Res.string.bible_book_3,
        Res.string.bible_book_4, Res.string.bible_book_5, Res.string.bible_book_6,
        Res.string.bible_book_7, Res.string.bible_book_8, Res.string.bible_book_9,
        Res.string.bible_book_10, Res.string.bible_book_11, Res.string.bible_book_12,
        Res.string.bible_book_13, Res.string.bible_book_14, Res.string.bible_book_15,
        Res.string.bible_book_16, Res.string.bible_book_17, Res.string.bible_book_18,
        Res.string.bible_book_19, Res.string.bible_book_20, Res.string.bible_book_21,
        Res.string.bible_book_22, Res.string.bible_book_23, Res.string.bible_book_24,
        Res.string.bible_book_25, Res.string.bible_book_26, Res.string.bible_book_27,
        Res.string.bible_book_28, Res.string.bible_book_29, Res.string.bible_book_30,
        Res.string.bible_book_31, Res.string.bible_book_32, Res.string.bible_book_33,
        Res.string.bible_book_34, Res.string.bible_book_35, Res.string.bible_book_36,
        Res.string.bible_book_37, Res.string.bible_book_38, Res.string.bible_book_39,
        Res.string.bible_book_40, Res.string.bible_book_41, Res.string.bible_book_42,
        Res.string.bible_book_43, Res.string.bible_book_44, Res.string.bible_book_45,
        Res.string.bible_book_46, Res.string.bible_book_47, Res.string.bible_book_48,
        Res.string.bible_book_49, Res.string.bible_book_50, Res.string.bible_book_51,
        Res.string.bible_book_52, Res.string.bible_book_53, Res.string.bible_book_54,
        Res.string.bible_book_55, Res.string.bible_book_56, Res.string.bible_book_57,
        Res.string.bible_book_58, Res.string.bible_book_59, Res.string.bible_book_60,
        Res.string.bible_book_61, Res.string.bible_book_62, Res.string.bible_book_63,
        Res.string.bible_book_64, Res.string.bible_book_65, Res.string.bible_book_66
    )

    // Cache results so we only pay the cost of 132 getString calls once per run
    @Volatile private var cachedEnglishNames: List<String>? = null
    @Volatile private var cachedMappingLocale: String? = null
    @Volatile private var cachedMapping: Map<String, String>? = null

    /**
     * Returns English book names in standard Bible order
     */
    suspend fun getEnglishBookNames(): List<String> {
        cachedEnglishNames?.let { return it }
        val englishLocale = Locale.ENGLISH
        val names = bookResourceIds.map { resource ->
            org.jetbrains.compose.resources.getString(resource, englishLocale)
        }
        cachedEnglishNames = names
        return names
    }

    /**
     * Returns a mapping of English book names (lowercase) to localized book names
     * for cross-language book search support.
     */
    suspend fun getBookNameMapping(): Map<String, String> {
        val currentLocale = Locale.getDefault().toLanguageTag()
        cachedMapping?.takeIf { cachedMappingLocale == currentLocale }?.let { return it }

        val englishNames = getEnglishBookNames()

        // Get localized book names for current locale
        val locale = Locale.getDefault()
        val localizedNames = bookResourceIds.map { resource ->
            org.jetbrains.compose.resources.getString(resource, locale)
        }

        val mapping = mutableMapOf<String, String>()
        englishNames.forEachIndexed { index, englishName ->
            mapping[englishName.lowercase()] = localizedNames[index]
        }

        cachedMappingLocale = currentLocale
        cachedMapping = mapping
        return mapping
    }

    /**
     * Returns the list of string resource IDs for all 66 Bible books
     */
    fun getBookResourceIds(): List<StringResource> = bookResourceIds
}

