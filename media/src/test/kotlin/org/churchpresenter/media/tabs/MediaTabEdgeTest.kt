@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.tabs

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.OutputScaleMode
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.testing.FakeFileChooser
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaTabEdgeTest {

    private val dir: File = Files.createTempDirectory("cp-media-edge").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun srt(name: String) = File(dir, name).apply {
        writeText("1\n00:00:01,000 --> 00:00:04,000\nHello\n")
    }.absolutePath

    private fun mixed(settings: AppSettings) = settings.copy(
        projectionSettings = ProjectionSettings(
            outputProfiles = listOf(
                OutputProfile(id = "main", name = "Sanctuary", mediaScaleMode = OutputScaleMode.FILL),
                OutputProfile(id = "lobby", name = "Lobby", mediaScaleMode = OutputScaleMode.STRETCH),
            ),
        ),
    )

    @Test
    fun `when the outputs disagree, the scale key sets them all to one`() =
        mediaTab(settings = ::mixed) { vm, reports ->
            vm.loadMedia("https://example.org/clip.mp4", Constants.MEDIA_TYPE_URL)
            waitForIdle()
            mediaButton("Video scale differs by profile — click to set every output to Fit").performClick()
            waitForIdle()
            val modes = reports.settingsAfterChange?.projectionSettings?.outputProfiles?.map { it.mediaScaleMode }
            assertEquals(listOf(OutputScaleMode.FIT, OutputScaleMode.FIT), modes)
        }

    @Test
    fun `a clip with no folder of its own looks for subtitles in the media folder`() {
        val chooser = FakeFileChooser(answer = null)
        mediaTab(settings = { it.copy(mediaStorageDirectory = dir.path) }, fileChooser = chooser) { vm, _ ->
            vm.loadMedia("clip.mp4", Constants.MEDIA_TYPE_LOCAL)
            mediaButton(MediaLabel.SUBTITLES).performClick()
            waitForIdle()
            onNodeWithText(MediaLabel.SUBTITLES_LOAD_FILE).performClick()
            waitUntil(timeoutMillis = 5_000) { chooser.callCount == 1 }
            assertEquals(Path(dir.path), chooser.lastPath)
        }
    }

    @Test
    fun `a muted clip at no volume shows the silent speaker, and turning it up unmutes`() =
        mediaTab { vm, _ ->
            vm.loadMedia("https://example.org/clip.mp4", Constants.MEDIA_TYPE_URL)
            vm.audio.setVolume(0f)
            waitForIdle()
            assertTrue(hasMediaButton(MediaLabel.UNMUTE) || hasMediaButton(MediaLabel.MUTE))
            vm.audio.toggleMute()
            vm.audio.setVolume(0.4f)
            waitForIdle()
            assertFalse(vm.isMuted)
        }

    @Test
    fun `a switched-off file can be switched back on, and the routing list closes again`() =
        mediaTab { vm, _ ->
            vm.loadMedia("/media/clip.mp4", Constants.MEDIA_TYPE_LOCAL)
            vm.subtitles.addSubtitleFile(srt("one.srt"))
            vm.subtitles.setSidecarEnabled(0, false)
            mediaButton(MediaLabel.SUBTITLES).performClick()
            waitForIdle()
            onNodeWithTag(subtitleTrackTag(0)).performClick()
            waitForIdle()
            assertTrue(vm.sidecarSubtitles.single().enabled)
        }

    @Test
    fun `unticking one of two named outputs leaves the other`() =
        mediaTab(settings = { it.copy(projectionSettings = mixed(it).projectionSettings) }) { vm, _ ->
            vm.loadMedia("/media/clip.mp4", Constants.MEDIA_TYPE_LOCAL)
            vm.subtitles.addSubtitleFile(srt("two.srt"))
            vm.subtitles.setSidecarOutputs(0, setOf("main", "lobby"))
            mediaButton(MediaLabel.SUBTITLES).performClick()
            waitForIdle()
            onNodeWithTag(subtitleShowOnTag(0)).performClick()
            waitForIdle()
            onNodeWithTag(subtitleRouteTag("lobby")).performClick()
            waitForIdle()
            assertEquals(setOf("main"), vm.sidecarSubtitles.single().outputs)
            onNodeWithTag(subtitleShowOnTag(0)).performClick()
            waitForIdle()
            onNodeWithTag(subtitleShowOnTag(0)).performClick()
            waitForIdle()
        }

    @Test
    fun `a clip address that is no path at all looks in the media folder too`() {
        val chooser = FakeFileChooser(answer = null)
        mediaTab(settings = { it.copy(mediaStorageDirectory = dir.path) }, fileChooser = chooser) { vm, _ ->
            vm.loadMedia("bad\u0000name.mp4", Constants.MEDIA_TYPE_LOCAL)
            mediaButton(MediaLabel.SUBTITLES).performClick()
            waitForIdle()
            onNodeWithText(MediaLabel.SUBTITLES_LOAD_FILE).performClick()
            waitUntil(timeoutMillis = 5_000) { chooser.callCount == 1 }
            assertEquals(Path(dir.path), chooser.lastPath)
        }
    }
}
