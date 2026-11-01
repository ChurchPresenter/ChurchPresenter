@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import org.churchpresenter.sharedui.utils.FALLBACK_STAGE_ASPECT
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.QuickBackgroundTray
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.QuickBackground
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithTag
import org.churchpresenter.profiles.SONG_BACKGROUND_VIDEO_STILL_TAG
import org.churchpresenter.profiles.seedVideoFrame
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import org.churchpresenter.sharedui.screenshot.captureComponent

/**
 * The quick backgrounds tray, as it sits under the live preview.
 *
 * Shot on its own rather than through the sidebar: it is one component with three states that have
 * nothing to do with the panels above it — open, shut, and with a pick standing. Shut is not just
 * the open one with the tiles hidden; it collapses to a row of mini swatches so a pick is still one
 * click away, which is the state a narrow sidebar spends most of its time in.
 *
 * Given a fixed width, because the tray lays its tiles out three to a row against whatever the
 * sidebar gives it, and a shot at the test window's full width would show a row it never draws.
 */
class QuickBackgroundTrayScreenshotTest {

    private fun tile(id: String, hex: String) = QuickBackground(
        id = id,
        background = SongBackground(type = SongBackgroundType.COLOR, color = hex),
        lowerThirdBackground = SongBackground(type = SongBackgroundType.COLOR, color = hex),
    )

    private val tray = listOf(
        tile("tile1", "#1B2A5B"),
        tile("tile2", "#7B3FA6"),
        tile("tile3", "#2E6B4F"),
        tile("tile4", "#8A3B2E"),
        tile("tile5", "#000000"),
    )

    private fun shoot(
        name: String,
        activeId: String? = null,
        expanded: Boolean = true,
        backgrounds: List<QuickBackground> = tray,
        drive: ComposeUiTest.() -> Unit = {},
    ) =
        captureComponent(SECTION, name, drive = drive) {
            Box(Modifier.width(SIDEBAR_WIDTH)) {
                QuickBackgroundTray(
                    backgrounds = backgrounds,
                    tileAspect = FALLBACK_STAGE_ASPECT,
                    activeId = activeId,
                    expanded = expanded,
                    onExpandedChange = {},
                    onPick = {},
                )
            }
        }

    /** Open: every tile with its slot number and its name, three to a row. */
    @Test
    fun `the tray open`() = shoot("tray_open")

    /** One picked — the tile is ringed, and the header grows the control that undoes it. */
    @Test
    fun `a background picked`() = shoot("tray_picked", activeId = "tile2")

    /** Shut: the tiles collapse into the header as mini swatches. */
    @Test
    fun `the tray shut`() = shoot("tray_shut", expanded = false)

    /** Shut with a pick standing, which is what an operator sees for most of a service. */
    @Test
    fun `the tray shut with a background picked`() =
        shoot("tray_shut_picked", activeId = "tile2", expanded = false)

    /** A motion background: its first frame as the tile, with the video badge in the corner. */
    @Test
    fun `the tray with a motion background`() {
        val dir = Files.createTempDirectory("cp-tray-video").toFile()
        val clip = File(dir, "Welcome Loop.mp4")
        val frame = seedVideoFrame(clip)
        try {
            val video = QuickBackground(
                id = "video",
                background = SongBackground(type = SongBackgroundType.VIDEO, video = clip.absolutePath),
            )
            shoot("tray_open_video", backgrounds = listOf(video) + tray.take(2)) {
                waitUntil("the first frame must reach the tile") {
                    hasVideoStill()
                }
                waitForIdle()
            }
        } finally {
            frame.delete()
            dir.deleteRecursively()
        }
    }

    private companion object {
        const val SECTION = "quickBackgroundTray"

        /** What the right-hand sidebar gives the tray at its default width. */
        val SIDEBAR_WIDTH = 260.dp
    }

    private fun ComposeUiTest.hasVideoStill() =
        onAllNodesWithTag(SONG_BACKGROUND_VIDEO_STILL_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
}
