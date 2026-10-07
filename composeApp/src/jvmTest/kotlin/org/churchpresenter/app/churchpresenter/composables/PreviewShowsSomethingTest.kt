package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.OutputLook
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether a preview tile reads as live: its own content, or an overlay this output draws over the
 * slide. The overlay half only counts when the tile is following the live slide -- a screen locked
 * to something else is not showing the overlay that went up over the slide.
 */
class PreviewShowsSomethingTest {

    private val overContent = OutputProfile(lowerThirdOverContent = true)

    private fun presenter(slide: Presenting = Presenting.NONE, vararg overlays: Presenting) =
        PresenterManager().apply {
            setPresentingMode(slide)
            overlays.forEach { setPresentingMode(it) }
        }

    @Test
    fun `a cleared output with nothing over it shows nothing`() {
        assertFalse(previewShowsSomething(presenter(), Presenting.NONE, OutputProfile()))
    }

    @Test
    fun `content the output shows is live`() {
        assertTrue(previewShowsSomething(presenter(Presenting.BIBLE), Presenting.BIBLE, OutputProfile()))
    }

    @Test
    fun `content the output hides is not live on its own`() {
        val hidesBible = OutputProfile(bibleMode = Constants.SONG_LANG_OFF)

        assertFalse(previewShowsSomething(presenter(Presenting.BIBLE), Presenting.BIBLE, hidesBible))
    }

    @Test
    fun `a lower third drawn over a cleared slide makes the tile live`() {
        val pm = presenter(Presenting.NONE, Presenting.LOWER_THIRD)

        assertTrue(previewShowsSomething(pm, Presenting.NONE, overContent))
    }

    @Test
    fun `a lower third over a slide this output hides still makes the tile live`() {
        val pm = presenter(Presenting.BIBLE, Presenting.LOWER_THIRD)
        val hidesBible = overContent.copy(bibleMode = Constants.SONG_LANG_OFF)

        assertTrue(previewShowsSomething(pm, Presenting.BIBLE, hidesBible))
    }

    @Test
    fun `an overlay this output does not draw graphics for is not live`() {
        val pm = presenter(Presenting.NONE, Presenting.LOWER_THIRD)
        val noGraphics = overContent.copy(look = OutputLook(graphics = false))

        assertFalse(previewShowsSomething(pm, Presenting.NONE, noGraphics))
    }

    @Test
    fun `an overlay that replaces the slide here is not counted as drawn over it`() {
        // The output takes the lower third in place of the slide, so the slide mode it is asked about
        // is not what it is showing.
        val pm = presenter(Presenting.NONE, Presenting.LOWER_THIRD)

        assertFalse(previewShowsSomething(pm, Presenting.NONE, OutputProfile(lowerThirdOverContent = false)))
    }

    @Test
    fun `a screen locked away from the live slide does not show the overlay over it`() {
        val pm = presenter(Presenting.NONE, Presenting.LOWER_THIRD)
        val look = OutputLook()
        val hidesPictures = overContent.copy(look = look.copy(media = look.media.copy(pictures = false)))

        assertFalse(previewShowsSomething(pm, Presenting.PICTURES, hidesPictures))
    }
}
