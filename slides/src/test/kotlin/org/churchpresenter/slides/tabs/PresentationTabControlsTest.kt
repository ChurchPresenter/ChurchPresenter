@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.testing.showsContainingText
import org.churchpresenter.slides.presenter.PresentationFrame
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import org.churchpresenter.slides.FakeSlidesOutput
import org.churchpresenter.slides.SlidesOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresentationTabControlsTest {

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun withFakeSlides(
        count: Int,
        presenterManager: SlidesOutput? = null,
        block: ComposeUiTest.(vm: PresentationViewModel, reports: PresentationReports) -> Unit,
    ) {
        val (dir, files) = fakeSlideFiles(count)
        try {
            presentationTab(presenterManager = presenterManager) { vm, reports ->
                vm.slideFiles.addAll(files)
                // The thumbnails are decoded off the composition, so an idle composition does not
                // mean they are on screen yet. Waiting for the first one — a positive signal — is
                // what makes this independent of whatever ran before it.
                waitUntil {
                    onAllNodesWithContentDescription("Slide 1").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithContentDescription("Slide 1").performClick()
                waitForIdle()
                block(vm, reports)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── Keyboard: slide navigation ───────────────────────────────────────────────

    @Test
    fun `the right arrow advances to the next slide`() = withFakeSlides(3) { vm, _ ->
        assertEquals(0, vm.selectedSlideIndex)

        press(Key.DirectionRight)

        assertEquals(1, vm.selectedSlideIndex)
    }

    @Test
    fun `the down arrow also advances to the next slide`() = withFakeSlides(3) { vm, _ ->
        press(Key.DirectionDown)

        assertEquals(1, vm.selectedSlideIndex)
    }

    @Test
    fun `page down also advances to the next slide`() = withFakeSlides(3) { vm, _ ->
        press(Key.PageDown)

        assertEquals(1, vm.selectedSlideIndex)
    }

    @Test
    fun `the left arrow moves back to the previous slide`() = withFakeSlides(3) { vm, _ ->
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        assertEquals(2, vm.selectedSlideIndex)

        press(Key.DirectionLeft)

        assertEquals(1, vm.selectedSlideIndex)
    }

    @Test
    fun `page up also moves back to the previous slide`() = withFakeSlides(3) { vm, _ ->
        press(Key.DirectionRight)

        press(Key.PageUp)

        assertEquals(0, vm.selectedSlideIndex)
    }

    @Test
    fun `arrow keys do nothing while no deck is loaded`() = presentationTab { vm, _ ->
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        press(Key.PageDown)

        assertEquals(0, vm.selectedSlideIndex)
    }

    // ── Keyboard: play/pause ─────────────────────────────────────────────────────

    @Test
    fun `spacebar starts and stops auto-advance`() = withFakeSlides(3) { vm, _ ->
        assertFalse(vm.isPlaying)

        press(Key.Spacebar)
        assertTrue(vm.isPlaying)

        press(Key.Spacebar)
        assertFalse(vm.isPlaying)
    }

    // ── Keyboard: clicker blank-screen key ───────────────────────────────────────

    @Test
    fun `the B key toggles freeze while the presentation is live`() {
        val presenter = FakeSlidesOutput()
        presenter.setPresentingMode(Presenting.PRESENTATION)
        withFakeSlides(3, presenterManager = presenter) { _, reports ->
            press(Key.B)

            assertEquals(1, reports.freezeToggles)
        }
    }

    @Test
    fun `the period key also toggles freeze while the presentation is live`() {
        val presenter = FakeSlidesOutput()
        presenter.setPresentingMode(Presenting.PRESENTATION)
        withFakeSlides(3, presenterManager = presenter) { _, reports ->
            press(Key.Period)

            assertEquals(1, reports.freezeToggles)
        }
    }

    @Test
    fun `the B key does nothing when the presentation is not live`() {
        val presenter = FakeSlidesOutput()
        withFakeSlides(3, presenterManager = presenter) { _, reports ->
            press(Key.B)

            assertEquals(0, reports.freezeToggles)
        }
    }

    @Test
    fun `the B key does nothing with no output wired`() = withFakeSlides(3) { _, reports ->
        press(Key.B)

        assertEquals(0, reports.freezeToggles)
    }

    // ── Freeze button ─────────────────────────────────────────────────────────────

    @Test
    fun `the freeze button exists once a presenter manager is wired in, even with no deck loaded`() {
        val presenter = FakeSlidesOutput()
        presentationTab(presenterManager = presenter) { _, _ ->
            presentationButton(PresentationLabel.BLANK_OUTPUT).assertExists()
        }
    }

    // ── Double-clicking a slide thumbnail ─────────────────────────────────────────

    @Test
    fun `double-clicking a thumbnail takes it live`() {
        val presenter = FakeSlidesOutput()
        withFakeSlides(3, presenterManager = presenter) { _, _ ->
            onNodeWithContentDescription("Slide 2").performTouchInput {
                down(center)
                up()
                advanceEventTime(50)
                down(center)
                up()
            }
            waitForIdle()

            assertEquals(Presenting.PRESENTATION, presenter.onAir.value)
            assertTrue(presenter.showPresenterWindow.value)
        }
    }

    private fun ComposeUiTest.doubleClick(description: String) {
        onNodeWithContentDescription(description).performTouchInput {
            down(center)
            up()
            advanceEventTime(50)
            down(center)
            up()
        }
        waitForIdle()
    }

    @Test
    fun `double-clicking the last thumbnail takes it live with nothing after it`() {
        val presenter = FakeSlidesOutput()
        withFakeSlides(3, presenterManager = presenter) { vm, _ ->
            vm.isLooping = false
            doubleClick("Slide 3")

            waitUntil("the slide sent", 5_000) { presenter.liveSlide.value?.second == 2 }
            assertEquals(null, presenter.nextSlide.value)
        }
    }

    @Test
    fun `Go Live on the last slide sends nothing after it`() {
        val presenter = FakeSlidesOutput()
        withFakeSlides(3, presenterManager = presenter) { vm, _ ->
            vm.isLooping = false
            vm.selectSlide(2)
            waitForIdle()
            presentationButton(PresentationLabel.GO_LIVE).performClick()

            waitUntil("the slide sent", 5_000) { presenter.selectedSlide.value != null }
            assertEquals(2, presenter.liveSlide.value?.second)
            assertEquals(null, presenter.nextSlide.value)
        }
    }

    @Test
    fun `double-clicking a thumbnail with no output only selects it`() = withFakeSlides(3) { vm, _ ->
        doubleClick("Slide 2")

        assertEquals(1, vm.selectedSlideIndex)
    }

    @Test
    fun `a live frame with no builds shows no build counter`() {
        val presenter = FakeSlidesOutput()
        withFakeSlides(2, presenterManager = presenter) { _, _ ->
            presenter.presentationFrame.value = PresentationFrame(0, 100, 100, 1f, emptyList(), 0, 0)
            waitForIdle()

            assertFalse(showsContainingText("Build "))
        }
    }

    // ── Remote control dialog ─────────────────────────────────────────────────────
    // Not tested: PresentationRemoteDialog constructs a real java.awt.Window (via
    // ComposeDialog/JDialog), which throws HeadlessException in this test JVM — the same
    // fundamentally headless-incompatible class of gap as BibleTab's "Copy Verse" clipboard call.

    @Test
    fun `clicking the freeze button reports to the host`() {
        val presenter = FakeSlidesOutput()
        withFakeSlides(2, presenterManager = presenter) { _, reports ->
            presentationButton(PresentationLabel.BLANK_OUTPUT).assertIsEnabled()
            presentationButton(PresentationLabel.BLANK_OUTPUT).performClick()
            waitForIdle()

            assertEquals(1, reports.freezeToggles)
        }
    }
}
