@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Playback: the transport controls, the volume popup, Go Live (including Instance Link), and what
 * the preview area shows before/while/after something is loaded and live.
 *
 * None of this needs VLC — it drives the view model directly, the same as `MediaTabTest`. See
 * `MediaTabTestSupport.kt` for the harness.
 */
class MediaTabPlaybackTest {

    private fun ComposeUiTest.loadUrl(url: String = "https://example.org/clip.mp4") {
        onNodeWithText(MediaLabel.NETWORK_URL).performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction())[0].performTextReplacement(url)
        waitForIdle()
        onNodeWithText("Load").performClick()
        waitForIdle()
    }

    // ── Transport controls ──────────────────────────────────────────────────────

    @Test
    fun `transport controls are disabled until something is loaded`() = mediaTab { _, _ ->
        mediaButton(MediaLabel.PLAY).assertIsNotEnabled()
        mediaButton(MediaLabel.STOP).assertIsNotEnabled()
        mediaButton(MediaLabel.SEEK_BACKWARD).assertIsNotEnabled()
        mediaButton(MediaLabel.SEEK_FORWARD).assertIsNotEnabled()
        mediaButton(MediaLabel.VOLUME).assertIsNotEnabled()
    }

    @Test
    fun `play toggles to pause and back`() = mediaTab { vm, _ ->
        loadUrl()
        assertTrue(hasMediaButton(MediaLabel.PLAY))

        mediaButton(MediaLabel.PLAY).performClick()
        waitForIdle()

        assertTrue(vm.isPlaying)
        assertTrue(hasMediaButton(MediaLabel.PAUSE))
        assertFalse(hasMediaButton(MediaLabel.PLAY))

        mediaButton(MediaLabel.PAUSE).performClick()
        waitForIdle()

        assertFalse(vm.isPlaying)
        assertTrue(hasMediaButton(MediaLabel.PLAY))
    }

    @Test
    fun `stop resets playback to the beginning`() = mediaTab { vm, _ ->
        loadUrl()
        mediaButton(MediaLabel.PLAY).performClick()
        waitForIdle()

        mediaButton(MediaLabel.STOP).performClick()
        waitForIdle()

        assertFalse(vm.isPlaying)
        assertEquals(0L, vm.currentPosition)
    }

    @Test
    fun `seeking forward and backward moves the position`() = mediaTab { vm, _ ->
        loadUrl()
        vm.setDuration(60_000L)

        mediaButton(MediaLabel.SEEK_FORWARD).performClick()
        waitForIdle()
        assertEquals(10_000L, vm.currentPosition)

        mediaButton(MediaLabel.SEEK_BACKWARD).performClick()
        waitForIdle()
        assertEquals(0L, vm.currentPosition)
    }

    // ── Volume popup ────────────────────────────────────────────────────────────

    @Test
    fun `the volume popup opens on demand and can mute`() = mediaTab { vm, _ ->
        loadUrl()
        assertFalse(hasMediaButton(MediaLabel.MUTE), "the popup is closed to begin with")

        mediaButton(MediaLabel.VOLUME).performClick()
        waitForIdle()

        assertTrue(hasMediaButton(MediaLabel.MUTE), "the popup's own mute toggle appears")

        mediaButton(MediaLabel.MUTE).performClick()
        waitForIdle()

        assertTrue(vm.isMuted)
        assertTrue(hasMediaButton(MediaLabel.UNMUTE))
    }

    // ── Loop ────────────────────────────────────────────────────────────────────

    @Test
    fun `the loop button starts off and toggles on`() = mediaTab { vm, _ ->
        assertFalse(vm.isLooping, "media looping is off by default, unlike pictures and slides")
        assertTrue(hasMediaButton(MediaLabel.LOOP_OFF))

        mediaButton(MediaLabel.LOOP_OFF).performClick()
        waitForIdle()

        assertTrue(vm.isLooping)
        assertTrue(hasMediaButton(MediaLabel.LOOP_ON), "the button says which state it is in now")
    }

    @Test
    fun `toggling loop persists the choice`() = mediaTab { vm, reports ->
        mediaButton(MediaLabel.LOOP_OFF).performClick()
        waitForIdle()

        assertEquals(true, reports.settingsAfterChange?.mediaIsLooping)

        mediaButton(MediaLabel.LOOP_ON).performClick()
        waitForIdle()

        assertFalse(vm.isLooping, "clicking again turns it back off")
        assertEquals(false, reports.settingsAfterChange?.mediaIsLooping)
    }

    @Test
    fun `the saved setting is what the button starts as`() =
        mediaTab(settings = { it.copy(mediaIsLooping = true) }) { vm, _ ->
            assertTrue(vm.isLooping)
            assertTrue(hasMediaButton(MediaLabel.LOOP_ON))
        }

    @Test
    fun `loop can be set before anything is loaded`() = mediaTab { vm, _ ->
        // Unlike the transport buttons it is not gated on isLoaded: choosing to loop and then
        // picking the file is the order an operator setting up before a service works in.
        mediaButton(MediaLabel.LOOP_OFF).performClick()
        waitForIdle()

        assertTrue(vm.isLooping)
    }

    // ── Go Live / Instance Link ─────────────────────────────────────────────────

    @Test
    fun `Go Live projects the media to Instance Link when wired`() {
        val presenter = PresenterManager()
        val sent = mutableListOf<ScheduleItem>()
        mediaTab(presenterManager = presenter, onInstanceLinkSendProject = { sent += it }) { vm, _ ->
            loadUrl("https://example.org/clip.mp4")
            mediaButton(MediaLabel.GO_LIVE).performClick()
            waitForIdle()

            assertEquals(Presenting.MEDIA, presenter.presentingMode.value)
            val item = sent.single() as ScheduleItem.MediaItem
            assertEquals(vm.mediaUrl, item.mediaUrl)
            assertEquals(vm.mediaType, item.mediaType)
        }
    }

    // ── Content area ────────────────────────────────────────────────────────────

    @Test
    fun `while presenting the preview shows what is live instead of a duplicate player`() {
        val presenter = PresenterManager()
        mediaTab(presenterManager = presenter) { _, _ ->
            loadUrl("https://example.org/clip.mp4")
            mediaButton(MediaLabel.GO_LIVE).performClick()
            waitForIdle()

            assertTrue(showsContainingText(MediaLabel.NOW_PRESENTING), "got ${renderedText()}")
        }
    }

    @Test
    fun `with nothing loaded the preview says so`() = mediaTab { _, _ ->
        assertTrue(showsContainingText(MediaLabel.NO_SOURCE))
    }

    // ── Seek bar ────────────────────────────────────────────────────────────────

    @Test
    fun `once a duration is known the seek bar shows elapsed and total time`() = mediaTab { vm, _ ->
        loadUrl()
        vm.setDuration(125_000L)

        assertTrue(showsContainingText("2:05"), "got ${renderedText()}")
    }
}
