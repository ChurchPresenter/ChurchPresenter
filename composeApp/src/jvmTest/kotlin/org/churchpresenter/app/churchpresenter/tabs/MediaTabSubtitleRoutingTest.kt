@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Subtitles menu's routing: which loaded file goes to which output (#612 item 2).
 *
 * Routing lives on this tab rather than in a profile's settings because the tracks belong to the
 * video, not to the install -- a profile has nothing stable to remember between one clip and the
 * next. The profile decides only whether it draws subtitles at all, and how they look.
 *
 * Only the app-drawn files can be routed, and that is a hardware fact rather than a choice: VLC
 * decodes once into a frame every output shares, so an embedded track is necessarily identical on all
 * of them.
 */
class MediaTabSubtitleRoutingTest {

    private fun srt(text: String = "Hello there"): String {
        val file = createTempFile(suffix = ".srt").toFile()
        file.writeText("1\n00:00:01,000 --> 00:00:04,000\n$text\n")
        file.deleteOnExit()
        return file.absolutePath
    }

    /** Two outputs, so the routing handle has something to choose between. */
    private fun twoProfiles(base: AppSettings): AppSettings = base.copy(
        projectionSettings = ProjectionSettings(
            outputProfiles = listOf(
                OutputProfile(id = "main", name = "Sanctuary"),
                OutputProfile(id = "lobby", name = "Lobby"),
            ),
        ),
    )

    private fun ComposeUiTest.openSubtitles() {
        mediaButton(MediaLabel.SUBTITLES).performClick()
        waitForIdle()
    }

    @Test
    fun `a loaded file is listed and routed everywhere to begin with`() =
        mediaTab(settings = ::twoProfiles) { vm, _ ->
            vm.loadMedia("/media/clip.mp4", org.churchpresenter.settings.utils.Constants.MEDIA_TYPE_LOCAL)
            vm.addSubtitleFile(srt())
            openSubtitles()

            onNodeWithTag(subtitleTrackTag(0)).assertIsDisplayed()
            assertTrue(vm.sidecarSubtitles.single().outputs.isEmpty(), "empty means every output")
        }

    @Test
    fun `routing one file to one output writes just that output`() =
        mediaTab(settings = ::twoProfiles) { vm, _ ->
            vm.loadMedia("/media/clip.mp4", org.churchpresenter.settings.utils.Constants.MEDIA_TYPE_LOCAL)
            vm.addSubtitleFile(srt())
            openSubtitles()

            onNodeWithTag(subtitleShowOnTag(0)).performClick()
            waitForIdle()
            onNodeWithTag(subtitleRouteTag("lobby")).performClick()
            waitForIdle()

            assertEquals(setOf("lobby"), vm.sidecarSubtitles.single().outputs)
        }

    @Test
    fun `unticking the last named output falls back to every output`() =
        mediaTab(settings = ::twoProfiles) { vm, _ ->
            vm.loadMedia("/media/clip.mp4", org.churchpresenter.settings.utils.Constants.MEDIA_TYPE_LOCAL)
            vm.addSubtitleFile(srt())
            vm.setSidecarOutputs(0, setOf("lobby"))
            openSubtitles()

            onNodeWithTag(subtitleShowOnTag(0)).performClick()
            waitForIdle()
            onNodeWithTag(subtitleRouteTag("lobby")).performClick()
            waitForIdle()

            // Empty means every output, so this is the only sane landing place: a track routed
            // nowhere would be silently invisible with its tick still on.
            assertTrue(vm.sidecarSubtitles.single().outputs.isEmpty())
        }

    @Test
    fun `all outputs puts a routed file back everywhere`() =
        mediaTab(settings = ::twoProfiles) { vm, _ ->
            vm.loadMedia("/media/clip.mp4", org.churchpresenter.settings.utils.Constants.MEDIA_TYPE_LOCAL)
            vm.addSubtitleFile(srt())
            vm.setSidecarOutputs(0, setOf("main"))
            openSubtitles()

            onNodeWithTag(subtitleShowOnTag(0)).performClick()
            waitForIdle()
            onNodeWithTag(SUBTITLE_ROUTE_ALL_TAG).performClick()
            waitForIdle()

            assertTrue(vm.sidecarSubtitles.single().outputs.isEmpty())
        }

    @Test
    fun `clicking a file's own row turns it off without unloading it`() =
        mediaTab(settings = ::twoProfiles) { vm, _ ->
            vm.loadMedia("/media/clip.mp4", org.churchpresenter.settings.utils.Constants.MEDIA_TYPE_LOCAL)
            vm.addSubtitleFile(srt())
            openSubtitles()

            onNodeWithTag(subtitleTrackTag(0)).performClick()
            waitForIdle()

            assertEquals(false, vm.sidecarSubtitles.single().enabled)
            assertEquals(1, vm.sidecarSubtitles.size, "off, not unloaded -- it is still in the list")
        }

    @Test
    fun `Off turns every loaded file off at once`() =
        mediaTab(settings = ::twoProfiles) { vm, _ ->
            vm.loadMedia("/media/clip.mp4", org.churchpresenter.settings.utils.Constants.MEDIA_TYPE_LOCAL)
            vm.addSubtitleFile(srt("English"))
            vm.addSubtitleFile(srt("Spanish"))
            openSubtitles()

            onNodeWithTag(SUBTITLE_OFF_TAG).performClick()
            waitForIdle()

            assertTrue(vm.sidecarSubtitles.none { it.enabled })
            assertEquals(MediaViewModel.SUBTITLES_OFF, vm.selectedSubtitleTrack)
        }

    @Test
    fun `a single-output install is offered no routing handle at all`() =
        mediaTab { vm, _ ->
            // One profile means the handle could only ever open a list holding one always-on entry,
            // which is what "Show on" with a lone tick beside it looked like.
            vm.loadMedia("/media/clip.mp4", org.churchpresenter.settings.utils.Constants.MEDIA_TYPE_LOCAL)
            vm.addSubtitleFile(srt())
            openSubtitles()

            onNodeWithTag(subtitleTrackTag(0)).assertIsDisplayed()
            onNodeWithTag(subtitleShowOnTag(0)).assertDoesNotExist()
        }

    @Test
    fun `two files each get their own row and their own routing`() =
        mediaTab(settings = ::twoProfiles) { vm, _ ->
            vm.loadMedia("/media/clip.mp4", org.churchpresenter.settings.utils.Constants.MEDIA_TYPE_LOCAL)
            vm.addSubtitleFile(srt("English"))
            vm.addSubtitleFile(srt("Spanish"))
            openSubtitles()

            onNodeWithTag(subtitleShowOnTag(1)).performClick()
            waitForIdle()
            onNodeWithTag(subtitleRouteTag("lobby")).performClick()
            waitForIdle()

            assertTrue(vm.sidecarSubtitles[0].outputs.isEmpty(), "the first was not touched")
            assertEquals(setOf("lobby"), vm.sidecarSubtitles[1].outputs)
        }
}
