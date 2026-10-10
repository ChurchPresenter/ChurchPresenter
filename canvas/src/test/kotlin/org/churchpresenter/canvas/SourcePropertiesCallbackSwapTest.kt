@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.scene.ClockModes
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.settings.AppSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SourcePropertiesCallbackSwapTest {

    @AfterTest
    fun stopTimers() = TimerStateManager.clear()

    private class Swap(val swap: () -> Unit, val received: () -> List<Pair<Int, SceneSource>>)

    private fun swappingPanel(
        source: SceneSource,
        cameraHost: CameraHost = NO_CAMERAS,
        block: ComposeUiTest.(Swap) -> Unit,
    ) = withOsName(OS_WITHOUT_ENUMERATOR) {
        runComposeUiTest {
            val received = mutableListOf<Pair<Int, SceneSource>>()
            var generation by mutableStateOf(0)
            setContent {
                MaterialTheme {
                    var state by remember { mutableStateOf(source) }
                    val gen = generation
                    SourcePropertiesPanel(
                        source = state,
                        appSettings = AppSettings(),
                        cameraHost = cameraHost,
                        onSourceUpdate = { updated -> received += gen to updated; state = updated },
                    )
                }
            }
            waitForIdle()
            block(Swap({ generation++; waitForIdle() }, { received.toList() }))
        }
    }

    private fun assertSwapChangesNothing(source: SceneSource, cameraHost: CameraHost = NO_CAMERAS) =
        swappingPanel(source, cameraHost) { swap ->
            val before = renderedText()
            val editsBefore = swap.received()

            swap.swap()
            swap.swap()

            assertEquals(before, renderedText(), "a new callback changes nothing a ${source.name} panel shows")
            assertEquals(editsBefore, swap.received(), "and edits nothing on its own")
        }

    private val camera = CameraDevice(name = "Studio Camera", path = "/dev/video0", displayName = "Studio Camera")

    private val deckLinkRecorder = CameraDevice(
        name = "UltraStudio Recorder",
        path = "decklink://0",
        displayName = "DeckLink: UltraStudio Recorder",
        isDeckLink = true,
        deckLinkIndex = 0,
    )

    private class FakeCard : DeckLinkInputs {
        override fun findDevice(index: Int) = DeckLinkManager.DeckLinkDevice(index, "UltraStudio Recorder")
        override fun inputModes(index: Int) = listOf(DeckLinkManager.InputMode("1080p 29.97", "Hp29"))
        override fun videoConnections(index: Int) = listOf(DeckLinkManager.VideoConnection("SDI", 1))
        override fun openInput(index: Int, mode: String, connection: Int) = true
        override fun isOutputActive(index: Int) = true
        override fun inputFrame(index: Int): IntArray? = null
        override suspend fun pause(millis: Long) = Unit
    }

    @Test
    fun `swapping the callback leaves every default editor as it was`() {
        Fixture.everyKind("swap").forEach { assertSwapChangesNothing(it) }
    }

    @Test
    fun `swapping the callback leaves a gradient color editor as it was`() =
        assertSwapChangesNothing(SceneSource.ColorSource(id = "grad-swap", name = "Wash", isGradient = true))

    @Test
    fun `swapping the callback leaves gradient and stroke-only shape editors as they were`() {
        assertSwapChangesNothing(SceneSource.ShapeSource(id = "shp-grad", name = "Box", isGradient = true))
        assertSwapChangesNothing(SceneSource.ShapeSource(id = "shp-line", name = "Line", shapeType = "line"))
    }

    @Test
    fun `swapping the callback leaves every clock mode as it was`() {
        listOf(ClockModes.COUNTDOWN, ClockModes.COUNT_UP, ClockModes.TARGET_TIME).forEach { mode ->
            assertSwapChangesNothing(
                SceneSource.ClockSource(
                    id = "clk-$mode", name = mode, mode = mode, timeFormat = "12h", targetMinute = 1,
                ),
            )
        }
    }

    @Test
    fun `swapping the callback leaves WiFi and transparent QR editors as they were`() {
        assertSwapChangesNothing(SceneSource.QRCodeSource(id = "qr-wifi", name = "WiFi", contentType = "wifi"))
        assertSwapChangesNothing(
            SceneSource.QRCodeSource(id = "qr-clear", name = "Clear", transparentBackground = true),
        )
    }

    @Test
    fun `swapping the callback leaves a transparent text editor as it was`() =
        assertSwapChangesNothing(
            SceneSource.TextSource(id = "txt-clear", name = "Clear", backgroundColor = "#00000000"),
        )

    @Test
    fun `swapping the callback leaves a window capture editor as it was`() =
        assertSwapChangesNothing(SceneSource.ScreenCaptureSource(id = "cap-win", name = "Win", captureMode = "window"))

    @Test
    fun `swapping the callback leaves network editors as they were`() {
        assertSwapChangesNothing(SceneSource.NdiSource(id = "ndi-swap", name = "NDI"))
        assertSwapChangesNothing(SceneSource.OmtSource(id = "omt-swap", name = "OMT"))
    }

    @Test
    fun `swapping the callback leaves a camera editor with a device as it was`() =
        assertSwapChangesNothing(
            SceneSource.CameraSource(
                id = "cam-dev", name = "Cam", devicePath = camera.path, deviceName = camera.name,
            ),
            CameraHost(listOf(camera), ffmpegAvailable = true),
        )

    @Test
    fun `swapping the callback leaves a DeckLink editor as it was`() =
        assertSwapChangesNothing(
            SceneSource.CameraSource(
                id = "cam-dl", name = "Stage", devicePath = deckLinkRecorder.path,
                deviceName = deckLinkRecorder.name, isDeckLink = true, deckLinkIndex = 0, videoConnection = 1,
            ),
            CameraHost(listOf(deckLinkRecorder), ffmpegAvailable = true, deckLink = FakeCard()),
        )

    @Test
    fun `an edit after a swap reaches the new callback`() = swappingPanel(Fixture.qr("qr-after")) { swap ->
        swap.swap()

        fieldShowing("https://example.com").performScrollTo().performTextReplacement("https://example.org/after")
        waitForIdle()

        val edits = swap.received()
        assertEquals(1, edits.last().first, "the edit goes to the callback handed over last, not the stale one")
        assertEquals("https://example.org/after", (edits.last().second as SceneSource.QRCodeSource).content)
    }
}
