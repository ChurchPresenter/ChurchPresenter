@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.model.Fidelity
import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.presentationengine.model.RectPt
import org.churchpresenter.presentationengine.model.Slide
import org.churchpresenter.presentationengine.model.pdfDeck
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The banner that explains why an embedded video is not playing.
 *
 * A deck with embedded video degrades gracefully without VLC — the slide shows its static poster
 * forever — so the banner is the *only* thing telling an operator why, and it has to appear during
 * preparation rather than leave them wondering mid-service.
 *
 * **`presentationTab` has taken a `vlcAvailable` parameter all along, documented as deciding exactly
 * this banner, and no test ever passed it.** That is why the whole composable was uncovered: not
 * because VLC availability is environment-dependent — it is a plain parameter here — but because the
 * scaffolding was sitting unused. (`MediaTab`'s equivalent is driven the same way by
 * `MediaTabVlcUnavailableTest`.)
 *
 * These two error cases were briefly deleted on the theory that their extra deck rasterisations were
 * killing the CI runner — three jobs had died at exactly 15 minutes where a passing run took 18. The
 * theory was written down as falsifiable and then falsified: the run with them removed died at 15
 * minutes too, and a control run on `main` could not get a runner at all. They are restored, and the
 * episode is worth remembering as *the* argument for stating a hypothesis in a form that can lose.
 *
 * **Not asserted: which reason wins when both flags are set.** Swapping the two branches fails no
 * test here, and that is deliberate rather than an oversight — the real
 * `isVlcArchMismatch`/`isVlcLoadFailed` are mutually exclusive by construction (`isVlcLoadFailed`
 * excludes the arch case), so "both true" is a state the app cannot produce. Pinning an order for it
 * would be testing defensive code that never runs. They are parameters here only so the *wording*
 * stops depending on whether the machine running the tests has VLC installed.
 *
 * The deck is a synthetic deck over a **real one-page PDF**: the view model rasterises whatever
 * `loadDeck` returns, so the source file has to be openable, while the `slides` list is ours to
 * shape — which is the only way to get a `LayerSpec.Media` layer without an actual PowerPoint
 * carrying an embedded video.
 */
class PresentationTabVlcBannerTest {

    private companion object {
        const val TITLE = "VLC media player is required for media playback"
        const val INSTALL = "Please install VLC from videolan.org and restart the application"
        const val ARCH_MISMATCH_WORD = "architecture"
        const val LOAD_FAILED_WORD = "found but could not be loaded"
    }

    private val temps = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        temps.forEach { it.deleteRecursively() }
        temps.clear()
    }

    /** A real one-page PDF — the rasteriser has to be able to open it. */
    private fun pdf(): File {
        val dir = Files.createTempDirectory("cp-vlc-banner").toFile().also { temps += it }
        val file = File(dir, "deck.pdf")
        PDDocument().use { doc -> doc.addPage(PDPage()); doc.save(file) }
        return file
    }

    private fun deck(file: File, withVideo: Boolean) = pdfDeck(
        sourceFile = file,
        slideWidthPt = 720.0,
        slideHeightPt = 540.0,
        slides = listOf(
            Slide(
                index = 0,
                notes = "",
                transition = null,
                layers = if (withVideo) listOf(
                    LayerSpec.Media(
                        id = "media-0",
                        zIndex = 0,
                        boundsPt = RectPt(0.0, 0.0, 100.0, 100.0),
                        shapeIndex = 0,
                        contentRectPt = RectPt(0.0, 0.0, 100.0, 100.0),
                        mediaFile = null,
                    )
                ) else emptyList(),
                timeline = null,
                fidelity = Fidelity.NATIVE,
            )
        ),
    )

    /**
     * Waits for a load that started at [generationBefore] to finish.
     *
     * Keyed on `loadGeneration` rather than on `slideFiles.isNotEmpty()`: the second deck in a test
     * starts with the first one's slides still on screen, so a "has slides" wait returns instantly
     * and the assertions run against the *previous* deck. That cost a failure that looked like a
     * production bug — the banner appearing not to come back — and was this helper all along.
     */
    private fun ComposeUiTest.awaitLoad(vm: PresentationViewModel, generationBefore: Int) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (vm.loadGeneration != generationBefore && !vm.isLoading) { waitForIdle(); return }
            Thread.sleep(20)
        }
        throw AssertionError("the deck never finished rasterising")
    }

    private fun ComposeUiTest.countOf(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes(false).size

    /**
     * The banner's own dismiss button: the clickable "Clear" sitting in the banner's own row.
     *
     * Three things make this harder than it looks, and all three were found the hard way:
     *
     *  * `"Clear"` matches the `IconButton` **and** the `Icon` inside it, and only the outer one is
     *    clickable — hence `hasClickAction()`.
     *  * The tab has other `"Clear"` buttons. One belongs to a **presentation left in the shared
     *    test home by another suite** (`uploaded.pptx`), so how many exist depends on what ran
     *    before this class. Addressing "the only Clear" therefore passes alone and fails in a full
     *    run — which is what it did on CI while passing locally.
     *  * An ancestry matcher does not separate them either: the root is an ancestor of every button
     *    and does contain the banner's title.
     *
     * So it is located by row — the banner's title and its dismiss button share a `Row` with
     * `verticalAlignment = CenterVertically`, so the right button is the one whose centre falls
     * inside the title's vertical span. Exactly one must, and that is asserted rather than assumed.
     */
    private fun ComposeUiTest.dismissBanner() {
        val title = onAllNodesWithText(TITLE, substring = true)
            .fetchSemanticsNodes(false).single().boundsInRoot
        val clears = onAllNodes(hasContentDescription("Clear") and hasClickAction())
        val inTitleRow = clears.fetchSemanticsNodes(false).withIndex().filter { (_, node) ->
            node.boundsInRoot.center.y in title.top..title.bottom
        }
        assertEquals(
            1, inTitleRow.size,
            "expected exactly one dismiss button in the banner's row, found ${inTitleRow.size}",
        )
        clears[inTitleRow.single().index].performClick()
        waitForIdle()
    }

    /** Loads one synthetic deck through the real load path and waits for its slides. */
    private fun ComposeUiTest.load(vm: PresentationViewModel, withVideo: Boolean) {
        val file = pdf()
        val before = vm.loadGeneration
        vm.loadDeck = { LoadResult.Success(deck(file, withVideo)) }
        vm.addPresentation(file)
        awaitLoad(vm, before)
    }

    @Test
    fun `a deck with embedded video says why it will not play`() =
        presentationTab(vlcAvailable = false) { vm, _ ->
            load(vm, withVideo = true)

            assertEquals(1, countOf(TITLE))
            assertTrue(countOf(INSTALL) > 0, "and says what to do about it")
        }

    @Test
    fun `a corrupt install is not told to install it again`() =
        // "Install VLC" is unhelpful advice to someone who already has it — the reason line exists
        // to tell those cases apart, and both of them are error states rather than a missing app.
        presentationTab(vlcAvailable = false, vlcLoadFailed = true) { vm, _ ->
            load(vm, withVideo = true)

            assertEquals(1, countOf(TITLE))
            assertTrue(countOf(LOAD_FAILED_WORD) > 0, "it says the install is there but broken")
            assertEquals(0, countOf(INSTALL), "and does not tell them to install what they have")
        }

    @Test
    fun `the wrong-architecture case says so`() =
        // An x86 VLC under an arm64 JVM: present, loadable-looking, and never going to work. This is
        // the one where "reinstall" alone would send an operator round in circles.
        presentationTab(vlcAvailable = false, vlcArchMismatch = true) { vm, _ ->
            load(vm, withVideo = true)

            assertTrue(countOf(ARCH_MISMATCH_WORD) > 0)
            assertEquals(0, countOf(INSTALL))
        }

    @Test
    fun `with VLC present there is nothing to explain`() =
        // The positive twin for the gate: `vlcAvailable` defaults to true here.
        presentationTab { vm, _ ->
            load(vm, withVideo = true)

            assertEquals(0, countOf(TITLE))
        }

    @Test
    fun `a deck with no video is not warned about`() =
        // Most decks are slides only. Warning about VLC on those would be noise on every load for
        // an operator who has no video to play.
        presentationTab(vlcAvailable = false) { vm, _ ->
            load(vm, withVideo = false)

            assertEquals(0, countOf(TITLE))
        }

    @Test
    fun `dismissing it puts it away`() =
        presentationTab(vlcAvailable = false) { vm, _ ->
            load(vm, withVideo = true)
            assertEquals(1, countOf(TITLE))

            dismissBanner()

            assertEquals(0, countOf(TITLE))
        }

    @Test
    fun `loading another deck brings the warning back`() =
        // The dismissal is keyed on the load generation, so it means "not for this deck" rather than
        // "never again" — an operator who waved it away on a slides-only rehearsal still gets told
        // when they open the deck that actually has the video in it.
        presentationTab(vlcAvailable = false) { vm, _ ->
            load(vm, withVideo = true)
            dismissBanner()
            assertEquals(0, countOf(TITLE))

            load(vm, withVideo = true)

            assertEquals(1, countOf(TITLE), "a dismissal must not carry across decks")
        }
}
