@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertCountEquals
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
 * Covers the rule that keeps two outputs from fighting over one piece of hardware: a display can be
 * driven by exactly one window, so pointing a row at a display that something else already uses
 * takes it away from that other thing first.
 *
 * This matters in the booth. Two windows aimed at the same projector means one silently wins and the
 * operator cannot tell which; the tab avoids that by clearing the loser as the choice is made,
 * across both the primary targets and the key outputs.
 *
 * It also covers what the tab shows when the stored settings name something it does not recognise —
 * a display mode, a language mode or an output type from a newer build, or hardware that is no
 * longer plugged in. Every one of those falls back to a sensible label rather than rendering blank.
 */
class ProjectionSettingsTabExclusivityTest {

    private fun settingsWith(change: ProjectionSettings.() -> ProjectionSettings): AppSettings =
        AppSettings().let { it.copy(projectionSettings = it.projectionSettings.change()) }

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
     * differ — the projector was swapped for one of another resolution, so only part of the rectangle
     * still lines up — is a different output and must be left alone.
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
                "row 1's bounds do not match D2's, so it is a different output and is left alone",
            )
        }
    }

    @Test
    fun `a key output whose stored bounds only partly match is not swept`() {
        val staleKey = settingsWith {
            copy(
                screenAssignments = listOf(
                    display1(),
                    display2().copy(
                        keyTargetDisplay = 2, keyTargetType = "screen",
                        keyTargetBoundsX = 3200, keyTargetBoundsY = 0,
                        keyTargetBoundsW = 3840, keyTargetBoundsH = 1080,
                    ),
                ),
            )
        }
        projectionTab(initial = staleKey) { get ->
            gridButton(Grid.targetDisplay(row = 0)).performScrollTo().performClick()
            waitForIdle()
            onNodeWithText(pickDisplay2).performClick()
            waitForIdle()

            assertEquals(
                2,
                get().projectionSettings.screenAssignments[1].keyTargetDisplay,
                "the key output's bounds do not match, so it is left alone",
            )
        }
    }

    // ── Values the build does not recognise ─────────────────────────────────────────────────────

    @Test
    fun `an unrecognised display mode falls back to Full Screen`() {
        projectionTab(
            initial = settingsWith {
                copy(screenAssignments = listOf(display1().copy(displayMode = "holographic"), display2()))
            },
        ) { get ->
            gridButton(Grid.displayMode(row = 0)).assertTextEquals("Full Screen")
            assertEquals(
                "holographic",
                get().projectionSettings.screenAssignments[0].displayMode,
                "the stored value itself is left alone, so a newer build still understands it",
            )
        }
    }

    @Test
    fun `an unrecognised browser source display mode falls back to Full Screen`() {
        projectionTab(
            initial = settingsWith {
                copy(browserSourceOutputs = listOf(ScreenAssignment(displayMode = "holographic")))
            },
        ) { _ ->
            onAllNodesWithText("Full Screen").assertCountAtLeast(1)
        }
    }

    @Test
    fun `an unrecognised song language mode falls back to the first option`() {
        projectionTab(
            initial = settingsWith {
                copy(
                    screenAssignments = listOf(
                        display1().copy(bibleMode = "quadlingual", songMode = "quadlingual"),
                        display2(),
                    ),
                )
            },
        ) { _ ->
            gridButton(Grid.contentOutputs(row = 0)).performScrollTo().performClick()
            waitForIdle()
            // Songs is the only dropdown left — Bible is a checkbox — and its mode is not one this
            // build knows, so it falls back to showing the first option.
            onAllNodesWithText("Off").assertCountAtLeast(1)
        }
    }

    @Test
    fun `an output stored against hardware that is gone falls back to None`() {
        // targetType "decklink" with no DeckLink device present: no option matches, so the tab
        // shows the first option rather than rendering the row blank.
        projectionTab(
            initial = settingsWith {
                copy(
                    screenAssignments = listOf(
                        ScreenAssignment(targetType = "decklink", targetDisplay = 0),
                        display2(),
                    ),
                )
            },
        ) { get ->
            gridButton(Grid.targetDisplay(row = 0)).assertTextEquals("None")
            assertEquals(
                "decklink",
                get().projectionSettings.screenAssignments[0].targetType,
                "the stored target is kept so the device works again when reconnected",
            )
        }
    }

    @Test
    fun `a key output stored against hardware that is gone falls back to None`() {
        projectionTab(
            initial = settingsWith {
                copy(
                    screenAssignments = listOf(
                        display1().copy(keyTargetType = "decklink", keyTargetDisplay = 0),
                        display2(),
                    ),
                )
            },
        ) { _ ->
            gridButton(Grid.keyOutput(row = 0)).assertTextEquals("None")
        }
    }

    // ── The dev-window fallback with several simulated outputs ──────────────────────────────────

    /**
     * Only the first simulated output is the "Dev Window"; the rest are numbered like real screens,
     * which is the branch a single simulated output never reaches.
     */
    @Test
    fun `only the first simulated output is labelled the dev window`() {
        projectionTab(
            initial = settingsWith { copy(devWindowCount = 3) },
            screens = noExternalScreens(),
        ) { _ ->
            onNodeWithText("Presenter windows: 3").assertExists()
            onNodeWithText("Dev Window").assertExists("slot 0 is the dev window")
            onNodeWithText("Screen 2").assertExists("and the rest are numbered")
            onNodeWithText("Screen 3").assertExists()
            onAllNodesWithText("Screen 1").assertCountEquals(0)
        }
    }
}

private fun SemanticsNodeInteractionCollection.assertCountAtLeast(n: Int) {
    val found = fetchSemanticsNodes(atLeastOneRootRequired = false).size
    kotlin.test.assertTrue(found >= n, "expected at least $n nodes but found $found")
}
