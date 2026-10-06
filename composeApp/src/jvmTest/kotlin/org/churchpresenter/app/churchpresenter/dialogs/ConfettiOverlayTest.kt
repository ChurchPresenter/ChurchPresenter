@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

class ConfettiOverlayTest {

    private fun confettiPixels(frames: Int): Int {
        var count = 0
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                Box(Modifier.testTag("sky").size(200.dp).background(Color.White)) {
                    ConfettiOverlay()
                }
            }
            repeat(frames) { mainClock.advanceTimeByFrame() }
            val pixels = onNodeWithTag("sky").captureToImage().toPixelMap()
            for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
                if (pixels[x, y] != Color.White) count++
            }
        }
        return count
    }

    @Test
    fun `the confetti falls into view and is drawn`() {
        assertTrue(confettiPixels(frames = 120) > 0, "after two seconds some confetti must be on screen")
    }

    @Test
    fun `confetti that falls off the bottom comes back in at the top`() {
        assertTrue(confettiPixels(frames = 1_200) > 0, "the confetti must keep falling rather than run out")
    }
}
