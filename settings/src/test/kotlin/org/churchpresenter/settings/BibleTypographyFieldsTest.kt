package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/** The typography fields added for the redesigned Bible settings tab, through settings.json. */
class BibleTypographyFieldsTest {

    private fun roundTrip(settings: AppSettings): AppSettings =
        Json { ignoreUnknownKeys = true }.decodeFromString(
            AppSettings.serializer(),
            Json { encodeDefaults = true }.encodeToString(AppSettings.serializer(), settings),
        )

    private val styled = BibleTranslationSettings(
        fileName = "kjv.spb",
        textStrikethrough = true,
        lowerThirdTextStrikethrough = true,
        referenceStrikethrough = true,
        lowerThirdReferenceStrikethrough = true,
        textLetterSpacing = 3,
        lowerThirdTextLetterSpacing = 4,
        referenceLetterSpacing = 5,
        lowerThirdReferenceLetterSpacing = 6,
        textWordSpacing = 7,
        lowerThirdTextWordSpacing = 8,
        referenceWordSpacing = 9,
        lowerThirdReferenceWordSpacing = 10,
        textTransform = Constants.TEXT_TRANSFORM_UPPERCASE,
        lowerThirdTextTransform = Constants.TEXT_TRANSFORM_LOWERCASE,
        referenceTransform = Constants.TEXT_TRANSFORM_CAPITALIZE,
        lowerThirdReferenceTransform = Constants.TEXT_TRANSFORM_NONE,
    )

    @Test
    fun `every new field survives a settings file`() {
        val settings = AppSettings(bibleSettings = BibleSettings().withTranslations(listOf(styled)))

        assertEquals(styled, roundTrip(settings).bibleSettings.translationList().single())
    }

    @Test
    fun `they default to off, unspaced and untransformed`() {
        val fresh = BibleTranslationSettings()

        assertEquals(false, fresh.textStrikethrough)
        assertEquals(0, fresh.textLetterSpacing)
        assertEquals(0, fresh.textWordSpacing)
        assertEquals(Constants.TEXT_TRANSFORM_NONE, fresh.textTransform)
    }

    @Test
    fun `a settings file written before they existed still reads`() {
        // The fields are additive, so an older file simply has none of them and takes the defaults.
        val older = """{"bibleSettings":{"translations":[{"fileName":"kjv.spb","textFontSize":88}]}}"""

        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(AppSettings.serializer(), older)
        val translation = decoded.bibleSettings.translationList().single()

        assertEquals(88, translation.textFontSize)
        assertEquals(Constants.TEXT_TRANSFORM_NONE, translation.textTransform)
        assertEquals(0, translation.textLetterSpacing)
    }
}
