package org.churchpresenter.hosanna.composables.defaults

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class EmptyStateMetrics(
    val tile: Dp,
    val icon: Dp,
    val padding: Dp,
    val tileGap: Dp,
    val bodyGap: Dp,
    val actionsGap: Dp,
)

internal object EmptyStateDefaults {
    val Regular = EmptyStateMetrics(56.dp, 26.dp, 24.dp, 16.dp, 6.dp, 20.dp)
    val Compact = EmptyStateMetrics(40.dp, 20.dp, 16.dp, 12.dp, 4.dp, 14.dp)
    val MaxWidth: Dp = 340.dp
    val ButtonGap: Dp = 8.dp
    val HintGap: Dp = 12.dp

    fun metrics(compact: Boolean): EmptyStateMetrics = if (compact) Compact else Regular
}
