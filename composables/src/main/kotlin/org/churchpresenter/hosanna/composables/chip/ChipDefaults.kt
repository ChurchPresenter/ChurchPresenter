package org.churchpresenter.hosanna.composables.chip

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.HosannaRadius

@Immutable
internal data class ChipMetrics(val height: Dp, val padding: Dp, val iconSidePadding: Dp)

internal object ChipDefaults {
    val Default = ChipMetrics(height = 28.dp, padding = 11.dp, iconSidePadding = 9.dp)
    val Small = ChipMetrics(height = 24.dp, padding = 9.dp, iconSidePadding = 7.dp)
    val Radius: Dp = HosannaRadius.S
    val Edge: Dp = 1.dp
    val Icon: Dp = 12.dp
    val IconGap: Dp = 6.dp
    val RemoveCircle: Dp = 16.dp
    val RemoveGlyph: Dp = 8.dp
    val RemoveGap: Dp = 4.dp
    val DragHandleHeight: Dp = 12.dp
    val TagHeight: Dp = 18.dp
    val TagPadding: Dp = 6.dp
    val TagRadius: Dp = HosannaRadius.XS
    val FileNameMaxWidth: Dp = 180.dp
    val StarCircle: Dp = 20.dp
    val StarGlyph: Dp = 13.dp
    val StarEndPadding: Dp = 4.dp
    const val TRANSITION_MILLIS: Int = 120

    fun metrics(size: ChipSize): ChipMetrics = when (size) {
        ChipSize.Default -> Default
        ChipSize.Small -> Small
    }
}
