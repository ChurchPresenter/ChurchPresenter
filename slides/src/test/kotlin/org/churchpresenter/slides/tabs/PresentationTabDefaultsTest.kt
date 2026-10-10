@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.composables.SLIDESHOW_HIDE_TOGGLE_TAG
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.FakeSlidesOutput
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresentationTabDefaultsTest {

    private fun withDefaults(block: ComposeUiTest.(PresentationViewModel, FakeSlidesOutput) -> Unit) {
        val settings = AppSettings()
        val vm = PresentationViewModel(settings)
        val output = FakeSlidesOutput()
        val (dir, files) = fakeSlideFiles(3)
        try {
            runComposeUiTest {
                setContent {
                    MaterialTheme { PresentationTab(appSettings = settings, viewModel = vm, presenterManager = output) }
                }
                vm.slideFiles.addAll(files)
                waitUntil("the thumbnails drawn", 5_000) {
                    onAllNodesWithContentDescription("Slide 1").fetchSemanticsNodes().isNotEmpty()
                }
                block(vm, output)
            }
        } finally {
            runCatching { vm.dispose() }
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the transport buttons step through the slides`() = withDefaults { vm, _ ->
        presentationButton("Next Image").performClick()
        waitForIdle()
        assertEquals(1, vm.selectedSlideIndex)

        presentationButton("Previous Image").performClick()
        waitForIdle()
        assertEquals(0, vm.selectedSlideIndex)
    }

    @Test
    fun `with no host to tell, the loop button still toggles looping`() = withDefaults { vm, _ ->
        val before = vm.isLooping
        presentationButton(if (before) "Loop On" else "Loop Off").performClick()
        waitForIdle()
        assertEquals(!before, vm.isLooping)
    }

    @Test
    fun `with no host to tell, clear still empties the tab`() = withDefaults { vm, _ ->
        presentationButton(PresentationLabel.CLEAR).performClick()
        waitForIdle()
        assertTrue(vm.slideFiles.isEmpty())
    }

    @Test
    fun `with no host to tell, the blank button leaves the output alone`() = withDefaults { _, output ->
        presentationButton(PresentationLabel.BLANK_OUTPUT).performClick()
        waitForIdle()
        assertEquals(Presenting.NONE, output.onAir.value)
    }

    @Test
    fun `a thumbnail given only a click handler ignores double-clicks and the eye`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme { SlideThumbnail(slide = null, slideNumber = 1, isSelected = false, onClick = { clicks++ }) }
        }
        onNodeWithText("Slide 1").performTouchInput {
            down(center); up(); advanceEventTime(50); down(center); up()
        }
        waitForIdle()
        onNodeWithTag(SLIDESHOW_HIDE_TOGGLE_TAG + 0).performClick()
        waitForIdle()
        assertEquals(0, clicks, "a double-click and the eye are not a click")
        onNodeWithContentDescription("Hide", substring = true).assertExists()

        onNodeWithText("Slide 1").performClick()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals(1, clicks)
    }

    private fun ComposeUiTest.hoverShowsTooltip(label: String) {
        mainClock.autoAdvance = false
        presentationButton(label).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        assertTrue(onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty(), "tooltip $label")
    }

    @Test
    fun `hovering the play button while playing names pause`() = withDefaults { vm, _ ->
        vm.isLooping = true
        vm.autoScrollInterval = 30f
        vm.togglePlayPause()
        waitForIdle()
        hoverShowsTooltip("Pause")
    }

    @Test
    fun `hovering the loop button with looping off says so`() = withDefaults { vm, _ ->
        vm.isLooping = false
        waitForIdle()
        hoverShowsTooltip("Loop Off")
    }
}
