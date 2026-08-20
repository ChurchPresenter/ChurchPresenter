@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.tabs.MediaLabel
import org.churchpresenter.app.churchpresenter.tabs.RecentMediaFiles
import org.churchpresenter.app.churchpresenter.tabs.mediaButton
import org.churchpresenter.app.churchpresenter.tabs.mediaTab
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The Media tab's states, in both themes.
 *
 * Media was the only one of the three content tabs (Pictures, Presentation, Media) without a
 * screenshot suite, which is how its bar heights and action-button placement drifted away from the
 * other two unnoticed — the drift was only ever visible in `previewApp/media_*.png`, where the tab
 * is one panel of a whole-window shot. Shoot the states here and the next drift shows up in review.
 *
 * Nothing here needs VLC: `loadMedia` is plain state assignment, so a fixture path that does not
 * exist still drives the loaded, playing and seek-bar layouts. The one state that genuinely depends
 * on VLC is its absence, which the harness models with `vlcAvailable = false`.
 */
class MediaTabScreenshotTest {

    private fun shoot(
        name: String,
        presenter: Boolean = true,
        vlcAvailable: Boolean = true,
        width: Dp? = null,
        drive: ComposeUiTest.(MediaViewModel) -> Unit = { waitForIdle() },
    ) = stackedThemes(SECTION, name) { mode, file ->
        mediaTab(
            presenterManager = if (presenter) PresenterManager() else null,
            vlcAvailable = vlcAvailable,
            width = width,
            themeMode = mode,
        ) { vm, _ ->
            drive(vm)
            captureTo(file)
        }
    }

    /** Puts the tab in its loaded-video state, exactly as picking a file would. */
    private fun ComposeUiTest.loadVideo(vm: MediaViewModel) {
        vm.loadMedia("$FIXTURES/Welcome Loop.mp4", Constants.MEDIA_TYPE_LOCAL)
        waitForIdle()
    }

    private lateinit var savedPaths: List<String>
    private lateinit var savedPinned: List<String>

    @BeforeTest
    fun snapshotRecents() {
        savedPaths = RecentMediaFiles.paths.toList()
        savedPinned = RecentMediaFiles.pinned.toList()
        RecentMediaFiles.paths.clear()
        RecentMediaFiles.pinned.clear()
    }

    @AfterTest
    fun restoreRecents() {
        RecentMediaFiles.paths.clear()
        RecentMediaFiles.paths.addAll(savedPaths)
        RecentMediaFiles.pinned.clear()
        RecentMediaFiles.pinned.addAll(savedPinned)
    }

    @Test
    fun `nothing loaded yet`() = shoot("no_file")

    @Test
    fun `a local video loaded`() = shoot("local_loaded") { vm -> loadVideo(vm) }

    @Test
    fun `playing, with the seek bar showing a position`() = shoot("playing") { vm ->
        loadVideo(vm)
        vm.setDuration(SEEK_DURATION_MS)
        vm.setCurrentPosition(SEEK_POSITION_MS)
        mediaButton(MediaLabel.PLAY).performClick()
        waitForIdle()
    }

    @Test
    fun `the recents bar, one entry pinned`() = shoot("recent_files") { vm ->
        RecentMediaFiles.add("$FIXTURES/Baptism Testimony.mp4")
        RecentMediaFiles.add("$FIXTURES/Welcome Loop.mp4")
        RecentMediaFiles.togglePin("$FIXTURES/Welcome Loop.mp4")
        loadVideo(vm)
    }

    @Test
    fun `the network URL source`() = shoot("network_url") {
        onNodeWithText(MediaLabel.NETWORK_URL).performClick()
        waitForIdle()
    }

    @Test
    fun `with no output to go live on the button is gone`() =
        shoot("no_presenter", presenter = false) { vm -> loadVideo(vm) }

    @Test
    fun `a narrow panel wraps the controls`() = shoot("narrow_panel", width = 420.dp) { vm ->
        loadVideo(vm)
    }

    @Test
    fun `VLC missing`() = shoot("vlc_missing", vlcAvailable = false)

    private companion object {
        const val SECTION = "mediaTab"

        /**
         * A neutral root, not a repo-relative one: the tab prints the file name in every shot, and
         * a repo-relative fixture resolves through the developer's home directory — their name,
         * committed into the PNG for ever. Fixed, not a temp dir, so a re-record is not a diff.
         */
        const val FIXTURES = "/tmp/churchpresenter-screenshots/media"

        const val SEEK_DURATION_MS = 214_000L
        const val SEEK_POSITION_MS = 72_000L
    }
}
