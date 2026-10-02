@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.media.FakeMediaOutput
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.testing.FakeFileChooser
import org.churchpresenter.sharedui.testing.RecentFilesSwap
import org.churchpresenter.sharedui.testing.showsExactly
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaTabPickTest {

    private val dir: File = Files.createTempDirectory("cp-media-pick").toFile()

    private val swap = RecentFilesSwap(
        readPaths = { RecentMediaFiles.file to RecentMediaFiles.pinnedFile },
        writePaths = { f, p -> RecentMediaFiles.file = f; RecentMediaFiles.pinnedFile = p },
        entries = RecentMediaFiles.paths,
        pinned = RecentMediaFiles.pinned,
        prefix = "cp-media-pick-recent",
    )

    @BeforeTest
    fun setUp() = swap.install()

    @AfterTest
    fun tearDown() {
        swap.restore()
        dir.deleteRecursively()
    }

    private fun file(name: String) = File(dir, name).apply { writeText("x") }

    @Test
    fun `a picked video is loaded, remembered, and replaces what was live`() {
        val clip = file("sermon.mp4")
        val output = FakeMediaOutput().apply { setPresentingMode(Presenting.MEDIA) }
        val chooser = FakeFileChooser(answer = clip.toPath())
        mediaTab(fileChooser = chooser, presenterManager = output) { vm, _ ->
            onNodeWithText(MediaLabel.SELECT_FILE).performClick()
            waitUntil(timeoutMillis = 5_000) { vm.isLoaded }
            assertEquals(clip.absolutePath, vm.mediaUrl)
            assertEquals(Constants.MEDIA_TYPE_LOCAL, vm.mediaType)
            assertTrue(clip.absolutePath in RecentMediaFiles.paths)
            assertTrue(output.clearDisplayRequested.value)
        }
    }

    @Test
    fun `a picked sound file is loaded as audio, and says it plays on in the background`() {
        val song = file("anthem.mp3")
        mediaTab(fileChooser = FakeFileChooser(answer = song.toPath())) { vm, _ ->
            onNodeWithText(MediaLabel.SELECT_FILE).performClick()
            waitUntil(timeoutMillis = 5_000) { vm.isLoaded }
            assertEquals(Constants.MEDIA_TYPE_AUDIO, vm.mediaType)
            waitForIdle()
            assertTrue(showsExactly(AUDIO_CONTINUES))
        }
    }

    @Test
    fun `a pick that is cancelled loads nothing`() {
        val chooser = FakeFileChooser(answer = null)
        mediaTab(fileChooser = chooser) { vm, _ ->
            onNodeWithText(MediaLabel.SELECT_FILE).performClick()
            waitUntil(timeoutMillis = 5_000) { chooser.callCount == 1 }
            waitForIdle()
            assertFalse(vm.isLoaded)
            assertTrue(RecentMediaFiles.paths.isEmpty())
        }
    }

    @Test
    fun `a picked video does not clear an output showing something else`() {
        val clip = file("bumper.mp4")
        val output = FakeMediaOutput().apply { setPresentingMode(Presenting.BIBLE) }
        mediaTab(fileChooser = FakeFileChooser(answer = clip.toPath()), presenterManager = output) { vm, _ ->
            onNodeWithText(MediaLabel.SELECT_FILE).performClick()
            waitUntil(timeoutMillis = 5_000) { vm.isLoaded }
            assertFalse(output.clearDisplayRequested.value)
        }
    }

    @Test
    fun `save preset hands over the loaded clip`() {
        val saved = mutableListOf<Triple<String, String, String>>()
        mediaTab(onSavePreset = { url, title, type -> saved += Triple(url, title, type) }) { vm, _ ->
            vm.loadMedia("https://example.org/welcome.mp4", Constants.MEDIA_TYPE_URL)
            waitForIdle()
            mediaButton(SAVE_PRESET).performClick()
            waitForIdle()
            val expected = Triple("https://example.org/welcome.mp4", "welcome.mp4", Constants.MEDIA_TYPE_URL)
            assertEquals(listOf(expected), saved)
        }
    }

    private companion object {
        const val AUDIO_CONTINUES =
            "Audio will continue playing in the background when switching tabs or displaying other content"
        const val SAVE_PRESET = "Save preset"
    }
}
