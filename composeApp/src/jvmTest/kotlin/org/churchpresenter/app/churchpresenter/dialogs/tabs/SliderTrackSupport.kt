@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performMouseInput

/**
 * Taps a `SlimSlider` that has a caption above it and a readout beside it.
 *
 * The slider draws its track on a bare `Canvas` and publishes no semantics, so the caption and the
 * readout are the only nodes in the tree and the track is the strip between them. The click goes to
 * the root, because the point it needs is inside neither node; with a dialog open there are two
 * roots at the same origin and the dialog is the last one.
 */
internal fun ComposeUiTest.tapSliderTrack(caption: String, readout: String, fraction: Float) {
    val captionBounds = onAllNodesWithText(caption).fetchSemanticsNodes(atLeastOneRootRequired = false)
        .firstOrNull()?.boundsInRoot ?: error("no slider captioned \"$caption\" is on screen")
    val readoutBounds = onAllNodesWithText(readout).fetchSemanticsNodes(atLeastOneRootRequired = false)
        .firstOrNull()?.boundsInRoot ?: error("no slider readout reading \"$readout\" is on screen")

    // The track starts where the caption starts and ends a gap short of the readout beside it;
    // `SlimSlider` lays the two out in one `Row` spaced by that gap, vertically centred, so the
    // readout's own middle is on the track's line.
    val trackLeft = captionBounds.left
    val trackRight = readoutBounds.left - SLIDER_ROW_GAP
    // Held one pixel inside the right edge: hit testing is exclusive there, so a tap at the track's
    // own right edge lands on nothing and the slider never hears it. `fraction = 1f` therefore means
    // "as far along as the track can be tapped", not exactly the range's end.
    val x = (trackLeft + (trackRight - trackLeft) * fraction).coerceAtMost(trackRight - 1f)

    val roots = onAllNodes(isRoot())
    val last = roots.fetchSemanticsNodes(atLeastOneRootRequired = false).size - 1
    roots[last].performMouseInput { click(Offset(x, readoutBounds.center.y)) }
    waitForIdle()
}

/** `SlimSlider`'s own `Arrangement.spacedBy(10.dp)`, at the tests' density of 1. */
private const val SLIDER_ROW_GAP = 10f
