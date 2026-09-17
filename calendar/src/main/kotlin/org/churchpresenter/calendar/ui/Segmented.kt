package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SELECTOR_HEIGHT = 34.dp
private val CHIP_HEIGHT = 23.dp
private val TRACK_PADDING = 2.dp
private val TRACK_GAP = 2.dp
private const val TRACK_TINT = 0.5f
private const val CHIP_TINT = 0.16f
private const val CHIP_BORDER = 0.55f

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
) {
    val scheme = MaterialTheme.colorScheme
    val trackShape = RoundedCornerShape(8.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(TRACK_GAP),
        modifier = modifier
            .fillMaxWidth()
            .height(SELECTOR_HEIGHT)
            .clip(trackShape)
            .background(scheme.surfaceVariant.copy(alpha = TRACK_TINT))
            .border(1.dp, scheme.outlineVariant, trackShape)
            .padding(TRACK_PADDING),
    ) {
        options.forEach { option ->
            val on = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (on) scheme.primary else Color.Transparent)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A small bordered option chip — the design's `Until` row, tinted and outlined when chosen. */
@Composable
fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .height(CHIP_HEIGHT)
            .clip(shape)
            .background(if (selected) scheme.primary.copy(alpha = CHIP_TINT) else Color.Transparent)
            .border(1.dp, if (selected) scheme.primary.copy(alpha = CHIP_BORDER) else scheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}
