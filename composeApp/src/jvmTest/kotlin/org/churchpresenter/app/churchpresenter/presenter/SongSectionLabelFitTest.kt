package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.ElementOffset
import org.churchpresenter.settings.SongSectionLabel
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Whether the lyrics' auto-fit leaves room for the section label.
 *
 * A **pre-existing bug**, found while adding the label's styling and fixed with it: the fit's
 * `reserved` accumulator counted the title row, the number row, the look-ahead spacer and the gaps
 * between language blocks -- and never the label, though it sits above the lyrics and takes height
 * from them exactly as the title row does. With the label on, the lyrics were sized for a box one
 * label taller than the one they got.
 *
 * **Tested as the decision rather than through a render**, deliberately. The first version of this
 * suite measured rendered line heights with the label on and off, and passed with the reservation
 * commented out -- because an enabled label physically occupies space in the column whatever the fit
 * believes, so the lyrics shrink either way. That test proved the label has a height, which was never
 * in doubt. This one fails the moment the reservation stops happening.
 */
class SongSectionLabelFitTest {

    private fun sections(vararg headers: String) =
        headers.map { LyricSection(header = it, type = Constants.SECTION_TYPE_VERSE, lines = listOf("grace")) }

    private val on = SongSectionLabel(enabled = true)

    @Test
    fun `an enabled label is reserved for`() {
        assertEquals("Verse 1", sectionLabelToReserve(on, sections("[Verse 1]"), isLowerThird = false))
    }

    @Test
    fun `a switched-off label costs the lyrics nothing`() {
        assertNull(sectionLabelToReserve(SongSectionLabel(enabled = false), sections("[Verse 1]"), false))
    }

    @Test
    fun `the longest label across the whole song is what is reserved`() {
        // The fit has to hold for every section the song will show, not just the one on screen --
        // the same reasoning the title and number reservations use.
        val song = sections("[Verse 1]", "[Chorus]", "[Verse 12 reprise]")
        assertEquals("Verse 12 reprise", sectionLabelToReserve(on, song, isLowerThird = false))
    }

    @Test
    fun `the reserved text is the stripped label, not the raw header`() {
        // Reserving for "[Verse 1]" would measure two characters that are never drawn.
        assertEquals("Chorus", sectionLabelToReserve(on, sections("{Chorus}"), isLowerThird = false))
    }

    @Test
    fun `a positioned label costs the lyrics nothing, like a cornered number`() {
        // Out of the flow and over the slide: reserving would shrink the lyrics for height the label
        // no longer takes from them.
        val positioned = SongSectionLabel(enabled = true, offset = ElementOffset(yPercent = 50))
        assertNull(sectionLabelToReserve(positioned, sections("[Verse 1]"), isLowerThird = false))
    }

    @Test
    fun `the band reserves for a label even when one is positioned`() {
        // Positioning is full screen only -- the band ignores the offset everywhere else too, so it
        // must not read one here and quietly stop reserving.
        val positioned = SongSectionLabel(enabled = true, offset = ElementOffset(yPercent = 50))
        assertEquals("Verse 1", sectionLabelToReserve(positioned, sections("[Verse 1]"), isLowerThird = true))
    }

    @Test
    fun `a song whose labels are all empty reserves nothing`() {
        val blank = listOf(LyricSection(header = "[]", type = "", lines = listOf("grace")))
        assertNull(sectionLabelToReserve(on, blank, isLowerThird = false))
    }

    @Test
    fun `no sections at all reserves nothing`() {
        assertNull(sectionLabelToReserve(on, emptyList(), isLowerThird = false))
    }
}
