package org.churchpresenter.app.churchpresenter.viewmodel

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.PresentationSettings
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [PresentationViewModel] against genuinely renderable decks.
 *
 * PDFBox is already a dependency of the presentation engine, so a real multi-page PDF is generated
 * here rather than checking a binary fixture into the repo — that exercises the whole
 * load → render → disk-cache path, which is what slide navigation depends on.
 *
 * Rendering writes into `~/.churchpresenter/slides/`; the test task points `user.home` at
 * `build/test-home`, and each test uses its own document so cache entries cannot collide.
 */
class PresentationViewModelTest {

    private lateinit var dir: File
    private val created = mutableListOf<PresentationViewModel>()
    private var counter = 0

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-presentation-test").toFile()
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    /** Writes a real [pages]-page PDF, each page carrying distinct text. */
    private fun pdf(pages: Int = 3, name: String = "deck${counter++}.pdf"): File {
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
            Thread.sleep(50)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun viewModel(settings: AppSettings? = null) =
        PresentationViewModel(settings).also { created.add(it) }

    /** Opens [file] and waits for its slides to finish rendering. */
    private fun PresentationViewModel.open(file: File): PresentationViewModel {
        addPresentation(file)
        awaitUntil("slides of ${file.name}") { !isLoading && slideFiles.isNotEmpty() }
        return this
    }

    // ── Loading ─────────────────────────────────────────────────────────────────

    @Test
    fun `a pdf loads into one slide per page`() {
        val vm = viewModel().open(pdf(pages = 3))
        assertEquals(3, vm.slideFiles.size)
        assertEquals(3, vm.totalSlides)
        assertNull(vm.loadError)
        assertTrue(vm.slideFiles.all { it.exists() }, "each slide is rendered to a real file")
    }

    @Test
    fun `opening a presentation selects it and starts at the first slide`() {
        val file = pdf()
        val vm = viewModel().open(file)
        assertEquals(file.absolutePath, vm.selectedPresentation?.absolutePath)
        assertEquals(0, vm.selectedSlideIndex)
    }

    @Test
    fun `the same file is not opened twice`() {
        val file = pdf()
        val vm = viewModel().open(file)
        vm.addPresentation(file)
        assertEquals(1, vm.presentations.size, "re-opening should focus the existing entry")
    }

    @Test
    fun `several presentations can be open at once`() {
        val vm = viewModel().open(pdf(pages = 2))
        val second = pdf(pages = 4)
        vm.addPresentation(second)
        awaitUntil("the second deck") { !vm.isLoading && vm.slideFiles.size == 4 }

        assertEquals(2, vm.presentations.size)
        assertEquals(second.absolutePath, vm.selectedPresentation?.absolutePath, "the newest becomes current")
    }

    @Test
    fun `a missing file is ignored`() {
        val vm = viewModel()
        vm.addPresentation(File(dir, "nope.pdf"))
        assertTrue(vm.presentations.isEmpty())
    }

    @Test
    fun `an unsupported file type is ignored`() {
        val notADeck = File(dir, "notes.txt").also { it.writeText("not a presentation") }
        val vm = viewModel()
        vm.addPresentation(notADeck)
        assertTrue(vm.presentations.isEmpty(), "only presentation formats belong in the list")
    }

    @Test
    fun `a corrupt file of a supported type reports an error rather than hanging`() {
        val corrupt = File(dir, "broken.pdf").also { it.writeText("this is not a PDF at all") }
        val vm = viewModel()
        vm.addPresentation(corrupt)
        awaitUntil("the load to fail") { !vm.isLoading && vm.loadError != null }
        assertTrue(vm.slideFiles.isEmpty(), "a failed deck must not leave stale slides on screen")
    }

    @Test
    fun `loading by path opens the file`() {
        val file = pdf(pages = 2)
        val vm = viewModel()
        vm.loadPresentationByPath(file.absolutePath)
        awaitUntil("slides") { !vm.isLoading && vm.slideFiles.size == 2 }
        assertEquals(file.absolutePath, vm.selectedPresentation?.absolutePath)
    }

    @Test
    fun `the load generation increments when slides finish`() {
        // PresentationTab keys a LaunchedEffect off this, including to re-focus for arrow keys.
        val vm = viewModel()
        val before = vm.loadGeneration
        vm.open(pdf(pages = 2))
        assertTrue(vm.loadGeneration > before)
    }

    // ── Slide navigation ────────────────────────────────────────────────────────

    @Test
    fun `next and previous step through the slides`() {
        val vm = viewModel().open(pdf(pages = 3))
        vm.nextSlide()
        assertEquals(1, vm.selectedSlideIndex)
        vm.nextSlide()
        assertEquals(2, vm.selectedSlideIndex)
        vm.previousSlide()
        assertEquals(1, vm.selectedSlideIndex)
    }

    @Test
    fun `next at the last slide wraps when looping is on`() {
        val vm = viewModel().open(pdf(pages = 3))
        vm.isLooping = true
        vm.selectSlide(2)
        vm.nextSlide()
        assertEquals(0, vm.selectedSlideIndex)
    }

    @Test
    fun `next at the last slide stops playback when looping is off`() {
        val vm = viewModel().open(pdf(pages = 3))
        vm.isLooping = false
        vm.selectSlide(2)
        vm.togglePlayPause()
        assertTrue(vm.isPlaying)

        vm.nextSlide()
        assertEquals(2, vm.selectedSlideIndex, "the last slide stays on screen")
        assertFalse(vm.isPlaying, "the deck stops rather than looping")
    }

    @Test
    fun `previous at the first slide wraps only when looping is on`() {
        val vm = viewModel().open(pdf(pages = 3))
        vm.isLooping = false
        vm.selectSlide(0)
        vm.previousSlide()
        assertEquals(0, vm.selectedSlideIndex, "with looping off it stays put")

        vm.isLooping = true
        vm.previousSlide()
        assertEquals(2, vm.selectedSlideIndex)
    }

    @Test
    fun `selectSlide ignores out-of-range indices`() {
        val vm = viewModel().open(pdf(pages = 3))
        vm.selectSlide(1)
        vm.selectSlide(-1)
        vm.selectSlide(99)
        assertEquals(1, vm.selectedSlideIndex)
    }

    @Test
    fun `navigation on an empty deck is harmless`() {
        val vm = viewModel()
        vm.nextSlide()
        vm.previousSlide()
        assertEquals(0, vm.selectedSlideIndex)
    }

    @Test
    fun `the instance-link callbacks fire even with no local slides`() {
        // Controller mode does not mirror the primary's deck, so next/prev must still be forwarded.
        val vm = viewModel()
        var next = 0
        var previous = 0
        vm.nextSlide { next++ }
        vm.previousSlide { previous++ }
        assertEquals(1, next)
        assertEquals(1, previous)
    }

    // ── Backward-entry flag ─────────────────────────────────────────────────────

    @Test
    fun `stepping backwards is flagged so the slide enters fully built`() {
        // Real PowerPoint/Keynote show a slide you step BACK onto with all its builds complete.
        val vm = viewModel().open(pdf(pages = 3))
        vm.selectSlide(2)

        vm.previousSlide()
        assertTrue(vm.consumeEnteredViaPreviousSlide())
        assertFalse(vm.consumeEnteredViaPreviousSlide(), "the flag is one-shot")
    }

    @Test
    fun `stepping forwards does not set the backward flag`() {
        val vm = viewModel().open(pdf(pages = 3))
        vm.previousSlide() // at slide 0 with looping on -> wraps, sets the flag
        vm.consumeEnteredViaPreviousSlide()

        vm.nextSlide()
        assertFalse(vm.consumeEnteredViaPreviousSlide(), "forward navigation starts a slide unbuilt")
    }

    @Test
    fun `clicking a thumbnail does not set the backward flag`() {
        val vm = viewModel().open(pdf(pages = 3))
        vm.selectSlide(2)
        vm.previousSlide()
        vm.consumeEnteredViaPreviousSlide()

        vm.selectSlide(0)
        assertFalse(vm.consumeEnteredViaPreviousSlide(), "only genuine back-stepping counts")
    }

    // ── Closing ─────────────────────────────────────────────────────────────────

    @Test
    fun `removing the open presentation falls back to another`() {
        val first = pdf(pages = 2)
        val vm = viewModel().open(first)
        val second = pdf(pages = 3)
        vm.addPresentation(second)
        awaitUntil("second deck") { !vm.isLoading && vm.slideFiles.size == 3 }

        vm.removePresentation(second)
        assertEquals(1, vm.presentations.size)
        assertEquals(first.absolutePath, vm.selectedPresentation?.absolutePath)
    }

    @Test
    fun `removing the only presentation clears the slides`() {
        val file = pdf(pages = 2)
        val vm = viewModel().open(file)

        vm.removePresentation(file)
        assertTrue(vm.presentations.isEmpty())
        assertNull(vm.selectedPresentation)
        assertTrue(vm.slideFiles.isEmpty(), "no stale slides may remain after closing")
        assertEquals(0, vm.selectedSlideIndex)
    }

    @Test
    fun `removing a presentation that is not open is harmless`() {
        val vm = viewModel().open(pdf(pages = 2))
        vm.removePresentation(File(dir, "other.pdf"))
        assertEquals(1, vm.presentations.size)
    }

    @Test
    fun `clearing closes everything`() {
        val vm = viewModel().open(pdf(pages = 3))
        vm.clearPresentations()

        assertTrue(vm.presentations.isEmpty())
        assertNull(vm.selectedPresentation)
        assertTrue(vm.slideFiles.isEmpty())
        assertEquals(0, vm.totalSlides)
        assertEquals(0, vm.selectedSlideIndex)
        assertFalse(vm.isLoading)
    }

    // ── Disk cache ──────────────────────────────────────────────────────────────

    @Test
    fun `a second open of the same deck is served from the cache`() {
        val file = pdf(pages = 3)
        val first = viewModel().open(file)
        val renderedPaths = first.slideFiles.map { it.absolutePath }

        val second = viewModel().open(file)
        assertEquals(renderedPaths, second.slideFiles.map { it.absolutePath }, "the cached render is reused")
    }

    // ── Settings ────────────────────────────────────────────────────────────────

    @Test
    fun `playback settings are seeded from the media settings`() {
        val vm = viewModel(
            AppSettings(
                presentationSettings = PresentationSettings(isLooping = false, transitionDuration = 1200f),
            ),
        )
        assertFalse(vm.isLooping)
        assertEquals(1200f, vm.transitionDuration)
    }

    @Test
    fun `defaults apply when no settings are supplied`() {
        val vm = viewModel()
        assertTrue(vm.isLooping)
        assertEquals(500f, vm.transitionDuration)
        assertEquals(AnimationType.CROSSFADE, vm.animationType)
    }

    @Test
    fun `each animation type maps from its stored name`() {
        val cases = mapOf(
            Constants.ANIMATION_FADE to AnimationType.FADE,
            Constants.ANIMATION_SLIDE_LEFT to AnimationType.SLIDE_LEFT,
            Constants.ANIMATION_SLIDE_RIGHT to AnimationType.SLIDE_RIGHT,
            Constants.ANIMATION_NONE to AnimationType.NONE,
        )
        for ((stored, expected) in cases) {
            val vm = viewModel(AppSettings(presentationSettings = PresentationSettings(animationType = stored)))
            assertEquals(expected, vm.animationType, "for stored value $stored")
        }
    }

    @Test
    fun `an unrecognised animation name falls back to crossfade`() {
        val vm = viewModel(AppSettings(presentationSettings = PresentationSettings(animationType = "not-a-type")))
        assertEquals(AnimationType.CROSSFADE, vm.animationType)
    }

    @Test
    fun `playback settings can be overridden at runtime`() {
        val vm = viewModel()
        vm.autoScrollInterval = 12f
        vm.isLooping = false
        vm.animationType = AnimationType.NONE
        assertEquals(12f, vm.autoScrollInterval)
        assertFalse(vm.isLooping)
        assertEquals(AnimationType.NONE, vm.animationType)
    }

    // ── Notes and deck ──────────────────────────────────────────────────────────

    @Test
    fun `a pdf exposes no presenter notes`() {
        // PDFs carry no speaker notes; the list must be empty rather than null-padded.
        val vm = viewModel().open(pdf(pages = 2))
        assertTrue(vm.slideNotes.isEmpty() || vm.slideNotes.all { it.isEmpty() })
    }

    @Test
    fun `a deck is exposed for playback`() {
        val vm = viewModel().open(pdf(pages = 2))
        assertNotNull(vm.deck, "the parsed deck drives the animated player")
    }
}
