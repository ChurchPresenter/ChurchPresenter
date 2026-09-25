package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.churchpresenter.settings.ElementOffset

/**
 * The band's own two layouts, given the stack-or-float split the grid searches already had.
 *
 * `BiblePresenter` draws a band through one of two hand-rolled layouts -- the side-by-side pair and
 * the single column -- and both simply stacked their elements in a `Column`. Positioning was
 * therefore full-screen only: an offset here would have moved an element the fit search had measured
 * into the stack, solving for a box nothing was drawn in.
 *
 * Both are here rather than inline for the reason the split exists at all: the decision about which
 * elements are stacked and which float has to be made once and read by both the search and the
 * layout. Two copies of it in one 1,700-line composable is how they come apart.
 */

/**
 * One thing a band draws: where it sits once positioned, and how it draws itself.
 *
 * [draw]'s parameter is whether to fill the width. A positioned element is drawn with `false`:
 * filling the width leaves no room for the offset to move it through, so X would silently do
 * nothing. Both paths go through the same lambda so a stacked and a positioned element cannot drift
 * into drawing different things.
 */
internal class BandElement(
    val offset: ElementOffset?,
    val draw: @Composable (Boolean) -> Unit,
)

/**
 * One half of the side-by-side pair: [elements] stacked at the bottom of this half's cell, except
 * any the operator has positioned, which float in the cell instead.
 *
 * The unpositioned path is the exact `Column` this replaced, down to the `wrapContentHeight` that
 * bottom-aligns a short half -- so a band with nothing positioned is laid out as it always was and
 * no wrapper `Box` is added at all.
 */
@Composable
internal fun RowScope.BibleBandHalf(elements: List<BandElement>) {
    if (elements.none { it.offset != null }) {
        Column(Modifier.weight(1f).fillMaxHeight().wrapContentHeight(Alignment.Bottom)) {
            elements.forEach { it.draw(true) }
        }
        return
    }
    Box(Modifier.weight(1f).fillMaxHeight()) {
        Column(Modifier.fillMaxWidth().wrapContentHeight().align(Alignment.BottomCenter)) {
            elements.forEach { if (it.offset == null) it.draw(true) }
        }
        elements.forEach { element ->
            element.offset?.let { Box(Modifier.elementOffset(it)) { element.draw(false) } }
        }
    }
}

/**
 * The band's single column: [elements] stacked in order, except any positioned, which float in the
 * band.
 *
 * [stackAlignment] is where the stack sits once a `Box` is wrapped around it — the alignment the
 * caller's own container was placing it with, passed in rather than assumed, so the floated case
 * puts the remaining stack exactly where the unfloated one does.
 */
@Composable
internal fun BibleBandColumn(
    elements: List<BandElement>,
    verticalArrangement: Arrangement.Vertical,
    stackAlignment: Alignment,
) {
    if (elements.none { it.offset != null }) {
        Column(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            verticalArrangement = verticalArrangement,
        ) {
            elements.forEach { it.draw(true) }
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().wrapContentHeight().align(stackAlignment),
            verticalArrangement = verticalArrangement,
        ) {
            elements.forEach { if (it.offset == null) it.draw(true) }
        }
        elements.forEach { element ->
            element.offset?.let { Box(Modifier.elementOffset(it)) { element.draw(false) } }
        }
    }
}
