package org.churchpresenter.calendar.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.theme.components.SegmentTrackItem
import org.churchpresenter.theme.components.SegmentTrack
import org.churchpresenter.theme.components.RaisedChip
import org.churchpresenter.theme.elevationPalette
import androidx.compose.material3.LocalContentColor

private val SELECTOR_HEIGHT = 34.dp
private val CHIP_HEIGHT = 23.dp

/**
 * The design's **segmented** selector: one inset track — its own background, one border, 2dp of
 * padding — holding borderless segments that share it, with the selected one filled.
 *
 * Not a row of separately bordered pills: that draws three lines where the design has one and
 * reads as independent toggles rather than as one field with a few states, which is what `Type`,
 * `Repeats` and `Applies to` each are. No color dots either — inside a picker whose options are
 * already named they are decoration competing with the label.
 */
@Composable
fun <T> SegmentedSelector(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = SELECTOR_HEIGHT,
) {
    SegmentTrack(modifier = modifier.fillMaxWidth().height(height), shape = RoundedCornerShape(9.dp)) {
        options.forEach { option ->
            val on = option == selected
            SegmentTrackItem(
                selected = on,
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = LocalContentColor.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A small option chip — the design's `Until` row: a raised key, lit when chosen. */
@Composable
fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = elevationPalette()
    RaisedChip(
        onClick = onClick,
        fill = if (selected) palette.selected else palette.key,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        },
        modifier = Modifier.height(CHIP_HEIGHT),
        shape = RoundedCornerShape(6.dp),
    )
}
