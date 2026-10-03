package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.asDisplayRect
import org.churchpresenter.app.churchpresenter.mergedWindowBounds
import org.churchpresenter.app.churchpresenter.screenWindowRect
import org.churchpresenter.settings.DisplayRect
import org.churchpresenter.settings.MergeKind
import org.churchpresenter.settings.ResolvedMerge
import org.churchpresenter.settings.ScreenAssignment
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Where each output's tile of a merged picture comes from (#721). */
class MergeTileTest {

    private val wall = ResolvedMerge(
        profileId = "wall",
        kind = MergeKind.DEV_WINDOW,
        width = 3840,
        height = 1080,
        tiles = linkedMapOf(
            "screen:0" to DisplayRect(0, 0, 1920, 1080),
            "screen:1" to DisplayRect(1920, 0, 1920, 1080),
        ),
    )

    @Test
    fun `an output at its own size lays the picture out whole and shows its own half`() {
        assertEquals(TilePlacement(3840, 1080, 0, 0), tilePlacement(1920, 1080, wall, wall.tiles.getValue("screen:0")))
        val right = wall.tiles.getValue("screen:1")
        assertEquals(TilePlacement(3840, 1080, -1920, 0), tilePlacement(1920, 1080, wall, right))
    }

    @Test
    fun `a dev window drawn smaller than its resolution scales the picture with it`() {
        // 960x540 on screen for a 1920x1080 output: everything at half size.
        assertEquals(TilePlacement(1920, 540, -960, 0), tilePlacement(960, 540, wall, wall.tiles.getValue("screen:1")))
    }

    @Test
    fun `merged real displays open one window across the picture while every display is attached`() {
        val displays = wall.copy(
            kind = MergeKind.REAL_DISPLAY,
            desktop = DisplayRect(1920, 0, 3840, 1080),
        )
        val left = DisplayRect(1920, 0, 1920, 1080)
        val right = DisplayRect(3840, 0, 1920, 1080)
        val primary = DisplayRect(0, 0, 1920, 1080)

        assertEquals(DisplayRect(1920, 0, 3840, 1080), mergedWindowBounds(displays, listOf(primary, left, right)))
        assertNull(mergedWindowBounds(displays, listOf(primary, left)), "half a wall stays shut")
        assertNull(mergedWindowBounds(wall, listOf(left, right)), "a tiled merge has no one window")
    }

    @Test
    fun `every tile follows the first output of its picture, and other outputs their own`() {
        val alone = wall.copy(tiles = linkedMapOf("screen:3" to DisplayRect(0, 0, 1, 1)))
        val merges = mapOf("screen:3" to alone, "screen:1" to wall)
        assertEquals(0, mergeHostIndex(merges, "screen", 1))
        assertEquals(3, mergeHostIndex(merges, "screen", 3))
        assertEquals(5, mergeHostIndex(merges, "screen", 5))
        assertEquals(1, mergeHostIndex(merges, "ndi", 1), "another list's output 1 is not this one")
    }

    @Test
    fun `AWT bounds convert to the settings rectangle`() {
        assertEquals(DisplayRect(1920, 0, 1920, 1080), Rectangle(1920, 0, 1920, 1080).asDisplayRect())
    }

    @Test
    fun `a screen output opens on its own display, or across the merge when it is the first of one`() {
        val own = DisplayRect(1920, 0, 1920, 1080)
        val displays = wall.copy(kind = MergeKind.REAL_DISPLAY, desktop = DisplayRect(0, 0, 3840, 1080))
        val attached = listOf(DisplayRect(0, 0, 1920, 1080), own)

        assertEquals(own, screenWindowRect(null, "screen:0", attached) { own })
        assertEquals(DisplayRect(0, 0, 3840, 1080), screenWindowRect(displays, "screen:0", attached) { own })
        assertNull(screenWindowRect(displays, "screen:1", attached) { own }, "its window is the first one's")
    }

    @Test
    fun `an output sized as its merge takes the whole picture, keeping a display unset where it was`() {
        val onDisplay = ScreenAssignment(targetBoundsW = 1920, targetBoundsH = 1080).sizedAs(wall)
        assertEquals(listOf(3840, 1080), listOf(onDisplay.targetBoundsW, onDisplay.targetBoundsH))
        assertEquals(listOf(3840, 1080), listOf(onDisplay.ndiWidth, onDisplay.ndiHeight))

        val windowed = ScreenAssignment().sizedAs(wall)
        assertEquals(listOf(0, 0), listOf(windowed.targetBoundsW, windowed.targetBoundsH), "no display to size")
        assertEquals(3840, windowed.devWindowWidth)
    }

    @Test
    fun `a host key without an index leaves the output following its own`() {
        val odd = wall.copy(
            tiles = linkedMapOf("screen:main" to DisplayRect(0, 0, 1, 1), "screen:2" to DisplayRect(1, 0, 1, 1)),
        )
        assertEquals(2, mergeHostIndex(mapOf("screen:2" to odd), "screen", 2))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `content inside a merged tile knows it is in one, and content outside does not`() = runComposeUiTest {
        setContent {
            Column {
                Box(Modifier.size(192.dp, 108.dp)) {
                    MergedTile(wall, "screen:1") { Text(if (LocalInMergedTile.current) "in a tile" else "whole") }
                }
                Box(Modifier.size(192.dp, 108.dp)) {
                    MergedTile(wall, "screen:7") { Text(if (LocalInMergedTile.current) "in a tile" else "whole") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    MergedTile(wall, "screen:0") { Text("unbounded") }
                }
            }
        }
        onNodeWithText("in a tile").assertExists()
        onNodeWithText("whole").assertExists()
        onNodeWithText("unbounded").assertExists()
    }
}
