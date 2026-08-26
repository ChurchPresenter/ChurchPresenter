@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.screenKey
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Renaming a monitor from the assignment grid.
 *
 * The two fixture displays are 1280x720 @ 1920,0 (row 0) and 3840x2160 @ 3200,0 (row 1); their keys
 * are what a typed name is stored against, so the assertions read the settings by key rather than
 * by row.
 */
class ProjectionSettingsTabScreenNameTest {

    private val firstScreen = screenKey(1920, 0, 1280, 720)
    private val secondScreen = screenKey(3200, 0, 3840, 2160)

    /** The name box of the row standing empty, found by the numbered label it offers. */
    private fun ComposeUiTest.nameBoxOffering(default: String) =
        onNode(hasSetTextAction() and hasText(default))

    @Test
    fun `the name box starts empty, offering the numbered label`() = projectionTab { get ->
        nameBoxOffering("Screen 1").assertExists()
        nameBoxOffering("Screen 2").assertExists()
        assertEquals(emptyMap(), get().projectionSettings.screenNames, "nothing is stored until typed")
    }

    @Test
    fun `typing a name stores it against the monitor that row drives`() = projectionTab { get ->
        nameBoxOffering("Screen 1").performTextReplacement("Foyer TV")
        waitForIdle()

        assertEquals("Foyer TV", get().projectionSettings.screenName(firstScreen))
        assertEquals("", get().projectionSettings.screenName(secondScreen), "the other is untouched")
    }

    @Test
    fun `a name keeps the spaces it was typed with`() = projectionTab { get ->
        nameBoxOffering("Screen 1").performTextReplacement("Foyer ")
        waitForIdle()

        assertEquals("Foyer ", get().projectionSettings.screenNames.getValue(firstScreen))
    }

    @Test
    fun `clearing the box gives the numbered label back`() = projectionTab { get ->
        nameBoxOffering("Screen 1").performTextReplacement("Foyer TV")
        waitForIdle()
        onNode(hasSetTextAction() and hasText("Foyer TV")).performTextReplacement("")
        waitForIdle()

        assertEquals(emptyMap(), get().projectionSettings.screenNames)
        nameBoxOffering("Screen 1").assertExists()
    }

    @Test
    fun `a renamed monitor is named in the target display menu`() {
        val named = AppSettings().let {
            it.copy(projectionSettings = it.projectionSettings.withScreenName(firstScreen, "Foyer TV"))
        }
        projectionTab(initial = named) { _ ->
            // The button shows the short form; the menu behind it the full one with the geometry,
            // which is what tells two same-named monitors apart.
            gridButton(Grid.targetDisplay(row = 0)).assertTextEquals("Foyer TV")
            gridButton(Grid.targetDisplay(row = 0)).performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Foyer TV (1280x720 @ 1920,0)").assertExists()
        }
    }

    @Test
    fun `an output driving no display is named on the slot instead`() {
        // There is no monitor to key the name to, and it is still a row the operator wants to
        // label. Offering the box only for rows driving hardware meant that on a single-monitor
        // machine — where the only row is the dev fallback — the feature was not there at all.
        val detached = AppSettings().let {
            it.copy(
                projectionSettings = it.projectionSettings.withAssignment(
                    0,
                    ScreenAssignment(targetDisplay = Constants.KEY_TARGET_NONE),
                ),
            )
        }
        projectionTab(initial = detached) { get ->
            nameBoxOffering("Screen 1").performTextReplacement("Overflow")
            waitForIdle()

            assertEquals("Overflow", get().projectionSettings.screenAssignments[0].screenName)
            assertEquals(emptyMap(), get().projectionSettings.screenNames, "no monitor was named")
        }
    }

    @Test
    fun `the dev window row can be renamed`() {
        // The single-monitor case: no external display, so the tab renders one simulated row.
        projectionTab(screens = noExternalScreens()) { get ->
            nameBoxOffering("Dev Window").performTextReplacement("Rehearsal")
            waitForIdle()

            assertEquals("Rehearsal", get().projectionSettings.screenAssignments[0].screenName)
        }
    }

    @Test
    fun `a slot name gives way to the monitor's own name`() {
        // A row named while detached, then pointed at a monitor that has a name of its own: the
        // more specific of the two wins, so the label follows the hardware.
        val both = AppSettings().let {
            it.copy(
                projectionSettings = it.projectionSettings
                    .withScreenName(firstScreen, "Foyer TV")
                    .withAssignment(
                        0,
                        it.projectionSettings.getAssignment(0).copy(
                            screenName = "Overflow",
                            targetDisplay = 1,
                            targetBoundsX = 1920, targetBoundsY = 0,
                            targetBoundsW = 1280, targetBoundsH = 720,
                        ),
                    ),
            )
        }
        projectionTab(initial = both) { get ->
            assertEquals(
                "Foyer TV",
                get().projectionSettings.screenLabelOr(
                    get().projectionSettings.getAssignment(0),
                    "Screen 1",
                ),
            )
        }
    }

    @Test
    fun `renaming survives the row moving to another slot`() {
        // The name is keyed by the monitor's geometry, so it belongs to the display rather than to
        // the position of the row driving it — which is the whole reason it is not stored per row.
        val named = AppSettings().let {
            it.copy(projectionSettings = it.projectionSettings.withScreenName(secondScreen, "Balcony"))
        }
        projectionTab(initial = named) { _ ->
            gridButton(Grid.targetDisplay(row = 1)).assertTextEquals("Balcony")
        }
    }
}
