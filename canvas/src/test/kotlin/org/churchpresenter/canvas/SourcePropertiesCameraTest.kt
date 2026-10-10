@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Camera source, on a machine with no capture hardware — which is the state these tests can pin.
 *
 * **Why the machine is pinned.** The device list is built by `listCameraDevicesWithDeckLink()`, which
 * asks the DeckLink SDK and then shells out per platform: `/dev/video*` on Linux, `ffmpeg
 * -list_devices` plus PowerShell on Windows, `system_profiler` plus `ffmpeg` on macOS. What comes back
 * is whatever hardware is plugged into the machine running the suite, so a test that asserted on it
 * would pass on one developer's laptop and fail on the next. Every test here hands the panel a
 * [CameraHost] instead, so it asks the machine nothing, and runs under [withOsName] naming an OS with
 * no enumerator, so Refresh spawns nothing either. "No cameras found" is a real state an operator
 * sees, and it is the one that can be asserted deterministically.
 *
 * A host can also list devices, which reaches the device dropdown, what choosing an entry writes,
 * and the format dropdown -- empty but for Auto, as the faked OS has no format enumerator.
 *
 * The DeckLink branch is `SourcePropertiesDeckLinkTest`. Known gap, downstream of real hardware:
 *
 *  * **The formats ffmpeg lists** for a real device.
 *
 * *Which* hint each platform gets is pinned in `CameraToolHintsTest`; this suite checks that the
 * panel draws them.
 */
class SourcePropertiesCameraTest {

    /**
     * Renders the camera panel on a machine that is handed to it — [host] — rather than asked.
     *
     * It used to ask. [CameraDeviceCatalog] is process-wide, so the panel had to re-enumerate on
     * composition and the test waited for that answer to land; on a slow machine's cold JVM the
     * first answer took longer than the wait allowed (#701). A pinned host has answered before the
     * first frame. The OS is still faked, because the privacy button and the hints branch on it.
     */
    private fun cameraPanel(
        source: SceneSource.CameraSource = Fixture.camera(),
        host: CameraHost = NO_CAMERAS,
        block: ComposeUiTest.(get: () -> SceneSource) -> Unit,
    ) = withOsName(OS_WITHOUT_ENUMERATOR) {
        sourcePanel(source, cameraHost = host) { get -> block(get) }
    }

    // ── What the panel displays ───────────────────────────────────────────────

    @Test
    fun `the section is headed and the refresh button offered`() = cameraPanel { _ ->
        onNodeWithText(Label.CAMERA).assertIsDisplayed()
        onNodeWithText("Refresh Cameras").assertExists()
    }

    @Test
    fun `a machine with no cameras says so instead of offering an empty dropdown`() = cameraPanel { _ ->
        onNodeWithText("No cameras found").assertExists("the operator must be told why there is no picker")
        assertEquals(0, countOf("CAMERA"), "and no dropdown may be left behind")
    }

    @Test
    fun `a machine with ffmpeg is not told to find it`() = cameraPanel { _ ->
        onNodeWithText("could not find its copy", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a machine without ffmpeg is told where to point the app at one`() =
        cameraPanel(host = NO_CAMERAS.copy(ffmpegAvailable = false)) { _ ->
            onNodeWithText("No cameras found").assertExists()
            onNodeWithText("could not find its copy", substring = true)
                .assertExists("without ffmpeg the operator must be told why nothing is listed")
        }

    @Test
    fun `the camera panel adds no field and no checkbox to the header`() = cameraPanel { _ ->
        textFields().assertCountEquals(6)
        checkboxes().assertCountEquals(0)
    }

    @Test
    fun `the refresh button is the panel's only button`() = cameraPanel { _ ->
        roleButtons().assertCountEquals(1)
        onNodeWithText("Refresh Cameras").assertHasClickAction()
    }

    @Test
    fun `an unknown platform is offered no privacy settings button`() = cameraPanel { _ ->
        // Neither the macOS nor the Windows privacy button belongs on an OS that is neither, and
        // the count above is what would catch one appearing — this says why that number is 1.
        onNodeWithText("Open Camera Privacy Settings").assertDoesNotExist()
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Test
    fun `pressing Refresh re-enumerates without changing the source`() = cameraPanel { get ->
        val before = get()

        onNodeWithText("Refresh Cameras").performScrollTo().performClick()
        waitForIdle()

        assertEquals(before, get(), "refreshing the device list must not edit the source")
        onNodeWithText("No cameras found").assertExists("and the empty result is reported again")
    }

    @Test
    fun `pressing Refresh twice is harmless`() = cameraPanel { get ->
        val before = get()
        repeat(2) {
            onNodeWithText("Refresh Cameras").performScrollTo().performClick()
            waitForIdle()
        }

        assertEquals(before, get())
        onNodeWithText("Refresh Cameras").assertExists()
    }

    // ── A stored device the machine cannot see ────────────────────────────────

    @Test
    fun `a source pointing at a device that is gone still renders its header`() {
        // The camera was configured on another machine; nothing here can enumerate it. The panel must
        // still be usable — the operator has to be able to rename and reposition the source.
        val missing = Fixture.camera().copy(
            devicePath = "avfoundation://0", deviceName = "Studio Cam", videoFormat = "1920x1080@30",
        )
        cameraPanel(missing) { _ ->
            onNodeWithText("No cameras found").assertExists()
            assertFieldShows("Cam 1", "the Name field")
            assertEquals(0, countOf("VIDEO FORMAT"), "no format picker without a device to enumerate")
        }
    }

    @Test
    fun `a source flagged as DeckLink renders without the SDK present`() {
        // `isDeckLink` is stored on the source, but every DeckLink control is gated on the SDK being
        // loadable as well — so with no native library the panel must fall back cleanly rather than
        // half-render a connection picker it cannot populate.
        val deckLink = Fixture.camera().copy(isDeckLink = true, deckLinkIndex = 0)
        cameraPanel(deckLink) { _ ->
            onNodeWithText("No cameras found").assertExists()
            assertEquals(0, countOf("VIDEO CONNECTION"), "no connection picker without the SDK")
            assertEquals(0, countOf("MODE"), "and no mode picker either")
        }
    }

    @Test
    fun `the source can still be renamed while no camera is available`() = cameraPanel { get ->
        typeField(0, "Balcony Cam")

        assertEquals("Balcony Cam", get().name, "the header stays usable with no device attached")
        assertEquals(
            "", (get() as SceneSource.CameraSource).devicePath,
            "and renaming must not invent a device path",
        )
    }

    // ── A machine with cameras ────────────────────────────────────────────────

    private val studio = CameraDevice("Studio Cam", "avfoundation://0", "Studio Cam")
    private val balcony = CameraDevice("Balcony Cam", "avfoundation://1", "Balcony Cam")
    private val screen = CameraDevice("Capture screen 0", "avfoundation://2", "Capture screen 0")
    private val twoCameras = NO_CAMERAS.copy(devices = listOf(studio, balcony, screen))

    @Test
    fun `choosing a camera points the source at it, and its format starts on Auto`() =
        cameraPanel(host = twoCameras) { get ->
            assertEquals(0, countOf("Capture screen 0"), "a display is not offered as a camera")

            chooseFromDropdown("Studio Cam", "Balcony Cam")

            val chosen = get() as SceneSource.CameraSource
            assertEquals("avfoundation://1", chosen.devicePath)
            assertEquals("Balcony Cam", chosen.deviceName)
            onNodeWithText("Auto (default)").assertExists()
        }

    @Test
    fun `choosing Auto clears a format the camera was pinned to`() {
        val pinned = Fixture.camera().copy(
            devicePath = studio.path, deviceName = studio.name, videoFormat = "1920x1080@30",
        )
        cameraPanel(pinned, host = twoCameras) { get ->
            chooseFromDropdown("Auto (default)", "Auto (default)")
            assertEquals("", (get() as SceneSource.CameraSource).videoFormat)
        }
    }

    @Test
    fun `a source saved on a display says it is one`() {
        val onScreen = Fixture.camera().copy(devicePath = screen.path, deviceName = screen.name)
        cameraPanel(onScreen, host = twoCameras) { _ ->
            onNodeWithText("This is a display, not a camera", substring = true).assertExists()
        }
    }

    @Test
    fun `a camera not yet pointed at a device offers the picker but no format`() =
        cameraPanel(host = twoCameras) { _ ->
            onNodeWithText("Studio Cam").assertExists()
            assertEquals(0, countOf("Auto (default)"), "there is no device to ask for formats")
        }

    @Test
    fun `a DeckLink source with no card index offers neither connectors nor formats`() {
        val unindexed = Fixture.camera().copy(
            devicePath = "decklink://", deviceName = "Mini Recorder", isDeckLink = true, deckLinkIndex = -1,
        )
        cameraPanel(unindexed, host = twoCameras) { get ->
            assertEquals(0, countOf("Auto (default)"))
            assertEquals(0, countOf("VIDEO CONNECTION"))
            assertEquals(0, countOf("Auto"), "nor a mode picker")
            assertEquals(unindexed, get(), "and the source is left as it was")
        }
    }

    @Test
    fun `choosing a format the camera offers pins the source to it, and Auto lets it go`() {
        val asked = mutableListOf<Pair<String, String>>()
        val withFormats = twoCameras.copy(
            formats = { path, name ->
                asked += path to name
                listOf(CameraFormat(1920, 1080, 30), CameraFormat(1280, 720, 60))
            },
        )
        val onStudio = Fixture.camera().copy(devicePath = studio.path, deviceName = studio.name)
        cameraPanel(onStudio, host = withFormats) { get ->
            waitUntil(timeoutMillis = 5_000) { asked.isNotEmpty() }
            waitForIdle()

            chooseFromDropdown("Auto (default)", "1280x720 @ 60fps")
            assertEquals("1280x720@60", (get() as SceneSource.CameraSource).videoFormat)

            chooseFromDropdown("1280x720 @ 60fps", "Auto (default)")
            assertEquals("", (get() as SceneSource.CameraSource).videoFormat)
            assertEquals(studio.path to studio.name, asked.first(), "the formats are asked of the chosen device")
        }
    }
}
