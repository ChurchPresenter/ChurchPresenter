package org.churchpresenter.app.churchpresenter.viewmodel

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.PresentationSettings
import org.churchpresenter.app.churchpresenter.data.settings.ProjectionSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
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
 * The Instance Link follower path for presentations, plus the per-output render width and the
 * Keynote animate/static gate.
 *
 * A follower gets a schedule item whose file path points at the *primary's* disk — a network share
 * mounted differently, or not at all. Rather than failing, it downloads the already-rendered slide
 * JPEGs and populates exactly the same state the local path does, so navigation, thumbnails and
 * go-live all behave identically afterwards.
 */
class PresentationViewModelRemoteTest {

    private lateinit var dir: File
    private lateinit var testHome: File
    private var realHome: String? = null
    private val created = mutableListOf<PresentationViewModel>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-presentation-remote-test").toFile()

        // Bind CrashReporter's paths to the standard test home BEFORE swapping user.home below.
        // It resolves crashDir/installIdFile once at object init, and the presentation load path
        // breadcrumbs through it — if that first touch happened under this test's temporary home,
        // it would stay pointed at a directory tearDown deletes, breaking CrashReporterTest.
        CrashReporter.installId()

        // Own home per test: the remote slide cache lives under ~/.churchpresenter/slides and is
        // keyed by schedule-item id, so tests would otherwise reuse each other's downloads.
        realHome = System.getProperty("user.home")
        testHome = Files.createTempDirectory("cp-presentation-remote-home").toFile()
        System.setProperty("user.home", testHome.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        realHome?.let { System.setProperty("user.home", it) }
        dir.deleteRecursively()
        testHome.deleteRecursively()
    }

    private fun viewModel(settings: AppSettings? = null) =
        PresentationViewModel(settings).also { created.add(it) }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(30)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    /** Stand-in for a slide JPEG served by the primary; content is irrelevant, only the bytes. */
    private fun slideBytes(index: Int) = "slide-$index-bytes".toByteArray()

    private val remotePath = "/Volumes/PrimaryShare/decks/Sunday.pptx"

    // ── Downloading a remote deck ───────────────────────────────────────────────

    @Test
    fun `a remote deck downloads its slides and populates the same state as a local one`() {
        val vm = viewModel()
        vm.loadPresentationFromRemote("sched-1", remotePath, slideCount = 3) { slideBytes(it) }
        awaitUntil("the download") { !vm.isLoading && vm.slideFiles.size == 3 }

        assertEquals(3, vm.totalSlides)
        assertEquals(remotePath, vm.selectedPresentation?.absolutePath)
        assertEquals(1, vm.presentations.size)
        assertNull(vm.loadError)
        assertTrue(vm.slideFiles.all { it.exists() }, "slides are cached to real files")
    }

    @Test
    fun `the downloaded slides carry their bytes`() {
        val vm = viewModel()
        vm.loadPresentationFromRemote("sched-bytes", remotePath, slideCount = 2) { slideBytes(it) }
        awaitUntil("download") { !vm.isLoading && vm.slideFiles.size == 2 }

        assertEquals("slide-0-bytes", vm.slideFiles[0].readText())
        assertEquals("slide-1-bytes", vm.slideFiles[1].readText())
    }

    @Test
    fun `a remote deck gets one empty note per slide`() {
        // The wire protocol carries no speaker notes, but the notes list has to stay index-aligned
        // with the slides or the stage monitor reads the wrong entry.
        val vm = viewModel()
        vm.loadPresentationFromRemote("sched-notes", remotePath, slideCount = 3) { slideBytes(it) }
        awaitUntil("download") { !vm.isLoading && vm.slideFiles.size == 3 }
        assertEquals(3, vm.slideNotes.size)
        assertTrue(vm.slideNotes.all { it.isEmpty() })
    }

    @Test
    fun `the load generation bumps when a remote deck finishes`() {
        val vm = viewModel()
        val before = vm.loadGeneration
        vm.loadPresentationFromRemote("sched-gen", remotePath, slideCount = 2) { slideBytes(it) }
        awaitUntil("download") { !vm.isLoading && vm.slideFiles.size == 2 }
        assertTrue(vm.loadGeneration > before, "the tab re-focuses for arrow keys off this")
    }

    @Test
    fun `a cached remote deck is not downloaded twice`() {
        var fetches = 0
        val first = viewModel()
        first.loadPresentationFromRemote("sched-cache", remotePath, slideCount = 2) { fetches++; slideBytes(it) }
        awaitUntil("first download") { !first.isLoading && first.slideFiles.size == 2 }
        assertEquals(2, fetches)

        val second = viewModel()
        second.loadPresentationFromRemote("sched-cache", remotePath, slideCount = 2) { fetches++; slideBytes(it) }
        awaitUntil("second load") { !second.isLoading && second.slideFiles.size == 2 }
        assertEquals(2, fetches, "the cached slides must be reused on reconnect")
    }

    // ── Failure handling ────────────────────────────────────────────────────────

    @Test
    fun `a deck whose slides all fail to download reports an error`() {
        val vm = viewModel()
        vm.loadPresentationFromRemote("sched-fail", remotePath, slideCount = 3) { null }
        // NOT `!isLoading`: that is still false until the load coroutine reaches the main
        // dispatcher, so it would pass before the download had even begun.
        awaitUntil("the failure") { vm.loadError != null }

        assertTrue(vm.slideFiles.isEmpty())
        assertNotNull(vm.loadError, "a silent empty deck would look like a blank presentation")
    }

    @Test
    fun `a partially downloaded deck keeps the slides it got`() {
        // One flaky slide must not throw away the rest of the service's deck.
        val vm = viewModel()
        vm.loadPresentationFromRemote("sched-partial", remotePath, slideCount = 4) { index ->
            if (index == 2) null else slideBytes(index)
        }
        awaitUntil("the partial download") { vm.slideFiles.size == 3 || vm.loadError != null }

        assertEquals(3, vm.slideFiles.size, "the three good slides survive")
        assertNull(vm.loadError)
    }

    @Test
    fun `a failed remote deck leaves no cache directory behind`() {
        val vm = viewModel()
        vm.loadPresentationFromRemote("sched-cleanup", remotePath, slideCount = 2) { null }
        awaitUntil("the failure") { vm.loadError != null }

        val cacheDir = File(File(testHome, ".churchpresenter/slides"), "remote_sched-cleanup")
        assertFalse(cacheDir.exists(), "a half-built cache would be served as complete next time")
    }

    @Test
    fun `a zero-slide deck reports an error rather than appearing empty`() {
        val vm = viewModel()
        vm.loadPresentationFromRemote("sched-zero", remotePath, slideCount = 0) { slideBytes(it) }
        awaitUntil("the failure") { vm.loadError != null }
        assertNotNull(vm.loadError)
    }

    // ── Interaction with the open list ──────────────────────────────────────────

    @Test
    fun `a remote deck can be navigated like any other`() {
        val vm = viewModel()
        vm.loadPresentationFromRemote("sched-nav", remotePath, slideCount = 3) { slideBytes(it) }
        awaitUntil("download") { !vm.isLoading && vm.slideFiles.size == 3 }

        vm.nextSlide()
        assertEquals(1, vm.selectedSlideIndex)
        vm.previousSlide()
        assertEquals(0, vm.selectedSlideIndex)
        assertTrue(vm.consumeEnteredViaPreviousSlide(), "back-stepping still flags a built entry")
    }

    @Test
    fun `re-opening the same remote deck does not duplicate the entry`() {
        val vm = viewModel()
        vm.loadPresentationFromRemote("sched-dup", remotePath, slideCount = 2) { slideBytes(it) }
        awaitUntil("first") { !vm.isLoading && vm.slideFiles.size == 2 }

        vm.loadPresentationFromRemote("sched-dup", remotePath, slideCount = 2) { slideBytes(it) }
        awaitUntil("second") { !vm.isLoading && vm.slideFiles.size == 2 }
        assertEquals(1, vm.presentations.size)
    }

    @Test
    fun `loading a remote deck replaces the previous deck's slides`() {
        val vm = viewModel()
        vm.loadPresentationFromRemote("sched-a", remotePath, slideCount = 4) { slideBytes(it) }
        awaitUntil("first deck") { !vm.isLoading && vm.slideFiles.size == 4 }

        vm.loadPresentationFromRemote("sched-b", "/Volumes/PrimaryShare/decks/Other.pptx", slideCount = 2) {
            slideBytes(it)
        }
        awaitUntil("second deck") { !vm.isLoading && vm.slideFiles.size == 2 }
        assertEquals(2, vm.totalSlides, "no stale slides from the previous deck")
    }

    @Test
    fun `clearing removes a remote deck too`() {
        val vm = viewModel()
        vm.loadPresentationFromRemote("sched-clear", remotePath, slideCount = 2) { slideBytes(it) }
        awaitUntil("download") { !vm.isLoading && vm.slideFiles.size == 2 }

        vm.clearPresentations()
        assertTrue(vm.presentations.isEmpty())
        assertTrue(vm.slideFiles.isEmpty())
        assertNull(vm.selectedPresentation)
    }

    // ── Render width from the output configuration ──────────────────────────────

    /**
     * A 4K output must not be fed slides rendered for 1080p. The rasterizer fits the page's own
     * aspect ratio rather than forcing the literal pixel width, so this compares the configured
     * output against the default instead of asserting an exact size.
     */
    @Test
    fun `a larger configured output renders larger slides`() {
        val defaultVm = viewModel()
        val defaultFile = pdfFile(pages = 1)
        defaultVm.addPresentation(defaultFile)
        awaitUntil("default render") { !defaultVm.isLoading && defaultVm.slideFiles.isNotEmpty() }
        val defaultWidth = javax.imageio.ImageIO.read(defaultVm.slideFiles.first()).width

        val uhdVm = viewModel(
            AppSettings(
                projectionSettings = ProjectionSettings().withAssignment(
                    0, ScreenAssignment(targetBoundsW = 3840, targetBoundsH = 2160),
                ),
            ),
        )
        val uhdFile = pdfFile(pages = 1) // a distinct file, so the disk cache cannot serve the other render
        uhdVm.addPresentation(uhdFile)
        awaitUntil("4K render") { !uhdVm.isLoading && uhdVm.slideFiles.isNotEmpty() }
        val uhdWidth = javax.imageio.ImageIO.read(uhdVm.slideFiles.first()).width

        assertTrue(defaultWidth > 0, "a sensible default is used rather than zero")
        assertTrue(
            uhdWidth > defaultWidth,
            "a 4K output should render bigger slides than the default (got $uhdWidth vs $defaultWidth)",
        )
    }

    // ── Keynote animation gate ──────────────────────────────────────────────────

    @Test
    fun `a non-keynote deck is exposed for playback whatever the keynote setting`() {
        // The animateKeynote switch is a Keynote-only escape hatch; it must not hide a PDF or PPTX.
        val vm = viewModel(AppSettings(presentationSettings = PresentationSettings(animateKeynote = false)))
        val file = pdfFile(pages = 2)
        vm.addPresentation(file)
        awaitUntil("render") { !vm.isLoading && vm.slideFiles.isNotEmpty() }

        assertNotNull(vm.deck, "a PDF deck must still drive the player")
    }

    private fun pdfFile(pages: Int): File {
        val file = File(dir, "deck-${System.nanoTime()}.pdf")
        PDDocument().use { doc ->
            repeat(pages) { doc.addPage(PDPage()) }
            doc.save(file)
        }
        return file
    }
}
