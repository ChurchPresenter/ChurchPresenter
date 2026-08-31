package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How two or more Bible translations sit against each other, per output shape.
 *
 * Both arrangements were hardcoded in `BiblePresenter` before these fields existed, so the defaults
 * are load-bearing: they are what every installed copy already draws, and an upgrade that changed
 * either of them would silently redraw a live screen.
 */
class BibleBilingualLayoutTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun roundTrip(settings: AppSettings): AppSettings = json.decodeFromString(
        AppSettings.serializer(),
        Json { encodeDefaults = true }.encodeToString(AppSettings.serializer(), settings),
    )

    @Test
    fun `a full screen stacks by default`() {
        assertEquals(Constants.BILINGUAL_TOP_BOTTOM, BibleSettings().bilingualLayout)
    }

    @Test
    fun `a band splits across its width by default`() {
        assertEquals(Constants.BILINGUAL_SIDE_BY_SIDE, BibleSettings().bilingualLayoutLowerThird)
    }

    @Test
    fun `the two shapes are stored apart`() {
        val edited = BibleSettings(
            bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE,
            bilingualLayoutLowerThird = Constants.BILINGUAL_TOP_BOTTOM,
        )
        assertEquals(Constants.BILINGUAL_SIDE_BY_SIDE, edited.bilingualLayout)
        assertEquals(Constants.BILINGUAL_TOP_BOTTOM, edited.bilingualLayoutLowerThird)
    }

    @Test
    fun `both survive settings json`() {
        val settings = AppSettings(
            bibleSettings = BibleSettings(
                bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE,
                bilingualLayoutLowerThird = Constants.BILINGUAL_TOP_BOTTOM,
            ),
        )
        val restored = roundTrip(settings).bibleSettings
        assertEquals(Constants.BILINGUAL_SIDE_BY_SIDE, restored.bilingualLayout)
        assertEquals(Constants.BILINGUAL_TOP_BOTTOM, restored.bilingualLayoutLowerThird)
    }

    @Test
    fun `a settings file written before these existed keeps drawing what it drew`() {
        // No `bilingualLayout` keys at all, which is every file saved by an older build.
        val legacy = """{"bibleSettings":{"marginTop":54}}"""
        val restored = json.decodeFromString(AppSettings.serializer(), legacy).bibleSettings
        assertEquals(Constants.BILINGUAL_TOP_BOTTOM, restored.bilingualLayout)
        assertEquals(Constants.BILINGUAL_SIDE_BY_SIDE, restored.bilingualLayoutLowerThird)
    }

    @Test
    fun `an output override carries its own arrangement`() {
        val global = AppSettings(bibleSettings = BibleSettings())
        val assignment = ScreenAssignment(
            bibleOverride = BibleSettings(bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE),
        )
        assertEquals(
            Constants.BILINGUAL_SIDE_BY_SIDE,
            global.resolvedFor(assignment).bibleSettings.bilingualLayout,
        )
        assertEquals(
            Constants.BILINGUAL_TOP_BOTTOM,
            global.bibleSettings.bilingualLayout,
            "the global document is untouched by one screen's choice",
        )
    }
}
