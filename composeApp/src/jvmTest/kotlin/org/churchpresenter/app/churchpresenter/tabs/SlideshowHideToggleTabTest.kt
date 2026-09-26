package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.composables.SLIDESHOW_HIDE_TOGGLE_TAG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The eye on each picture and slide tile (#676), clicked for real.
 *
 * The picture tile used to take its click before its children did, so a click on the eye selected
 * the picture -- live, that put it on screen -- and hid nothing. These pin that the eye gets its own
 * click and the selection stays where it was.
 */
@OptIn(ExperimentalTestApi::class)
class SlideshowHideToggleTabTest {

    private fun ComposeUiTest.hiddenBadges() =
        onAllNodesWithText("Hidden").fetchSemanticsNodes(atLeastOneRootRequired = false).size

    @Test
    fun `the eye hides a picture without selecting it`() = picturesTab { vm, _ ->
        waitForIdle()
        assertEquals(0, vm.selectedImageIndex)

        onNodeWithTag(SLIDESHOW_HIDE_TOGGLE_TAG + 2).performClick()
        waitForIdle()

        assertTrue(vm.isHidden(vm.images[2]), "the picture is hidden")
        assertEquals(0, vm.selectedImageIndex, "and the selection did not move to it")
        assertEquals(1, hiddenBadges(), "its tile says so")
        assertTrue(
            onAllNodesWithText("· 1 hidden", substring = true).fetchSemanticsNodes().isNotEmpty(),
            "and the counter counts it",
        )
    }

    @Test
    fun `clicking the eye again shows the picture again`() = picturesTab { vm, _ ->
        onNodeWithTag(SLIDESHOW_HIDE_TOGGLE_TAG + 1).performClick()
        waitForIdle()
        onNodeWithTag(SLIDESHOW_HIDE_TOGGLE_TAG + 1).performClick()
        waitForIdle()

        assertEquals(emptySet(), vm.hiddenImageNames)
        assertEquals(0, hiddenBadges())
    }

    @Test
    fun `the eye hides a slide without selecting it`() = presentationTab { vm, _ ->
        vm.loadPresentationFromRemote("sched-eye", "/Volumes/Share/Eye.pptx", slideCount = 3) {
            "slide-$it".toByteArray()
        }
        waitUntil(timeoutMillis = 5_000) { !vm.isLoading && vm.slideFiles.size == 3 }
        waitForIdle()

        onNodeWithTag(SLIDESHOW_HIDE_TOGGLE_TAG + 1).performClick()
        waitForIdle()

        assertEquals(setOf(1), vm.hiddenSlides)
        assertEquals(0, vm.selectedSlideIndex)
        assertEquals(1, hiddenBadges())

        // Leave nothing behind for the next test in this fork: the store is the fork's own home.
        vm.toggleSlideHidden(1)
    }
}
