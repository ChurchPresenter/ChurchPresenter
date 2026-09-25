@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.theme.AppThemeWrapper
import kotlin.test.Test

/**
 * The scale row keeping both its controls reachable in the width the editor actually gives it.
 *
 * This is a layout test, not a behaviour one, and it exists because the bug it pins was invisible to
 * every other suite. `ProfilesCustomizeTestSupport` opens the dialog at 1400dp -- wide enough that
 * everything fits -- and its own doc comment explains why that matters: these controls are
 * fixed-size cells that a row too narrow **clips rather than shrinks**, and a clipped node keeps its
 * semantics, so `assertExists` still matches it. Only `assertIsDisplayed` sees the difference.
 *
 * So the row is driven here at a width it cannot fit on one line. Measured rather than assumed: the
 * pair needs a little over 400dp, and at the 360dp below a plain `Row` drops the second control --
 * Media -- off the right edge, which is what was reported. `FlowRow` wraps it onto a second line
 * instead. Both assertions fail against the `Row` and pass against the `FlowRow`, which is the only
 * thing that makes this test worth its runtime.
 */
class ProfileScaleRowWrapTest {

    /**
     * The **window** is the narrow thing, not a box inside it.
     *
     * That distinction is the whole test. A `Box` with a width does not clip its children, so an
     * overflowing `Row` inside one still draws -- outside the box, but inside the window, where
     * `assertIsDisplayed` is perfectly happy with it. Constraining the window instead puts the
     * overflow off-screen, which is what the operator was seeing and what the assertion can catch.
     */
    private fun scaleRow(block: SkikoComposeUiTest.() -> Unit) {
        runSkikoComposeUiTest(size = Size(NARROW_WIDTH, 400f), density = Density(1f)) {
            setContent {
                AppThemeWrapper {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ProfileScaleRow(
                            profile = OutputProfile(showPictures = true, showMedia = true),
                            onProfileChange = {},
                        )
                    }
                }
            }
            block()
        }
    }

    private companion object {
        /** Narrower than the pair needs on one line; a plausible width for the editor's column. */
        const val NARROW_WIDTH = 360f
    }

    @Test
    fun `both scale controls are visible when the profile shows pictures and video`() = scaleRow {
        onNodeWithText("Pictures").assertIsDisplayed()
        onNodeWithText("Media").assertIsDisplayed()
    }

    @Test
    fun `both sets of segments are reachable, not clipped off the edge`() = scaleRow {
        // One "Fit" segment per control, so two of them displayed is the proof that the second
        // control is really on screen rather than merely in the tree. `assertCountEquals` alone
        // would not be: a clipped node keeps its semantics and still counts.
        val fits = onAllNodesWithText("Fit")
        fits.assertCountEquals(2)
        fits[0].assertIsDisplayed()
        fits[1].assertIsDisplayed()
    }
}
