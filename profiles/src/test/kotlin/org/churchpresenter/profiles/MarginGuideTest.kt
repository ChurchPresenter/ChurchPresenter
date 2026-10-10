@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.sharedui.utils.FallbackOutputSize
import org.churchpresenter.sharedui.utils.OutputSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarginGuideTest {

    private val output = OutputSize(1920, 1080)

    private fun SkikoComposeUiTest.redPixels(): Int {
        val pixels = onNodeWithTag("guide").captureToImage().toPixelMap()
        var red = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val c = pixels[x, y]
                if (c.red > 0.8f && c.green < 0.2f && c.blue < 0.2f) red++
            }
        }
        return red
    }

    @Test
    fun `the guide is drawn inside the margins, on a band too, and not at all when nothing is left`() =
        runSkikoComposeUiTest(size = Size(400f, 300f), density = Density(1f)) {
            var margins by mutableStateOf(PreviewMargins(100, 100, 100, 100))
            var lowerThird by mutableStateOf(false)
            setContent {
                MaterialTheme {
                    Box(Modifier.size(192.dp, 108.dp).testTag("guide")) {
                        MarginGuide(
                            output, AppSettings(), margins, bandPercent = 40, lowerThird = lowerThird, color = Color.Red,
                        )
                    }
                }
            }
            assertTrue(redPixels() > 0, "full screen")
            lowerThird = true
            waitForIdle()
            assertTrue(redPixels() > 0, "lower third")
            margins = PreviewMargins(1000, 1000, 100, 100)
            waitForIdle()
            assertEquals(0, redPixels(), "no room left: nothing drawn")
        }

    @Test
    fun `the preview takes the first output that has reported its size`() {
        val assigned = AppSettings(
            projectionSettings = ProjectionSettings(
                screenAssignments = listOf(
                    ScreenAssignment(targetBoundsW = 0, targetBoundsH = 1080),
                    ScreenAssignment(targetBoundsW = 1024, targetBoundsH = 0),
                    ScreenAssignment(targetBoundsW = 1024, targetBoundsH = 768),
                ),
            ),
        )
        assertEquals(OutputSize(1024, 768), previewOutputSize(assigned))
        assertEquals(FallbackOutputSize, previewOutputSize(AppSettings()))
    }
}
