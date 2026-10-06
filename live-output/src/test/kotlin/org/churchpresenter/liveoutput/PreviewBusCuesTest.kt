package org.churchpresenter.liveoutput

import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PreviewBusCuesTest {

    private val program = PresenterManager(showPresenterWindowInitially = false)
    private val bus = program.previewBus
    private val preview = bus.manager

    private fun pictureOnAir(path: String) {
        program.setSelectedImagePath(path)
        program.setPresentingMode(Presenting.PICTURES)
        bus.setEnabled(true)
    }

    @Test
    fun `a picture from the folder on air is a step, any other is cued`() {
        pictureOnAir("/service/pictures/01.png")
        assertSame(program, bus.forPicture("/service/pictures/02.png"))
        assertSame(preview, bus.forPicture("/other/01.png"))
        assertSame(preview, bus.forPicture(null))
    }

    @Test
    fun `a picture with no path on air makes every picture new`() {
        program.setPresentingMode(Presenting.PICTURES)
        bus.setEnabled(true)
        assertSame(preview, bus.forPicture("/service/pictures/02.png"))
    }

    @Test
    fun `a song push with nothing cued runs its action now`() {
        bus.setEnabled(true)
        var ran = false
        bus.onAir(Presenting.LYRICS) { ran = true }
        assertTrue(ran)
    }

    @Test
    fun `pictures cued with no path still release what waited for them on Take`() {
        bus.setEnabled(true)
        preview.setPresentingMode(Presenting.PICTURES)
        var ran = false
        bus.onAir(Presenting.PICTURES) { ran = true }
        assertFalse(ran)

        bus.take()
        assertTrue(ran)
        assertTrue(program.isLive(Presenting.PICTURES))
        assertNull(program.selectedImagePath.value)
    }

    @Test
    fun `a presentation cued with no slide goes on air with no playback`() {
        bus.setEnabled(true)
        preview.setPresentingMode(Presenting.PRESENTATION)
        var ran = false
        bus.onAir(Presenting.PRESENTATION) { ran = true }

        bus.take()
        assertTrue(ran)
        assertTrue(program.isLive(Presenting.PRESENTATION))
        assertNull(program.liveSlide.value)
        assertNull(bus.cuedPlayback)
    }

    @Test
    fun `an action for an item replaced on Preview never runs`() {
        bus.setEnabled(true)
        preview.setPresentingMode(Presenting.ANNOUNCEMENTS)
        var ran = false
        bus.onAir(Presenting.ANNOUNCEMENTS) { ran = true }
        preview.setPresentingMode(Presenting.NONE)
        preview.setPresentingMode(Presenting.PICTURES)

        bus.take()
        assertFalse(ran)
    }

    @Test
    fun `a push of no verses while scripture is on air is cued`() {
        program.setSelectedVerses(
            listOf(SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16, verseText = "x")),
        )
        program.setPresentingMode(Presenting.BIBLE)
        bus.setEnabled(true)

        assertSame(preview, bus.forVerses(emptyList()))
        assertEquals(Presenting.BIBLE, program.slideContent.value)
    }
}
