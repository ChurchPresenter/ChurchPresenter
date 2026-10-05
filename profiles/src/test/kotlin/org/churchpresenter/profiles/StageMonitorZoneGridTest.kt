package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.MetronomePosition
import org.churchpresenter.settings.StageMonitorLayout
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.layoutSizes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class StageMonitorZoneGridTest {

    private class Calls {
        val selected = mutableListOf<StageMonitorStyleZone>()
        val widths = mutableListOf<Pair<StageMonitorStyleZone, Float>>()
        val heights = mutableListOf<Pair<StageMonitorStyleZone, Float>>()
    }

    private fun grid(block: ComposeUiTest.(Calls) -> Unit) = runComposeUiTest {
        val calls = Calls()
        val sm = StageMonitorSettings(layout = StageMonitorLayout.TOP_TWO_BELOW)
        setContent {
            Column(Modifier.width(600.dp)) {
                ZoneGrid(
                    layout = sm.layout,
                    sizes = sm.layoutSizes(),
                    screenAspect = 16f / 9f,
                    selected = StageMonitorStyleZone.A,
                    metronomePosition = MetronomePosition.NONE,
                    contentsOf = { "" },
                    onSelect = { calls.selected += it },
                    onWidthChange = { zone, percent -> calls.widths += zone to percent },
                    onHeightChange = { zone, percent -> calls.heights += zone to percent },
                )
            }
        }
        waitForIdle()
        block(calls)
    }

    @Test
    fun `clicking a zone selects it`() = grid { calls ->
        onAllNodes(hasText("Zone 2") and hasClickAction())[0].performClick()
        waitForIdle()
        assertEquals(listOf(StageMonitorStyleZone.B), calls.selected)
    }

    @Test
    fun `dragging a zone's edge resizes it, and dragging a row's bottom resizes the row`() = grid { calls ->
        onNodeWithTag(zoneSizeDividerTag(StageMonitorStyleZone.B)).performTouchInput {
            down(center)
            repeat(6) { moveBy(Offset(10f, 0f)) }
            up()
        }
        waitForIdle()
        assertTrue(calls.widths.isNotEmpty(), "no width")
        assertEquals(StageMonitorStyleZone.B, calls.widths.last().first)
        assertTrue(calls.widths.last().second > 50f, "w=${calls.widths}")
        onNodeWithTag(zoneSizeDividerTag(StageMonitorStyleZone.A, horizontal = false)).performTouchInput {
            down(center)
            repeat(6) { moveBy(Offset(0f, -8f)) }
            up()
        }
        waitForIdle()
        assertTrue(calls.heights.isNotEmpty(), "no height")
        assertEquals(StageMonitorStyleZone.A, calls.heights.last().first)
    }

    @Test
    fun `the selected zone's width and row height are typed in`() = runComposeUiTest {
        var width = 0f
        var height = 0f
        setContent {
            Column(Modifier.width(600.dp)) {
                ZoneSizeControls(
                    selectedLabel = "Zone 1",
                    widthPercent = 50f,
                    heightPercent = 67f,
                    onWidthChange = { width = it },
                    onHeightChange = { height = it },
                    onEvenRow = {},
                    onEvenAll = {},
                )
            }
        }
        retypeNumberField(50, 60)
        retypeNumberField(67, 40)
        assertEquals(60f, width)
        assertEquals(40f, height)
    }
}
