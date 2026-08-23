@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.companionserver.CompanionServer
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the assignment grid's dropdowns, the numeric fields below it, and the two buttons that
 * report outwards — asserting the [ProjectionSettings] each writes and the change it makes on screen.
 *
 * Every dropdown here displays its value straight from the settings passed in, so a display
 * assertion after a pick also proves the value round-tripped.
 */
class ProjectionSettingsTabGridTest {

    private fun settingsWith(change: ProjectionSettings.() -> ProjectionSettings): AppSettings =
        AppSettings().let { it.copy(projectionSettings = it.projectionSettings.change()) }

    // ── Target display ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the target display dropdown moves an output to another display`() = projectionTab { get ->
        gridButton(Grid.targetDisplay(row = 0)).assertTextEquals("D1 (1280x720)")

        // Row 1 already shows D2, so pick by the full label the menu uses instead.
        gridButton(Grid.targetDisplay(row = 0)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Display 2 (3840x2160 @ 3200,0)").performClick()
        waitForIdle()

        val assignment = get().projectionSettings.screenAssignments[0]
        assertEquals(2, assignment.targetDisplay, "the picked display index must be stored")
        assertEquals(3840, assignment.targetBoundsW, "along with that display's bounds")
        assertEquals(3200, assignment.targetBoundsX)
        gridButton(Grid.targetDisplay(row = 0)).assertTextEquals("D2 (3840x2160)")
    }

    @Test
    fun `the target display dropdown can detach an output`() {
        // Both key-output dropdowns read "None" out of the box, which would be indistinguishable
        // from the menu item; give the one row's key output a display so "None" is unique.
        val keyed = settingsWith {
            copy(
                screenAssignments = listOf(
                    ScreenAssignment(
                        targetDisplay = 1, targetBoundsX = 1920, targetBoundsW = 1280, targetBoundsH = 720,
                        keyTargetDisplay = 1, keyTargetBoundsX = 1920, keyTargetBoundsW = 1280, keyTargetBoundsH = 720,
                    ),
                ),
            )
        }
        projectionTab(initial = keyed, screens = oneExternalScreen()) { get ->
            chooseFromDropdown(Grid.targetDisplay(row = 0), "None")
            assertEquals(
                Constants.KEY_TARGET_NONE,
                get().projectionSettings.screenAssignments[0].targetDisplay,
                "picking None must store the none marker",
            )
            gridButton(Grid.targetDisplay(row = 0)).assertTextEquals("None")
        }
    }

    // ── Key output ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the key output dropdown assigns a fill and key pair`() = projectionTab { get ->
        assertEquals(
            Constants.KEY_TARGET_NONE,
            get().projectionSettings.screenAssignments[0].keyTargetDisplay,
            "no key output out of the box",
        )
        gridButton(Grid.keyOutput(row = 0)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Display 2 (3840x2160 @ 3200,0)").performClick()
        waitForIdle()

        val assignment = get().projectionSettings.screenAssignments[0]
        assertEquals(2, assignment.keyTargetDisplay, "the key target must be stored")
        assertEquals(3840, assignment.keyTargetBoundsW, "with its bounds")
        assertTrue(assignment.hasKeyOutput, "the row must now report a key output")
        assertEquals(
            Constants.OUTPUT_ROLE_FILL,
            assignment.primaryOutputRole,
            "and the primary window becomes the fill",
        )
        gridButton(Grid.keyOutput(row = 0)).assertTextEquals("D2 (3840x2160)")
    }

    @Test
    fun `the key output dropdown leaves the other row alone`() = projectionTab { get ->
        gridButton(Grid.keyOutput(row = 1)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Display 1 (1280x720 @ 1920,0)").performClick()
        waitForIdle()

        assertEquals(1, get().projectionSettings.screenAssignments[1].keyTargetDisplay)
        assertEquals(
            Constants.KEY_TARGET_NONE,
            get().projectionSettings.screenAssignments[0].keyTargetDisplay,
            "row 0's key output must be untouched",
        )
    }

    // ── Display mode ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the display mode dropdown stores each mode`() = projectionTab { get ->
        val modes = listOf(
            "Horizontal Lower Third" to Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
            "Vertical Lower Third" to Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL,
            "Stage Monitor" to Constants.DISPLAY_MODE_STAGE_MONITOR,
            "Full Screen" to Constants.DISPLAY_MODE_FULLSCREEN,
        )
        for ((label, stored) in modes) {
            gridButton(Grid.displayMode(row = 0)).performScrollTo().performClick()
            waitForIdle()
            onAllNodesWithText(label).onLast().performClick()
            waitForIdle()
            assertEquals(
                stored,
                get().projectionSettings.screenAssignments[0].displayMode,
                "picking $label must be stored",
            )
            gridButton(Grid.displayMode(row = 0)).assertTextEquals(label)
        }
    }

    @Test
    fun `a lower-third display mode is reported as such`() = projectionTab { get ->
        gridButton(Grid.displayMode(row = 0)).performScrollTo().performClick()
        waitForIdle()
        onAllNodesWithText("Vertical Lower Third").onLast().performClick()
        waitForIdle()

        val assignment = get().projectionSettings.screenAssignments[0]
        assertTrue(assignment.isLowerThird, "a vertical band still counts as a lower third")
        assertTrue(assignment.isLowerThirdVertical, "and specifically as the vertical one")
        assertEquals(
            Constants.DISPLAY_MODE_FULLSCREEN,
            get().projectionSettings.screenAssignments[1].displayMode,
            "the other row must be untouched",
        )
    }

    // ── Numeric fields ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the lower third height field stores a new percentage`() = projectionTab { get ->
        retypeNumberField(showing = 33, to = 45)
        assertEquals(45, get().projectionSettings.lowerThirdHeightPercent, "the height must be stored")
    }

    @Test
    fun `the lower third height field keeps the stored value when given one outside its range`() =
        projectionTab { get ->
            retypeNumberField(showing = 33, to = 90)
            assertEquals(
                33,
                get().projectionSettings.lowerThirdHeightPercent,
                "90 is outside the allowed range and must not be stored",
            )
        }

    @Test
    fun `each window position field stores its own offset`() {
        // The four offsets share a default, so give each one a value only it holds.
        val distinct = settingsWith { copy(windowTop = 41, windowLeft = 42, windowRight = 43, windowBottom = 44) }
        projectionTab(initial = distinct) { get ->
            retypeNumberField(showing = 41, to = 51)
            assertEquals(51, get().projectionSettings.windowTop, "the top offset must be stored")

            retypeNumberField(showing = 42, to = 52)
            assertEquals(52, get().projectionSettings.windowLeft, "the left offset must be stored")

            retypeNumberField(showing = 43, to = 53)
            assertEquals(53, get().projectionSettings.windowRight, "the right offset must be stored")

            retypeNumberField(showing = 44, to = 54)
            assertEquals(54, get().projectionSettings.windowBottom, "the bottom offset must be stored")

            assertEquals(51, get().projectionSettings.windowTop, "and none of them disturbed the others")
        }
    }

    /** The simulate stepper only exists in the dev fallback, and it adds assignment rows. */
    @Test
    fun `the simulate outputs stepper adds presenter windows`() {
        projectionTab(screens = noExternalScreens()) { get ->
            onNodeWithText("Presenter windows: 1").assertExists()
            retypeNumberField(showing = 1, to = 3)

            assertEquals(3, get().projectionSettings.devWindowCount, "the count must be stored")
            onNodeWithText("Presenter windows: 3").assertExists("and the tab must report three windows")
            gridButtons().assertCountEquals(1 + 3 * Grid.CONTROLS_PER_ROW + Grid.trailing)
            assertEquals(3, get().projectionSettings.screenAssignments.size, "with an assignment each")
        }
    }

    /**
     * `NumberSettingsTextField` copies what you type into its own state, so the field reading back
     * the new number right after typing would look right even if nothing were stored. This closes
     * that loop the only way that means anything: type in one composition, then render a fresh tab
     * from the settings that came out and assert the field shows the value there.
     */
    @Test
    fun `a typed number is what a fresh render of the saved settings shows`() {
        var saved = AppSettings()
        projectionTab { get ->
            retypeNumberField(showing = 33, to = 47)
            saved = get()
        }
        assertEquals(47, saved.projectionSettings.lowerThirdHeightPercent, "the value must have been stored")
        projectionTab(initial = saved) { _ ->
            // Re-rendered from settings alone: the field can only be showing what was stored.
            numberFields()[0].assertTextEquals("47")
        }
    }

    /** The mirror image: a rejected value is echoed on screen but never reaches the settings. */
    @Test
    fun `a rejected number never reaches a fresh render`() {
        var saved = AppSettings()
        projectionTab { get ->
            retypeNumberField(showing = 33, to = 90)
            saved = get()
        }
        assertEquals(33, saved.projectionSettings.lowerThirdHeightPercent, "90 is out of range")
        projectionTab(initial = saved) { _ ->
            numberFields()[0].assertTextEquals("33")
        }
    }

    // ── Buttons that report outwards ────────────────────────────────────────────────────────────

    @Test
    fun `the Identify button asks the app to flash the screens`() = runComposeUiTest {
        var identified = 0
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(AppSettings()) }
                ProjectionSettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state) },
                    companionServer = CompanionServer(),
                    onIdentifyScreen = { identified++ },
                    detectScreens = { twoExternalScreens() },
                )
            }
        }
        gridButton(Grid.IDENTIFY).performScrollTo().performClick()
        waitForIdle()
        assertEquals(1, identified, "clicking Identify must ask the app to flash the screens")
    }

    @Test
    fun `Add Output creates a browser source`() = projectionTab { get ->
        assertEquals(0, get().projectionSettings.browserSourceOutputs.size, "none out of the box")

        onNodeWithText("Add Output").performScrollTo().performClick()
        waitForIdle()

        assertEquals(1, get().projectionSettings.browserSourceOutputs.size, "the button must add one")
        onNodeWithText("Browser Source 1").assertExists("and the new output must appear in the table")
    }

    @Test
    fun `Add Output can create several browser sources`() = projectionTab { get ->
        // Addressed by label rather than ordinal: each added row shifts the buttons after it.
        repeat(3) {
            onNodeWithText("Add Output").performScrollTo().performClick()
            waitForIdle()
        }
        assertEquals(3, get().projectionSettings.browserSourceOutputs.size)
        for (n in 1..3) onNodeWithText("Browser Source $n").assertExists()
    }

    // ── Persistence ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the values the controls write survive a settings json round trip`() = projectionTab { get ->
        gridButton(Grid.targetDisplay(row = 0)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Display 2 (3840x2160 @ 3200,0)").performClick()
        waitForIdle()
        retypeNumberField(showing = 33, to = 40)

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val restored = json.decodeFromString<AppSettings>(json.encodeToString(get()))

        assertEquals(
            2,
            restored.projectionSettings.screenAssignments[0].targetDisplay,
            "the reassigned output must survive",
        )
        assertEquals(40, restored.projectionSettings.lowerThirdHeightPercent, "the height must survive")
    }
}
