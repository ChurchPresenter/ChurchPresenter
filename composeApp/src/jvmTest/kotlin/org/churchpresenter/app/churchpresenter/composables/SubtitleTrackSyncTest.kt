@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.SubpictureApi
import javax.swing.SwingUtilities
import kotlin.test.Test

/**
 * [SubtitleTrackSync], driven directly with a mocked [MediaPlayer] the same way
 * [SoftwarePlayerEventsTest] drives `softwarePlayerEvents` -- no real VLC install needed since the
 * player is never constructed, only mocked.
 */
class SubtitleTrackSyncTest {

    private fun mediaPlayer(): Pair<MediaPlayer, SubpictureApi> {
        val mp = mockk<MediaPlayer>(relaxed = true)
        val subpictures = mockk<SubpictureApi>(relaxed = true)
        every { mp.subpictures() } returns subpictures
        return mp to subpictures
    }

    @Test
    fun `a resolved track is applied to VLC when the app is not rendering subtitles itself`() = runComposeUiTest {
        val (mp, subpictures) = mediaPlayer()
        val viewModel = MediaViewModel().apply { selectSubtitleTrack(3) }

        setContent {
            SubtitleTrackSync(viewModel = viewModel, mp = mp, gate = PlayerReleaseGate())
        }
        waitForIdle()
        SwingUtilities.invokeAndWait { }

        verify { subpictures.setTrack(3) }
    }

    @Test
    fun `an undecided track is never sent to VLC`() = runComposeUiTest {
        val (mp, subpictures) = mediaPlayer()
        val viewModel = MediaViewModel()

        setContent {
            SubtitleTrackSync(viewModel = viewModel, mp = mp, gate = PlayerReleaseGate())
        }
        waitForIdle()
        SwingUtilities.invokeAndWait { }

        verify(exactly = 0) { subpictures.setTrack(any()) }
    }

    @Test
    fun `nothing is sent to VLC once the app is drawing the subtitles itself`() = runComposeUiTest {
        val temp = kotlin.io.path.createTempFile(suffix = ".srt").toFile()
        temp.writeText("1\n00:00:00,000 --> 00:00:10,000\nHello\n")
        val (mp, subpictures) = mediaPlayer()
        val viewModel = MediaViewModel().apply {
            setSubtitleFile(temp.absolutePath)
            selectSubtitleTrack(3)
        }

        try {
            setContent {
                SubtitleTrackSync(viewModel = viewModel, mp = mp, gate = PlayerReleaseGate())
            }
            waitForIdle()
            SwingUtilities.invokeAndWait { }

            verify(exactly = 0) { subpictures.setTrack(any()) }
        } finally {
            temp.delete()
        }
    }
}
