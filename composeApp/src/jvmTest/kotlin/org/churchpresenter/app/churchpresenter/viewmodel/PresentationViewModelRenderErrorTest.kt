package org.churchpresenter.app.churchpresenter.viewmodel

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.churchpresenter.app.churchpresenter.models.PresentationLoadError
import presentation.engine.LoadResult
import presentation.engine.model.DeckLoadError
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What [PresentationViewModel] does when a deck will not load.
 *
 * The renderable-deck tests can't reach this: a real fixture parses successfully, so the branches
 * that map an engine [DeckLoadError] onto a UI error and abort the render never run. Here the deck
 * loader is swapped for one that returns a chosen failure ([PresentationViewModel.loadDeck] is a
 * per-instance seam), so the actual render orchestration executes and its failure path — set the
 * error, clear the loading flag, keep the slide list empty — is exercised end to end.
 *
 * The load is asynchronous with no completion callback, so tests wait on the observable end state
 * (`loadError` set and `isLoading` cleared) rather than a fixed pause; `loadError` starts null, so
 * the wait cannot pass before the render has actually run.
 */
class PresentationViewModelRenderErrorTest {

    private lateinit var dir: File
    private val created = mutableListOf<PresentationViewModel>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-presentation-error-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    private fun viewModel() = PresentationViewModel().also { created.add(it) }

    /**
     * An empty file with a supported extension: enough for the ViewModel to accept and open, while
     * its bytes are never parsed because [PresentationViewModel.loadDeck] is stubbed for the test.
     */
    private fun presentationFile(name: String = "deck.pdf") = File(dir, name).apply { writeText("") }

    /** Opens [file] with the stubbed loader and waits for the render to finish and report. */
    private fun PresentationViewModel.openExpectingError(file: File) {
        addPresentation(file)
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (!isLoading && loadError != null) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out waiting for the failed load of ${file.name} to report")
    }

    // ── toUiError: each engine error maps to the right UI error ──────────────────

    @Test
    fun `each engine load error maps to a UI error`() = with(viewModel()) {
        assertEquals(PresentationLoadError.PASSWORD_PROTECTED, DeckLoadError.PASSWORD_PROTECTED.toUiError())
        assertEquals(PresentationLoadError.EMPTY_DOCUMENT, DeckLoadError.EMPTY_DOCUMENT.toUiError())
        assertEquals(
            PresentationLoadError.RENDER_FAILED,
            DeckLoadError.UNSUPPORTED_FORMAT.toUiError(),
            "an unsupported format is surfaced as a generic render failure, not its own message",
        )
        assertEquals(PresentationLoadError.RENDER_FAILED, DeckLoadError.PARSE_FAILED.toUiError())
    }

    // ── the render aborts and reports when the deck will not load ────────────────

    @Test
    fun `a password-protected deck surfaces as a password error and renders nothing`() {
        val vm = viewModel()
        vm.loadDeck = { LoadResult.Failure(DeckLoadError.PASSWORD_PROTECTED) }

        vm.openExpectingError(presentationFile())

        assertEquals(PresentationLoadError.PASSWORD_PROTECTED, vm.loadError)
        assertTrue(vm.slideFiles.isEmpty(), "a deck that could not be opened has no slides")
        assertEquals(0, vm.totalSlides)
    }

    @Test
    fun `an empty document surfaces as an empty-document error`() {
        val vm = viewModel()
        vm.loadDeck = { LoadResult.Failure(DeckLoadError.EMPTY_DOCUMENT) }

        vm.openExpectingError(presentationFile())

        assertEquals(PresentationLoadError.EMPTY_DOCUMENT, vm.loadError)
    }

    @Test
    fun `a parse failure surfaces as a generic render error`() {
        val vm = viewModel()
        vm.loadDeck = { LoadResult.Failure(DeckLoadError.PARSE_FAILED) }

        vm.openExpectingError(presentationFile())

        assertEquals(PresentationLoadError.RENDER_FAILED, vm.loadError)
    }

    @Test
    fun `the loading flag is cleared after a failed load, not left spinning`() {
        val vm = viewModel()
        vm.loadDeck = { LoadResult.Failure(DeckLoadError.PARSE_FAILED) }

        vm.openExpectingError(presentationFile())

        assertFalse(vm.isLoading, "the finally block must clear isLoading whether the deck loaded or not")
    }

    @Test
    fun `a failing load does not leave a stale deck exposed for playback`() {
        val vm = viewModel()
        vm.loadDeck = { LoadResult.Failure(DeckLoadError.PARSE_FAILED) }

        vm.openExpectingError(presentationFile())

        assertEquals(null, vm.deck, "playback must not run against a deck that never loaded")
    }

    // ── the deck loads but its slides won't rasterize ────────────────────────────
    //
    // These use a genuinely-loaded deck (a real one-page PDF) and a real rasterizer, failing only
    // at the single frame-render step, so the per-slide-failure and no-slides branches run over
    // real deck data — no mocking of the engine.

    /** A real one-page PDF, so the deck loads and a real rasterizer is built; only the frame fails. */
    private fun onePagePdf(name: String = "deck.pdf"): File {
        val file = File(dir, name)
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(file)
        }
        return file
    }

    @Test
    fun `a deck whose every slide fails to rasterize reports a render failure`() {
        val vm = viewModel()
        vm.renderSlideFrame = { _, _ -> throw RuntimeException("no graphics pipeline") }

        vm.openExpectingError(onePagePdf())

        assertEquals(
            PresentationLoadError.RENDER_FAILED,
            vm.loadError,
            "a deck that loaded but produced no usable slides is a render failure, not a success",
        )
        assertTrue(vm.slideFiles.isEmpty(), "a slide that would not render must be left out, not shown blank")
    }

    @Test
    fun `an error thrown mid-render is caught and reported rather than escaping`() {
        // A loader that throws (rather than returning a Failure) lands in renderSlides' outer catch,
        // the backstop for anything the per-slide guard doesn't — a broken cache writer, say.
        val vm = viewModel()
        vm.loadDeck = { throw RuntimeException("engine blew up mid-parse") }

        vm.openExpectingError(presentationFile())

        assertEquals(PresentationLoadError.RENDER_FAILED, vm.loadError)
        assertFalse(vm.isLoading, "an error thrown mid-render must still clear the loading flag")
    }
}
