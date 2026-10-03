package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.lottiegen.band.BandContentKind
import org.churchpresenter.lottiegen.band.ReferencePlacement
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the band generator opens on: the band the outputs draw -- the first output's width at that
 * content's band height -- set in the content's own lower-third type, one slot per translation up to
 * two, with the reference or title where the outputs put it.
 */
class LottieBandSeedTest {

    private fun bible(vararg translations: BibleTranslationSettings) =
        AppSettings(bibleSettings = BibleSettings(translations = translations.toList()))

    @Test
    fun `a Bible band with one translation is a single slot, its reference where the outputs put it`() {
        val seed = lottieBandSeed(
            bible(
                BibleTranslationSettings(fileName = "kjv.spb", lowerThirdReferencePosition = Constants.POSITION_ABOVE),
            ),
            BackgroundScope.BIBLE_LOWER_THIRD,
        )
        assertEquals(BandContentKind.BIBLE, seed.kind)
        assertEquals(SlotLayout.SINGLE, seed.layout)
        assertEquals(ReferencePlacement.ABOVE, seed.referencePlacement)
        assertEquals("Arial", seed.previewFontFamily)
    }

    @Test
    fun `two translations sit side by side, and the reference defaults below`() {
        val seed = lottieBandSeed(
            bible(BibleTranslationSettings(fileName = "kjv.spb"), BibleTranslationSettings(fileName = "rst.spb")),
            BackgroundScope.BIBLE_LOWER_THIRD,
        )
        assertEquals(SlotLayout.SIDE_BY_SIDE, seed.layout)
        assertEquals(ReferencePlacement.BELOW, seed.referencePlacement)
    }

    @Test
    fun `with no translation the band is the right size and nothing else`() {
        val seed = lottieBandSeed(AppSettings(), BackgroundScope.BIBLE_LOWER_THIRD)
        assertEquals(BandContentKind.BIBLE, seed.kind)
        assertEquals(true, seed.canvasW > 0 && seed.canvasH > 0)
    }

    @Test
    fun `a song band takes the lyrics' type and puts the title where the outputs do`() {
        val above = lottieBandSeed(
            AppSettings(songSettings = SongSettings(titleLowerThirdPosition = Constants.ABOVE_VERSE)),
            BackgroundScope.SONG_LOWER_THIRD,
        )
        assertEquals(BandContentKind.SONG, above.kind)
        assertEquals(ReferencePlacement.ABOVE, above.referencePlacement)
        assertEquals("Amazing Grace", above.previewReference1)

        val below = lottieBandSeed(
            AppSettings(songSettings = SongSettings(titleLowerThirdPosition = Constants.BELOW_VERSE)),
            BackgroundScope.SONG_LOWER_THIRD,
        )
        assertEquals(ReferencePlacement.BELOW, below.referencePlacement)
    }
}
