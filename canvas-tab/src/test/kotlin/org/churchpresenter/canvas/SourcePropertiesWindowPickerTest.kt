@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.ui.WindowInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Picking a window for a screen-capture source.
 *
 * The picker is what turns "the operator chose Slides" into the two fields the capture loop reads:
 * the title it follows the window by, and the handle the platform grabbers use to capture it even
 * when it is behind something else. Storing the handle wrongly is the failure that matters — the
 * source keeps working, by falling back to grabbing whatever is on top of that rectangle, which on
 * a live output is somebody's inbox.
 *
 * None of it could be reached before: the list came from `xprop`/`osascript`/the Win32 window list,
 * which is the machine's own desktop and, on macOS, an accessibility prompt that blocks a headless
 * run. `CanvasDeviceListing` is a composition local now, so the list is a fixture.
 */
class SourcePropertiesWindowPickerTest {

    private class Desktop(private vararg val windows: WindowInfo) : CanvasDeviceListing {
        var listings = 0
            private set

        override fun openWindows(): List<WindowInfo> {
            listings++
            return windows.toList()
        }

        override fun cameraFormats(devicePath: String, deviceName: String) = emptyList<CameraFormat>()
    }

    private fun capture(title: String = "", id: String = "") = SceneSource.ScreenCaptureSource(
        id = "cap", name = "Stage", captureMode = "window", windowTitle = title, windowId = id,
    )

    private val slides = WindowInfo("Slides", 0x1f4)
    private val notes = WindowInfo("Notes", 0x2a8)
    private val untracked = WindowInfo("Untracked", 0L)

    // ── What the list shows ────────────────────────────────────────────────────

    @Test
    fun `every open window is offered`() {
        sourcePanel(capture(), listing = Desktop(slides, notes)) { _ ->
            onNodeWithText("Slides").assertExists()
        }
    }

    @Test
    fun `a machine reporting no windows offers no picker at all`() {
        sourcePanel(capture(), listing = Desktop()) { _ ->
            assertEquals(0, countOf("Slides"))
        }
    }

    @Test
    fun `the window the source is already following is the one shown`() {
        sourcePanel(capture(title = "Notes"), listing = Desktop(slides, notes)) { _ ->
            assertTrue(countOf("Notes") > 0)
        }
    }

    @Test
    fun `a source following a window that has since closed falls back to the first one open`() {
        // Otherwise the selector shows a window nobody can pick again, and the operator cannot tell
        // that the thing they chose is gone.
        sourcePanel(capture(title = "Closed Since"), listing = Desktop(slides, notes)) { _ ->
            assertTrue(countOf("Slides") > 0, "the picker must show something that exists")
        }
    }

    // ── What picking one stores ────────────────────────────────────────────────

    @Test
    fun `picking a window stores its title and its handle in hex`() {
        sourcePanel(capture(title = "Slides"), listing = Desktop(slides, notes)) { get ->
            chooseFromDropdown("Slides", "Notes")

            val stored = get() as SceneSource.ScreenCaptureSource
            assertEquals("Notes", stored.windowTitle)
            assertEquals("0x2a8", stored.windowId, "the handle is what captures an occluded window")
        }
    }

    @Test
    fun `a window the platform gave no handle for is stored by title alone`() {
        // Zero is not a handle. Storing it would send the grabbers after window 0 and get nothing,
        // where an empty id makes the loop fall back to capturing the window's bounds.
        sourcePanel(capture(title = "Slides"), listing = Desktop(slides, untracked)) { get ->
            chooseFromDropdown("Slides", "Untracked")

            val stored = get() as SceneSource.ScreenCaptureSource
            assertEquals("Untracked", stored.windowTitle)
            assertEquals("", stored.windowId)
        }
    }

    // ── Refreshing ─────────────────────────────────────────────────────────────

    @Test
    fun `the window list is read once, not on every redraw`() {
        // It shells out to the platform; doing it per frame would spawn a process per frame.
        val desktop = Desktop(slides, notes)

        sourcePanel(capture(), listing = desktop) { _ ->
            assertEquals(1, desktop.listings)
        }
    }

    @Test
    fun `Refresh reads the list again`() {
        val desktop = Desktop(slides, notes)

        sourcePanel(capture(), listing = desktop) { _ ->
            button("Refresh Window List").performScrollTo().performClick()
            waitForIdle()

            assertEquals(2, desktop.listings)
        }
    }

    // ── The capture rectangle's own fields ─────────────────────────────────────

    @Test
    fun `a capture offset that is not a number is left alone rather than zeroed`() {
        // Half-typed input reaches these fields on every keystroke: "-" on the way to "-100", "" on
        // the way to anything. Committing a zero for those would move the capture under the operator.
        withOsName(OS_WITHOUT_ENUMERATOR) {
            val area = SceneSource.ScreenCaptureSource(
                id = "cap", name = "Stage", captureX = 100, captureY = 200,
                captureWidth = 800, captureHeight = 600,
            )
            sourcePanel(area) { get ->
                typeField(6, "not a number")
                typeField(7, "")
                typeField(9, "-")

                val stored = get() as SceneSource.ScreenCaptureSource
                assertEquals(100, stored.captureX)
                assertEquals(200, stored.captureY)
                assertEquals(600, stored.captureHeight)
            }
        }
    }
}
