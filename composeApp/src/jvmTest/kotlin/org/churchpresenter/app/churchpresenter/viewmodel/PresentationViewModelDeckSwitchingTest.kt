package org.churchpresenter.app.churchpresenter.viewmodel

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Switching between open decks, and what closing one throws away.
 *
 * [PresentationViewModelTest] covers loading, navigating and the settings; this covers the parts
 * that only bite with more than one deck open, or after a deck is closed: selecting between them,
 * whether the slide position resets, and whether the rendered slide cache on disk survives.
 *
 * That last one is the sharp edge. `removePresentation` keeps the cache when the deck is still in
 * recents or pinned (so re-opening is instant) and deletes it when it isn't (so a deck removed for
 * good doesn't leave megabytes of rendered JPEGs behind). Getting the flag backwards is invisible
 * until a disk fills up or an operator wonders why a re-added deck re-renders every time.
 *
 * Slides render for real from real PDFs, into `~/.churchpresenter/slides/` under the test home.
 * Each test builds its own document so cache entries never collide with another test's.
 */
class PresentationViewModelDeckSwitchingTest {

    private lateinit var dir: File
    private val created = mutableListOf<PresentationViewModel>()
    private var counter = 0

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-presentation-switching-test").toFile()
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    /** Writes a real [pages]-page PDF, each page carrying distinct text. */
    private fun pdf(pages: Int = 3, name: String = "switch-deck${counter++}.pdf"): File {
        val file = File(dir, name)
        PDDocument().use { doc ->
            repeat(pages) { index ->
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 36f)
                    stream.newLineAtOffset(72f, 500f)
                    stream.showText("Slide ${index + 1}")
                    stream.endText()
                }
            }
            doc.save(file)
        }
        return file
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun viewModel() = PresentationViewModel(null).also { created.add(it) }

    /** Opens [file] and waits for its slides to finish rendering. */
    private fun PresentationViewModel.open(file: File): PresentationViewModel {
        addPresentation(file)
        awaitUntil("slides of ${file.name}") { !isLoading && slideFiles.isNotEmpty() }
        return this
    }

    /** Switches to [file] and waits for its slides. */
    private fun PresentationViewModel.switchTo(file: File, slides: Int) {
        selectPresentation(file)
        awaitUntil("slides of ${file.name}") { !isLoading && slideFiles.size == slides }
    }

    // ── Switching between open decks ────────────────────────────────────────────

    @Test
    fun `selecting another open deck loads its slides`() {
        val first = pdf(pages = 2)
        val second = pdf(pages = 4)
        val vm = viewModel().open(first)
        vm.addPresentation(second)
        awaitUntil("second deck") { !vm.isLoading && vm.slideFiles.size == 4 }

        vm.switchTo(first, slides = 2)

        assertEquals(first.absolutePath, vm.selectedPresentation?.absolutePath)
        assertEquals(2, vm.slideFiles.size)
        assertEquals(2, vm.totalSlides)
    }

    @Test
    fun `switching decks starts the new one at its first slide`() {
        val first = pdf(pages = 3)
        val second = pdf(pages = 4)
        val vm = viewModel().open(first)
        vm.addPresentation(second)
        awaitUntil("second deck") { !vm.isLoading && vm.slideFiles.size == 4 }
        vm.selectSlide(2)

        vm.switchTo(first, slides = 3)

        assertEquals(0, vm.selectedSlideIndex, "the other deck's position must not carry over")
    }

    @Test
    fun `coming back to a deck starts it at the first slide again`() {
        val first = pdf(pages = 3)
        val second = pdf(pages = 2)
        val vm = viewModel().open(first)
        vm.selectSlide(2)
        vm.addPresentation(second)
        awaitUntil("second deck") { !vm.isLoading && vm.slideFiles.size == 2 }

        vm.switchTo(first, slides = 3)

        assertEquals(
            0,
            vm.selectedSlideIndex,
            "position is not remembered per deck — the presenter starts from the top"
        )
    }

    @Test
    fun `selecting a deck that was never opened does nothing`() {
        val open = pdf(pages = 2)
        val vm = viewModel().open(open)
        val stranger = pdf(pages = 5)

        vm.selectPresentation(stranger)

        assertEquals(open.absolutePath, vm.selectedPresentation?.absolutePath, "only opened decks can be selected")
        assertEquals(2, vm.slideFiles.size)
        assertEquals(1, vm.presentations.size, "selecting must not quietly add it")
    }

    @Test
    fun `switching decks clears a previous load error`() {
        val broken = File(dir, "broken.pdf").also { it.writeText("this is not a pdf") }
        val good = pdf(pages = 2)
        val vm = viewModel()
        vm.addPresentation(broken)
        awaitUntil("the failure to be reported") { vm.loadError != null }
        vm.addPresentation(good)
        awaitUntil("the good deck") { !vm.isLoading && vm.slideFiles.size == 2 }

        assertNull(vm.loadError, "a stale error banner over a deck that opened fine is wrong")
    }

    // ── Play / pause ────────────────────────────────────────────────────────────

    @Test
    fun `a deck does not auto-advance until it is played`() {
        assertFalse(viewModel().open(pdf(pages = 3)).isPlaying, "opening a deck must not start a slideshow")
    }

    @Test
    fun `play and pause toggle`() {
        val vm = viewModel().open(pdf(pages = 3))

        vm.togglePlayPause()
        assertTrue(vm.isPlaying)

        vm.togglePlayPause()
        assertFalse(vm.isPlaying)
    }

    @Test
    fun `auto-advance carries across a deck switch`() {
        // Documents CURRENT behaviour: isPlaying belongs to the tab, not to the deck, so a running
        // slideshow keeps running when the operator switches decks.
        // The two decks differ in length on purpose: with both at the same page count, a wait on
        // "2 slides are loaded" is satisfied by the deck that is ALREADY loaded, so the switch
        // happens mid-load, cancels it, and nothing ever settles.
        val first = pdf(pages = 2)
        val second = pdf(pages = 3)
        val vm = viewModel().open(first)
        vm.addPresentation(second)
        awaitUntil("second deck") { !vm.isLoading && vm.slideFiles.size == 3 }
        vm.togglePlayPause()

        vm.switchTo(first, slides = 2)

        assertTrue(vm.isPlaying)
    }

    // ── Closing a deck and its rendered slides ──────────────────────────────────

    @Test
    fun `closing a deck that is still in recents keeps its rendered slides`() {
        val file = pdf(pages = 2)
        val vm = viewModel().open(file)
        val rendered = vm.slideFiles.toList()
        assertTrue(rendered.all { it.exists() }, "precondition: the slides rendered to disk")

        vm.removePresentation(file, isInRecentsOrPinned = true)

        assertTrue(
            rendered.all { it.exists() },
            "re-opening from recents should be instant, not a full re-render"
        )
    }

    @Test
    fun `closing a deck for good deletes its rendered slides`() {
        val file = pdf(pages = 2)
        val vm = viewModel().open(file)
        val rendered = vm.slideFiles.toList()
        assertTrue(rendered.all { it.exists() }, "precondition: the slides rendered to disk")

        vm.removePresentation(file, isInRecentsOrPinned = false)

        assertTrue(
            rendered.none { it.exists() },
            "a deck removed for good must not leave its rendered slides on disk forever"
        )
    }

    @Test
    fun `a deck closed for good renders again if it is re-added`() {
        val file = pdf(pages = 2)
        val vm = viewModel().open(file)
        vm.removePresentation(file, isInRecentsOrPinned = false)

        val reopened = viewModel().open(file)

        assertEquals(2, reopened.slideFiles.size)
        assertTrue(reopened.slideFiles.all { it.exists() }, "the cache was dropped, so it must render fresh")
    }

    @Test
    fun `closing one deck leaves another deck's slides alone`() {
        val kept = pdf(pages = 2)
        val closed = pdf(pages = 3)
        val vm = viewModel().open(kept)
        val keptSlides = vm.slideFiles.toList()
        vm.addPresentation(closed)
        awaitUntil("second deck") { !vm.isLoading && vm.slideFiles.size == 3 }

        vm.removePresentation(closed, isInRecentsOrPinned = false)

        assertTrue(keptSlides.all { it.exists() }, "each deck's cache is its own")
        assertEquals(kept.absolutePath, vm.selectedPresentation?.absolutePath)
    }

    @Test
    fun `closing the last deck leaves nothing selected and no slides`() {
        val file = pdf(pages = 2)
        val vm = viewModel().open(file)

        vm.removePresentation(file)

        assertNull(vm.selectedPresentation)
        assertTrue(vm.slideFiles.isEmpty())
        assertEquals(0, vm.selectedSlideIndex)
    }

    // NOT covered here: `PresentationViewModel.cleanupOrphanedCaches`. It deletes every cached deck
    // whose source path is absent from the list it is given, across the whole shared slide cache —
    // so calling it from a test would reach into other tests' cache entries in the same test home.
}
