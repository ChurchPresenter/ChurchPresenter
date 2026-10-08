@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.FakeSlidesOutput
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Go Live key on the Presentation tab: Enter on the tab root puts the selected slide on the
 * output, as the Go Live button does, and does nothing with no deck open.
 *
 * Slides are real JPEGs dropped into `slideFiles` (see [fakeSlideFiles]), as the other tab suites do.
 */
class PresentationGoLiveKeyTest {

    private fun ComposeUiTest.focusRoot() {
        onNodeWithTag(ROOT).requestFocus()
        waitForIdle()
    }

    private fun ComposeUiTest.pressEnter() {
        onNodeWithTag(ROOT).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
    }

    private fun withSlides(
        presenter: FakeSlidesOutput,
        block: ComposeUiTest.(vm: PresentationViewModel) -> Unit,
    ) {
        val (dir, files) = fakeSlideFiles(3)
        try {
            presentationTab(presenterManager = presenter) { vm, _ ->
                vm.slideFiles.addAll(files)
                waitUntil("the first thumbnail drawn", WAIT_MS) {
                    onAllNodesWithContentDescription("Slide 1").fetchSemanticsNodes().isNotEmpty()
                }
                block(vm)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `enter on the tab root puts the selected slide live`() {
        val presenter = FakeSlidesOutput()
        withSlides(presenter) { _ ->
            focusRoot()

            pressEnter()

            waitUntil("the slide on the output", WAIT_MS) { presenter.liveSlide.value != null }
            assertEquals(Presenting.PRESENTATION, presenter.onAir.value)
            assertTrue(presenter.showPresenterWindow.value)
            assertEquals(0, presenter.liveSlide.value?.second, "the selected slide")
        }
    }

    @Test
    fun `enter after clicking a slide puts that slide live`() {
        val presenter = FakeSlidesOutput()
        withSlides(presenter) { vm ->
            onNodeWithContentDescription("Slide 2").performClick()
            waitForIdle()
            assertEquals(1, vm.selectedSlideIndex)

            pressEnter()

            waitUntil("the slide on the output", WAIT_MS) { presenter.liveSlide.value != null }
            assertEquals(Presenting.PRESENTATION, presenter.onAir.value)
            assertEquals(1, presenter.liveSlide.value?.second, "the clicked slide")
        }
    }

    @Test
    fun `enter does nothing with no deck open`() {
        val presenter = FakeSlidesOutput()
        presentationTab(presenterManager = presenter) { _, _ ->
            focusRoot()

            pressEnter()

            assertEquals(Presenting.NONE, presenter.onAir.value)
            assertFalse(presenter.showPresenterWindow.value)
        }
    }

    private companion object {
        const val ROOT = "presentation_root"
        const val WAIT_MS = 5_000L
    }
}
