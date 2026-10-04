@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A screen-capture layer's region: where on the desktop it is cut from and how big, typed in. A
 * position cannot go off the top or left of the screen, a size cannot be nothing, and what is not a
 * number is not taken. (Window mode lists the machine's real windows, so it is not driven here.)
 */
class ScreenCapturePropertiesTest {

    @Test
    fun `the region is typed in, and kept on the screen and at least a pixel big`() = runComposeUiTest {
        var source by mutableStateOf(SceneSource.ScreenCaptureSource(id = "c", name = "Capture"))
        setContent {
            MaterialTheme {
                Column { ScreenCaptureProperties(source, onUpdate = { source = it as SceneSource.ScreenCaptureSource }) }
            }
        }
        waitForIdle()
        onNodeWithText("Screen Region").assertExists()

        // In the order they are drawn: X, Y, width, height.
        val fields = onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("-5")
        fields[1].performTextReplacement("high")
        fields[2].performTextReplacement("0")
        fields[3].performTextReplacement("720")
        waitForIdle()

        assertEquals(0, source.captureX, "not off the left of the screen")
        assertEquals(0, source.captureY, "letters are not a position")
        assertEquals(1, source.captureWidth, "never nothing")
        assertEquals(720, source.captureHeight)
    }
}
