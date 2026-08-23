package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.bible_book_1
import org.churchpresenter.resources.generated.resources.bible_book_10
import org.churchpresenter.resources.generated.resources.bible_book_11
import org.churchpresenter.resources.generated.resources.bible_book_12
import org.churchpresenter.resources.generated.resources.bible_book_13
import org.churchpresenter.resources.generated.resources.bible_book_14
import org.churchpresenter.resources.generated.resources.bible_book_15
import org.churchpresenter.resources.generated.resources.bible_book_16
import org.churchpresenter.resources.generated.resources.bible_book_17
import org.churchpresenter.resources.generated.resources.bible_book_18
import org.churchpresenter.resources.generated.resources.bible_book_19
import org.churchpresenter.resources.generated.resources.bible_book_2
import org.churchpresenter.resources.generated.resources.bible_book_20
import org.churchpresenter.resources.generated.resources.bible_book_21
import org.churchpresenter.resources.generated.resources.bible_book_22
import org.churchpresenter.resources.generated.resources.bible_book_23
import org.churchpresenter.resources.generated.resources.bible_book_24
import org.churchpresenter.resources.generated.resources.bible_book_25
import org.churchpresenter.resources.generated.resources.bible_book_26
import org.churchpresenter.resources.generated.resources.bible_book_27
import org.churchpresenter.resources.generated.resources.bible_book_28
import org.churchpresenter.resources.generated.resources.bible_book_29
import org.churchpresenter.resources.generated.resources.bible_book_3
import org.churchpresenter.resources.generated.resources.bible_book_30
import org.churchpresenter.resources.generated.resources.bible_book_31
import org.churchpresenter.resources.generated.resources.bible_book_32
import org.churchpresenter.resources.generated.resources.bible_book_33
import org.churchpresenter.resources.generated.resources.bible_book_34
import org.churchpresenter.resources.generated.resources.bible_book_35
import org.churchpresenter.resources.generated.resources.bible_book_36
import org.churchpresenter.resources.generated.resources.bible_book_37
import org.churchpresenter.resources.generated.resources.bible_book_38
import org.churchpresenter.resources.generated.resources.bible_book_39
import org.churchpresenter.resources.generated.resources.bible_book_4
import org.churchpresenter.resources.generated.resources.bible_book_40
import org.churchpresenter.resources.generated.resources.bible_book_41
import org.churchpresenter.resources.generated.resources.bible_book_42
import org.churchpresenter.resources.generated.resources.bible_book_43
import org.churchpresenter.resources.generated.resources.bible_book_44
import org.churchpresenter.resources.generated.resources.bible_book_45
import org.churchpresenter.resources.generated.resources.bible_book_46
import org.churchpresenter.resources.generated.resources.bible_book_47
import org.churchpresenter.resources.generated.resources.bible_book_48
import org.churchpresenter.resources.generated.resources.bible_book_49
import org.churchpresenter.resources.generated.resources.bible_book_5
import org.churchpresenter.resources.generated.resources.bible_book_50
import org.churchpresenter.resources.generated.resources.bible_book_51
import org.churchpresenter.resources.generated.resources.bible_book_52
import org.churchpresenter.resources.generated.resources.bible_book_53
import org.churchpresenter.resources.generated.resources.bible_book_54
import org.churchpresenter.resources.generated.resources.bible_book_55
import org.churchpresenter.resources.generated.resources.bible_book_56
import org.churchpresenter.resources.generated.resources.bible_book_57
import org.churchpresenter.resources.generated.resources.bible_book_58
import org.churchpresenter.resources.generated.resources.bible_book_59
import org.churchpresenter.resources.generated.resources.bible_book_6
import org.churchpresenter.resources.generated.resources.bible_book_60
import org.churchpresenter.resources.generated.resources.bible_book_61
import org.churchpresenter.resources.generated.resources.bible_book_62
import org.churchpresenter.resources.generated.resources.bible_book_63
import org.churchpresenter.resources.generated.resources.bible_book_64
import org.churchpresenter.resources.generated.resources.bible_book_65
import org.churchpresenter.resources.generated.resources.bible_book_66
import org.churchpresenter.resources.generated.resources.bible_book_7
import org.churchpresenter.resources.generated.resources.bible_book_8
import org.churchpresenter.resources.generated.resources.bible_book_9
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

