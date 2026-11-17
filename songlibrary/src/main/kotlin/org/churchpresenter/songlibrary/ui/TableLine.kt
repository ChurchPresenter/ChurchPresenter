package org.churchpresenter.songlibrary.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.SongField

// The grid's lines in three parts, so a song's title and its buttons are on screen together however
// far the columns between them are scrolled.

/**
 * One line of the grid: [frozen] on the left and [actions] on the right stay put, and [scrolling]
 * between them moves sideways with [scroll] — one state shared by the header and every row, so the
 * columns stay lined up whichever line the wheel or the scrollbar moved.
 *
 * The middle takes only the room its columns need, so in a window wide enough for all of them the
 * actions sit straight after the last column rather than across an empty gap at the far edge.
 */
@Composable
internal fun TableLine(
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    frozen: @Composable () -> Unit,
    scrolling: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    val edge = MaterialTheme.colorScheme.scrim.copy(alpha = EDGE_SHADOW_ALPHA)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) { frozen() }
        Row(
            Modifier.weight(1f, fill = false).scrollEdges(scroll, edge).horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
        ) { scrolling() }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/**
 * A soft shadow on each side of the scrolling middle that has more columns beyond it, so the frozen
 * edges read as lying over the columns that slide under them. Read in the draw phase: the scroll
 * position changes every frame of a drag.
 */
private fun Modifier.scrollEdges(scroll: ScrollState, color: Color): Modifier = drawWithContent {
    drawContent()
    val width = EDGE_SHADOW_WIDTH.toPx()
    if (scroll.value > 0) {
        drawRect(
            Brush.horizontalGradient(listOf(color, Color.Transparent), startX = 0f, endX = width),
            size = Size(width, size.height),
        )
    }
    if (scroll.value < scroll.maxValue) {
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, color),
                startX = size.width - width,
                endX = size.width,
            ),
            topLeft = Offset(size.width - width, 0f),
            size = Size(width, size.height),
        )
    }
}

/**
 * The columns that stay on the left — Number, when shown, and Title — and the ones that scroll, in
 * the grid's order. Title cannot be hidden and Number always leads, so the frozen part is the same
 * shape whatever else the Columns menu turned on.
 */
internal fun splitFrozen(columns: List<SongField>): Pair<List<SongField>, List<SongField>> =
    columns.partition { it == SongField.NUMBER || it == SongField.TITLE }

/** How wide [fields] are side by side, each with the hairline after it. */
internal fun columnsWidth(fields: List<SongField>): Dp =
    fields.fold(0.dp) { total, field -> total + field.width() + 1.dp }

/** The hairline between two columns, [height] tall. */
@Composable
internal fun ColumnDivider(height: Dp = LibraryMetrics.rowHeight) {
    Box(
        Modifier.width(1.dp)
            .height(height)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = HAIRLINE_ALPHA))
    )
}

private val EDGE_SHADOW_WIDTH = 8.dp
private const val EDGE_SHADOW_ALPHA = 0.12f
