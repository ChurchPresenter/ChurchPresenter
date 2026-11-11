@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Content sliding across a box as an announcement does (#991): its leading edge crosses in the set
 * time, whatever its length, and a set number of passes ends in `onFinished`.
 */
class SlideAcrossTest {

    private fun ComposeUiTest.slide(
        crossingMs: Int,
        contentWidth: Dp,
        movesPositive: Boolean = true,
        loopCount: Int = 0,
        onFinished: () -> Unit = {},
    ) {
        mainClock.autoAdvance = false
        setContent {
            Box(Modifier.size(1000.dp, 200.dp)) {
                SlideAcross(
                    crossingMs = crossingMs,
                    horizontal = true,
                    movesPositive = movesPositive,
                    alignment = Alignment.Center,
                    loopCount = loopCount,
                    onFinished = onFinished,
                ) { Box(Modifier.size(contentWidth, 40.dp).testTag("text")) }
            }
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
    }

    private fun ComposeUiTest.textLeft(): Dp = onNodeWithTag("text").getBoundsInRoot().left
    private fun ComposeUiTest.textRight(): Dp = onNodeWithTag("text").getBoundsInRoot().right

    @Test
    fun `it starts just off the left edge and its front reaches the right edge in the set time`() = runComposeUiTest {
        slide(crossingMs = 5_000, contentWidth = 200.dp)
        assertTrue(textRight() <= 1.dp, "starts off screen: ${textRight()}")
        mainClock.advanceTimeBy(5_000)
        assertEquals(1000f, textRight().value, 20f)
    }

    @Test
    fun `a longer line moves at the same speed`() = runComposeUiTest {
        slide(crossingMs = 5_000, contentWidth = 600.dp)
        mainClock.advanceTimeBy(2_500)
        assertEquals(500f, textRight().value, 20f, "halfway across at half the time, as a short line is")
    }

    @Test
    fun `from the right it starts off the right edge and moves left`() = runComposeUiTest {
        slide(crossingMs = 5_000, contentWidth = 200.dp, movesPositive = false)
        assertTrue(textLeft() >= 999.dp, "starts off screen: ${textLeft()}")
        mainClock.advanceTimeBy(5_000)
        assertEquals(0f, textLeft().value, 20f)
    }

    @Test
    fun `a set number of passes ends, and calls back once the last has left`() = runComposeUiTest {
        var finished = 0
        // Each pass: 1000 + 200 wide at 1000 per 2 s is 2.4 s.
        slide(crossingMs = 2_000, contentWidth = 200.dp, loopCount = 2, onFinished = { finished++ })
        mainClock.advanceTimeBy(4_600)
        assertFalse(finished > 0, "still on its second pass")
        mainClock.advanceTimeBy(400)
        assertEquals(1, finished)
    }
}
