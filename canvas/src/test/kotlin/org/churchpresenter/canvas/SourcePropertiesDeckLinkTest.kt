@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals

class SourcePropertiesDeckLinkTest {

    private val recorder = CameraDevice(
        name = "UltraStudio Recorder",
        path = "decklink://0",
        displayName = "DeckLink: UltraStudio Recorder",
        isDeckLink = true,
        deckLinkIndex = 0,
    )

    private val source = SceneSource.CameraSource(
        id = "dl-1",
        name = "Stage camera",
        devicePath = recorder.path,
        deviceName = recorder.name,
        isDeckLink = true,
        deckLinkIndex = 0,
    )

    private class FakeCard(private val outputActive: Boolean = false) : DeckLinkInputs {
        override fun findDevice(index: Int) = DeckLinkManager.DeckLinkDevice(index, "UltraStudio Recorder")
        override fun inputModes(index: Int) = listOf(
            DeckLinkManager.InputMode("1080p 29.97", "Hp29"),
            DeckLinkManager.InputMode("720p 59.94", "hp59"),
        )
        override fun videoConnections(index: Int) = listOf(
            DeckLinkManager.VideoConnection("SDI", 1),
            DeckLinkManager.VideoConnection("HDMI", 2),
        )
        override fun openInput(index: Int, mode: String, connection: Int) = true
        override fun isOutputActive(index: Int) = outputActive
        override fun inputFrame(index: Int): IntArray? = null
        override suspend fun pause(millis: Long) = Unit
    }

    private fun deckLinkPanel(
        card: FakeCard = FakeCard(),
        start: SceneSource.CameraSource = source,
        block: ComposeUiTest.(get: () -> SceneSource) -> Unit,
    ) = withOsName(OS_WITHOUT_ENUMERATOR) {
        sourcePanel(start, cameraHost = CameraHost(listOf(recorder), ffmpegAvailable = true, deckLink = card)) { get ->
            waitForIdle()
            block(get)
        }
    }

    private fun (() -> SceneSource).camera() = this() as SceneSource.CameraSource

    @Test
    fun `a card with no connection chosen starts on its first one`() = deckLinkPanel { get ->
        waitUntil { get.camera().videoConnection != 0 }
        assertEquals(1, get.camera().videoConnection)
    }

    @Test
    fun `choosing another connection points the source at it`() =
        deckLinkPanel(start = source.copy(videoConnection = 1)) { get ->
            chooseFromDropdown("SDI", "HDMI")
            assertEquals(2, get.camera().videoConnection)
        }

    @Test
    fun `choosing a mode pins the source to it, and Auto lets it go again`() =
        deckLinkPanel(start = source.copy(videoConnection = 1)) { get ->
            chooseFromDropdown("Auto", "720p 59.94")
            assertEquals("hp59", get.camera().videoFormat)

            chooseFromDropdown("720p 59.94", "Auto")
            assertEquals("", get.camera().videoFormat)
        }

    @Test
    fun `a card busy sending an output warns that its input may not work`() =
        deckLinkPanel(card = FakeCard(outputActive = true), start = source.copy(videoConnection = 1)) { _ ->
            onNodeWithText("This device is currently used for output", substring = true).assertExists()
        }

    @Test
    fun `a card that is not sending gives no such warning`() =
        deckLinkPanel(start = source.copy(videoConnection = 1)) { _ ->
            onNodeWithText("This device is currently used for output", substring = true).assertDoesNotExist()
        }
}
