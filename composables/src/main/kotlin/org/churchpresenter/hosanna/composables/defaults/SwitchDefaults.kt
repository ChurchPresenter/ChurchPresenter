package org.churchpresenter.hosanna.composables.defaults

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class SwitchMetrics(val width: Dp, val height: Dp, val knobOn: Dp, val knobOff: Dp)

internal object SwitchDefaults {
    val Regular = SwitchMetrics(40.dp, 24.dp, 18.dp, 14.dp)
    val Small = SwitchMetrics(32.dp, 20.dp, 14.dp, 10.dp)
    val Edge: Dp = 1.5.dp
    val Stretch: Dp = 4.dp
    val Spinner: Dp = 10.dp
    val Check: Dp = 10.dp
    val FormGap: Dp = 12.dp
    val InlineGap: Dp = 10.dp
    val CompactGap: Dp = 8.dp
    val TrailingGap: Dp = 12.dp
    val MinHeight: Dp = 36.dp
    val RowHeight: Dp = 52.dp
    val CompactHeight: Dp = 32.dp
    val DescriptionGap: Dp = 2.dp
    const val SLIDE_MILLIS: Int = 150

    fun metrics(compact: Boolean): SwitchMetrics = if (compact) Small else Regular
}
