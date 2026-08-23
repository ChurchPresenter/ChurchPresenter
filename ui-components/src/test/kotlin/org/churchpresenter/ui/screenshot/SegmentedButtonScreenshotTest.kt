@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.ui.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.ui.SegmentedButton
import org.churchpresenter.ui.SegmentedButtonItem
import kotlin.test.Test

/**
 * The joined row of mutually exclusive buttons — the Announcements timer's four modes, the Bible's
 * layout picker, the Q&A sort order.
 *
 * Which one is selected is the state worth seeing, and so is the wrap: given `compactColumns` and
 * less width than a full row needs, the same items fold into a grid with different corner rounding.
 */
class SegmentedButtonScreenshotTest {

    @Test
    fun `two items, the first chosen`() = captureComponent(SECTION, "two_items") {
        SegmentedButton(
            items = listOf(
                SegmentedButtonItem("stacked", "Stacked"),
                SegmentedButtonItem("side", "Side by Side"),
            ),
            selectedValue = "stacked",
            onValueChange = {},
            buttonWidth = 88.dp,
            buttonHeight = 28.dp,
            fontSize = 10.sp,
        )
    }

    @Test
    fun `four items in one row`() = captureComponent(SECTION, "four_items") {
        SegmentedButton(
            items = MODES,
            selectedValue = "duration",
            onValueChange = {},
            buttonWidth = 76.dp,
            buttonHeight = 28.dp,
            fontSize = 9.sp,
        )
    }

    @Test
    fun `a later item chosen`() = captureComponent(SECTION, "four_items_last_chosen") {
        SegmentedButton(
            items = MODES,
            selectedValue = "clock_display",
            onValueChange = {},
            buttonWidth = 76.dp,
            buttonHeight = 28.dp,
            fontSize = 9.sp,
        )
    }

    /**
     * Too narrow for four across, so it folds to two columns of two.
     *
     * The other half of `compactColumns` — given room for the full row it stays one row — renders
     * byte-identically to `four_items` and so is not shot separately.
     */
    @Test
    fun `a compact row folded into a grid`() = captureComponent(SECTION, "compact_folded") {
        Box(Modifier.width(180.dp)) {
            SegmentedButton(
                items = MODES,
                selectedValue = "clock",
                onValueChange = {},
                buttonWidth = 76.dp,
                buttonHeight = 28.dp,
                fontSize = 9.sp,
                compactColumns = 2,
            )
        }
    }

    @Test
    fun `items drawn as icons`() = captureComponent(SECTION, "icon_items") {
        SegmentedButton(
            items = listOf(
                SegmentedButtonItem("timer", "Timer", icon = Icons.Filled.Timer),
                SegmentedButtonItem("cast", "Cast", icon = Icons.Filled.Cast),
            ),
            selectedValue = "timer",
            onValueChange = {},
            buttonWidth = 40.dp,
            buttonHeight = 28.dp,
        )
    }

    private companion object {
        const val SECTION = "segmentedButton"

        val MODES = listOf(
            SegmentedButtonItem("duration", "Timer"),
            SegmentedButtonItem("count_up", "Duration"),
            SegmentedButtonItem("clock", "Specific Time"),
            SegmentedButtonItem("clock_display", "Clock"),
        )
    }
}
