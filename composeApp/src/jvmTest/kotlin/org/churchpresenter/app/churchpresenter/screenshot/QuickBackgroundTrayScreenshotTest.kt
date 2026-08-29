@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.QuickBackgroundTray
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.QuickBackground
import kotlin.test.Test

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

    private fun shoot(name: String, activeId: String? = null, expanded: Boolean = true) =
        captureComponent(SECTION, name) {
            Box(Modifier.width(SIDEBAR_WIDTH)) {
                QuickBackgroundTray(
                    backgrounds = tray,
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

    private companion object {
        const val SECTION = "quickBackgroundTray"

        /** What the right-hand sidebar gives the tray at its default width. */
        val SIDEBAR_WIDTH = 260.dp
    }
}
