@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.composables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.v2.runComposeUiTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.churchpresenter.media.viewmodel.MediaViewModel
import uk.co.caprica.vlcj.player.base.ControlsApi
import uk.co.caprica.vlcj.player.base.MediaApi
import uk.co.caprica.vlcj.player.base.MediaPlayer
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoftwarePlaybackTest {

    private val url = "https://example.org/clip.mp4"
    private val dir: File = Files.createTempDirectory("cp-software-playback").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private class Player {
        val mp = mockk<MediaPlayer>(relaxed = true)
        val media = mockk<MediaApi>(relaxed = true)
        val controls = mockk<ControlsApi>(relaxed = true)

        init {
            every { mp.media() } returns media
            every { mp.controls() } returns controls
        }
    }

    @Test
    fun `the player listens, opens the clip, and is released when it leaves`() = runComposeUiTest {
        val player = Player()
        val vm = MediaViewModel().apply { loadMedia(url, "url") }
        var shown by mutableStateOf(true)
        var released = false
        setContent {
            if (shown) {
                SoftwarePlayback(
                    vm, player.mp, mutableStateOf(false), audioEnabled = false, release = { released = true },
                )
            }
        }
        waitForIdle()
        verify { player.mp.events().addMediaPlayerEventListener(any()) }
        verify { player.media.play(url, *anyVararg()) }
        shown = false
        waitForIdle()
        assertTrue(released)
        verify { player.controls.stop() }
    }

    @Test
    fun `nothing loaded clears the shared frame and opens nothing`() = runComposeUiTest {
        val player = Player()
        SharedVideoOutput.frame.value = androidx.compose.ui.graphics.ImageBitmap(1, 1)
        setContent { SoftwarePlayback(MediaViewModel(), player.mp, mutableStateOf(false), audioEnabled = false) }
        waitForIdle()
        assertNull(SharedVideoOutput.frame.value)
        verify(exactly = 0) { player.media.play(any<String>(), *anyVararg()) }
    }

    @Test
    fun `a load with sound sets the volume first`() = runComposeUiTest {
        val player = Player()
        val vm = MediaViewModel().apply { loadMedia(url, "url"); audio.setVolume(0.25f) }
        setContent { SoftwarePlayback(vm, player.mp, mutableStateOf(false), audioEnabled = true) }
        waitForIdle()
        verify { player.mp.audio().setVolume(25) }
        verify { player.mp.audio().setMute(true) }
    }

    @Test
    fun `a subtitle VLC must burn in reopens the clip where the operator was`() = runComposeUiTest {
        val player = Player()
        val vm = MediaViewModel().apply { loadMedia(url, "url"); position.setDuration(60_000L) }
        setContent { SoftwarePlayback(vm, player.mp, mutableStateOf(false), audioEnabled = false) }
        waitForIdle()
        vm.position.setCurrentPosition(20_000L)
        val ass = File(dir, "styled.ass").apply { writeText("[Script Info]\n") }
        vm.subtitles.addSubtitleFile(ass.path)
        waitForIdle()
        verify(exactly = 2) { player.media.play(url, *anyVararg()) }
        verify { player.controls.setTime(20_000L) }
    }

    @Test
    fun `a new clip starts from the beginning`() = runComposeUiTest {
        val player = Player()
        val vm = MediaViewModel().apply { loadMedia(url, "url") }
        setContent { SoftwarePlayback(vm, player.mp, mutableStateOf(false), audioEnabled = false) }
        waitForIdle()
        vm.position.setCurrentPosition(20_000L)
        vm.loadMedia("https://example.org/next.mp4", "url")
        waitForIdle()
        verify { player.media.play("https://example.org/next.mp4", *anyVararg()) }
        verify(exactly = 0) { player.controls.setTime(20_000L) }
    }
}
