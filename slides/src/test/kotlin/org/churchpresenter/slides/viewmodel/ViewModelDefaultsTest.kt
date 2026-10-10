package org.churchpresenter.slides.viewmodel

import org.churchpresenter.presentationengine.DeckRasterizer
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.FakeSlidesOutput
import org.churchpresenter.slides.pdfDeck
import org.churchpresenter.slides.solidImage
import org.churchpresenter.slides.tempDir
import java.awt.Color
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewModelDefaultsTest {

    private val dir = tempDir("cp-viewmodel-defaults")
    private val presentations = mutableListOf<PresentationViewModel>()
    private val pictures = mutableListOf<PicturesViewModel>()

    @AfterTest
    fun cleanUp() {
        presentations.forEach { runCatching { it.dispose() } }
        pictures.forEach { runCatching { it.dispose() } }
        dir.deleteRecursively()
    }

    private fun presentation() = PresentationViewModel().also { presentations += it }

    private fun picturesOver(count: Int): PicturesViewModel {
        val folder = File(dir, "pics").apply { mkdirs() }
        repeat(count) { solidImage(folder, "p$it.png", Color.BLUE) }
        return PicturesViewModel().also { pictures += it; it.loadImagesFromFolder(folder) }
    }

    @Test
    fun `the default loader and renderer read a real deck`() {
        val vm = presentation()
        val deck = (vm.loadDeck(pdfDeck(dir, 2)) as LoadResult.Success).deck
        val frame = DeckRasterizer(deck, 64).use { vm.renderSlideFrame(it, 0) }
        assertEquals(2, deck.slideCount)
        assertTrue(frame.width > 0 && frame.height > 0)
    }

    @Test
    fun `slide navigation through its interface needs no Instance Link callback`() {
        val vm = presentation()
        vm.slideFiles.addAll(List(3) { File(dir, "s$it.jpg") })
        val nav: SlideNavigation = vm
        nav.nextSlide()
        assertEquals(1, vm.selectedSlideIndex)
        nav.previousSlide()
        assertEquals(0, vm.selectedSlideIndex)
    }

    @Test
    fun `a playback request through its interface plays whatever deck is open`() {
        val vm = presentation()
        vm.slideFiles.addAll(List(2) { File(dir, "s$it.jpg") })
        val controls: SlidePlaybackControls = vm
        controls.requestPlayback(1)
        assertTrue(vm.isPlaying)
    }

    @Test
    fun `picture navigation and playback through their interfaces need no extra arguments`() {
        val vm = picturesOver(3)
        val nav: PictureNavigation = vm
        nav.nextImage()
        assertEquals(1, vm.selectedImageIndex)
        nav.previousImage()
        assertEquals(0, vm.selectedImageIndex)

        val controls: PicturePlaybackControls = vm
        controls.requestPlayback(1)
        assertTrue(vm.isPlaying)
    }

    @Test
    fun `going live through the presenting interface needs no listeners`() {
        val vm = picturesOver(2)
        val out = FakeSlidesOutput()
        val presenting: PicturesPresenting = vm
        presenting.goLive(out)
        assertEquals(vm.images.first().absolutePath, out.selectedImagePath.value)
        assertEquals(Presenting.PICTURES, out.onAir.value)
    }
}
