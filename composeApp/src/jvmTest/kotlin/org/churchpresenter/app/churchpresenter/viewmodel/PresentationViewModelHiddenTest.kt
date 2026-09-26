package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.HiddenItemsStore
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PresentationSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Slides hidden from the slideshow (#676): Next, Previous and looping pass over them, the stage
 * monitor's "next" skips them, a click still shows one, and a deck reopened with its first slide
 * hidden starts on the first shown one.
 *
 * Decks are loaded through the Instance Link path, which takes slide bytes from a lambda: it fills
 * the same state as a local deck without needing a real presentation to be rasterised.
 */
class PresentationViewModelHiddenTest {

    private lateinit var testHome: File
    private var realHome: String? = null
    private val created = mutableListOf<PresentationViewModel>()
    private val deckPath = "/Volumes/Share/Sunday.pptx"

    @BeforeTest
    fun setUp() {
        // Bound before the swap: CrashReporter resolves its paths once, and the load breadcrumbs.
        CrashReporter.installId()
        realHome = System.getProperty("user.home")
        testHome = Files.createTempDirectory("cp-presentation-hidden").toFile()
        System.setProperty("user.home", testHome.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        created.forEach { runCatching { it.dispose() } }
        realHome?.let { System.setProperty("user.home", it) }
        testHome.deleteRecursively()
    }

    private val storeFile get() = File(testHome, "hidden_items.json")

    private fun vm(looping: Boolean = true) = PresentationViewModel(
        AppSettings(presentationSettings = PresentationSettings(isLooping = looping)),
        hiddenStore = HiddenItemsStore(storeFile),
    ).also { created += it }

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for $what")
            Thread.yield()
        }
    }

    private fun PresentationViewModel.loaded(slides: Int = 5, id: String = "sched-1") = apply {
        loadPresentationFromRemote(id, deckPath, slideCount = slides) { "slide-$it".toByteArray() }
        awaitUntil("the slides") { !isLoading && slideFiles.size == slides }
    }

    @Test
    fun `Next and Previous pass over hidden slides`() {
        val vm = vm().loaded()
        vm.toggleSlideHidden(1)
        vm.toggleSlideHidden(2)

        vm.nextSlide()
        assertEquals(3, vm.selectedSlideIndex)
        vm.previousSlide()
        assertEquals(0, vm.selectedSlideIndex)
    }

    @Test
    fun `looping wraps to the first shown slide in both directions`() {
        val vm = vm(looping = true).loaded()
        vm.toggleSlideHidden(0)
        vm.toggleSlideHidden(4)
        vm.selectSlide(3)

        vm.nextSlide()
        assertEquals(1, vm.selectedSlideIndex)
        vm.previousSlide()
        assertEquals(3, vm.selectedSlideIndex)
    }

    @Test
    fun `without looping, the slideshow stops before hidden slides at the end`() {
        val vm = vm(looping = false).loaded()
        vm.toggleSlideHidden(3)
        vm.toggleSlideHidden(4)
        vm.selectSlide(2)
        vm.togglePlayPause()

        vm.nextSlide()
        assertEquals(2, vm.selectedSlideIndex)
        assertFalse(vm.isPlaying)

        vm.selectSlide(0)
        vm.previousSlide()
        assertEquals(0, vm.selectedSlideIndex, "Previous without looping stays on the first slide")
    }

    @Test
    fun `the stage monitor's next slide skips hidden ones`() {
        val vm = vm().loaded()
        vm.toggleSlideHidden(1)

        assertEquals(2, vm.nextShownSlideIndex(0))
        vm.toggleSlideHidden(2)
        vm.toggleSlideHidden(3)
        vm.toggleSlideHidden(4)
        assertNull(vm.nextShownSlideIndex(0), "nothing shown comes after it")
    }

    @Test
    fun `a hidden slide can still be picked on purpose`() {
        val vm = vm().loaded()
        vm.toggleSlideHidden(2)

        vm.selectSlide(2)

        assertEquals(2, vm.selectedSlideIndex)
    }

    @Test
    fun `hidden slides are remembered and a deck starts on its first shown slide`() {
        vm().loaded().apply {
            toggleSlideHidden(0)
            toggleSlideHidden(1)
        }

        val reopened = vm().loaded(id = "sched-2")

        assertEquals(setOf(0, 1), reopened.hiddenSlides)
        assertEquals(2, reopened.selectedSlideIndex)
    }

    @Test
    fun `showing a slide again puts it back in the run`() {
        val vm = vm().loaded()
        vm.toggleSlideHidden(1)
        vm.toggleSlideHidden(1)

        vm.nextSlide()

        assertEquals(1, vm.selectedSlideIndex)
        assertEquals(emptySet(), vm.hiddenSlides)
    }

    @Test
    fun `a cue's playback starts at the first shown slide`() {
        val vm = vm().loaded()
        vm.toggleSlideHidden(0)

        vm.requestPlayback(plays = 1, filePath = File(deckPath).absolutePath)

        assertEquals(1, vm.selectedSlideIndex)
    }

    @Test
    fun `with no deck open there is nothing to hide`() {
        val vm = vm()
        vm.toggleSlideHidden(0)
        assertEquals(emptySet(), vm.hiddenSlides)
        assertFalse(storeFile.exists())
    }
}
