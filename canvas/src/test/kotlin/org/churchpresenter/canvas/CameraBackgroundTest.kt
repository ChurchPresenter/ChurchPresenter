@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.camera.CameraDeviceRef
import kotlin.test.Test
import kotlin.test.assertEquals

class CameraBackgroundTest {

    @Test
    fun `a camera with no picture draws black, and lets the device go when it leaves`() = runComposeUiTest {
        val camera = CameraDeviceRef(devicePath = "unknown://camera", deviceName = "Nowhere")
        var shown by mutableStateOf(true)
        setContent {
            if (shown) CameraBackground(camera, Modifier.size(20.dp).testTag("bg"))
        }

        val pixels = onNodeWithTag("bg").captureToImage().toPixelMap()
        assertEquals(Color.Black, pixels[10, 10])

        shown = false
        waitForIdle()
    }
}
