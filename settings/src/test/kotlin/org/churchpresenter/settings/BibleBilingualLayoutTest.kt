package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.utils.bilingualColumns
import org.churchpresenter.settings.utils.bilingualGrid
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

    // ── What an arrangement means ──────────────────────────────────────────────────────────────

    /**
     * Every named grid reads as the rows × cols it says.
     *
     * The mapping is made in one place precisely because two renderers read it, and they disagreed
     * before it existed: the full screen took the setting as a side-by-side boolean and drew every
     * grid as a plain stack, while the band took the grid and dropped any translation past its cell
     * count. Both were quiet.
     */
    @Test
    fun `each grid reads as the shape its name says`() {
        assertEquals(2 to 1, bilingualGrid(Constants.BILINGUAL_TOP_BOTTOM))
        assertEquals(1 to 3, bilingualGrid(Constants.BILINGUAL_GRID_1X3))
        assertEquals(3 to 1, bilingualGrid(Constants.BILINGUAL_GRID_3X1))
        assertEquals(1 to 4, bilingualGrid(Constants.BILINGUAL_GRID_1X4))
        assertEquals(4 to 1, bilingualGrid(Constants.BILINGUAL_GRID_4X1))
        assertEquals(2 to 2, bilingualGrid(Constants.BILINGUAL_GRID_2X2))
        assertEquals(1 to 2, bilingualGrid(Constants.BILINGUAL_SIDE_BY_SIDE))
    }

    @Test
    fun `a value from a future or corrupted file reads as side by side`() {
        // Not an exception and not a blank screen: an unknown arrangement draws what side by side
        // has always drawn, so a document from a build with a grid this one lacks still presents.
        assertEquals(1 to 2, bilingualGrid("grid_9x9"))
        assertEquals(1 to 2, bilingualGrid(""))
    }

    /**
     * How many columns an arrangement puts a stack of translations in.
     *
     * The two original arrangements are counts rather than fixed grids -- side by side is one row
     * however many there are, top/bottom one column -- so they have to answer against the stack
     * size, and a grid has to ignore it. Six translations under Side-by-side is six columns; six
     * under 2x2 is two columns and three rows, not four translations and two dropped.
     */
    @Test
    fun `side by side and top bottom follow the stack, and a grid does not`() {
        assertEquals(6, bilingualColumns(Constants.BILINGUAL_SIDE_BY_SIDE, 6))
        assertEquals(2, bilingualColumns(Constants.BILINGUAL_SIDE_BY_SIDE, 2))
        assertEquals(1, bilingualColumns(Constants.BILINGUAL_TOP_BOTTOM, 6))

        assertEquals(2, bilingualColumns(Constants.BILINGUAL_GRID_2X2, 6))
        assertEquals(4, bilingualColumns(Constants.BILINGUAL_GRID_1X4, 6))
        assertEquals(1, bilingualColumns(Constants.BILINGUAL_GRID_4X1, 6))
        assertEquals(4, bilingualColumns(Constants.BILINGUAL_GRID_1X4, 2), "a grid ignores the stack size")
    }

    @Test
    fun `an empty stack still asks for one column, never zero`() {
        // A zero would divide the width by nothing on the way to a cell size.
        assertEquals(1, bilingualColumns(Constants.BILINGUAL_SIDE_BY_SIDE, 0))
        assertEquals(1, bilingualColumns(Constants.BILINGUAL_TOP_BOTTOM, 0))
        assertEquals(2, bilingualColumns("grid_9x9", 0))
    }

    @Test
    fun `a profile carries its own arrangement`() {
        val global = AppSettings(bibleSettings = BibleSettings())
        val profile = OutputProfile(bibleSettings = BibleSettings(bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE))
        assertEquals(
            Constants.BILINGUAL_SIDE_BY_SIDE,
            global.resolvedFor(profile).bibleSettings.bilingualLayout,
        )
        assertEquals(
            Constants.BILINGUAL_TOP_BOTTOM,
            global.bibleSettings.bilingualLayout,
            "the global document is untouched by one profile's choice",
        )
    }
}
