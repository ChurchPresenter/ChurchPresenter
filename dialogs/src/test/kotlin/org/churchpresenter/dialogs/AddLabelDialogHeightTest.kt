@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The control for [DialogViewportTest]: proof that its assertions can still fail.
 *
 * `AddLabelDialog` was shipped at 400dp, and its height comment records what that did — the nested
 * colour picker was cut off "with no visible scrollbar to hint more was there". It was found by hand
 * and fixed by hand-measuring up to 640dp. That makes it the one case in this repo with a known true
 * answer on both sides, so it is used here to hold the measurement honest in both directions: the
 * content must genuinely not fit in 400dp, and must genuinely fit in 640dp.
 *
 * This exists because the first two versions of this check were wrong in ways that still looked
 * green. Composing into a box of the declared size and hunting for nodes outside it cannot work —
 * `Modifier.size` caps the content, so a squeezed layout reports every node inside the box.
 * Measuring under unbounded height cannot work either — the editor's content column is
 * `Modifier.weight(1f)`, and a weighted child collapses when the height it is dividing is infinite,
 * which reported 210dp for content that really needs 474dp. Only a minimum-intrinsic measurement
 * handles both. Without a case that must fail, either mistake looked exactly like a suite of nine
 * passing tests. Any future rework of `ViewportAssertions` has to keep this red.
 *
 * **The colour picker overlay is deliberately not measured here, and does not need to be.** It was
 * the original reason for the 640dp, so it was the obvious thing to drive open and measure — but
 * `ColorPickerDialog` is a Compose `Dialog`, which composes into its own layer rather than into this
 * dialog's layout, so opening it changes this measurement by exactly nothing (verified: 474dp with
 * it open and closed alike). It is also no longer the hazard the comment describes: that component
 * has since grown a scrollable middle section, documented in its own source as sized to "whatever
 * height is actually left over after the fixed title/button rows, scrolling if that's not enough".
 * A dialog that scrolls cannot be cut off, which is the whole criterion this suite selects on.
 */
class AddLabelDialogHeightTest {

    private fun labelContentHeightDp(): Float {
        var measured = 0f
        runComposeUiTest {
            val probe = ViewportProbe()
            setContent {
                MaterialTheme {
                    Viewport(500.dp, probe) {
                        AddLabelDialogContent(onDismiss = {}, onConfirm = { _, _, _ -> })
                    }
                }
            }
            measured = measuredContentHeight(probe).value
        }
        return measured
    }

    @Test
    fun `the label editor does not fit the 400dp height it originally shipped at`() {
        val needed = labelContentHeightDp()
        assertTrue(
            needed > 400f,
            "the 400dp regression must still be detectable, but the content measured ${needed}dp — " +
                "either the dialog got much shorter, or the measurement has stopped measuring anything",
        )
    }

    @Test
    fun `the label editor fits the 640dp it was raised to`() {
        val needed = labelContentHeightDp()
        assertTrue(
            needed <= 640f,
            "the height was raised to 640dp specifically to fit this content, but it measured ${needed}dp",
        )
    }
}
