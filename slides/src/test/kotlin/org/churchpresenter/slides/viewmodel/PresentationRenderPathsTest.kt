package org.churchpresenter.slides.viewmodel

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.presentation.PresentationLoadError
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.cache.SlideCacheSupersededException
import org.churchpresenter.presentationengine.model.DeckLoadError
import org.churchpresenter.presentationengine.model.Fidelity
import org.churchpresenter.presentationengine.model.Slide
import org.churchpresenter.presentationengine.model.Step
import org.churchpresenter.presentationengine.model.Timeline
import org.churchpresenter.presentationengine.model.pdfDeck
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.slides.pdfDeck as realPdf
import org.churchpresenter.slides.tempDir
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PresentationRenderPathsTest {

    private val dir = tempDir("cp-presentation-render-paths")
    private val made = mutableListOf<PresentationViewModel>()

    @AfterTest
    fun cleanUp() {
        made.forEach { runCatching { it.dispose() } }
        dir.deleteRecursively()
    }

    private fun vm() = PresentationViewModel(AppSettings()).also { made += it }

    private fun slide(index: Int, builds: Int = 0) = Slide(
        index = index,
        notes = "note $index",
        transition = null,
        layers = emptyList(),
        timeline = if (builds > 0) Timeline(List(builds) { Step(emptyList()) }) else null,
        fidelity = Fidelity.NATIVE,
    )

    private fun frame() = BufferedImage(32, 18, BufferedImage.TYPE_INT_RGB)

    /** A real, valid file name for the loader to be asked about; the deck itself is supplied. */
    private fun file(name: String) = realPdf(dir, 1, name)

    private fun ComposeUiTest.settle(vm: PresentationViewModel, before: Int) {
        waitUntil("the load settled", 10_000) { !vm.isLoading && (vm.loadGeneration > before || vm.loadError != null) }
    }

    @Test
    fun `a deck carrying warnings still renders every slide`() = runComposeUiTest {
        val vm = vm()
        val f = file("warned.pdf")
        vm.loadDeck = { LoadResult.Success(pdfDeck(f, listOf(slide(0), slide(1)), warnings = listOf("fonts substituted"))) }
        vm.renderSlideFrame = { _, _ -> frame() }
        val before = vm.loadGeneration
        vm.addPresentation(f)
        settle(vm, before)
        assertEquals(2, vm.slideFiles.size)
        assertNull(vm.loadError)
    }

    @Test
    fun `slides with builds are rendered like any other`() = runComposeUiTest {
        val vm = vm()
        val f = file("builds.pdf")
        vm.loadDeck = { LoadResult.Success(pdfDeck(f, listOf(slide(0, builds = 2), slide(1)))) }
        vm.renderSlideFrame = { _, _ -> frame() }
        val before = vm.loadGeneration
        vm.addPresentation(f)
        settle(vm, before)
        assertEquals(2, vm.slideFiles.size)
        assertNotNull(vm.deck)
    }

    @Test
    fun `one slide that will not render is skipped and the rest are kept`() = runComposeUiTest {
        val vm = vm()
        val f = file("partial.pdf")
        vm.loadDeck = { LoadResult.Success(pdfDeck(f, listOf(slide(0), slide(1), slide(2)))) }
        vm.renderSlideFrame = { _, index -> if (index == 1) error("bad slide") else frame() }
        val before = vm.loadGeneration
        vm.addPresentation(f)
        settle(vm, before)
        assertEquals(2, vm.slideFiles.size)
        assertEquals(listOf("note 0", "note 2"), vm.slideNotes)
    }

    @Test
    fun `two broken slides are reported once and the good one is kept`() = runComposeUiTest {
        val vm = vm()
        val f = file("twoBad.pdf")
        vm.loadDeck = { LoadResult.Success(pdfDeck(f, listOf(slide(0), slide(1), slide(2)))) }
        vm.renderSlideFrame = { _, index -> if (index < 2) error("bad slide $index") else frame() }
        val before = vm.loadGeneration
        vm.addPresentation(f)
        settle(vm, before)
        assertEquals(1, vm.slideFiles.size)
        assertNull(vm.loadError)
    }

    @Test
    fun `a newer render taking over the deck ends this one quietly`() = runComposeUiTest {
        val vm = vm()
        val f = file("superseded.pdf")
        vm.loadDeck = { LoadResult.Success(pdfDeck(f, listOf(slide(0), slide(1)))) }
        vm.renderSlideFrame = { _, index -> if (index == 1) throw SlideCacheSupersededException(dir) else frame() }
        vm.addPresentation(f)
        waitUntil("the render finished", 10_000) { !vm.isLoading && vm.slideFiles.isNotEmpty() }
        assertNull(vm.loadError)
    }

    @Test
    fun `a loader that throws is reported as a render failure`() = runComposeUiTest {
        val vm = vm()
        val f = file("throws.pdf")
        vm.loadDeck = { throw IllegalStateException("parser crashed") }
        val before = vm.loadGeneration
        vm.addPresentation(f)
        settle(vm, before)
        assertEquals(PresentationLoadError.RENDER_FAILED, vm.loadError)
        assertTrue(vm.slideFiles.isEmpty())
        assertFalse(vm.isLoading)
    }

    @Test
    fun `a deck whose slides all fail says so`() = runComposeUiTest {
        val vm = vm()
        val f = file("allBad.pdf")
        vm.loadDeck = { LoadResult.Success(pdfDeck(f, listOf(slide(0), slide(1)))) }
        vm.renderSlideFrame = { _, _ -> error("nothing renders") }
        val before = vm.loadGeneration
        vm.addPresentation(f)
        settle(vm, before)
        assertEquals(PresentationLoadError.RENDER_FAILED, vm.loadError)
    }

    @Test
    fun `a cached deck that no longer parses still shows its slides, without playback`() = runComposeUiTest {
        val vm = vm()
        val f = file("cached.pdf")
        vm.loadDeck = { LoadResult.Success(pdfDeck(f, listOf(slide(0), slide(1)))) }
        vm.renderSlideFrame = { _, _ -> frame() }
        var before = vm.loadGeneration
        vm.addPresentation(f)
        settle(vm, before)
        vm.loadDeck = { LoadResult.Failure(DeckLoadError.PARSE_FAILED) }
        before = vm.loadGeneration
        vm.selectPresentation(f)
        waitUntil("the cached slides back", 10_000) { vm.loadGeneration > before }
        assertEquals(2, vm.slideFiles.size)
        assertNull(vm.deck)
    }

    @Test
    fun `a file that has gone missing is not reopened by path`() {
        val vm = vm()
        val gone = File(dir, "gone.pdf")
        vm.loadPresentationByPath(gone.absolutePath)
        assertTrue(vm.presentations.isEmpty())
    }
}
