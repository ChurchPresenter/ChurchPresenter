@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.v2.runComposeUiTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
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
import org.churchpresenter.sharedui.models.Presenting
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PresenterManagerAnimatedDeckTest {

    private lateinit var dir: File
    private val managers = mutableListOf<PresenterManager>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-animated-deck-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        managers.forEach { runCatching { it.clearPresentationPlayback() } }
        dir.deleteRecursively()
    }

    private fun manager() = PresenterManager().also { managers.add(it) }

    private fun animatedDeck(name: String, slides: Int = 2): Deck {
        val file = File(dir, name)
        PDDocument().use { doc ->
            repeat(slides) { doc.addPage(PDPage()) }
            doc.save(file)
        }
        val bounds = RectPt(0.0, 0.0, 720.0, 405.0)
        val layer = LayerSpec.Shape("title", 0, bounds, shapeIndex = 0, initiallyVisible = false)
        val build = Timeline(listOf(Step(listOf(EffectInterval("title", EffectSpec.Appear(), 0, 0)))))
        return pdfDeck(
            file,
            List(slides) {
                Slide(it, "", transition = null, layers = listOf(layer), timeline = build, fidelity = Fidelity.NATIVE)
            },
        )
    }

    @Test
    fun `an animated deck gets a player, and another deck replaces it`() {
        val m = manager()
        val first = animatedDeck("first.pdf")
        m.presentationShowSlide(first, 0)
        val player = assertNotNull(m.playback.presentationPlayer)
        assertSame(first, player.deck)

        m.presentationShowSlide(first, 1)
        assertSame(player, m.playback.presentationPlayer, "the same deck keeps its player")

        val second = animatedDeck("second.pdf")
        m.presentationShowSlide(second, 0)
        assertNotSame(player, m.playback.presentationPlayer)
        assertSame(second, m.playback.presentationPlayer?.deck)
    }

    @Test
    fun `the clock publishes the live slide's frame, and a click builds it`() = runComposeUiTest {
        val m = manager()
        val deck = animatedDeck("live.pdf")
        setContent { LaunchedEffect(Unit) { m.runPresentationClock() } }
        m.setPresentingMode(Presenting.PRESENTATION)
        m.presentationShowSlide(deck, 0)

        waitUntil("the slide is rasterized", timeoutMillis = 5_000) { m.presentationFrame.value != null }
        assertEquals(0, assertNotNull(m.presentationFrame.value).completedSteps)

        assertTrue(m.advancePresentationStep(deck, 0))
        waitUntil("the build is published", timeoutMillis = 5_000) {
            m.presentationFrame.value?.completedSteps == 1
        }
        assertFalse(m.advancePresentationStep(deck, 0), "one step only")
        assertTrue(m.rewindPresentationStep(deck, 0))
    }

    @Test
    fun `clearing playback drops the player and its frame`() {
        val m = manager()
        m.presentationShowSlide(animatedDeck("gone.pdf"), 0)
        m.clearPresentationPlayback()
        assertNull(m.playback.presentationPlayer)
        assertNull(m.presentationFrame.value)
    }
}
