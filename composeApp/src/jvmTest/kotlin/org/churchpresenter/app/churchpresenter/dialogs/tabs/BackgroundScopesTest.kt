package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which part of the output each surface paints, and how tall its band is. */
class BackgroundScopesTest {

    @Test
    fun `every lower-third surface paints the band, the default one included`() {
        BackgroundScope.entries.filter { it.lowerThird }.forEach {
            assertEquals(BackgroundCoverage.BAND, it.coverage, "$it paints the band")
        }
    }

    @Test
    fun `every full-screen surface paints the whole output`() {
        BackgroundScope.entries.filterNot { it.lowerThird }.forEach {
            assertEquals(BackgroundCoverage.FULL_SCREEN, it.coverage, "$it paints the whole output")
        }
    }

    @Test
    fun `a content surface measures its own band, and a Default surface the taller of the two`() {
        val settings = AppSettings(
            bibleSettings = BibleSettings(lowerThirdHeightPercent = 25),
            songSettings = SongSettings(lowerThirdHeightPercent = 40),
        )
        assertEquals(0.25f, settings.bandFractionFor(BackgroundScope.BIBLE_LOWER_THIRD), 0.001f)
        assertEquals(0.40f, settings.bandFractionFor(BackgroundScope.SONG_LOWER_THIRD), 0.001f)
        assertEquals(
            0.40f,
            settings.bandFractionFor(BackgroundScope.DEFAULT_LOWER_THIRD),
            0.001f,
            "a Default surface sits behind both bands and takes the taller",
        )
    }
}
