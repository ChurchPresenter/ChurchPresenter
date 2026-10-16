@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.scene.ClockModes
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.settings.WindowLayoutSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CanvasEdgeCasesTest {

    @Test
    fun `a layer poking out past the top edge only is partly outside`() {
        val transform = SourceTransform(x = 0.4f, y = -0.1f, width = 0.2f, height = 0.2f)

        assertEquals(CanvasPlacement.PARTLY_OUTSIDE, transform.placement())
    }

    @Test
    fun `the OMT panel with its defaults reports the missing library`() = runComposeUiTest {
        setContent {
            Surface {
                Column {
                    OmtProperties(source = SceneSource.OmtSource(id = "omt", name = "OMT"), onUpdate = {})
                }
            }
        }
        waitForIdle()

        onNodeWithText("OMT sources need the OMT library", substring = true).assertExists()
    }

    @Test
    fun `the panel widths follow a new saved width and a change of maximized state`() = runComposeUiTest {
        var layout by mutableStateOf(WindowLayoutSettings(canvasLeftPanelWidthDp = 200, canvasRightPanelWidthDp = 200))
        var maximized by mutableStateOf(false)
        var state: CanvasTabState? = null
        setContent {
            state = rememberCanvasTabState(layout, maximized, LocalDensity.current)
        }
        waitForIdle()
        val first = checkNotNull(state)
        val startLeft = first.leftPanelPx.value

        layout = layout.copy(canvasLeftPanelWidthDp = 300, canvasRightPanelWidthDp = 260)
        waitForIdle()
        val resized = checkNotNull(state)
        assertNotEquals(startLeft, resized.leftPanelPx.value)
        assertEquals(resized.leftPanelPx.value * 260f / 300f, resized.rightPanelPx.value, 0.01f)

        resized.leftPanelPx.value = 1f
        maximized = true
        waitForIdle()
        assertEquals(resized.rightPanelPx.value * 300f / 260f, checkNotNull(state).leftPanelPx.value, 0.01f)
    }

    @Test
    fun `the add source menu closes on a press outside it without adding anything`() =
        canvasTab(seed = { addScene("Scene") }) { vm, _ ->
            canvasButton(CanvasLabel.ADD_SOURCE).performClick()
            waitForIdle()
            onNodeWithText(CanvasLabel.COLOR).assertExists()

            onAllNodes(isRoot())[0].performMouseInput { click(Offset(width - 4f, height - 4f)) }
            waitForIdle()

            onNodeWithText(CanvasLabel.COLOR).assertDoesNotExist()
            assertEquals(emptyList(), vm.sourceNames())
        }

    @Test
    fun `a settings change elsewhere keeps a drawn layer selected and untouched`() =
        keepsSelectionThroughSettingsChange(
            SceneSource.TextSource(id = "t", name = "Text"),
            SceneSource.ClockSource(id = "c", name = "Clock", mode = ClockModes.COUNTDOWN),
            SceneSource.ShapeSource(id = "s", name = "Shape", isGradient = true),
            SceneSource.ColorSource(id = "col", name = "Color"),
            SceneSource.QRCodeSource(id = "q", name = "QR"),
            SceneSource.BibleSource(id = "bi", name = "Bible"),
        )

    @Test
    fun `a settings change elsewhere keeps a media or input layer selected and untouched`() =
        keepsSelectionThroughSettingsChange(
            SceneSource.VideoSource(id = "v", name = "Video", filePath = ""),
            SceneSource.ImageSource(id = "i", name = "Image", filePath = ""),
            SceneSource.OmtSource(id = "o", name = "OMT"),
            SceneSource.ScreenCaptureSource(id = "sc", name = "Screen"),
            SceneSource.BrowserSource(id = "b", name = "Browser", url = ""),
            SceneSource.NdiSource(id = "n", name = "NDI"),
            SceneSource.CameraSource(id = "cam", name = "Camera", devicePath = "unsupported://test-device"),
        )

    private fun keepsSelectionThroughSettingsChange(vararg layers: SceneSource) {
        var tick by mutableStateOf(0)
        canvasTab(
            seed = { addScene("Scene"); layers.forEach { addSource(it) } },
            settings = { it.copy(windowedLayout = it.windowedLayout.copy(schedulePanelWidthDp = 280 + tick)) },
            liveSceneId = { vm -> vm.currentSceneId.value.takeIf { tick % 2 == 1 } },
        ) { vm, _ ->
            val before = vm.currentScene!!.sources
            (layers.map { it.id } + listOf(null)).forEach { id ->
                vm.selectSource(id)
                waitForIdle()
                tick++
                waitForIdle()

                assertEquals(id, vm.selectedSourceId.value)
            }
            assertEquals(before, vm.currentScene!!.sources)
        }
    }
}
