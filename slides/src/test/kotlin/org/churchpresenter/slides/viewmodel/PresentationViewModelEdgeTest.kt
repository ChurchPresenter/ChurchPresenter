package org.churchpresenter.slides.viewmodel

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.core.models.presentation.PresentationLoadError
import org.churchpresenter.presentationengine.model.DeckLoadError
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PresentationSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.slides.awaitDeck
import org.churchpresenter.slides.pdfDeck
import org.churchpresenter.slides.solidImage
import org.churchpresenter.slides.tempDir
import java.awt.Color
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PresentationViewModelEdgeTest {

    private val dir = tempDir("cp-presentation-vm-edge")
    private val made = mutableListOf<PresentationViewModel>()

    @AfterTest
    fun cleanUp() {
        made.forEach { runCatching { it.dispose() } }
        dir.deleteRecursively()
    }

    private fun vm(settings: PresentationSettings = PresentationSettings()): PresentationViewModel =
        PresentationViewModel(AppSettings(presentationSettings = settings)).also { made += it }

    private fun withSlides(count: Int, looping: Boolean = false): PresentationViewModel =
        vm(PresentationSettings(isLooping = looping)).apply {
            slideFiles.addAll((1..count).map { solidImage(dir, "s$it.jpg", Color.GRAY) })
        }

    @Test
    fun `stepping past the last slide without looping stops playback there`() {
        val vm = withSlides(2)
        vm.selectSlide(1)
        vm.togglePlayPause()
        vm.nextSlide()
        assertEquals(1, vm.selectedSlideIndex)
        assertFalse(vm.isPlaying)
    }

    @Test
    fun `stepping past the last slide while looping starts again`() {
        val vm = withSlides(3, looping = true)
        vm.selectSlide(2)
        vm.nextSlide()
        assertEquals(0, vm.selectedSlideIndex)
    }

    @Test
    fun `stepping back from the first slide while looping goes to the last`() {
        val vm = withSlides(3, looping = true)
        vm.previousSlide()
        assertEquals(2, vm.selectedSlideIndex)
    }

    @Test
    fun `stepping back from the first slide without looping stays put`() {
        val vm = withSlides(3)
        vm.previousSlide()
        assertEquals(0, vm.selectedSlideIndex)
    }

    @Test
    fun `stepping back remembers that it came from the slide after`() {
        val vm = withSlides(3)
        vm.selectSlide(2)
        vm.previousSlide()
        assertTrue(vm.consumeEnteredViaPreviousSlide())
        assertFalse(vm.consumeEnteredViaPreviousSlide(), "the flag is read once")
    }

    @Test
    fun `stepping forward clears the came-back flag`() {
        val vm = withSlides(3)
        vm.selectSlide(2)
        vm.previousSlide()
        vm.nextSlide()
        assertFalse(vm.consumeEnteredViaPreviousSlide())
    }

    @Test
    fun `the step callbacks reach Instance Link either way`() {
        val vm = withSlides(3)
        var next = 0
        var previous = 0
        vm.nextSlide { next++ }
        vm.previousSlide { previous++ }
        assertEquals(1, next)
        assertEquals(1, previous)
    }

    @Test
    fun `a slide outside the deck cannot be selected`() {
        val vm = withSlides(2)
        vm.selectSlide(5)
        vm.selectSlide(-1)
        assertEquals(0, vm.selectedSlideIndex)
    }

    @Test
    fun `the next shown slide is the one after unless there is none`() {
        val vm = withSlides(3)
        assertEquals(1, vm.nextShownSlideIndex())
        assertNull(vm.nextShownSlideIndex(2))
    }

    @Test
    fun `hiding a slide needs a deck to remember it against`() {
        val vm = withSlides(3)
        vm.toggleSlideHidden(1)
        assertTrue(vm.hiddenSlides.isEmpty())
    }

    @Test
    fun `a request to play once starts at the top and does not loop`() {
        val vm = withSlides(3, looping = true)
        vm.selectSlide(2)
        vm.requestPlayback(1)
        assertTrue(vm.isPlaying)
        assertFalse(vm.isLooping)
        assertEquals(0, vm.selectedSlideIndex)
    }

    @Test
    fun `a request to play twice wraps once and then stops`() {
        val vm = withSlides(2, looping = false)
        vm.requestPlayback(2)
        assertTrue(vm.isLooping)
        vm.nextSlide()
        vm.nextSlide()
        assertEquals(0, vm.selectedSlideIndex, "the second pass started")
        vm.nextSlide()
        vm.nextSlide()
        assertFalse(vm.isPlaying, "two passes and done")
    }

    @Test
    fun `a request for another deck waits until that deck is open`() {
        val vm = withSlides(2)
        vm.requestPlayback(3, filePath = File(dir, "other.pdf").absolutePath)
        assertFalse(vm.isPlaying)
    }

    @Test
    fun `a request with nothing loaded waits for slides`() {
        val vm = vm()
        vm.requestPlayback(1)
        assertFalse(vm.isPlaying)
    }

    @Test
    fun `a path that does not exist opens nothing`() {
        val vm = vm()
        vm.loadPresentationByPath(File(dir, "missing.pdf").absolutePath)
        assertTrue(vm.presentations.isEmpty())
    }

    @Test
    fun `a file that is not a deck opens nothing`() {
        val vm = vm()
        val notes = File(dir, "notes.txt").apply { writeText("hello") }
        vm.loadPresentationByPath(notes.absolutePath)
        vm.addPresentation(notes)
        assertTrue(vm.presentations.isEmpty())
    }

    @Test
    fun `a deck opened by path is listed and selected`() = runComposeUiTest {
        val vm = vm()
        val file = pdfDeck(dir, 2, "byPath.pdf")
        vm.loadPresentationByPath(file.absolutePath)
        awaitDeck(vm)
        assertEquals(listOf(file.absolutePath), vm.presentations.map { it.absolutePath })
        assertEquals(2, vm.slideFiles.size)
    }

    @Test
    fun `opening a deck twice lists it once`() = runComposeUiTest {
        val vm = vm()
        val file = pdfDeck(dir, 1, "twice.pdf")
        vm.addPresentation(file)
        awaitDeck(vm)
        val before = vm.loadGeneration
        vm.loadPresentationByPath(file.absolutePath)
        waitUntil("the deck reloaded", 10_000) { vm.loadGeneration > before && !vm.isLoading }
        assertEquals(1, vm.presentations.size)
    }

    @Test
    fun `removing the open deck moves to the next one`() = runComposeUiTest {
        val vm = vm()
        val first = pdfDeck(dir, 1, "first.pdf")
        val second = pdfDeck(dir, 2, "second.pdf")
        vm.addPresentation(first)
        awaitDeck(vm)
        val firstLoad = vm.loadGeneration
        vm.addPresentation(second)
        waitUntil("the second deck rendered", 10_000) { vm.loadGeneration > firstLoad && !vm.isLoading }
        val secondLoad = vm.loadGeneration
        vm.removePresentation(second)
        waitUntil("the first deck back", 10_000) { vm.loadGeneration > secondLoad }
        assertEquals(first.absolutePath, vm.selectedPresentation?.absolutePath)
        assertEquals(1, vm.slideFiles.size)
    }

    @Test
    fun `removing the only deck empties the tab`() = runComposeUiTest {
        val vm = vm()
        val only = pdfDeck(dir, 2, "only.pdf")
        vm.addPresentation(only)
        awaitDeck(vm)
        vm.removePresentation(only)
        assertNull(vm.selectedPresentation)
        assertTrue(vm.slideFiles.isEmpty())
        assertEquals(0, vm.selectedSlideIndex)
    }

    @Test
    fun `removing a deck that is not open leaves the open one alone`() = runComposeUiTest {
        val vm = vm()
        val open = pdfDeck(dir, 1, "open.pdf")
        val other = pdfDeck(dir, 1, "other.pdf")
        vm.addPresentation(other)
        awaitDeck(vm)
        val before = vm.loadGeneration
        vm.addPresentation(open)
        waitUntil("the open deck rendered", 10_000) { vm.loadGeneration > before && !vm.isLoading }
        vm.removePresentation(other)
        assertEquals(open.absolutePath, vm.selectedPresentation?.absolutePath)
        assertEquals(1, vm.presentations.size)
    }

    @Test
    fun `a hidden slide is remembered against its deck and skipped`() = runComposeUiTest {
        val vm = vm()
        val file = pdfDeck(dir, 3, "hidden.pdf")
        vm.addPresentation(file)
        awaitDeck(vm)
        vm.toggleSlideHidden(1)
        assertEquals(setOf(1), vm.hiddenSlides)
        assertEquals(2, vm.nextShownSlideIndex(0))
        vm.toggleSlideHidden(1)
        assertTrue(vm.hiddenSlides.isEmpty())
    }

    @Test
    fun `clearing drops every deck and its slides`() = runComposeUiTest {
        val vm = vm()
        vm.addPresentation(pdfDeck(dir, 2, "clear.pdf"))
        awaitDeck(vm)
        vm.clearPresentations()
        assertTrue(vm.presentations.isEmpty())
        assertTrue(vm.slideFiles.isEmpty())
        assertFalse(vm.isLoading)
        assertEquals(0, vm.totalSlides)
    }

    @Test
    fun `engine load errors become what the tab can say`() {
        val vm = vm()
        with(vm) {
            assertEquals(PresentationLoadError.PASSWORD_PROTECTED, DeckLoadError.PASSWORD_PROTECTED.toUiError())
            assertEquals(PresentationLoadError.EMPTY_DOCUMENT, DeckLoadError.EMPTY_DOCUMENT.toUiError())
            assertEquals(PresentationLoadError.RENDER_FAILED, DeckLoadError.UNSUPPORTED_FORMAT.toUiError())
            assertEquals(PresentationLoadError.RENDER_FAILED, DeckLoadError.PARSE_FAILED.toUiError())
        }
    }

    @Test
    fun `playback settings come from the document`() {
        val vm = vm(PresentationSettings(autoScrollInterval = 9f, isLooping = false, transitionDuration = 250f))
        assertEquals(9f, vm.autoScrollInterval)
        assertFalse(vm.isLooping)
        assertEquals(250f, vm.transitionDuration)
    }

    @Test
    fun `each stored animation name becomes its animation`() {
        mapOf(
            Constants.ANIMATION_FADE to AnimationType.FADE,
            Constants.ANIMATION_SLIDE_LEFT to AnimationType.SLIDE_LEFT,
            Constants.ANIMATION_SLIDE_RIGHT to AnimationType.SLIDE_RIGHT,
            Constants.ANIMATION_NONE to AnimationType.NONE,
            "something else" to AnimationType.CROSSFADE,
        ).forEach { (stored, expected) ->
            assertEquals(expected, vm(PresentationSettings(animationType = stored)).animationType, stored)
        }
    }

    @Test
    fun `a viewmodel with no settings falls back to the defaults`() {
        val vm = PresentationViewModel(null).also { made += it }
        assertEquals(5f, vm.autoScrollInterval)
        assertTrue(vm.isLooping)
        assertEquals(500f, vm.transitionDuration)
        assertEquals(AnimationType.CROSSFADE, vm.animationType)
    }
}
