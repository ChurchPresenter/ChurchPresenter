package org.churchpresenter.songlibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.songs.sectionOrderAbbreviation
import org.churchpresenter.core.models.songs.sectionOrderLabel
import org.churchpresenter.songchords.SongSectionWordGroup
import org.churchpresenter.songchords.SongSectionWords
import org.churchpresenter.songlibrary.generated.resources.Res
import org.churchpresenter.songlibrary.generated.resources.column_order
import org.churchpresenter.songlibrary.generated.resources.order_as_written
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

// The Order column: the order each song is sung in, as a row of section chips. Read only -- the
// order is set in the song editor, where the sections it names can be seen.

/** The Order column's heading; not sortable, so it is a label rather than a [HeadCell]. */
@Composable
internal fun OrderHeadCell() {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.width(ORDER_WIDTH).fillMaxHeight().padding(horizontal = 9.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            stringResource(Res.string.column_order).uppercase(),
            style = LibraryType.columnHead,
            color = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
            maxLines = 1,
        )
    }
    Box(Modifier.width(1.dp).fillMaxHeight().background(scheme.onSurface.copy(alpha = HAIRLINE_ALPHA)))
}

/**
 * A song's order: its first [ORDER_CHIPS_SHOWN] sections, then `+n`, or "As written" with none.
 * The whole order shows in a tooltip on hover.
 */
@Composable
internal fun OrderCell(order: List<String>) {
    if (order.isEmpty()) {
        OrderChips(order)
    } else {
        WithTooltip(order.joinToString(" ", transform = ::sectionOrderAbbreviation)) { OrderChips(order) }
    }
}

@Composable
private fun OrderChips(order: List<String>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.width(ORDER_WIDTH).height(LibraryMetrics.rowHeight).padding(horizontal = 8.dp).clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (order.isEmpty()) {
            Text(
                stringResource(Res.string.order_as_written),
                style = LibraryType.body,
                color = muted.copy(alpha = FAINT_TEXT_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            return@Row
        }
        order.take(ORDER_CHIPS_SHOWN).forEach { key ->
            val ink = sectionInk(sectionOrderLabel(key))
            Text(
                sectionOrderAbbreviation(key),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.background(ink.copy(alpha = CHIP_FILL_ALPHA), AppShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (order.size > ORDER_CHIPS_SHOWN) {
            Text("+${order.size - ORDER_CHIPS_SHOWN}", fontSize = 11.sp, color = muted, maxLines = 1, softWrap = false)
        }
    }
}

/** The legend ink the editor and the Songs tab give a section of this name. */
@Composable
private fun sectionInk(label: String): Color = with(MaterialTheme.semantic) {
    when (SongSectionWords.groupOf(label)) {
        SongSectionWordGroup.CHORUS -> chordChorus
        SongSectionWordGroup.BRIDGE -> chordBridge
        SongSectionWordGroup.TAG -> chordTag
        else -> chordVerse
    }
}

private const val ORDER_CHIPS_SHOWN = 5
private const val CHIP_FILL_ALPHA = 0.16f

/** The Order column, and what it adds to the table's width when shown. */
internal val ORDER_WIDTH = 170.dp
internal fun orderColumnWidth(shown: Boolean): Dp = if (shown) ORDER_WIDTH + 1.dp else 0.dp
