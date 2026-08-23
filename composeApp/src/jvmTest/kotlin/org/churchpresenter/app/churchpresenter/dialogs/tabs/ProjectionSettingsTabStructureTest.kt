@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import org.churchpresenter.ui.numberFields

/**
 * Pins the shape of the tab in both of the layouts it can render, and validates the ordinals the
 * behaviour tests use to address the assignment grid.
 *
 * The grid repeats the same four controls per output and publishes no tags, so those tests reach
 * them by position. That only stays honest while the layout holds, which is what this class asserts:
 * a control added or reordered fails here first and says so.
 */
class ProjectionSettingsTabStructureTest {

    // ── Section headings ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the tab renders every section`() = projectionTab { _ ->
        for (section in listOf("Screen Assignment", "Browser Source Outputs", "Audio Output", "Window Position")) {
            onAllNodesWithText(section).assertCountEquals(1)
        }
    }

    @Test
    fun `the assignment grid heads its four columns`() = projectionTab { _ ->
        for (heading in listOf("Display", "Key Output", "Display Mode", "Content Outputs")) {
            onAllNodesWithText(heading).assertCountEquals(1)
        }
    }

    // ── One row per output, driven by the detected screens ──────────────────────────────────────

    @Test
    fun `two external displays produce one assignment row each`() = projectionTab { _ ->
        onNodeWithText("Detected screens: 3").assertExists("the primary display counts as detected")
        onNodeWithText("Presenter windows: 2").assertExists("but only the two externals can present")
        onNodeWithText("Screen 1").assertExists()
        onNodeWithText("Screen 2").assertExists()
        onAllNodesWithText("Dev Window").assertCountEquals(0)
    }

    @Test
    fun `each row's target dropdown names its display and resolution`() = projectionTab { _ ->
        gridButton(Grid.targetDisplay(row = 0)).assertTextEquals("D1 (1280x720)")
        gridButton(Grid.targetDisplay(row = 1)).assertTextEquals("D2 (3840x2160)")
    }

    @Test
    fun `a single external display produces a single row`() {
        projectionTab(screens = oneExternalScreen()) { _ ->
            onNodeWithText("Detected screens: 2").assertExists()
            onNodeWithText("Presenter windows: 1").assertExists()
            onNodeWithText("Screen 1").assertExists()
            onAllNodesWithText("Screen 2").assertCountEquals(0)
            gridButton(Grid.targetDisplay(row = 0)).assertTextEquals("D1 (1280x720)")
        }
    }

    /**
     * On a single-monitor machine there is nothing to present on, so the tab falls back to a
     * simulated output — the row is labelled "Dev Window" and a stepper appears to simulate more.
     */
    @Test
    fun `no external display falls back to a simulated dev window`() {
        projectionTab(screens = noExternalScreens()) { _ ->
            onNodeWithText("Detected screens: 1").assertExists()
            onNodeWithText("Presenter windows: 1").assertExists()
            onNodeWithText("Dev Window").assertExists("the fallback row must be labelled as such")
            onAllNodesWithText("Screen 1").assertCountEquals(0)
            onNodeWithText("SIMULATE OUTPUTS").assertExists("and the simulate stepper must appear")
        }
    }

    @Test
    fun `the simulate stepper is offered only in the dev fallback`() = projectionTab { _ ->
        onAllNodesWithText("SIMULATE OUTPUTS").assertCountEquals(0)
    }

    // ── The grid ordinals the behaviour tests rely on ───────────────────────────────────────────

    @Test
    fun `the grid lays out four controls per row between Identify and Add Output`() = projectionTab { _ ->
        // Identify, then 4 per row for 2 rows, then Add Output, the audio device and Browse.
        gridButtons().assertCountEquals(1 + 2 * Grid.CONTROLS_PER_ROW + Grid.trailing)
        gridButton(Grid.IDENTIFY).assertTextEquals("Identify")
        gridButton(Grid.addOutput(rows = 2)).assertTextEquals("Add Output")
    }

    @Test
    fun `each row's four controls are the ones the ordinals name`() = projectionTab { _ ->
        for (row in 0..1) {
            gridButton(Grid.targetDisplay(row)).assertTextEquals(
                "D${row + 1} ${if (row == 0) "(1280x720)" else "(3840x2160)"}",
            )
            gridButton(Grid.keyOutput(row)).assertTextEquals("None")
            gridButton(Grid.displayMode(row)).assertTextEquals("Full Screen")
            gridButton(Grid.contentOutputs(row)).assertTextEquals("16 of 17 enabled")
        }
    }

    @Test
    fun `the dev fallback row carries the same four controls`() {
        projectionTab(screens = noExternalScreens()) { _ ->
            gridButtons().assertCountEquals(1 + 1 * Grid.CONTROLS_PER_ROW + Grid.trailing)
            gridButton(Grid.targetDisplay(row = 0)).assertTextEquals("None")
            gridButton(Grid.keyOutput(row = 0)).assertTextEquals("None")
            gridButton(Grid.displayMode(row = 0)).assertTextEquals("Full Screen")
            gridButton(Grid.contentOutputs(row = 0)).assertTextEquals("16 of 17 enabled")
        }
    }

    // ── The rest of the tab ─────────────────────────────────────────────────────────────────────

    /**
     * `scenes` feeds the DeckLink input-conflict check and defaults to empty. Passing it explicitly
     * exercises the supplied-argument path; every other test leaves it to the default.
     */
    @Test
    fun `the tab accepts an explicit scene list`() = androidx.compose.ui.test.runComposeUiTest {
        setContent {
            androidx.compose.material3.MaterialTheme {
                val state = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(AppSettings()) }
                ProjectionSettingsTab(
                    settings = state.value,
                    onSettingsChange = { transform -> state.value = transform(state.value) },
                    companionServer = org.churchpresenter.companionserver.CompanionServer(),
                    onIdentifyScreen = {},
                    onIdentifyBrowserSource = {},
                    scenes = emptyList(),
                    detectScreens = { twoExternalScreens() },
                )
            }
        }
        onNodeWithText("Screen Assignment").assertExists("the tab must render on an explicit scene list")
        gridButton(Grid.targetDisplay(row = 0)).assertTextEquals("D1 (1280x720)")
    }

    @Test
    fun `the lower third height field is offered once`() = projectionTab { _ ->
        onNodeWithText("Lower Third Height % (for Bible and Songs)").assertExists()
    }

    @Test
    fun `the browser source card explains itself and offers to add an output`() = projectionTab { _ ->
        onNodeWithText(
            "Virtual outputs served as a web page — paste the URL into a Browser Source to overlay " +
                "live content, transparent background supported.",
        ).assertExists("the card must explain what a browser source is for")
        gridButton(Grid.addOutput(rows = 2)).assertTextEquals("Add Output")
    }

    @Test
    fun `the audio card offers a device and a VLC path`() = projectionTab { _ ->
        // The device row is VLC's — the tab drops it and says so where VLC is absent. The path row
        // below it is composed either way, which is what makes VLC installable from here at all.
        if (isVlcAvailable) {
            onNodeWithText("Output device").assertExists()
            onNodeWithText("System Default").assertExists("the device dropdown starts on the system default")
        } else {
            onNodeWithText("Output device").assertDoesNotExist()
        }
        onNodeWithText("Custom VLC path").assertExists()
        onNodeWithText("Browse").assertExists("with a chooser button beside it")
    }

    @Test
    fun `the window position card offers all four edges`() = projectionTab { _ ->
        for (edge in listOf("TOP", "LEFT", "RIGHT", "BOTTOM")) {
            onNodeWithText(edge).assertExists("the window-position card must offer a $edge offset")
        }
        onNodeWithText("Screen").assertExists("with the screen diagram between them")
        onNodeWithText(
            "Position values represent pixel offsets from screen edges. Use these to adjust " +
                "projection window placement on secondary displays.",
        ).assertExists()
    }

    @Test
    fun `the tab offers one stepper field per numeric setting`() = projectionTab { _ ->
        // Lower-third height plus the four window-position offsets.
        numberFields().assertCountEquals(5)
    }

    @Test
    fun `the dev fallback adds the simulate stepper to the numeric fields`() {
        projectionTab(screens = noExternalScreens()) { _ ->
            numberFields().assertCountEquals(6)
        }
    }

    @Test
    fun `the stored assignments survive being rendered`() {
        // The tab fills in a row per detected output on first composition; nothing else should move.
        projectionTab { get ->
            val assignments = get().projectionSettings.screenAssignments
            kotlin.test.assertEquals(2, assignments.size, "one assignment per presenter window")
            kotlin.test.assertEquals(1, assignments[0].targetDisplay, "row 0 resolves to the first external display")
            kotlin.test.assertEquals(2, assignments[1].targetDisplay, "row 1 resolves to the second")
            kotlin.test.assertEquals(1280, assignments[0].targetBoundsW, "with that display's bounds")
            kotlin.test.assertEquals(3840, assignments[1].targetBoundsW)
            // And what was resolved is what each row shows.
            gridButton(Grid.targetDisplay(row = 0)).assertTextEquals("D1 (1280x720)")
            gridButton(Grid.targetDisplay(row = 1)).assertTextEquals("D2 (3840x2160)")
        }
    }

    @Test
    fun `an unrecognised stored assignment is left alone`() {
        // -2 means "none"; the resolver must not overwrite a deliberate choice with a display.
        val none = AppSettings().let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    screenAssignments = listOf(
                        ScreenAssignment(targetDisplay = -2),
                        ScreenAssignment(targetDisplay = -2),
                    ),
                ),
            )
        }
        projectionTab(initial = none) { get ->
            kotlin.test.assertEquals(
                -2,
                get().projectionSettings.screenAssignments[0].targetDisplay,
                "a deliberate None must not be resolved to a display",
            )
            gridButton(Grid.targetDisplay(row = 0)).assertTextEquals("None")
        }
    }
}
