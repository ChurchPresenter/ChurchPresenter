package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.ElementOffset
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Every title-slide element's own offset, per output — #615's second item.
 *
 * The slide could be styled freely and laid out exactly one way, a centred column top to bottom.
 * Five of its six elements had nowhere to store a position; the sixth, the number, already had the
 * richer corner-and-nudge pair and keeps it.
 *
 * What is actually worth pinning here is the *mapping*, because it is ten slots addressed by an enum
 * and an output flag and a wrong wire is invisible: an operator moves the composer and the CCLI line
 * shifts. So every element is round-tripped on both outputs, and each write is checked to have left
 * the other nine slots alone.
 */
class SongTitleSlideOffsetTest {

    private val positioned = listOf(
        SongStyleElement.TITLE,
        SongStyleElement.AUTHOR,
        SongStyleElement.COMPOSER,
        SongStyleElement.CCLI,
        SongStyleElement.TEMPO,
    )

    private val targets = listOf(SongStyleTarget.FULL_SCREEN, SongStyleTarget.LOWER_THIRD)

    @Test
    fun `nothing is positioned before anyone touches it`() {
        val settings = SongSettings()
        for ((element, target) in slots) {
            assertNull(settings.titleSlideOffset(element, target), "$element on $target")
        }
    }

    @Test
    fun `each element round-trips on each output`() {
        val offset = ElementOffset(xPercent = 20, yPercent = 80)
        for ((element, target) in slots) {
            val written = SongSettings().withTitleSlideOffset(element, target, offset)
            assertEquals(offset, written.titleSlideOffset(element, target), "$element on $target")
        }
    }

    /** Every element/output pair the slide can position, as one flat list. */
    private val slots = positioned.flatMap { element -> targets.map { element to it } }

    @Test
    fun `writing one element leaves every other slot alone`() {
        // The wiring test: ten slots, one enum and one flag, and a crossed wire moves the wrong line.
        for ((element, target) in slots) {
            val written = SongSettings().withTitleSlideOffset(element, target, ElementOffset(10, 90))
            for ((other, otherTarget) in slots.filterNot { it == element to target }) {
                assertNull(
                    written.titleSlideOffset(other, otherTarget),
                    "writing $element/$target also set $other/$otherTarget",
                )
            }
        }
    }

    @Test
    fun `the two outputs are independent`() {
        val full = ElementOffset(xPercent = 0, yPercent = 0)
        val band = ElementOffset(xPercent = 100, yPercent = 100)
        val settings = SongSettings()
            .withTitleSlideOffset(SongStyleElement.AUTHOR, SongStyleTarget.FULL_SCREEN, full)
            .withTitleSlideOffset(SongStyleElement.AUTHOR, SongStyleTarget.LOWER_THIRD, band)
        assertEquals(full, settings.titleSlideOffset(SongStyleElement.AUTHOR, SongStyleTarget.FULL_SCREEN))
        assertEquals(band, settings.titleSlideOffset(SongStyleElement.AUTHOR, SongStyleTarget.LOWER_THIRD))
    }

    @Test
    fun `clearing an element puts it back in the stack`() {
        val settings = SongSettings()
            .withTitleSlideOffset(SongStyleElement.CCLI, SongStyleTarget.FULL_SCREEN, ElementOffset(30, 30))
            .withTitleSlideOffset(SongStyleElement.CCLI, SongStyleTarget.FULL_SCREEN, null)
        assertNull(settings.titleSlideOffset(SongStyleElement.CCLI, SongStyleTarget.FULL_SCREEN))
    }

    @Test
    fun `the number has no offset slot, and asking for one changes nothing`() {
        // Not an oversight: it carries a corner plus a nudge inward from it, which is a richer
        // placement than an offset. A write must be a no-op rather than land in somebody else's slot.
        val settings = SongSettings()
        for (target in targets) {
            assertNull(settings.titleSlideOffset(SongStyleElement.TITLE_SLIDE_NUMBER, target))
            val written = settings.withTitleSlideOffset(
                SongStyleElement.TITLE_SLIDE_NUMBER,
                target,
                ElementOffset(10, 10),
            )
            assertSame(settings, written, "a write with no slot must return the settings untouched")
        }
    }

    @Test
    fun `an element the title slide never draws has no slot either`() {
        val settings = SongSettings()
        for (element in listOf(SongStyleElement.LYRICS, SongStyleElement.LOOK_AHEAD, SongStyleElement.NUMBER)) {
            assertNull(settings.titleSlideOffset(element, SongStyleTarget.FULL_SCREEN), "$element")
            assertSame(
                settings,
                settings.withTitleSlideOffset(element, SongStyleTarget.FULL_SCREEN, ElementOffset()),
                "$element",
            )
        }
    }

    @Test
    fun `the test handle names the element`() {
        // Distinct per element, which is the whole point: five switches on one pane otherwise.
        val tags = positioned.map { titleSlideOffsetTag(it) }
        assertEquals(tags.size, tags.toSet().size, "each element needs its own handle")
        assertEquals("title_slide_offset_author", titleSlideOffsetTag(SongStyleElement.AUTHOR))
    }
}
