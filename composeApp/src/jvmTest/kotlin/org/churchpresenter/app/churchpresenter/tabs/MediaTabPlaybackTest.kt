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
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.SubtitleTrack
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Playback: the transport controls, mute, Go Live (including Instance Link), and what
 * the preview area shows before/while/after something is loaded and live.
 *
 * None of this needs VLC — it drives the view model directly, the same as `MediaTabTest`. See
 * `MediaTabTestSupport.kt` for the harness.
 */
class MediaTabPlaybackTest {

    /**
     * Loads a URL and waits until the view model says it is loaded.
     *
     * The wait is the point. Every transport control below is **disabled** until `isLoaded`, and a
     * click on a disabled button is silently dropped — so a test that goes straight on to press Play
     * asserts against a view model that never heard the press. `waitForIdle()` does not cover it:
     * it settles composition, and what is being waited for here is the click having been dispatched
     * and the state written, which is a step earlier.
     *
     * It ends on the state itself, so it costs nothing in the happy path; the timeout is only how it
     * fails. This is what `MediaTabPlaybackTest > play toggles to pause and back` was flaking on
     * under the parallel suite (issue #610).
     */
    private fun ComposeUiTest.loadUrl(vm: MediaViewModel, url: String = "https://example.org/clip.mp4") {
        onNodeWithText(MediaLabel.NETWORK_URL).performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction())[0].performTextReplacement(url)
        waitForIdle()
        onNodeWithText("Load").performClick()
        waitUntil { vm.isLoaded }
        // The state, then the recomposition it triggers: the transport buttons are `enabled =
        // isLoaded`, and a click on one that has not been re-enabled yet is silently dropped.
        waitForIdle()
    }

    // ── Transport controls ──────────────────────────────────────────────────────

    @Test
    fun `transport controls are disabled until something is loaded`() = mediaTab { _, _ ->
        mediaButton(MediaLabel.PLAY).assertIsNotEnabled()
        mediaButton(MediaLabel.STOP).assertIsNotEnabled()
        mediaButton(MediaLabel.SEEK_BACKWARD).assertIsNotEnabled()
        mediaButton(MediaLabel.SEEK_FORWARD).assertIsNotEnabled()
        mediaButton(MediaLabel.MUTE).assertIsNotEnabled()
        mediaButton(MediaLabel.LOOP_OFF).assertIsNotEnabled()
    }

    @Test
    fun `play toggles to pause and back`() = mediaTab { vm, _ ->
        loadUrl(vm)
        assertTrue(hasMediaButton(MediaLabel.PLAY))

        mediaButton(MediaLabel.PLAY).performClick()
        // Waits on the *rendered* result rather than on `waitForIdle()` or on the flag alone.
        // There are two events here, not one — the view model writing `isPlaying`, and the
        // recomposition that swaps the glyph — and this test kept landing between them (#610).
        // The button changing implies both, so it is the only signal that covers the whole step.
        waitUntil { hasMediaButton(MediaLabel.PAUSE) }

        assertTrue(vm.isPlaying)
        assertFalse(hasMediaButton(MediaLabel.PLAY))

        mediaButton(MediaLabel.PAUSE).performClick()
        waitUntil { hasMediaButton(MediaLabel.PLAY) }

        assertFalse(vm.isPlaying)
    }

    @Test
    fun `stop resets playback to the beginning`() = mediaTab { vm, _ ->
        loadUrl(vm)
        mediaButton(MediaLabel.PLAY).performClick()
        waitUntil { hasMediaButton(MediaLabel.PAUSE) }

        mediaButton(MediaLabel.STOP).performClick()
        waitUntil { hasMediaButton(MediaLabel.PLAY) }

        assertFalse(vm.isPlaying)
        assertEquals(0L, vm.currentPosition)
    }

    @Test
    fun `seeking forward and backward moves the position`() = mediaTab { vm, _ ->
        loadUrl(vm)
        vm.setDuration(60_000L)

        mediaButton(MediaLabel.SEEK_FORWARD).performClick()
        waitForIdle()
        assertEquals(10_000L, vm.currentPosition)

        mediaButton(MediaLabel.SEEK_BACKWARD).performClick()
        waitForIdle()
        assertEquals(0L, vm.currentPosition)
    }

    // ── Volume ──────────────────────────────────────────────────────────────────

    @Test
    fun `the mute key in the bar mutes`() = mediaTab { vm, _ ->
        loadUrl(vm)
        mediaButton(MediaLabel.MUTE).performClick()
        waitForIdle()

        assertTrue(vm.isMuted)
        assertTrue(hasMediaButton(MediaLabel.UNMUTE))
    }

    // ── Subtitles ───────────────────────────────────────────────────────────────

    @Test
    fun `the subtitle button is disabled until something is loaded`() = mediaTab { _, _ ->
        mediaButton(MediaLabel.SUBTITLES).assertIsNotEnabled()
    }

    // Not covered: the button being disabled for an audio file. Loading audio mounts the Swing-backed
    // VideoPlayer, which cannot compose in a Compose UI test (no interop container).

    @Test
    fun `the subtitle menu lists off, every track and the file picker`() = mediaTab { vm, _ ->
        loadUrl(vm)
        vm.setSubtitleTracks(listOf(SubtitleTrack(3, "English"), SubtitleTrack(4, "Spanish")))
        mediaButton(MediaLabel.SUBTITLES).performClick()
        waitForIdle()

        assertTrue(showsExactly(MediaLabel.SUBTITLES_OFF))
        assertTrue(showsExactly("English"))
        assertTrue(showsExactly("Spanish"))
        assertTrue(showsExactly(MediaLabel.SUBTITLES_LOAD_FILE))
    }

    @Test
    fun `picking a track in the menu selects it`() = mediaTab { vm, _ ->
        loadUrl(vm)
        vm.setSubtitleTracks(listOf(SubtitleTrack(3, "English"), SubtitleTrack(4, "Spanish")))
        mediaButton(MediaLabel.SUBTITLES).performClick()
        waitForIdle()

        onNodeWithText("Spanish").performClick()
        waitForIdle()

        assertEquals(4, vm.selectedSubtitleTrack)
        assertFalse(showsExactly("Spanish"), "the menu closes after a choice")
    }

    @Test
    fun `choosing off in the menu hides the subtitles again`() = mediaTab { vm, _ ->
        loadUrl(vm)
        vm.setSubtitleTracks(listOf(SubtitleTrack(3, "English")))
        vm.selectSubtitleTrack(3)
        mediaButton(MediaLabel.SUBTITLES).performClick()
        waitForIdle()

        onNodeWithText(MediaLabel.SUBTITLES_OFF).performClick()
        waitForIdle()

        assertEquals(MediaViewModel.SUBTITLES_OFF, vm.selectedSubtitleTrack)
    }

    @Test
    fun `a media item added to the schedule carries the subtitle file`() = mediaTab { vm, reports ->
        loadUrl(vm)
        vm.setSubtitleFile("/media/en.srt")
        waitForIdle()

        mediaButton(MediaLabel.ADD_TO_SCHEDULE).performClick()
        waitForIdle()

        assertEquals(listOf("/media/en.srt"), reports.scheduledSubtitles)
    }

    @Test
    fun `a scheduled item with a subtitle file loads it into the player`() {
        val item = ScheduleItem.MediaItem(
            id = "1", mediaUrl = "/media/clip.mp4", mediaTitle = "Clip", mediaType = Constants.MEDIA_TYPE_LOCAL,
            subtitleUrl = "/media/en.srt",
        )

        mediaTab(selectedMediaItem = item) { vm, _ ->
            waitForIdle()

            assertEquals("/media/en.srt", vm.subtitleUrl)
        }
    }

    // ── Loop ────────────────────────────────────────────────────────────────────

    @Test
    fun `the loop button arms looping and shows the count beside it`() = mediaTab { vm, _ ->
        loadUrl(vm)
        assertFalse(vm.isLooping)
        assertFalse(showsExactly(MediaLabel.LOOP_COUNT), "the count is hidden until looping is armed")

        mediaButton(MediaLabel.LOOP_OFF).performClick()
        waitForIdle()

        assertTrue(vm.isLooping)
        assertTrue(hasMediaButton(MediaLabel.LOOP_ON), "the button reads back its new state")
        assertTrue(showsExactly(MediaLabel.LOOP_COUNT))
    }

    @Test
    fun `disarming looping takes the count away again`() = mediaTab { vm, _ ->
        loadUrl(vm)
        mediaButton(MediaLabel.LOOP_OFF).performClick()
        waitForIdle()

        mediaButton(MediaLabel.LOOP_ON).performClick()
        waitForIdle()

        assertFalse(vm.isLooping)
        assertFalse(showsExactly(MediaLabel.LOOP_COUNT))
    }

    @Test
    fun `the loop count typed into the field reaches the view model`() = mediaTab { vm, _ ->
        loadUrl(vm)
        mediaButton(MediaLabel.LOOP_OFF).performClick()
        waitForIdle()

        // Index 0 is the network-URL entry that loadUrl() used; the loop count is the last field.
        val fields = onAllNodes(hasSetTextAction())
        fields[fields.fetchSemanticsNodes().lastIndex].performTextReplacement("4")
        waitForIdle()

        assertEquals(4, vm.loopCount)
    }

    // ── Go Live / Instance Link ─────────────────────────────────────────────────

    @Test
    fun `Go Live projects the media to Instance Link when wired`() {
        val presenter = PresenterManager()
        val sent = mutableListOf<ScheduleItem>()
        mediaTab(presenterManager = presenter, onInstanceLinkSendProject = { sent += it }) { vm, _ ->
            loadUrl(vm, "https://example.org/clip.mp4")
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
        mediaTab(presenterManager = presenter) { vm, _ ->
            loadUrl(vm, "https://example.org/clip.mp4")
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
        loadUrl(vm)
        vm.setDuration(125_000L)

        assertTrue(showsContainingText("2:05"), "got ${renderedText()}")
    }
}
