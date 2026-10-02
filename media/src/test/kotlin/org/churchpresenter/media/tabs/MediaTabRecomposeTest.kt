@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.media.FakeMediaOutput
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.settings.AppSettings
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

class MediaTabRecomposeTest {

    private val dir: File = Files.createTempDirectory("cp-media-recompose").toFile()

    private val swap = RecentFilesSwap(
        readPaths = { RecentMediaFiles.file to RecentMediaFiles.pinnedFile },
        writePaths = { f, p -> RecentMediaFiles.file = f; RecentMediaFiles.pinnedFile = p },
        entries = RecentMediaFiles.paths,
        pinned = RecentMediaFiles.pinned,
        prefix = "cp-media-recompose-recent",
    )

    @BeforeTest
    fun setUp() = swap.install()

    @AfterTest
    fun tearDown() {
        swap.restore()
        dir.deleteRecursively()
    }

    @Test
    fun `the tab stands up on its own defaults`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalMediaViewModel provides MediaViewModel(),
                    LocalMediaVlcPlayers provides false,
                ) { MediaTab() }
            }
        }
        waitForIdle()
        assertTrue(showsExactly(MediaLabel.SELECT_FILE) || showsExactly(MediaLabel.VLC_REQUIRED))
    }

    @Test
    fun `everything handed in afresh is what the tab then uses`() = runComposeUiTest {
        val vm = MediaViewModel()
        val clip = File(dir, "clip.mp4").apply { writeText("x") }
        val firstChooser = FakeFileChooser(answer = null)
        val secondChooser = FakeFileChooser(answer = clip.toPath())
        val firstAdds = mutableListOf<String>()
        val secondAdds = mutableListOf<String>()
        var chooser by mutableStateOf(firstChooser)
        var settings by mutableStateOf(AppSettings())
        var output by mutableStateOf(FakeMediaOutput())
        var onAdd by mutableStateOf<(String, String, String, String) -> Unit>({ url, _, _, _ -> firstAdds += url })
        var onSave by mutableStateOf<((String, String, String) -> Unit)?>(null)
        var onSend by mutableStateOf<((org.churchpresenter.core.models.schedule.ScheduleItem) -> Unit)?>(null)
        var onSettings by mutableStateOf<((AppSettings) -> AppSettings) -> Unit>({})
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMediaViewModel provides vm, LocalMediaVlcPlayers provides false) {
                    MediaTab(
                        appSettings = settings,
                        onSettingsChange = onSettings,
                        onAddToSchedule = onAdd,
                        onSavePreset = onSave,
                        presenterManager = output,
                        onInstanceLinkSendProject = onSend,
                        vlcAvailable = true,
                        vlcArchMismatch = false,
                        vlcLoadFailed = false,
                        fileChooser = chooser,
                    )
                }
            }
        }
        waitForIdle()
        chooser = secondChooser
        settings = AppSettings().copy(mediaStorageDirectory = dir.path)
        output = FakeMediaOutput()
        onAdd = { url, _, _, _ -> secondAdds += url }
        onSave = { _, _, _ -> }
        onSend = {}
        onSettings = {}
        waitForIdle()
        onNodeWithText(MediaLabel.SELECT_FILE).performClick()
        waitUntil(timeoutMillis = 5_000) { vm.isLoaded }
        assertEquals(1, secondChooser.callCount)
        assertEquals(0, firstChooser.callCount)
        mediaButton(MediaLabel.ADD_TO_SCHEDULE).performClick()
        waitForIdle()
        assertEquals(listOf(clip.absolutePath), secondAdds)
        assertTrue(firstAdds.isEmpty())
    }

    @Test
    fun `an address loaded while media is live clears the output first`() {
        val output = FakeMediaOutput().apply { setPresentingMode(Presenting.MEDIA) }
        mediaTab(presenterManager = output) { vm, _ ->
            onNodeWithText(MediaLabel.NETWORK_URL).performClick()
            waitForIdle()
            onAllNodes(hasSetTextAction())[0].performTextReplacement("  https://example.org/live.m3u8  ")
            waitForIdle()
            onNodeWithText("Load").performClick()
            waitUntil(timeoutMillis = 5_000) { vm.isLoaded }
            assertEquals("https://example.org/live.m3u8", vm.mediaUrl)
            assertTrue(output.clearDisplayRequested.value)
            assertTrue("https://example.org/live.m3u8" in RecentMediaFiles.paths)
        }
    }

    @Test
    fun `a recent sound file opens as audio`() {
        RecentMediaFiles.paths.add("/media/anthem.mp3")
        mediaTab { vm, _ ->
            onNodeWithText("anthem.mp3").performClick()
            waitForIdle()
            assertEquals(Constants.MEDIA_TYPE_AUDIO, vm.mediaType)
        }
    }

    @Test
    fun `hovering a transport key names it`() {
        mediaTab { vm, _ ->
            vm.loadMedia("https://example.org/clip.mp4", Constants.MEDIA_TYPE_URL)
            waitForIdle()
            mediaButton(MediaLabel.STOP).performMouseInput { moveTo(center) }
            waitUntil(timeoutMillis = 5_000) { showsExactly(MediaLabel.STOP) }
        }
    }

    @Test
    fun `with nowhere to schedule it, there is no add key`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalMediaViewModel provides MediaViewModel().apply { loadMedia("https://e.org/a.mp4", "url") },
                    LocalMediaVlcPlayers provides false,
                ) { MediaTab(vlcAvailable = true, fileChooser = FakeFileChooser(answer = null)) }
            }
        }
        waitForIdle()
        assertFalse(hasMediaButton(MediaLabel.ADD_TO_SCHEDULE))
    }
}
