@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.dialogs.SONG_BACKGROUND_PANEL_TAG
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QuickBackground
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shelf under the stage preview, which is the only place a quick background is created, edited
 * or removed — the tray in the main window writes nothing.
 *
 * The tile's panel is gated behind its own OK, so a tile can be played with and abandoned. That is
 * separate from the Options dialog's OK, which is what keeps any of this out of `settings.json`.
 */
class QuickBackgroundStripTest {

    private fun withTiles(vararg colors: String) = AppSettings(
        quickBackgrounds = colors.mapIndexed { index, color ->
            QuickBackground(
                id = "tile$index",
                background = SongBackground(type = SongBackgroundType.COLOR, color = color),
                lowerThirdBackground = SongBackground(type = SongBackgroundType.COLOR, color = color),
            )
        },
    )

    @Test
    fun `an empty shelf offers the tile that starts the first one`() = backgroundTab { _ ->
        onNodeWithText("0 / 10").assertIsDisplayed()
        onNodeWithTag(QUICK_BACKGROUND_ADD_TAG).assertIsDisplayed()
    }

    @Test
    fun `adding a background puts one in the tray`() = backgroundTab { settings ->
        onNodeWithTag(QUICK_BACKGROUND_ADD_TAG).performClick()
        waitForIdle()
        assertEquals(1, settings().quickBackgrounds.size)
        assertEquals(
            SongBackgroundType.COLOR,
            settings().quickBackgrounds.single().background.type,
            "a new tile overrides rather than inherits",
        )
    }

    @Test
    fun `the count keeps up with the shelf`() = backgroundTab(withTiles("#112233", "#445566")) { _ ->
        onNodeWithText("2 / 10").assertIsDisplayed()
    }

    @Test
    fun `removing a tile takes it out of the tray`() = backgroundTab(withTiles("#112233")) { settings ->
        onNodeWithContentDescription("Remove").performClick()
        waitForIdle()
        assertTrue(settings().quickBackgrounds.isEmpty(), "the tile must go")
    }

    @Test
    fun `the add tile disappears once every slot is filled`() {
        val full = withTiles(*Array(10) { "#00000$it" })
        backgroundTab(full) { _ ->
            onNodeWithText("10 / 10").assertIsDisplayed()
            assertEquals(
                0,
                onAllNodesWithTag(QUICK_BACKGROUND_ADD_TAG)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).size,
                "there is no eleventh slot to add into",
            )
        }
    }

    @Test
    fun `a tile's panel opens on the tile and closes on OK`() = backgroundTab(withTiles("#112233")) { _ ->
        openTilePanel()
        onNodeWithTag(SONG_BACKGROUND_PANEL_TAG).assertIsDisplayed()
        inPanel("OK").performClick()
        waitForIdle()
        assertEquals(
            0,
            onAllNodesWithTag(SONG_BACKGROUND_PANEL_TAG)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).size,
            "the panel must be gone",
        )
    }

    @Test
    fun `cancelling a tile's panel leaves the tile as it was`() =
        backgroundTab(withTiles("#112233")) { settings ->
            val before = settings().quickBackgrounds.single()
            openTilePanel()
            // Anything the panel can change without a file chooser: one of its ready-made looks.
            inPanel("Cinema").performClick()
            waitForIdle()
            inPanel("Cancel").performClick()
            waitForIdle()
            assertEquals(
                before,
                settings().quickBackgrounds.single(),
                "an abandoned edit must leave the tile exactly as it was",
            )
        }

    @Test
    fun `confirming a tile's panel keeps the edit`() = backgroundTab(withTiles("#112233")) { settings ->
        openTilePanel()
        inPanel("Cinema").performClick()
        waitForIdle()
        inPanel("OK").performClick()
        waitForIdle()
        val edited = settings().quickBackgrounds.single()
        assertTrue(edited.background.dim > 0, "the Cinema look dims")
        assertTrue(edited.background.blur > 0, "and blurs")
    }

    /** Clicks the first tile's swatch, which is what opens the panel over it. */
    private fun androidx.compose.ui.test.ComposeUiTest.openTilePanel() {
        onNodeWithText("1").performClick()
        waitForIdle()
    }

    /**
     * The node reading [text] that is inside the open panel.
     *
     * The panel is a `Popup` laid over the tab, and the tab has ready-made looks of its own under
     * the same names — so "Cinema" means two different controls while the panel is up.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.inPanel(text: String): SemanticsNodeInteraction {
        val panel = onNodeWithTag(SONG_BACKGROUND_PANEL_TAG).fetchSemanticsNode().boundsInRoot
        val matches = onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false)
        val index = matches.indexOfFirst { panel.contains(it.boundsInRoot.center) }
        check(index >= 0) { "the open panel has no node reading \"${'$'}text\"" }
        return onAllNodesWithText(text)[index]
    }
}
