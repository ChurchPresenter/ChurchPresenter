@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.StageMonitorLayout
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.StageMonitorZoneSizes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.settings.withZoneWidth
import org.churchpresenter.settings.layoutSizes
import org.churchpresenter.settings.zoneHeightPercent
import org.churchpresenter.settings.zoneWidthPercent

/**
 * The Zone Size card: the diagram, the two fields under it and the shortcuts beside them.
 *
 * Classic's own proportions are the fixture — 50/50 over 36/28/36, rows at 67/33 — so every number
 * here is one the card actually shows out of the box.
 */
class StageMonitorSettingsTabZoneSizeTest {

    private fun AppSettings.sizes() = stageMonitorSettings
    private fun AppSettings.widthOf(zone: StageMonitorStyleZone) = sizes().zoneWidthPercent(zone)
    private fun AppSettings.heightOf(zone: StageMonitorStyleZone) = sizes().zoneHeightPercent(zone)

    /** A cell of the grid, which reads as its zone name over its contents over its size. */
    private fun ComposeUiTest.diagramCell(name: String, contents: String, meta: String) =
        onNode(hasTextExactly(name, contents, meta))

    // ── Selection ───────────────────────────────────────────────────────────────

    @Test
    fun `the card opens on the first zone with its own numbers`() = stageMonitorTab { _ ->
        onNode(hasTextExactly("SELECTED", ZoneLabel.ZONE_1)).assertExists()
        assertNumberFieldShows(50, "Zone 1's width")
        assertNumberFieldShows(67, "the height of Zone 1's row")
    }

    @Test
    fun `clicking a zone in the diagram points the fields at it`() = stageMonitorTab { _ ->
        diagramCell(ZoneLabel.ZONE_4, "Clock", "28% × 33%").performClick()

        onNode(hasTextExactly("SELECTED", ZoneLabel.ZONE_4)).assertExists()
        assertNumberFieldShows(28, "Zone 4's width")
        assertNumberFieldShows(33, "the height of Zone 4's row")
    }

    // ── Typing a size ───────────────────────────────────────────────────────────

    @Test
    fun `typing a width stores it and takes the difference from the neighbour`() = stageMonitorTab { get ->
        retypeNumberField(showing = 50, to = 70)

        assertEquals(70f, get().widthOf(StageMonitorStyleZone.A), "the typed width must be stored")
        assertEquals(30f, get().widthOf(StageMonitorStyleZone.B), "its neighbour gives up the difference")
        assertEquals(
            listOf(36f, 28f, 36f),
            get().sizes().layoutSizes().rowCellWidths[1],
            "the other row must not move",
        )
    }

    @Test
    fun `typing a row height stores it for every zone on the row`() = stageMonitorTab { get ->
        retypeNumberField(showing = 67, to = 80)

        assertEquals(80f, get().heightOf(StageMonitorStyleZone.A))
        assertEquals(80f, get().heightOf(StageMonitorStyleZone.B), "a row has one height")
        assertEquals(20f, get().heightOf(StageMonitorStyleZone.C), "the row below gives it up")
    }

    @Test
    fun `the width typed for a zone is the width of the zone that is selected`() = stageMonitorTab { get ->
        diagramCell(ZoneLabel.ZONE_3, "Announcements", "36% × 33%").performClick()
        retypeNumberField(showing = 36, to = 50)

        assertEquals(50f, get().widthOf(StageMonitorStyleZone.C))
        assertEquals(14f, get().widthOf(StageMonitorStyleZone.D))
        assertEquals(50f, get().widthOf(StageMonitorStyleZone.A), "the unselected row keeps its widths")
    }

    /** The diagram is the readout as well as the control, so it must show what was just typed. */
    @Test
    fun `the diagram redraws with the size that was typed`() = stageMonitorTab { _ ->
        retypeNumberField(showing = 50, to = 70)

        diagramCell(ZoneLabel.ZONE_1, "Bible, Songs", "70% × 67%").assertExists()
        diagramCell(ZoneLabel.ZONE_2, "Next", "30% × 67%").assertExists()
    }

    // ── Dragging a divider ──────────────────────────────────────────────────────

    @Test
    fun `dragging a divider to the right widens the zone on its left`() = stageMonitorTab { get ->
        onNodeWithTag(zoneSizeDividerTag(StageMonitorStyleZone.A)).performTouchInput {
            down(center)
            moveBy(Offset(60f, 0f))
            moveBy(Offset(60f, 0f))
            up()
        }

        val widened = get().widthOf(StageMonitorStyleZone.A)
        assertTrue(widened > 50f, "dragging right must widen Zone 1, got $widened")
        assertEquals(100f - widened, get().widthOf(StageMonitorStyleZone.B), "the pair still fills the row")
    }

    @Test
    fun `dragging a row divider down makes the row above it taller`() = stageMonitorTab { get ->
        onNodeWithTag(zoneSizeDividerTag(StageMonitorStyleZone.A, horizontal = false)).performTouchInput {
            down(center)
            moveBy(Offset(0f, 40f))
            moveBy(Offset(0f, 40f))
            up()
        }

        val taller = get().heightOf(StageMonitorStyleZone.A)
        assertTrue(taller > 67f, "dragging down must heighten the top row, got $taller")
        assertEquals(100f - taller, get().heightOf(StageMonitorStyleZone.C), "the rows still fill the screen")
    }

    // ── Evening out, and going back ─────────────────────────────────────────────

    @Test
    fun `even out this row splits the selected zone's row and leaves the other alone`() =
        stageMonitorTab { get ->
            diagramCell(ZoneLabel.ZONE_3, "Announcements", "36% × 33%").performClick()
            onNodeWithText("This row").performClick()

            assertEquals(listOf(33f, 33f, 34f), get().sizes().layoutSizes().rowCellWidths[1])
            assertEquals(listOf(50f, 50f), get().sizes().layoutSizes().rowCellWidths[0])
            assertEquals(listOf(67f, 33f), get().sizes().layoutSizes().rowHeights, "heights are not widths")
        }

    @Test
    fun `even out everything squares up the rows too`() = stageMonitorTab { get ->
        onNodeWithText("Everything").performClick()

        assertEquals(listOf(50f, 50f), get().sizes().layoutSizes().rowHeights)
        assertEquals(listOf(33f, 33f, 34f), get().sizes().layoutSizes().rowCellWidths[1])
    }

    @Test
    fun `reset is offered only once something has been resized`() = stageMonitorTab { get ->
        onNodeWithText("Reset").assertIsNotEnabled()

        retypeNumberField(showing = 50, to = 70)
        onNodeWithText("Reset").assertIsEnabled()
        onNodeWithText("Reset").performClick()

        assertEquals(
            StageMonitorZoneSizes.of(StageMonitorLayout.CLASSIC),
            get().sizes().layoutSizes(),
            "reset must put the layout back to its catalog proportions",
        )
        onNodeWithText("Reset").assertIsNotEnabled()
    }

    // ── One layout at a time ────────────────────────────────────────────────────

    @Test
    fun `a smaller layout brings its own sizes and keeps the resized one`() {
        val resized = stageSettings { withZoneWidth(StageMonitorStyleZone.A, 70f) }
        stageMonitorTab(initial = resized) { get ->
            chooseLayout(zoneCount = "2 zones", name = "Left / Right")
            assertEquals(listOf(50f, 50f), get().sizes().layoutSizes().rowCellWidths[0])

            chooseLayout(zoneCount = "5 zones", name = ZoneLabel.CLASSIC)
            assertEquals(70f, get().widthOf(StageMonitorStyleZone.A), "Classic keeps what it was given")
        }
    }

    @Test
    fun `the size fields follow a layout that no longer draws the selected zone`() {
        stageMonitorTab(initial = AppSettings()) { get ->
            diagramCell(ZoneLabel.ZONE_5, "—", "36% × 33%").performClick()
            chooseLayout(zoneCount = "2 zones", name = "Left / Right")

            onNode(hasTextExactly("SELECTED", ZoneLabel.ZONE_1)).assertExists()
            assertEquals(StageMonitorLayout.LEFT_RIGHT, get().sizes().layout)
        }
    }
}

/** Picks a layout card in the catalog above the size card, through its zone-count tab. */
private fun ComposeUiTest.chooseLayout(zoneCount: String, name: String) {
    onNodeWithText(zoneCount).performClick()
    waitForIdle()
    onNodeWithText(name).performClick()
    waitForIdle()
}
