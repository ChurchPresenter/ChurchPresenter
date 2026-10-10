@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.canvas.CameraEnumerationFacts
import org.churchpresenter.canvas.CameraEnumerator
import org.churchpresenter.canvas.CameraHost
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackgroundHostSupportTest {

    @Test
    fun `a camera can be opened with ffmpeg, or failing that a DeckLink card`() {
        var deckLinkAsked = 0
        assertTrue(canCaptureCamera(ffmpegAvailable = { true }, deckLinkAvailable = { deckLinkAsked++; false }))
        assertEquals(0, deckLinkAsked, "ffmpeg answers first")
        assertTrue(canCaptureCamera(ffmpegAvailable = { false }, deckLinkAvailable = { true }))
        assertFalse(canCaptureCamera(ffmpegAvailable = { false }, deckLinkAvailable = { false }))
    }

    @Test
    fun `the hints name what enumerated this machine, and nothing when a host answers`() {
        val facts = CameraEnumerationFacts(
            enumerator = CameraEnumerator.entries.first(),
            ffmpegListedCount = 0,
            fallbackListedCount = 0,
            deckLinkCount = 0,
            ffmpegAvailable = true,
            enumeratedAtMs = 0L,
            names = emptySet(),
        )
        assertEquals(CameraEnumerator.entries.first(), hintEnumerator(null, facts))
        assertNull(hintEnumerator(null, null))
        assertNull(hintEnumerator(CameraHost(emptyList(), ffmpegAvailable = true), facts))
    }

    private class Machine(val vlc: Boolean, val capture: Boolean) {
        val probes = AtomicInteger()
    }

    private fun segments(
        machine: Machine,
        body: ComposeUiTest.(get: () -> BackgroundConfig) -> Unit,
    ) = runComposeUiTest {
        var config by mutableStateOf(BackgroundConfig(backgroundType = Constants.BACKGROUND_COLOR))
        setContent {
            MaterialTheme {
                BackgroundTypeSegments(
                    scope = BackgroundScope.DEFAULT,
                    config = config,
                    onConfigChange = { config = it },
                    vlcAvailable = { machine.vlc },
                    probeCapture = { machine.probes.incrementAndGet(); machine.capture },
                )
            }
        }
        waitUntil { machine.probes.get() == 1 }
        waitForIdle()
        body { config }
    }

    private fun ComposeUiTest.enabled(label: String): Boolean =
        SemanticsProperties.Disabled !in onNodeWithText(label).fetchSemanticsNode().config

    @Test
    fun `without VLC or a capture device, Video and Camera are shown but cannot be picked`() =
        segments(Machine(vlc = false, capture = false)) { get ->
            assertFalse(enabled("Video Loop"))
            assertFalse(enabled("Camera"))
            onNodeWithText("Video Loop").performClick()
            onNodeWithText("Camera").performClick()
            waitForIdle()
            assertEquals(Constants.BACKGROUND_COLOR, get().backgroundType)
        }

    @Test
    fun `with VLC and a capture device, Video and Camera are picked like any other type`() =
        segments(Machine(vlc = true, capture = true)) { get ->
            assertTrue(enabled("Video Loop"))
            onNodeWithText("Video Loop").performClick()
            waitForIdle()
            assertEquals(Constants.BACKGROUND_VIDEO, get().backgroundType)
            onNodeWithText("Camera").performClick()
            waitForIdle()
            assertEquals(Constants.BACKGROUND_CAMERA, get().backgroundType)
        }
}
