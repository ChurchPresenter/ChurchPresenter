package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MediaRemoteTransportTest {

    @Test
    fun `a file loaded, played live and then unloaded is reported and then cleared`() = runComposeUiTest {
        val server = CompanionServer()
        val media = MediaViewModel()
        val manager = PresenterManager()
        setContent { MediaRemoteWiring(server, media, manager) }
        waitForIdle()

        media.loadMedia("file:///nowhere/clip.mp4", "local")
        manager.setPresentingMode(Presenting.MEDIA)
        mainClock.advanceTimeBy(600)
        waitForIdle()
        assertTrue(media.isLoaded)

        media.unload()
        mainClock.advanceTimeBy(600)
        waitForIdle()
        mainClock.advanceTimeBy(600)
        waitForIdle()

        assertFalse(media.isLoaded)
    }

    @Test
    fun `every transport command from a companion reaches the player`() = runComposeUiTest {
        val server = CompanionServer()
        val media = MediaViewModel()
        setContent { MediaRemoteWiring(server, media, PresenterManager()) }
        waitForIdle()
        waitUntil { server.onMediaMuteToggle.subscriptionCount.value > 0 }
        val mutedBefore = media.isMuted

        server.onMediaPlayPause.tryEmit(Unit)
        server.onMediaStop.tryEmit(Unit)
        server.onMediaSeekForward.tryEmit(Unit)
        server.onMediaSeekBackward.tryEmit(Unit)
        server.onMediaSeekTo.tryEmit(1_000L)
        server.onMediaSetVolume.tryEmit(0.25f)
        server.onMediaMuteToggle.tryEmit(Unit)
        waitForIdle()

        waitUntil { media.isMuted != mutedBefore }
        assertEquals(!mutedBefore, media.isMuted)
    }
}
