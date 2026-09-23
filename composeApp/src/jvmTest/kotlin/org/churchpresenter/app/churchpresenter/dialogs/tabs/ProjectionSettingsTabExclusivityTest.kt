@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rule that keeps two outputs from fighting over one piece of hardware: a display can be driven
 * by exactly one window, so pointing a row at a display something else already uses takes it away
 * from that other thing first.
 *
 * This matters in the booth. Two windows aimed at the same projector means one silently wins and the
 * operator cannot tell which; the tab avoids that by clearing the loser as the choice is made,
 * across both the primary targets and the key outputs.
 *
 * Ported from `ProjectionSettingsTabExclusivityTest` unchanged in substance -- what a row *shows*
 * moved onto a profile, but which hardware it drives did not.
 */
class ProjectionSettingsTabExclusivityTest {

    private fun settingsWith(change: ProjectionSettings.() -> ProjectionSettings): AppSettings =
        withProfiles().let { it.copy(projectionSettings = it.projectionSettings.change()) }

    /** The second external display, as the tab's own resolver fills it in. */
    private fun display2() = ScreenAssignment(
        targetDisplay = 2, targetBoundsX = 3200, targetBoundsY = 0, targetBoundsW = 3840, targetBoundsH = 2160,
    )

    /** The first external display. */
    private fun display1() = ScreenAssignment(
        targetDisplay = 1, targetBoundsX = 1920, targetBoundsY = 0, targetBoundsW = 1280, targetBoundsH = 720,
    )

    private val pickDisplay2 = "Display 2 (3840x2160 @ 3200,0)"
    private val pickDisplay1 = "Display 1 (1280x720 @ 1920,0)"

    // ── One display, one window ─────────────────────────────────────────────────────────────────

    @Test
    fun `pointing a row at a display another row uses takes it from that row`() = projectionTab { get ->
        assertEquals(1, get().projectionSettings.screenAssignments[0].targetDisplay, "row 0 starts on D1")
        assertEquals(2, get().projectionSettings.screenAssignments[1].targetDisplay, "row 1 starts on D2")

        // Point row 0 at the display row 1 already has.
        gridButton(Grid.targetDisplay(row = 0)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText(pickDisplay2).performClick()
        waitForIdle()

        assertEquals(2, get().projectionSettings.screenAssignments[0].targetDisplay, "row 0 takes the display")
        assertEquals(
            Constants.KEY_TARGET_NONE,
            get().projectionSettings.screenAssignments[1].targetDisplay,
            "and row 1 must be cleared rather than left fighting for it",
        )
        assertEquals(
            Int.MIN_VALUE,
            get().projectionSettings.screenAssignments[1].targetBoundsX,
            "its stale bounds must be cleared too",
        )
        gridButton(Grid.targetDisplay(row = 0)).assertTextEquals("D2 (3840x2160)")
        gridButton(Grid.targetDisplay(row = 1)).assertTextEquals("None")
    }

    @Test
    fun `pointing a row at a display used as another row's key output clears that key output`() {
        // Row 1 uses D1 as its key output; row 0 then claims D1 as its primary.
        val keyed = settingsWith {
            copy(
                screenAssignments = listOf(
                    display1(),
                    display2().copy(
                        keyTargetDisplay = 1, keyTargetType = "screen",
                        keyTargetBoundsX = 1920, keyTargetBoundsY = 0,
                        keyTargetBoundsW = 1280, keyTargetBoundsH = 720,
                    ),
                ),
            )
        }
        projectionTab(initial = keyed) { get ->
            assertEquals(1, get().projectionSettings.screenAssignments[1].keyTargetDisplay, "row 1 keys off D1")

            // Row 0 already targets D1, so move it away and back to trigger the sweep.
            gridButton(Grid.targetDisplay(row = 0)).performScrollTo().performClick()
            waitForIdle()
            onNodeWithText(pickDisplay1).performClick()
            waitForIdle()

            assertEquals(
                Constants.KEY_TARGET_NONE,
                get().projectionSettings.screenAssignments[1].keyTargetDisplay,
                "the key output pointing at the same display must be cleared",
            )
            assertEquals(
                Int.MIN_VALUE,
                get().projectionSettings.screenAssignments[1].keyTargetBoundsX,
                "along with its bounds",
            )
            gridButton(Grid.keyOutput(row = 1)).assertTextEquals("None")
        }
    }

    @Test
    fun `choosing a key output takes the display from a row that was driving it`() = projectionTab { get ->
        assertEquals(2, get().projectionSettings.screenAssignments[1].targetDisplay, "row 1 drives D2")

        gridButton(Grid.keyOutput(row = 0)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText(pickDisplay2).performClick()
        waitForIdle()

        assertEquals(2, get().projectionSettings.screenAssignments[0].keyTargetDisplay, "row 0 keys off D2")
        assertEquals(
            Constants.KEY_TARGET_NONE,
            get().projectionSettings.screenAssignments[1].targetDisplay,
            "so row 1 must stop driving it",
        )
        gridButton(Grid.targetDisplay(row = 1)).assertTextEquals("None")
    }

    @Test
    fun `two rows cannot key off the same display`() {
        val bothKeyed = settingsWith {
            copy(
                screenAssignments = listOf(
                    display1(),
                    display2().copy(
                        keyTargetDisplay = 1, keyTargetType = "screen",
                        keyTargetBoundsX = 1920, keyTargetBoundsY = 0,
                        keyTargetBoundsW = 1280, keyTargetBoundsH = 720,
                    ),
                ),
            )
        }
        projectionTab(initial = bothKeyed) { get ->
            gridButton(Grid.keyOutput(row = 0)).performScrollTo().performClick()
            waitForIdle()
            onNodeWithText(pickDisplay1).performClick()
            waitForIdle()

            assertEquals(1, get().projectionSettings.screenAssignments[0].keyTargetDisplay, "row 0 takes it")
            assertEquals(
                Constants.KEY_TARGET_NONE,
                get().projectionSettings.screenAssignments[1].keyTargetDisplay,
                "and row 1 must give it up",
            )
        }
    }

    @Test
    fun `detaching a row leaves every other row alone`() = projectionTab { get ->
        gridButton(Grid.targetDisplay(row = 0)).performScrollTo().performClick()
        waitForIdle()
        // Both key-output dropdowns also read "None"; the menu's own entry is composed last.
        onAllNodesWithText("None").onLast().performClick()
        waitForIdle()

        assertEquals(
            Constants.KEY_TARGET_NONE,
            get().projectionSettings.screenAssignments[0].targetDisplay,
            "row 0 is detached",
        )
        assertEquals(
            2,
            get().projectionSettings.screenAssignments[1].targetDisplay,
            "detaching claims no hardware, so nothing else is swept",
        )
        gridButton(Grid.targetDisplay(row = 1)).assertTextEquals("D2 (3840x2160)")
    }

    /**
     * The sweep matches on the display's full bounds, not just its origin. A row whose stored bounds
     * differ -- the projector was swapped for one of another resolution, so only part of the
     * rectangle still lines up -- is a different output and must be left alone.
     */
    @Test
    fun `a row whose stored bounds only partly match is not swept`() {
        val staleSize = settingsWith {
            copy(
                screenAssignments = listOf(
                    display1(),
                    // Same origin and width as D2, but the height of the old projector.
                    display2().copy(targetBoundsH = 1080),
                ),
            )
        }
        projectionTab(initial = staleSize) { get ->
            gridButton(Grid.targetDisplay(row = 0)).performScrollTo().performClick()
            waitForIdle()
            onNodeWithText(pickDisplay2).performClick()
            waitForIdle()

            assertEquals(2, get().projectionSettings.screenAssignments[0].targetDisplay, "row 0 takes D2")
            assertEquals(
                2,
                get().projectionSettings.screenAssignments[1].targetDisplay,
                "the row with different bounds is a different output and must be left alone",
            )
        }
    }
}
