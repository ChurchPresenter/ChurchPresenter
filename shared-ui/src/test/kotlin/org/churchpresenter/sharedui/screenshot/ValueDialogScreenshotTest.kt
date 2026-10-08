@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.screenshot

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.sharedui.composables.ValueDialog
import org.churchpresenter.sharedui.composables.ValueDialogSpec
import org.churchpresenter.theme.semantic
import kotlin.test.Test

class ValueDialogScreenshotTest {

    @Composable
    private fun Interval() = ValueDialog(
        title = "Auto-scroll interval",
        hint = "How long each slide stays before advancing.",
        icon = rememberVectorPainter(Icons.Default.Timer),
        accent = MaterialTheme.semantic.success,
        initial = 5,
        spec = ValueDialogSpec(1, 30, 1, listOf(3, 5, 10, 15), "s"),
        onConfirm = {},
        onDismiss = {},
    )

    @Test
    fun `a value on a preset`() = captureComponent(SECTION, "value_dialog", rootIndex = 1, drive = { freezeCaret() }) {
        Interval()
    }

    @Test
    fun `a value out of range`() = captureComponent(
        SECTION,
        "value_dialog_out_of_range",
        rootIndex = 1,
        drive = {
            onNode(hasSetTextAction()).performTextReplacement("300")
            waitForIdle()
            freezeCaret()
        },
    ) { Interval() }

    /** The field is focused, so its caret blinks; a frozen clock pins which half of the blink is shot. */
    private fun ComposeUiTest.freezeCaret() {
        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()
    }

    private companion object {
        const val SECTION = "valueDialog"
    }
}
