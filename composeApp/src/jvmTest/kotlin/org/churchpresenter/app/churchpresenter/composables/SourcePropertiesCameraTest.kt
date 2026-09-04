@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

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
 * **Why the OS is faked.** The device list is built by `listCameraDevicesWithDeckLink()`, which asks
 * the DeckLink SDK and then shells out per platform: `/dev/video*` on Linux, `ffmpeg -list_devices`
 * plus PowerShell on Windows, `system_profiler` plus `ffmpeg` on macOS. What comes back is whatever
 * hardware is plugged into the machine running the suite, so a test that asserted on it would pass on
 * one developer's laptop and fail on the next, and it would pay several process spawns per
 * composition to do it. Every test here runs under [withOsName] naming an OS the enumerator has no
 * branch for, so the list is empty immediately and nothing is spawned. "No cameras found" is a real
 * state an operator sees, and it is the one that can be asserted deterministically.
 *
 * Known gaps, all of them downstream of a device actually existing:
 *
 *  * **The device dropdown** and what choosing an entry writes (`devicePath`, `deviceName`, and the
 *    reset of format and connection that comes with it).
 *  * **The DeckLink branch** — the in-use warning, the video-connection dropdown and its auto-select,
 *    and the mode dropdown — all of which need `DeckLinkManager.isAvailable()` to be true, and that
 *    needs the native `decklink_jni` library the suite does not ship.
 *  * **The ffmpeg format dropdown**, which needs a non-DeckLink device path to enumerate against.
 *  * **The rendering of the "install the tools" hints**, which still depends on whether the machine
 *    running the suite has `ffmpeg` on its PATH. *Which* hint each platform should get no longer
 *    depends on the host and is pinned in `CameraToolHintsTest`; only the act of drawing the list
 *    is uncovered here.
 */
class SourcePropertiesCameraTest {

    /**
     * Renders the camera panel with the platform's device enumeration deliberately absent.
     *
     * [CameraDeviceCatalog] is process-wide and holds whatever the last enumeration found, so a
     * suite that ran before this one in the same fork can leave the host's real cameras in it and
     * the panel draws a picker instead of the message. The panel re-enumerates on composition, and
     * under a faked OS that lands an empty list — so the wait is for that landing, which is also
     * the state every test here asserts against.
     */
    private fun cameraPanel(
        source: SceneSource.CameraSource = Fixture.camera(),
        block: ComposeUiTest.(get: () -> SceneSource) -> Unit,
    ) = withOsName(OS_WITHOUT_ENUMERATOR) {
        sourcePanel(source) { get ->
            waitUntil { CameraDeviceCatalog.devices.value?.isEmpty() == true }
            block(get)
        }
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
}
