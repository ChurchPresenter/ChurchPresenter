@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The webcam half of the camera editor.
 *
 * A plain USB camera is configured differently from a capture card: no physical input to pick, and
 * the mode list comes from asking the device what it supports rather than from the driver. That
 * enumeration shells out, so on a machine with no camera it comes back empty — which is itself the
 * case worth pinning, because it is what every operator sees before they plug one in.
 *
 * [SourcePropertiesDeckLinkTest] covers the capture-card branch.
 */
class SourcePropertiesWebcamTest {

    private fun webcam(path: String = "/dev/video0", format: String = "") =
        SceneSource.CameraSource(
            id = "cam", name = "Camera",
            devicePath = path, deviceName = "USB Camera",
            isDeckLink = false, deckLinkIndex = -1,
            videoFormat = format,
        )

    @Test
    fun `a webcam offers a format selector`() {
        sourcePanel(webcam()) { _ ->
            assertTrue(
                onAllNodesWithText("VIDEO FORMAT", substring = true).fetchSemanticsNodes(false).isNotEmpty(),
                "the mode list is how an operator picks 720p over 480p",
            )
        }
    }

    @Test
    fun `a webcam with no reported formats still offers Auto`() {
        sourcePanel(webcam()) { _ ->
            // Nothing enumerated on a machine with no camera, but the selector must still work —
            // Auto is a valid choice and the only one available until a device appears.
            assertTrue(onAllNodesWithText("Auto", substring = true).fetchSemanticsNodes(false).isNotEmpty())
        }
    }

    @Test
    fun `choosing Auto clears any stored format`() {
        sourcePanel(webcam(format = "1280x720@30")) { get ->
            openDropdown("Auto (default)")
            onAllNodesWithText("Auto (default)", substring = true).onLast().performClick()
            waitForIdle()

            assertEquals("", (get() as SceneSource.CameraSource).videoFormat)
        }
    }

    @Test
    fun `a webcam shows no video-connection selector`() {
        sourcePanel(webcam()) { _ ->
            // Physical inputs are a capture-card idea; a USB camera has exactly one.
            assertTrue(onAllNodesWithText("VIDEO CONNECTION", substring = true).fetchSemanticsNodes(false).isEmpty())
        }
    }

    @Test
    fun `a camera with no device chosen offers no format selector either`() {
        sourcePanel(webcam(path = "")) { _ ->
            assertTrue(onAllNodesWithText("VIDEO FORMAT", substring = true).fetchSemanticsNodes(false).isEmpty())
        }
    }

    @Test
    fun `an unknown stored format falls back to Auto rather than showing a blank`() {
        sourcePanel(webcam(format = "a-mode-this-camera-lost")) { _ ->
            // The camera was swapped for one that cannot do the saved mode; the selector has to read
            // as something rather than empty.
            assertTrue(onAllNodesWithText("Auto", substring = true).fetchSemanticsNodes(false).isNotEmpty())
        }
    }
}
