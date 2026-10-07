package org.churchpresenter.app.churchpresenter

import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.churchpresenter.liveoutput.LiveSlide
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.presentationengine.model.EffectInterval
import org.churchpresenter.presentationengine.model.EffectSpec
import org.churchpresenter.presentationengine.model.Fidelity
import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.presentationengine.model.RectPt
import org.churchpresenter.presentationengine.model.Slide
import org.churchpresenter.presentationengine.model.Step
import org.churchpresenter.presentationengine.model.Timeline
import org.churchpresenter.presentationengine.model.pdfDeck
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The clicker, the live-slide push and Clear, driven over a real [PresenterManager] and a real
 * [PresentationViewModel] -- what the main window does with them, without the main window.
 */
class MainDesktopOutputActionsTest {

    private lateinit var dir: File
    private lateinit var home: File
    private var realHome: String? = null
    private lateinit var presenter: PresenterManager
    private lateinit var presentations: PresentationViewModel

    private var nextSent = 0
    private var previousSent = 0
    private val link get() = InstanceLinkBridge(
        sendNextSlide = { nextSent++ },
        sendPreviousSlide = { previousSent++ },
    )

    @BeforeTest
    fun setUp() {
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-output-actions-home").toFile()
        System.setProperty("user.home", home.absolutePath)
        dir = Files.createTempDirectory("cp-output-actions").toFile()
        presenter = PresenterManager()
        presentations = PresentationViewModel()
    }

    @AfterTest
    fun tearDown() {
        runCatching { presenter.clearPresentationPlayback() }
        runCatching { presentations.dispose() }
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
        dir.deleteRecursively()
    }

    private fun slides(count: Int) {
        val png = File(dir, "slide.png").apply {
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", this)
        }
        presentations.slideFiles.addAll(List(count) { png })
    }

    private fun pdf(name: String, pages: Int): File = File(dir, name).also { file ->
        PDDocument().use { doc ->
            repeat(pages) { doc.addPage(PDPage()) }
            doc.save(file)
        }
    }

    /** A deck whose every slide has one build step, so a click can step it. */
    private fun animatedDeck(): Deck {
        val bounds = RectPt(0.0, 0.0, 720.0, 405.0)
        val layer = LayerSpec.Shape("title", 0, bounds, shapeIndex = 0, initiallyVisible = false)
        val build = Timeline(listOf(Step(listOf(EffectInterval("title", EffectSpec.Appear(), 0, 0)))))
        return pdfDeck(
            pdf("animated.pdf", 2),
            List(2) {
                Slide(it, "", transition = null, layers = listOf(layer), timeline = build, fidelity = Fidelity.NATIVE)
            },
        )
    }

    /** A deck with no timing at all: the player never takes it, so a click never steps it. */
    private fun staticDeck(): Deck = pdfDeck(
        pdf("static.pdf", 1),
        listOf(Slide(0, "", transition = null, layers = emptyList(), timeline = null, fidelity = Fidelity.NATIVE)),
    )

    /**
     * Puts [deck]'s first slide on the player and waits for it to load: the player rasterizes a slide
     * off the caller's thread and can step it only once that lands. The probe that it has is a step,
     * which is then undone, so the slide is left unbuilt.
     */
    private fun showSteppable(deck: Deck) {
        presenter.setPresentingMode(Presenting.PRESENTATION)
        presenter.presentationShowSlide(deck, 0)
        val deadline = System.nanoTime() + LOAD_TIMEOUT_NANOS
        while (!presenter.advancePresentationStep(deck, 0)) {
            check(System.nanoTime() < deadline) { "the animated slide never loaded" }
            Thread.onSpinWait()
        }
        assertTrue(presenter.rewindPresentationStep(deck, 0))
    }

    // ── The clicker ─────────────────────────────────────────────────────────────

    @Test
    fun `a click on a slide with a build left steps the build and leaves the slide alone`() = runBlocking<Unit> {
        val deck = animatedDeck()
        slides(2)
        showSteppable(deck)

        clickPresentationSlide(forward = true, presentations, presenter, link, deck)

        assertEquals(0, presentations.selectedSlideIndex)
        assertEquals(0, nextSent, "a build step is not a slide change, so nothing is sent on")
        assertNull(presenter.selectedSlide.value, "and no slide is pushed")
        assertFalse(presenter.advancePresentationStep(deck, 0), "the one build step was taken")
    }

    @Test
    fun `a click back on a built slide undoes the build and leaves the slide alone`() = runBlocking<Unit> {
        val deck = animatedDeck()
        slides(2)
        showSteppable(deck)
        assertTrue(presenter.advancePresentationStep(deck, 0))

        clickPresentationSlide(forward = false, presentations, presenter, link, deck)

        assertEquals(0, previousSent)
        assertNull(presenter.selectedSlide.value)
        assertTrue(presenter.advancePresentationStep(deck, 0), "the build was undone, so it can be taken again")
    }

    @Test
    fun `a click on a deck that does not step moves to the next slide and pushes it`() = runBlocking<Unit> {
        slides(3)
        presenter.setPresentingMode(Presenting.PRESENTATION)

        clickPresentationSlide(forward = true, presentations, presenter, link, staticDeck())

        assertEquals(1, presentations.selectedSlideIndex)
        assertEquals(1, nextSent, "the follower is told to move too")
        assertEquals(LiveSlide(null, 1), presenter.liveSlide.value)
        assertNotNull(presenter.selectedSlide.value)
        assertNotNull(presenter.nextSlide.value, "slide 3 is staged for the stage monitor")
    }

    @Test
    fun `a click back with no deck moves to the previous slide and pushes it`() = runBlocking<Unit> {
        slides(3)
        presentations.selectSlide(2)
        presenter.setPresentingMode(Presenting.PRESENTATION)

        clickPresentationSlide(forward = false, presentations, presenter, link)

        assertEquals(1, presentations.selectedSlideIndex)
        assertEquals(1, previousSent)
        assertEquals(0, nextSent)
        assertEquals(LiveSlide(null, 1), presenter.liveSlide.value)
        assertNotNull(presenter.nextSlide.value)
    }

    @Test
    fun `a click forward with no deck moves on even when nothing is live, and pushes nothing`() = runBlocking<Unit> {
        slides(2)

        clickPresentationSlide(forward = true, presentations, presenter, link)

        assertEquals(1, presentations.selectedSlideIndex)
        assertEquals(1, nextSent)
        assertNull(presenter.liveSlide.value)
        assertNull(presenter.selectedSlide.value)
    }

    // ── The live-slide push ─────────────────────────────────────────────────────

    @Test
    fun `nothing is pushed while presentation is not the live content`() = runBlocking<Unit> {
        slides(2)
        presenter.setPresentingMode(Presenting.BIBLE)

        pushPresentationSlideIfLive(presentations, presenter)

        assertNull(presenter.selectedSlide.value)
        assertNull(presenter.liveSlide.value)
    }

    @Test
    fun `nothing is pushed when the selected slide is not in the deck`() = runBlocking<Unit> {
        presenter.setPresentingMode(Presenting.PRESENTATION)

        pushPresentationSlideIfLive(presentations, presenter)

        assertNull(presenter.selectedSlide.value)
        assertNull(presenter.liveSlide.value)
    }

    @Test
    fun `the last slide is pushed with nothing after it, and no notes replace the old ones`() = runBlocking<Unit> {
        slides(2)
        presentations.selectSlide(1)
        presenter.setPresentingMode(Presenting.PRESENTATION)
        presenter.setPresenterNotes("left from the slide before")

        pushPresentationSlideIfLive(presentations, presenter)

        assertNotNull(presenter.selectedSlide.value)
        assertNull(presenter.nextSlide.value)
        assertEquals(LiveSlide(null, 1), presenter.liveSlide.value)
        assertEquals("", presenter.presenterNotes.value)
    }

    @Test
    fun `a push with no deck stops an animated slide left playing`() = runBlocking<Unit> {
        val deck = animatedDeck()
        slides(2)
        showSteppable(deck)

        pushPresentationSlideIfLive(presentations, presenter)

        assertNotNull(presenter.selectedSlide.value)
        assertFalse(presenter.advancePresentationStep(deck, 0), "the old deck's player is gone")
    }

    @Test
    fun `a push of a loaded deck names its file and points the player at the slide`() = runBlocking<Unit> {
        val file = pdf("sermon.pdf", 2)
        presentations.addPresentation(file)
        val deadline = System.nanoTime() + LOAD_TIMEOUT_NANOS
        while (presentations.deck == null || presentations.slideFiles.size < 2 || presentations.isLoading) {
            check(System.nanoTime() < deadline) { "the deck never loaded" }
            Thread.onSpinWait()
        }
        presenter.setPresentingMode(Presenting.PRESENTATION)

        pushPresentationSlideIfLive(presentations, presenter)

        assertEquals(LiveSlide("sermon.pdf", 0), presenter.liveSlide.value)
        assertNotNull(presenter.nextSlide.value)
        assertFalse(
            presenter.advancePresentationStep(assertNotNull(presentations.deck), 0),
            "a PDF has no builds, so the player was cleared rather than started",
        )
    }

    // ── Clear ───────────────────────────────────────────────────────────────────

    /** One profile per [modes] entry, and one screen assignment pointing at each in order. */
    private fun projectionOf(vararg modes: String): ProjectionSettings {
        val profiles = modes.mapIndexed { index, mode -> OutputProfile(id = "p$index", displayMode = mode) }
        return ProjectionSettings(
            outputProfiles = profiles,
            screenAssignments = profiles.map { ScreenAssignment(activeProfileId = it.id) },
        )
    }

    private val stageMonitorsAroundAScreen = projectionOf(
        Constants.DISPLAY_MODE_STAGE_MONITOR,
        Constants.DISPLAY_MODE_FULLSCREEN,
        Constants.DISPLAY_MODE_STAGE_MONITOR,
    )

    private fun lockEveryScreen() = repeat(3) { presenter.setScreenLock(it, Presenting.ANNOUNCEMENTS) }

    @Test
    fun `clear pauses the media, clears the output, tells the follower and frees the stage monitors`() {
        presenter.setPresentingMode(Presenting.BIBLE)
        lockEveryScreen()
        val steps = mutableListOf<String>()
        val bridge = InstanceLinkBridge(
            sendClear = { steps += "sent, cleared=${presenter.clearDisplayRequested.value}" },
        )

        clearAllOutputs(
            presenter,
            stageMonitorsAroundAScreen,
            bridge,
            pauseMedia = { steps += "paused, cleared=${presenter.clearDisplayRequested.value}" },
        )

        assertEquals(listOf("paused, cleared=false", "sent, cleared=true"), steps)
        assertTrue(presenter.clearDisplayRequested.value)
        assertEquals(mapOf(1 to Presenting.ANNOUNCEMENTS), presenter.screenLocks.value, "a full screen keeps its lock")
    }

    @Test
    fun `clear with no media player and no link still clears and frees the stage monitors`() {
        presenter.setPresentingMode(Presenting.LYRICS)
        lockEveryScreen()

        clearAllOutputs(presenter, stageMonitorsAroundAScreen, InstanceLinkBridge(), pauseMedia = null)

        assertTrue(presenter.clearDisplayRequested.value)
        assertEquals(mapOf(1 to Presenting.ANNOUNCEMENTS), presenter.screenLocks.value)
    }

    private companion object {
        const val LOAD_TIMEOUT_NANOS = 10_000_000_000L
    }
}
