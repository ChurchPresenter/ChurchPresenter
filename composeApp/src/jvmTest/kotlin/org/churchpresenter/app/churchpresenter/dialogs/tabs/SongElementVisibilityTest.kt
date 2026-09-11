package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Which element has a Show setting, on which output, and whether the number and title land close
 * enough for their order to be a question.
 */
class SongElementVisibilityTest {

    private val full = SongStyleTarget.FULL_SCREEN
    private val band = SongStyleTarget.LOWER_THIRD

    private val settings = SongSettings(
        showNumber = Constants.FIRST_PAGE,
        showNumberLowerThird = Constants.EVERY_PAGE,
        titleDisplay = Constants.NONE,
        titleLowerThirdDisplay = Constants.FIRST_PAGE,
    )

    // ── Reading the setting ───────────────────────────────────────────────────

    @Test
    fun `the number reads its full-screen setting`() {
        assertEquals(Constants.FIRST_PAGE, settings.showFor(SongStyleElement.NUMBER, full))
    }

    @Test
    fun `the number reads the band's own setting`() {
        assertEquals(Constants.EVERY_PAGE, settings.showFor(SongStyleElement.NUMBER, band))
    }

    @Test
    fun `the title reads its full-screen setting`() {
        assertEquals(Constants.NONE, settings.showFor(SongStyleElement.TITLE, full))
    }

    @Test
    fun `the title reads the band's own setting`() {
        assertEquals(Constants.FIRST_PAGE, settings.showFor(SongStyleElement.TITLE, band))
    }

    @Test
    fun `the lyrics and the look-ahead lines have no such setting`() {
        for (element in listOf(SongStyleElement.LYRICS, SongStyleElement.LOOK_AHEAD, SongStyleElement.NEXT_SECTION)) {
            for (target in SongStyleTarget.entries) {
                assertNull(settings.showFor(element, target), "$element on $target must offer no Show control")
            }
        }
    }

    // ── Writing it back ───────────────────────────────────────────────────────

    @Test
    fun `writing the number's full-screen setting leaves the band's alone`() {
        val out = settings.withShow(SongStyleElement.NUMBER, full, Constants.NONE)
        assertEquals(Constants.NONE, out.showNumber)
        assertEquals(Constants.EVERY_PAGE, out.showNumberLowerThird)
    }

    @Test
    fun `writing the number's band setting leaves the full screen's alone`() {
        val out = settings.withShow(SongStyleElement.NUMBER, band, Constants.NONE)
        assertEquals(Constants.NONE, out.showNumberLowerThird)
        assertEquals(Constants.FIRST_PAGE, out.showNumber)
    }

    @Test
    fun `writing the title's full-screen setting leaves the band's alone`() {
        val out = settings.withShow(SongStyleElement.TITLE, full, Constants.EVERY_PAGE)
        assertEquals(Constants.EVERY_PAGE, out.titleDisplay)
        assertEquals(Constants.FIRST_PAGE, out.titleLowerThirdDisplay)
    }

    @Test
    fun `writing the title's band setting leaves the full screen's alone`() {
        val out = settings.withShow(SongStyleElement.TITLE, band, Constants.EVERY_PAGE)
        assertEquals(Constants.EVERY_PAGE, out.titleLowerThirdDisplay)
        assertEquals(Constants.NONE, out.titleDisplay)
    }

    @Test
    fun `writing an element with no such setting changes nothing`() {
        for (element in listOf(SongStyleElement.LYRICS, SongStyleElement.LOOK_AHEAD, SongStyleElement.NEXT_SECTION)) {
            assertSame(
                settings,
                settings.withShow(element, full, Constants.EVERY_PAGE),
                "$element has nowhere to write and must hand back what it was given",
            )
        }
    }

    @Test
    fun `every element and output round-trips through its own field`() {
        for (element in SongStyleElement.entries) {
            for (target in SongStyleTarget.entries) {
                val written = settings.withShow(element, target, Constants.EVERY_PAGE)
                val readBack = written.showFor(element, target)
                if (settings.showFor(element, target) == null) {
                    assertNull(readBack, "$element on $target still has no setting")
                } else {
                    assertEquals(Constants.EVERY_PAGE, readBack, "$element on $target must read back what was written")
                }
            }
        }
    }

    // ── Whether their order is a question ─────────────────────────────────────

    @Test
    fun `a number and title in the same place share a position`() {
        val together = SongSettings(
            songNumberCorner = Constants.NONE,
            songNumberPosition = Constants.ABOVE_VERSE,
            titlePosition = Constants.ABOVE_VERSE,
            songNumberHorizontalAlignment = Constants.CENTER,
            titleHorizontalAlignment = Constants.CENTER,
        )
        assertTrue(together.numberSharesTitlePosition(full))
    }

    @Test
    fun `a number above and a title below do not`() {
        val apart = SongSettings(
            songNumberCorner = Constants.NONE,
            songNumberPosition = Constants.ABOVE_VERSE,
            titlePosition = Constants.BELOW_VERSE,
            songNumberHorizontalAlignment = Constants.CENTER,
            titleHorizontalAlignment = Constants.CENTER,
        )
        assertFalse(apart.numberSharesTitlePosition(full))
    }

    @Test
    fun `the same row but different alignments do not`() {
        val apart = SongSettings(
            songNumberCorner = Constants.NONE,
            songNumberPosition = Constants.ABOVE_VERSE,
            titlePosition = Constants.ABOVE_VERSE,
            songNumberHorizontalAlignment = Constants.LEFT,
            titleHorizontalAlignment = Constants.RIGHT,
        )
        assertFalse(apart.numberSharesTitlePosition(full))
    }

    @Test
    fun `a cornered number is never in the title's row, which is how one ships`() {
        val cornered = SongSettings(
            songNumberCorner = Constants.TOP_LEFT,
            songNumberPosition = Constants.ABOVE_VERSE,
            titlePosition = Constants.ABOVE_VERSE,
            songNumberHorizontalAlignment = Constants.CENTER,
            titleHorizontalAlignment = Constants.CENTER,
        )
        assertFalse(cornered.numberSharesTitlePosition(full), "a corner is drawn over the slide, not in a row")
    }

    @Test
    fun `the band answers from its own pair of fields`() {
        val bandTogether = SongSettings(
            songNumberCorner = Constants.NONE,
            songNumberLowerThirdCorner = Constants.NONE,
            songNumberLowerThirdPosition = Constants.BELOW_VERSE,
            titleLowerThirdPosition = Constants.BELOW_VERSE,
            songNumberLowerThirdHorizontalAlignment = Constants.LEFT,
            titleLowerThirdHorizontalAlignment = Constants.LEFT,
            songNumberPosition = Constants.ABOVE_VERSE,
            titlePosition = Constants.BELOW_VERSE,
        )
        assertTrue(bandTogether.numberSharesTitlePosition(band), "the full screen's disagreement is not the band's")
        assertFalse(bandTogether.numberSharesTitlePosition(full))
    }

    @Test
    fun `a cornered band number is not in the band's title row either`() {
        val cornered = SongSettings(
            songNumberLowerThirdCorner = Constants.BOTTOM_RIGHT,
            songNumberLowerThirdPosition = Constants.BELOW_VERSE,
            titleLowerThirdPosition = Constants.BELOW_VERSE,
            songNumberLowerThirdHorizontalAlignment = Constants.LEFT,
            titleLowerThirdHorizontalAlignment = Constants.LEFT,
        )
        assertFalse(cornered.numberSharesTitlePosition(band))
    }

    // ── The preview's temporary unhide ────────────────────────────────────────

    @Test
    fun `an element set to None is shown for the preview`() {
        val preview = settings.shownForPreview(SongStyleElement.TITLE, full)
        assertEquals(Constants.EVERY_PAGE, preview.titleDisplay, "the preview must draw what is being styled")
    }

    @Test
    fun `the stored setting is untouched by that`() {
        settings.shownForPreview(SongStyleElement.TITLE, full)
        assertEquals(Constants.NONE, settings.titleDisplay, "the Show control still decides the real output")
    }

    @Test
    fun `an element already shown is handed back unchanged`() {
        assertSame(settings, settings.shownForPreview(SongStyleElement.NUMBER, full))
    }

    @Test
    fun `an element with no Show setting is handed back unchanged`() {
        assertSame(settings, settings.shownForPreview(SongStyleElement.LYRICS, full))
    }

    @Test
    fun `unhiding one element leaves the other alone`() {
        val preview = settings.shownForPreview(SongStyleElement.TITLE, full)
        assertEquals(Constants.FIRST_PAGE, preview.showNumber, "the number's own setting must not move")
        assertEquals(Constants.FIRST_PAGE, preview.titleLowerThirdDisplay, "nor the band's title")
    }

    // ── The title slide ───────────────────────────────────────────────────────

    @Test
    fun `every title-slide element reads and writes its own switch`() {
        TITLE_SLIDE_ELEMENTS.forEach { element ->
            val on = SongSettings().withShownOnTitleSlide(element, true)
            val off = on.withShownOnTitleSlide(element, false)
            assertEquals(true, on.shownOnTitleSlide(element), "$element on")
            assertEquals(false, off.shownOnTitleSlide(element), "$element off")
            // Its neighbours are untouched: only this element's switch moved.
            TITLE_SLIDE_ELEMENTS.filter { it != element }.forEach { other ->
                assertEquals(on.shownOnTitleSlide(other), off.shownOnTitleSlide(other), "$other beside $element")
            }
        }
    }

    @Test
    fun `an element the title slide never draws has no switch`() {
        listOf(SongStyleElement.LYRICS, SongStyleElement.LOOK_AHEAD, SongStyleElement.NEXT_SECTION).forEach {
            assertEquals(null, SongSettings().shownOnTitleSlide(it), "$it")
            assertEquals(SongSettings(), SongSettings().withShownOnTitleSlide(it, false), "$it is a no-op to write")
        }
    }

    @Test
    fun `the title slide's defaults keep what it always showed`() {
        val s = SongSettings()
        assertEquals(true, s.shownOnTitleSlide(SongStyleElement.NUMBER))
        assertEquals(true, s.shownOnTitleSlide(SongStyleElement.TITLE))
        assertEquals(true, s.shownOnTitleSlide(SongStyleElement.AUTHOR))
        assertEquals(true, s.shownOnTitleSlide(SongStyleElement.COMPOSER))
        assertEquals(false, s.shownOnTitleSlide(SongStyleElement.CCLI))
        assertEquals(false, s.shownOnTitleSlide(SongStyleElement.TEMPO))
    }

    @Test
    fun `the preview turns a hidden element on without touching one already shown`() {
        val hidden = SongSettings(titleSlideShowTempo = false)
        assertEquals(true, hidden.shownOnTitleSlideForPreview(SongStyleElement.TEMPO).titleSlideShowTempo)
        assertEquals(hidden, hidden.shownOnTitleSlideForPreview(SongStyleElement.TITLE))
        assertEquals(hidden, hidden.shownOnTitleSlideForPreview(SongStyleElement.LYRICS))
    }
}
