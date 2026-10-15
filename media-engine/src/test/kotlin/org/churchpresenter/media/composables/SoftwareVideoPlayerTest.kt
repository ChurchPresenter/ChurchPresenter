@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.composables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.mockk.mockk
import io.mockk.verify
import org.churchpresenter.media.viewmodel.MediaViewModel
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import java.nio.ByteBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoftwareVideoPlayerTest {

    private class FakeVlc {
        val mp = mockk<MediaPlayer>(relaxed = true)
        var format: BufferFormatCallback? = null
        var render: RenderCallback? = null
        var released = 0
        var opened = 0
        val open: () -> SoftwareVlc? = {
            opened++
            SoftwareVlc(mp, { f, r -> format = f; render = r }, { released++ })
        }
    }

    @AfterTest
    fun cleanUp() {
        SharedVideoOutput.frame.value = null
    }

    @Test
    fun `with no VLC to open, nothing is drawn`() = runComposeUiTest {
        setContent { SoftwareVideo(MediaViewModel(), Modifier.testTag("video"), true, "", true) { null } }
        waitForIdle()
        assertTrue(onAllNodesWithTag("video").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a decoded frame is drawn, and leaving releases the player`() = runComposeUiTest {
        val vlc = FakeVlc()
        val vm = MediaViewModel().apply { loadMedia("https://example.org/clip.mp4", "url") }
        var shown by mutableStateOf(true)
        setContent {
            if (shown) {
                SoftwareVideo(vm, Modifier.testTag("video"), false, "", true, vlc.open)
            }
        }
        waitForIdle()
        verify { vlc.mp.events().addMediaPlayerEventListener(any()) }
        vlc.format!!.getBufferFormat(2, 2)
        val pixels = ByteBuffer.allocate(2 * 2 * 4)
        vlc.render!!.display(vlc.mp, arrayOf(pixels), mockk(relaxed = true))
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("video").fetchSemanticsNodes().isNotEmpty() }
        shown = false
        waitForIdle()
        assertEquals(1, vlc.opened)
        assertEquals(1, vlc.released)
    }
}
