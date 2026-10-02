@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.churchpresenter.slides.FakeSlidesOutput
import org.churchpresenter.slides.SlidesOutput
import kotlin.test.Test
import kotlin.test.assertEquals

class PresentationTabInstanceLinkTest {

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithTag("presentation_root").performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.establishFocus() {
        onNodeWithTag("presentation_root").requestFocus()
        waitForIdle()
    }

    @Test
    fun `next-slide reaches Instance Link even with no local deck`() {
        var nextCalls = 0
        presentationTab(
            presenterManager = FakeSlidesOutput(),
            onInstanceLinkSendNextSlide = { nextCalls++ },
        ) { _, _ ->
            establishFocus()
            press(Key.DirectionRight)

            assertEquals(1, nextCalls)
        }
    }

    @Test
    fun `previous-slide reaches Instance Link even with no local deck`() {
        var previousCalls = 0
        presentationTab(
            presenterManager = FakeSlidesOutput(),
            onInstanceLinkSendPreviousSlide = { previousCalls++ },
        ) { _, _ ->
            establishFocus()
            press(Key.DirectionLeft)

            assertEquals(1, previousCalls)
        }
    }

    @Test
    fun `page down and page up also reach Instance Link with no local deck`() {
        var nextCalls = 0
        var previousCalls = 0
        presentationTab(
            presenterManager = FakeSlidesOutput(),
            onInstanceLinkSendNextSlide = { nextCalls++ },
            onInstanceLinkSendPreviousSlide = { previousCalls++ },
        ) { _, _ ->
            establishFocus()
            press(Key.PageDown)
            press(Key.PageUp)

            assertEquals(1, nextCalls)
            assertEquals(1, previousCalls)
        }
    }

    @Test
    fun `arrow keys do nothing with no deck and no Instance Link navigation wired`() {
        presentationTab(presenterManager = FakeSlidesOutput()) { vm, _ ->
            establishFocus()
            press(Key.DirectionRight)
            press(Key.DirectionLeft)

            assertEquals(0, vm.selectedSlideIndex)
        }
    }
}
