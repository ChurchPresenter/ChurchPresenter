package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The band's own two layouts, given the stack-or-box split the grid searches already have.
 *
 * `BiblePresenter` draws a band through one of two hand-rolled layouts -- the side-by-side pair and
 * the single column. Both are here rather than inline because the decision about which elements
 * stay in the stack and which are drawn in a box of their own has to be made once and read by both
 * the fit search and the layout. Two copies of it in one 1,700-line composable is how they come apart.
 */

/**
 * One thing a band draws: whether it is [boxed] -- drawn in a text box by the box layer, and so
 * left out of the band here -- and how it draws itself. [draw]'s parameter is whether to fill the
 * width, which a stacked element always does.
 */
internal class BandElement(
    val boxed: Boolean,
    val draw: @Composable (Boolean) -> Unit,
)

/** One half of the side-by-side pair: [elements] stacked at the bottom of this half's cell, bar any boxed. */
@Composable
internal fun RowScope.BibleBandHalf(elements: List<BandElement>) {
    Column(Modifier.weight(1f).fillMaxHeight().wrapContentHeight(Alignment.Bottom)) {
        elements.forEach { if (!it.boxed) it.draw(true) }
    }
}

/** The band's single column: [elements] stacked in order, bar any boxed. */
@Composable
internal fun BibleBandColumn(elements: List<BandElement>, verticalArrangement: Arrangement.Vertical) {
    Column(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        verticalArrangement = verticalArrangement,
    ) {
        elements.forEach { if (!it.boxed) it.draw(true) }
    }
}
