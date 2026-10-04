package org.churchpresenter.presenter

import androidx.compose.ui.Modifier
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class ContentRegionRulesTest {

    private val narrow = ContentRegion(xOffsetPercent = 10, widthPercent = 60)

    @Test
    fun `a region that moves the background is applied to the whole output`() {
        assertNotSame(Modifier, Modifier.wholeOutputRegion(narrow))
    }

    @Test
    fun `a region that keeps the background full screen leaves the whole output alone`() {
        assertSame(Modifier, Modifier.wholeOutputRegion(narrow.copy(movesBackground = false)))
    }

    @Test
    fun `only a full-screen output places its text in a region that keeps the background`() {
        val textOnly = narrow.copy(movesBackground = false)
        assertEquals(textOnly, textOnly.textOnly(lowerThird = false))
        assertNull(textOnly.textOnly(lowerThird = true), "a lower third's band is its region already")
        assertNull(narrow.textOnly(lowerThird = false), "the whole output already moved")
    }

    @Test
    fun `the band height is the content's own, or the taller of the two`() {
        val settings = AppSettings(
            bibleSettings = BibleSettings(lowerThirdHeightPercent = 30),
            songSettings = SongSettings(lowerThirdHeightPercent = 20),
        )
        assertEquals(0.3f, settings.lowerThirdBandFraction(Presenting.BIBLE))
        assertEquals(0.2f, settings.lowerThirdBandFraction(Presenting.LYRICS))
        assertEquals(0.3f, settings.lowerThirdBandFraction(null))
        assertEquals(0.3f, settings.lowerThirdBandFraction(Presenting.PICTURES))
    }

    @Test
    fun `a region that is the whole output leaves the layout alone, and any change moves it`() {
        assertSame(Modifier, Modifier.contentRegion(ContentRegion()))
        assertNotSame(Modifier, Modifier.contentRegion(ContentRegion(yOffsetPercent = 5)))
        assertNotSame(Modifier, Modifier.contentRegion(ContentRegion(widthPercent = 80)))
        assertNotSame(Modifier, Modifier.contentRegion(ContentRegion(xOffsetPercent = 5)))
    }
}
