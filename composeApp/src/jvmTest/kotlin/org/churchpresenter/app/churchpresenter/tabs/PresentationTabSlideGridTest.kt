@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The slide grid — the half of the Presentation tab that only exists once a deck is loaded.
 *
 * `PresentationTabTest` covers the empty state and said this part needed a rasterized deck. It does
 * not: `PresentationViewModel.slideFiles` is a public `SnapshotStateList<File>`, so a test can put
 * real JPEGs in it directly and the grid renders from those. The rasterizer's job is producing those
 * files, and that is the Presentation Engine's own suite to cover — not this tab's.
 *
 * That distinction is the point. What is deck-bound is *making* the slides; everything this tab does
 * with them — laying them out, tracking the selection, moving between them, enabling the controls
 * that were dead while empty — is ordinary Compose over a list of files.
 *
 * Decoding is wrapped in a `try`/`catch` that yields null, and `SlideThumbnail` takes a nullable
 * bitmap, so the grid also renders for a file that will not decode. Real images are written anyway:
 * a fixture that relied on the failure path would be testing the fallback rather than the tab.
 */
class PresentationTabSlideGridTest {

    /** Writes [count] small, genuinely decodable JPEGs, as the rasterizer would leave behind. */
    private fun slideFiles(count: Int): Pair<File, List<File>> {
        val dir = Files.createTempDirectory("cp-presentation-slides").toFile()
        val files = (1..count).map { n ->
            File(dir, "slide-$n.jpg").also { f ->
                ImageIO.write(BufferedImage(16, 9, BufferedImage.TYPE_INT_RGB), "jpg", f)
            }
        }
        return dir to files
    }

    private fun withSlides(count: Int,
        block: ComposeUiTest.(vm: PresentationViewModel, reports: PresentationReports) -> Unit) {
        val (dir, files) = slideFiles(count)
        try {
            presentationTab { vm, reports ->
                vm.slideFiles.addAll(files)
                waitForIdle()
                block(vm, reports)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun ComposeUiTest.slideThumbnail(n: Int): SemanticsNodeInteraction {
        waitUntil("slide $n's thumbnail to have decoded", 5_000) {
            onAllNodesWithContentDescription("Slide $n")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        return onNodeWithContentDescription("Slide $n")
    }

    // ── The grid itself ─────────────────────────────────────────────────────────

    @Test
    fun `every slide in the deck gets a thumbnail`() = withSlides(4) { vm, _ ->
        assertEquals(4, vm.slideFiles.size)
        // Each thumbnail is described by its slide number, which is also what an operator reads off
        // the screen when someone says "go to slide three".
        (1..4).forEach { n ->
            slideThumbnail(n).assertExists("slide $n must be in the grid")
        }
    }

    @Test
    fun `the grid is numbered from one, not from zero`() = withSlides(3) { _, _ ->
        slideThumbnail(1).assertExists()
        assertTrue(
            onAllNodesWithContentDescription("Slide 0")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty(),
            "a deck's first slide is slide 1 to everyone who is not a programmer",
        )
    }

    // ── Selecting ───────────────────────────────────────────────────────────────

    @Test
    fun `clicking a thumbnail selects that slide`() = withSlides(4) { vm, _ ->
        slideThumbnail(3).performScrollTo().performClick()
        waitForIdle()

        // Zero-based on the inside, one-based on screen — the off-by-one between them is exactly
        // the kind of thing that puts the wrong slide up.
        assertEquals(2, vm.selectedSlideIndex)
    }

    @Test
    fun `the first slide is selected when a deck arrives`() = withSlides(3) { vm, _ ->
        assertEquals(0, vm.selectedSlideIndex, "a freshly loaded deck starts at its first slide")
    }

    @Test
    fun `selecting a different slide moves the selection rather than adding one`() =
        withSlides(4) { vm, _ ->
            slideThumbnail(2).performScrollTo().performClick()
            waitForIdle()
            slideThumbnail(4).performScrollTo().performClick()
            waitForIdle()

            assertEquals(3, vm.selectedSlideIndex, "the last click wins")
        }

    // ── Controls that were dead while the tab was empty ─────────────────────────

    @Test
    fun `clear becomes usable once a deck is loaded`() = withSlides(2) { _, _ ->
        // `PresentationTabTest` pins the other half of this: present but disabled with no deck.
        presentationButton(PresentationLabel.CLEAR).assertIsEnabled()
    }

    @Test
    fun `clearing reports to the host`() = withSlides(2) { _, reports ->
        presentationButton(PresentationLabel.CLEAR).performClick()
        waitForIdle()

        assertEquals(1, reports.clears)
    }

    @Test
    fun `a loaded deck can be added to the schedule`() = withSlides(3) { _, _ ->
        // With no deck there is nothing to schedule; with one, the option has to appear or the deck
        // cannot be put into a service order at all.
        assertTrue(
            hasPresentationButton(PresentationLabel.ADD_TO_SCHEDULE),
            "a loaded deck must be schedulable: ${renderedText()}",
        )
    }

    // ── The deck changing under the grid ────────────────────────────────────────

    @Test
    fun `loading a shorter deck drops the thumbnails that are gone`() = withSlides(5) { vm, _ ->
        slideThumbnail(5).assertExists()

        vm.slideFiles.removeAt(4)
        vm.slideFiles.removeAt(3)
        waitForIdle()

        assertTrue(
            onAllNodesWithContentDescription("Slide 5")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty(),
            "a slide that is no longer in the deck must leave the grid",
        )
        slideThumbnail(3).assertExists("and the rest must stay")
    }
}
