@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every type the Add-source menu offers, and what each one actually adds.
 *
 * [CanvasTabTest] covers three of the ten (text, image, clock) and the fact that a source lands in
 * the current scene at all. The rest were never driven — and they are exactly where a wiring mistake
 * hides, because every entry looks alike at the call site: one menu item, one `addSource` with a
 * different `SceneSource` subclass and a different default rectangle.
 *
 * A menu entry bound to the wrong subclass gives the operator a text box when they asked for a QR
 * code, and nothing about the menu would look wrong. So each test asserts the **type**, not just
 * that something appeared.
 *
 * One source per test, deliberately: the add button cannot be driven twice in a single test (see
 * `addSourceOfType`).
 */
class CanvasTabAddSourceTest {

    /** The single source in the current scene, or null if the scene is empty. */
    private fun soleSource(vm: org.churchpresenter.app.churchpresenter.viewmodel.SceneViewModel) =
        vm.scenes.single().sources.singleOrNull()

    @Test
    fun `a colour source is added as a colour`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            addSourceOfType(CanvasLabel.COLOR)

            assertEquals(listOf("Color"), vm.sourceNames())
            assertTrue(soleSource(vm) is SceneSource.ColorSource, "got ${soleSource(vm)}")
        }

    @Test
    fun `a video source is added as a video`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            addSourceOfType(CanvasLabel.VIDEO)

            assertEquals(listOf("Video"), vm.sourceNames())
            assertTrue(soleSource(vm) is SceneSource.VideoSource, "got ${soleSource(vm)}")
        }

    @Test
    fun `a browser source is added as a browser, ready for a url`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            addSourceOfType(CanvasLabel.BROWSER)

            val source = soleSource(vm)
            assertTrue(source is SceneSource.BrowserSource, "got $source")
            // Seeded rather than blank so the properties panel shows the operator what shape of
            // value it wants.
            assertEquals("http://www.", source.url)
        }

    @Test
    fun `a QR code source is added as a QR code`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            addSourceOfType(CanvasLabel.QR_CODE)

            assertEquals(listOf("QR Code"), vm.sourceNames())
            assertTrue(soleSource(vm) is SceneSource.QRCodeSource, "got ${soleSource(vm)}")
        }

    @Test
    fun `a camera source is added as a camera`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            // No camera is opened by adding one — the source is a description until it is rendered,
            // which is why this is reachable on a machine with no device.
            addSourceOfType(CanvasLabel.CAMERA)

            assertTrue(soleSource(vm) is SceneSource.CameraSource, "got ${soleSource(vm)}")
        }

    @Test
    fun `a screen capture source is added as a screen capture`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            addSourceOfType(CanvasLabel.SCREEN_CAPTURE)

            assertTrue(soleSource(vm) is SceneSource.ScreenCaptureSource, "got ${soleSource(vm)}")
        }

    @Test
    fun `a bible source is added as a bible`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            addSourceOfType(CanvasLabel.BIBLE)

            assertEquals(listOf("Bible"), vm.sourceNames())
            assertTrue(soleSource(vm) is SceneSource.BibleSource, "got ${soleSource(vm)}")
        }

    @Test
    fun `a new source is placed inside the canvas rather than at its corner`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            addSourceOfType(CanvasLabel.BIBLE)

            // Every entry sets its own default rectangle in fractions of the canvas. What they share
            // is that a new source must be visible and grabbable — one pinned at 0,0 with zero size
            // could not be selected or dragged.
            val t = soleSource(vm)!!.transform
            assertTrue(t.x > 0f && t.y > 0f, "inset from the corner, got ${t.x},${t.y}")
            assertTrue(t.width > 0f && t.height > 0f, "and has size, got ${t.width}x${t.height}")
            assertTrue(t.x + t.width <= 1f && t.y + t.height <= 1f, "and fits the canvas")
        }
}
