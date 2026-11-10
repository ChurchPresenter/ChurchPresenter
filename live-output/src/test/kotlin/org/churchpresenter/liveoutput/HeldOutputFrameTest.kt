package org.churchpresenter.liveoutput

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a held screen draws: its last frame, whatever goes live under it, until it is released; or
 * black. The content is a plain colour, so the frame is one pixel read.
 */
@OptIn(ExperimentalTestApi::class)
class HeldOutputFrameTest {

    private var live by mutableStateOf(Color.Red)
    private var hold by mutableStateOf<ScreenHold?>(null)

    private fun ComposeUiTest.show() = setContent {
        Box(Modifier.size(40.dp).testTag(FRAME)) {
            HeldOutputFrame(hold) {
                Box(Modifier.fillMaxSize().background(live))
            }
        }
    }

    private fun ComposeUiTest.pixel(): Color {
        waitForIdle()
        val pixels = onNodeWithTag(FRAME).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    @Test
    fun `an unheld screen draws what is live`() = runComposeUiTest {
        show()
        assertEquals(Color.Red, pixel())
        live = Color.Blue
        assertEquals(Color.Blue, pixel())
    }

    @Test
    fun `a held screen keeps its last frame until it is released`() = runComposeUiTest {
        show()
        assertEquals(Color.Red, pixel())
        hold = ScreenHold.HOLD
        assertEquals(Color.Red, pixel())
        live = Color.Blue
        assertEquals(Color.Red, pixel(), "what goes live under a hold stays off the screen")
        live = Color.Green
        assertEquals(Color.Red, pixel())
        hold = null
        assertEquals(Color.Green, pixel(), "a release shows what is live now")
    }

    @Test
    fun `a blanked screen is black, and holding it again takes the frame live then`() = runComposeUiTest {
        show()
        hold = ScreenHold.BLANK
        assertEquals(Color.Black, pixel())
        live = Color.Blue
        assertEquals(Color.Black, pixel())
        hold = ScreenHold.HOLD
        assertEquals(Color.Blue, pixel())
        live = Color.Green
        assertEquals(Color.Blue, pixel())
    }

    private companion object {
        const val FRAME = "frame"
    }
}
