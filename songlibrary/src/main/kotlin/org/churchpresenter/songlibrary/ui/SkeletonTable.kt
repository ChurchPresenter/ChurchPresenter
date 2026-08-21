package org.churchpresenter.songlibrary.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.songlibrary.SongLibraryState

/**
 * What the table shows while the folder is still being read.
 *
 * The rows the real table will have, in the columns it will have them in, with a highlight sweeping
 * across — so the window arrives already the right shape and the grid fills in, rather than the
 * layout jumping when the load lands. A spinner in the middle of an empty table says only that
 * something is happening; this says what is coming.
 */
@Composable
internal fun SkeletonTable(state: SongLibraryState, width: Dp) {
    val sweep = rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SKELETON_SWEEP_MS, easing = LinearEasing)),
        label = "sweep",
    )
    Column(Modifier.width(width)) {
        repeat(SKELETON_ROWS) { row -> SkeletonRow(state, width, sweep, row) }
    }
}

@Composable
private fun SkeletonRow(state: SongLibraryState, width: Dp, sweep: State<Float>, row: Int) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.width(width)) {
        Row(
            Modifier.fillMaxWidth().height(LibraryMetrics.rowHeight).background(scheme.background),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Every bar is told where it sits across the table, so the highlight crosses the whole
            // row as one sweep rather than restarting inside each cell.
            var offset = 0.dp
            Box(Modifier.width(TICK_WIDTH), contentAlignment = Alignment.Center) {
                SkeletonBar(15.dp, sweep, offset + 10.dp, width)
            }
            offset += TICK_WIDTH
            state.visibleColumns.forEach { field ->
                val cell = field.width()
                Box(Modifier.width(cell).padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
                    SkeletonBar((cell - 18.dp) * barFraction(row, field.ordinal), sweep, offset + 9.dp, width)
                }
                Box(
                    Modifier.width(1.dp)
                        .height(LibraryMetrics.rowHeight)
                        .background(scheme.onSurface.copy(alpha = HAIRLINE_ALPHA))
                )
                offset += cell + 1.dp
            }
            Spacer(Modifier.width(ACTIONS_WIDTH))
        }
        Hairline()
    }
}

/** One bar, filled with the moving gradient. [xOffset] is where it starts across [tableWidth]. */
@Composable
private fun SkeletonBar(barWidth: Dp, sweep: State<Float>, xOffset: Dp, tableWidth: Dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.width(barWidth)
            .height(SKELETON_BAR_HEIGHT)
            .clip(RoundedCornerShape(3.dp))
            // Read in the draw phase, not composition: `sweep.value` changes every frame, and read
            // up in the composable it would recompose eighty cells sixty times a second.
            .drawBehind {
                val total = tableWidth.toPx()
                val band = total * SKELETON_BAND
                val head = -band + sweep.value * (total + band * 2) - xOffset.toPx()
                drawRect(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to scheme.onSurface.copy(alpha = SKELETON_ALPHA),
                            SKELETON_HIGHLIGHT_STOP to scheme.onSurface.copy(alpha = SKELETON_HIGHLIGHT_ALPHA),
                            1f to scheme.onSurface.copy(alpha = SKELETON_ALPHA),
                        ),
                        start = Offset(head, 0f),
                        end = Offset(head + band, 0f),
                    )
                )
            }
    )
}

/**
 * How much of its cell a bar fills, so the rows read as text of different lengths rather than a
 * block. Derived from the row and column rather than random, so it does not change under a redraw.
 */
private fun barFraction(row: Int, column: Int): Float =
    SKELETON_MIN_FILL +
        ((row * SKELETON_ROW_STRIDE + column * SKELETON_COLUMN_STRIDE) % SKELETON_FILL_STEPS) * SKELETON_FILL_STEP

private const val SKELETON_ROWS = 10
private const val SKELETON_SWEEP_MS = 1400
private const val SKELETON_BAND = 0.35f
private const val SKELETON_MIN_FILL = 0.42f
private const val SKELETON_FILL_STEPS = 5
private const val SKELETON_FILL_STEP = 0.12f
/** Where the bright middle of the sweep sits in its gradient. */
private const val SKELETON_HIGHLIGHT_STOP = 0.5f
/** Coprime strides, so the fill pattern does not repeat down a column or across a row. */
private const val SKELETON_ROW_STRIDE = 7
private const val SKELETON_COLUMN_STRIDE = 13
private val SKELETON_BAR_HEIGHT = 9.dp
