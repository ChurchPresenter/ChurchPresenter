package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.app.churchpresenter.asDisplayRect
import org.churchpresenter.app.churchpresenter.mergedWindowBounds
import org.churchpresenter.app.churchpresenter.screenWindowRect
import org.churchpresenter.settings.DisplayRect
import org.churchpresenter.settings.MergeKind
import org.churchpresenter.settings.ResolvedMerge
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
}
