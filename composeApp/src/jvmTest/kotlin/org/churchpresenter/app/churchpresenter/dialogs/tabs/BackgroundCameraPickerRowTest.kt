@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.canvas.CameraDevice
import org.churchpresenter.canvas.CameraHost
import org.churchpresenter.app.churchpresenter.composables.OS_WITHOUT_ENUMERATOR
import org.churchpresenter.app.churchpresenter.composables.withOsName
import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.settings.BackgroundConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A camera background's device row, on a machine handed to it rather than asked: the dropdown of
 * what can be chosen, what a choice stores, and the warning for a display saved as a camera. The
 * OS is faked so nothing is enumerated and no privacy button is drawn.
 */
class BackgroundCameraPickerRowTest {

    private val studio = CameraDevice("Studio Cam", "avfoundation://0", "Studio Cam")
    private val balcony = CameraDevice("Balcony Cam", "avfoundation://1", "Balcony Cam")
    private val screen = CameraDevice("Capture screen 0", "avfoundation://2", "Capture screen 0")
    private val host = CameraHost(listOf(studio, balcony, screen), ffmpegAvailable = true)

    private fun row(
        initial: BackgroundConfig = BackgroundConfig(),
        machine: CameraHost = host,
        body: ComposeUiTest.(get: () -> BackgroundConfig) -> Unit,
    ) = withOsName(OS_WITHOUT_ENUMERATOR) {
        runComposeUiTest {
            var config by mutableStateOf(initial)
            setContent { MaterialTheme { Column { CameraPickerRow(config, machine) { config = it } } } }
            waitForIdle()
            body { config }
        }
    }

    @Test
    fun `choosing a camera stores it, and a display is not offered`() = row { get ->
        assertTrue(onAllNodesWithText("Capture screen 0").fetchSemanticsNodes().isEmpty())

        onAllNodesWithText("Studio Cam").onLast().performClick()
        waitForIdle()
        onAllNodesWithText("Balcony Cam").onLast().performClick()
        waitForIdle()

        assertEquals("avfoundation://1", get().camera.devicePath)
        assertEquals("Balcony Cam", get().camera.deviceName)
    }

    @Test
    fun `a display saved as the camera is named as one`() =
        row(BackgroundConfig(camera = CameraDeviceRef(devicePath = screen.path, deviceName = screen.name))) { _ ->
            onNodeWithText("This is a display, not a camera", substring = true).assertExists()
        }

    @Test
    fun `with no cameras there is no dropdown`() = row(machine = host.copy(devices = emptyList())) { _ ->
        assertTrue(onAllNodesWithText("Studio Cam").fetchSemanticsNodes().isEmpty())
        onNodeWithText("Camera Device", ignoreCase = true).assertExists()
    }
}
