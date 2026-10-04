package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Preview mode: a new picture folder, lower third or announcement is cued on the Preview bus and
 * goes on air with Take; stepping through what is already on air, and everything else, still goes
 * straight out. Off, nothing reaches Preview at all.
 */
class PreviewBusTest {

    private val program = PresenterManager(showPresenterWindowInitially = false)
    private val bus = program.previewBus
    private val preview = bus.manager

    private val folderA = Files.createTempDirectory("cp-preview-a").toFile()
    private val folderB = Files.createTempDirectory("cp-preview-b").toFile()
    private fun picture(folder: File, name: String) = File(folder, name).absolutePath

    private fun on() = bus.setEnabled(true)

    private fun lowerThird(name: String = "Pastor") = bus.showLowerThird("{\"nm\":\"$name\"}", true, 2f, 3_000L, name)

    // ── Off ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `off, everything goes straight to air as it always did`() {
        program.slidesOutput.setSelectedImagePath(picture(folderA, "1.png"))
        bus.present(Presenting.PICTURES)
        lowerThird()
        cueOrSetAnnouncementText(program, "Welcome")

        assertEquals(Presenting.PICTURES, program.presentingMode.value)
        assertTrue(program.isLive(Presenting.LOWER_THIRD))
        assertEquals("Welcome", program.announcementText.value)
        assertFalse(preview.anythingLive, "nothing reaches Preview while preview mode is off")
        assertFalse(bus.anythingCued)
    }

    @Test
    fun `turning preview mode off empties Preview`() {
        on()
        lowerThird()
        bus.setEnabled(false)
        assertFalse(preview.anythingLive)
        bus.setEnabled(false)
        assertFalse(bus.enabled.value)
    }

    // ── Cueing ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a lower third is cued and Program is left alone`() {
        program.setPresentingMode(Presenting.BIBLE)
        on()
        lowerThird()
        assertTrue(bus.isCued(Presenting.LOWER_THIRD))
        assertEquals("Pastor", preview.currentLowerThirdName.value)
        assertFalse(program.isLive(Presenting.LOWER_THIRD))
        assertEquals(Presenting.BIBLE, program.lastLive.value)
    }

    @Test
    fun `a text announcement is cued, through the tab and the schedule alike`() {
        on()
        program.announcementsOutput.setAnnouncementText("Coffee after")
        program.announcementsOutput.setPresentingMode(Presenting.ANNOUNCEMENTS)
        assertTrue(bus.isCued(Presenting.ANNOUNCEMENTS))
        assertEquals("Coffee after", preview.announcementText.value)
        assertFalse(program.isLive(Presenting.ANNOUNCEMENTS))
        assertEquals("", program.announcementText.value)
    }

    @Test
    fun `a timer's announcement goes straight to air, where its ticker runs`() {
        on()
        program.announcementsOutput.setAnnouncementTickerLive(true)
        program.announcementsOutput.setAnnouncementText("05:00")
        bus.present(Presenting.ANNOUNCEMENTS)
        assertTrue(program.isLive(Presenting.ANNOUNCEMENTS))
        assertEquals("05:00", program.announcementText.value)
        assertFalse(bus.anythingCued)
    }

    @Test
    fun `a new folder of pictures is cued, and the tab steps it on Preview`() {
        on()
        val slides = program.slidesOutput
        slides.setSelectedImagePath(picture(folderA, "1.png"))
        slides.setNextImagePath(picture(folderA, "2.png"))
        slides.setPresentingMode(Presenting.PICTURES)

        assertTrue(bus.isCued(Presenting.PICTURES))
        assertTrue(slides.picturesCued)
        assertEquals(Presenting.NONE, program.presentingMode.value)
        assertEquals(picture(folderA, "2.png"), preview.nextImagePath.value)

        slides.setSelectedImagePath(picture(folderA, "2.png"))
        assertEquals(picture(folderA, "2.png"), preview.selectedImagePath.value)
        assertEquals(null, program.selectedImagePath.value)
    }

    @Test
    fun `the next picture in the folder on air is a step, and goes straight out`() {
        program.slidesOutput.setSelectedImagePath(picture(folderA, "1.png"))
        program.setPresentingMode(Presenting.PICTURES)
        on()

        program.slidesOutput.setSelectedImagePath(picture(folderA, "2.png"))
        program.slidesOutput.setNextImagePath(picture(folderA, "3.png"))
        assertEquals(picture(folderA, "2.png"), program.selectedImagePath.value)
        assertEquals(picture(folderA, "3.png"), program.nextImagePath.value)
        assertFalse(bus.anythingCued)

        program.slidesOutput.setSelectedImagePath(picture(folderB, "1.png"))
        assertTrue(bus.isCued(Presenting.PICTURES), "another folder is a new item")
        assertEquals(picture(folderA, "2.png"), program.selectedImagePath.value)
    }

    @Test
    fun `a schedule row's pictures are cued before the picture itself arrives`() {
        on()
        bus.present(Presenting.PICTURES)
        assertTrue(bus.isCued(Presenting.PICTURES))
        bus.present(Presenting.PICTURES)
        program.slidesOutput.setSelectedImagePath(picture(folderB, "1.png"))
        assertEquals(picture(folderB, "1.png"), preview.selectedImagePath.value)
        assertEquals(Presenting.NONE, program.presentingMode.value)
    }

    @Test
    fun `pictures already on air stay there when their row is gone to again`() {
        program.setPresentingMode(Presenting.PICTURES)
        on()
        bus.present(Presenting.PICTURES)
        assertFalse(bus.anythingCued)
        assertEquals(Presenting.PICTURES, program.presentingMode.value)
    }

    @Test
    fun `what is not cued yet goes straight to air`() {
        on()
        bus.present(Presenting.BIBLE)
        program.slidesOutput.setPresentingMode(Presenting.PRESENTATION)
        assertEquals(Presenting.PRESENTATION, program.presentingMode.value)
        assertFalse(bus.anythingCued)
    }

    @Test
    fun `a picture transition is set on both buses, so Take plays the same one`() {
        program.slidesOutput.setAnimationType(AnimationType.SLIDE_LEFT)
        program.slidesOutput.setTransitionDuration(700)
        assertEquals(AnimationType.SLIDE_LEFT, preview.animationType.value)
        assertEquals(700, preview.transitionDuration.value)
        assertEquals(700, program.transitionDuration.value)
    }

    // ── Take ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Take puts the cued lower third on air and empties Preview`() {
        program.setPresentingMode(Presenting.LYRICS)
        on()
        lowerThird("Welcome")
        bus.take()

        assertTrue(program.isLive(Presenting.LOWER_THIRD))
        assertEquals(Presenting.LYRICS, program.presentingMode.value, "an overlay leaves the slide")
        assertEquals("Welcome", program.currentLowerThirdName.value)
        assertEquals("{\"nm\":\"Welcome\"}", program.lottieJsonContent.value)
        assertTrue(program.lottiePauseAtFrame.value)
        assertEquals(2f, program.lottiePauseFrame.value)
        assertEquals(3_000L, program.lottiePauseDurationMs.value)
        assertFalse(preview.anythingLive)
        assertTrue(program.showPresenterWindow.value)
    }

    @Test
    fun `Take puts the cued pictures on air`() {
        on()
        program.slidesOutput.setSelectedImagePath(picture(folderA, "1.png"))
        program.slidesOutput.setNextImagePath(picture(folderA, "2.png"))
        bus.take()
        assertEquals(Presenting.PICTURES, program.presentingMode.value)
        assertEquals(picture(folderA, "1.png"), program.selectedImagePath.value)
        assertEquals(picture(folderA, "2.png"), program.nextImagePath.value)
        assertFalse(bus.anythingCued)
    }

    @Test
    fun `Take puts the slide up first, then the overlays cued with it`() {
        program.setPresentingMode(Presenting.LOWER_THIRD)
        on()
        program.slidesOutput.setSelectedImagePath(picture(folderA, "1.png"))
        cueOrSetAnnouncementText(program, "Offering")
        preview.setPresentingMode(Presenting.ANNOUNCEMENTS)
        bus.take()

        assertEquals(Presenting.PICTURES, program.presentingMode.value)
        assertEquals(
            setOf(Presenting.ANNOUNCEMENTS),
            program.overlays.value,
            "the pictures took the old lower third down",
        )
        assertEquals("Offering", program.announcementText.value)
        assertEquals(Presenting.ANNOUNCEMENTS, program.lastLive.value)
    }

    @Test
    fun `Take with nothing cued changes nothing`() {
        program.setPresentingMode(Presenting.BIBLE)
        bus.take()
        on()
        bus.take()
        assertEquals(Presenting.BIBLE, program.presentingMode.value)
        assertFalse(program.showPresenterWindow.value)
    }

    @Test
    fun `what Preview holds but cannot cue is not taken`() {
        on()
        preview.setPresentingMode(Presenting.BIBLE)
        bus.take()
        assertEquals(Presenting.NONE, program.presentingMode.value)
        assertFalse(preview.anythingLive)
    }

    @Test
    fun `the setting turns preview mode on`() {
        assertTrue(AppSettings().withPreviewMode(true).projectionSettings.previewModeEnabled)
        assertFalse(AppSettings().projectionSettings.previewModeEnabled, "off by default")
    }

    @Test
    fun `a new item of a kind Preview cannot cue goes to Program`() {
        on()
        assertSame(program, bus.forNewItem(Presenting.MEDIA))
        assertSame(preview, bus.forNewItem(Presenting.LOWER_THIRD))
    }
}
