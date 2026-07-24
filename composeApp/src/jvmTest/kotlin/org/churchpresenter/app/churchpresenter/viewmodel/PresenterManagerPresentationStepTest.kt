package org.churchpresenter.app.churchpresenter.viewmodel

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.churchpresenter.app.churchpresenter.presenter.PresentationPlayer
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import presentation.engine.LoadResult
import presentation.engine.PresentationLoader
import presentation.engine.model.Deck
import presentation.engine.model.Slide
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Animated-presentation step navigation on [PresenterManager].
 *
 * The step keys (advance/rewind) are identity- and visibility-guarded: they act only when the live
 * player is showing exactly the deck+slide the caller means AND that content is actually on an
 * output — otherwise the key must fall through to plain slide navigation instead of being silently
 * eaten by a player the operator can't see. [steppablePlayer] is the guard that decides this.
 *
 * A static PDF deck (never "animated") and a real player parked at its initial slide index (-1,
 * before any [PresentationPlayer.showSlide]) exercise the guard's branches without needing a real
 * rasterized frame; the player is closed in teardown.
 */
class PresenterManagerPresentationStepTest {

    private lateinit var dir: File
    private val managers = mutableListOf<PresenterManager>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-presenter-step-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        managers.forEach { runCatching { it.clearPresentationPlayback() } }
        managers.clear()
        dir.deleteRecursively()
    }

    private fun manager() = PresenterManager().also { managers.add(it) }

    /** A real, statically-rendered deck: a one-page PDF has no timelines or transitions. */
    private fun staticDeck(name: String = "deck.pdf"): Deck {
        val file = File(dir, name)
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(file)
        }
        return (PresentationLoader.load(file) as LoadResult.Success).deck
    }

    /**
     * An animated deck and a stand-in player, as a LAST RESORT: an animated `Deck` needs a real
     * PPTX/Keynote with a transition and a `DeckRasterizer` that renders (graphics, slow, flaky
     * headless), and a real `PresentationPlayer` cannot be built from a mock deck (its constructor
     * rasterizes). So `presentationShowSlide`'s animated idempotence/reuse branches are reached with
     * mocks — but the assertions are on the real outcome (which player instance ends up live, and
     * whether it was torn down), not merely that a mock method was called.
     */
    private fun animatedDeck(): Deck {
        val slide = mockk<Slide>()
        every { slide.timeline } returns null
        every { slide.transition } returns mockk() // non-null -> the deck counts as animated
        return mockk<Deck> { every { slides } returns listOf(slide) }
    }

    private fun stubPlayer(deck: Deck, showingIndex: Int): PresentationPlayer =
        mockk<PresentationPlayer>(relaxed = true).also {
            every { it.deck } returns deck
            every { it.currentSlideIndex } returns showingIndex
        }

    @Test
    fun `re-showing the live animated slide leaves its player running`() {
        val pm = manager()
        val deck = animatedDeck()
        val player = stubPlayer(deck, showingIndex = 0)
        pm.setPresentingMode(Presenting.PRESENTATION)
        pm.presentationPlayer = player

        pm.presentationShowSlide(deck, slideIndex = 0) // same slide, visible -> idempotent

        assertSame(player, pm.presentationPlayer, "the live slide's animation must not be torn down and restarted")
        verify(exactly = 0) { player.close() }
        verify(exactly = 0) { player.showSlide(any(), any()) }
    }

    @Test
    fun `showing a different slide of the same deck reuses the player`() {
        val pm = manager()
        val deck = animatedDeck()
        val player = stubPlayer(deck, showingIndex = 5) // currently on slide 5
        pm.setPresentingMode(Presenting.PRESENTATION)
        pm.presentationPlayer = player

        pm.presentationShowSlide(deck, slideIndex = 0) // different slide -> not idempotent, reuse

        assertSame(player, pm.presentationPlayer, "the same deck's player is reused, not rebuilt")
        verify { player.showSlide(0, any()) }
    }

    // ── A non-animated deck never starts the player ──────────────────────────────

    @Test
    fun `showing a slide of a static deck tears down any player and starts none`() {
        val pm = manager()
        pm.presentationPlayer = PresentationPlayer(staticDeck("previous.pdf"))

        pm.presentationShowSlide(staticDeck(), slideIndex = 0)

        assertNull(pm.presentationPlayer, "a static deck plays through the JPEG path, so no animated player runs")
    }

    // ── With no player, step keys fall through ───────────────────────────────────

    @Test
    fun `advancing with no live player falls through to slide navigation`() {
        val pm = manager()

        assertFalse(pm.advancePresentationStep(staticDeck(), slideIndex = 0), "false tells the caller to change slides instead")
    }

    @Test
    fun `rewinding with no live player falls through to slide navigation`() {
        val pm = manager()

        assertFalse(pm.rewindPresentationStep(staticDeck(), slideIndex = 0))
    }

    @Test
    fun `no player means nothing is steppable`() {
        val pm = manager()

        assertNull(pm.steppablePlayer(staticDeck(), slideIndex = 0))
    }

    // ── The identity guard ───────────────────────────────────────────────────────

    @Test
    fun `a player showing a different slide is not steppable`() {
        val pm = manager()
        val deck = staticDeck()
        pm.setPresentingMode(Presenting.PRESENTATION)
        pm.presentationPlayer = PresentationPlayer(deck) // parked at index -1

        assertNull(
            pm.steppablePlayer(deck, slideIndex = 3),
            "the key is for a slide the player isn't on; it must fall through, not step the wrong slide",
        )
    }

    @Test
    fun `a player showing a different deck is not steppable`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.PRESENTATION)
        pm.presentationPlayer = PresentationPlayer(staticDeck("live.pdf")) // parked at index -1

        assertNull(
            pm.steppablePlayer(staticDeck("other.pdf"), slideIndex = -1),
            "a step meant for another deck must not drive the live one",
        )
    }

    // ── The visibility guard ─────────────────────────────────────────────────────

    @Test
    fun `the live player is steppable while presentation mode shows it`() {
        val pm = manager()
        val deck = staticDeck()
        pm.setPresentingMode(Presenting.PRESENTATION)
        val player = PresentationPlayer(deck) // parked at index -1, which is what we ask for
        pm.presentationPlayer = player

        assertSame(player, pm.steppablePlayer(deck, slideIndex = -1))
    }

    @Test
    fun `a player is not steppable when nothing is showing it`() {
        // Default mode is NONE and there are no locks — the player exists but is off-screen, so a
        // step would be invisible; the key must fall through instead.
        val pm = manager()
        val deck = staticDeck()
        pm.presentationPlayer = PresentationPlayer(deck)

        assertNull(pm.steppablePlayer(deck, slideIndex = -1))
    }

    @Test
    fun `a cleared display makes the live player un-steppable`() {
        val pm = manager()
        val deck = staticDeck()
        pm.setPresentingMode(Presenting.PRESENTATION)
        pm.presentationPlayer = PresentationPlayer(deck)
        pm.requestClearDisplay()

        assertNull(
            pm.steppablePlayer(deck, slideIndex = -1),
            "a fade-out has blanked the mode-driven output, so steps there would be invisible",
        )
    }

    @Test
    fun `a lock on something other than presentation does not make the player steppable`() {
        // A screen lock exists, but it pins a different content type — presentation is still not
        // visible anywhere, so a step would be invisible and must fall through.
        val pm = manager()
        val deck = staticDeck()
        pm.setScreenLock(screenIndex = 0, mode = Presenting.ANNOUNCEMENTS) // not PRESENTATION; live mode NONE
        pm.presentationPlayer = PresentationPlayer(deck)

        assertNull(pm.steppablePlayer(deck, slideIndex = -1))
    }

    @Test
    fun `advancing a live player with no further build steps reports false`() {
        // The player IS steppable (right deck, right slide, visible), but its current slide has no
        // remaining build — advance returns false so the key falls through to a slide change.
        val pm = manager()
        val deck = staticDeck()
        pm.setPresentingMode(Presenting.PRESENTATION)
        pm.presentationPlayer = PresentationPlayer(deck) // parked at index -1, nothing loaded to step

        assertFalse(pm.advancePresentationStep(deck, slideIndex = -1))
    }

    @Test
    fun `rewinding a live player at the pre-click state reports false`() {
        val pm = manager()
        val deck = staticDeck()
        pm.setPresentingMode(Presenting.PRESENTATION)
        pm.presentationPlayer = PresentationPlayer(deck)

        assertFalse(pm.rewindPresentationStep(deck, slideIndex = -1))
    }

    @Test
    fun `a screen lock on presentation keeps the player steppable even off the live mode`() {
        // Screen locks pin one output to PRESENTATION regardless of the live mode; a step is then
        // visible there, so it must be allowed.
        val pm = manager()
        val deck = staticDeck()
        pm.setScreenLock(screenIndex = 0, mode = Presenting.PRESENTATION)
        val player = PresentationPlayer(deck)
        pm.presentationPlayer = player

        assertSame(player, pm.steppablePlayer(deck, slideIndex = -1))
    }
}
