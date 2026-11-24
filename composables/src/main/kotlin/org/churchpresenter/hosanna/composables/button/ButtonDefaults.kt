package org.churchpresenter.hosanna.composables.button

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.HosannaRadius

internal object ButtonFoundationDefaults {
    val SpinnerStroke: Dp = 2.dp
    const val TRANSITION_MILLIS: Int = 120
    const val SPIN_MILLIS: Int = 800
    const val SPINNER_SWEEP: Float = 270f
}

internal object StandardButtonDefaults {
    val Height: Dp = 34.dp
    val MinWidth: Dp = 64.dp
    val HorizontalPadding: Dp = 14.dp
    val IconSidePadding: Dp = 12.dp
    val TertiaryHorizontalPadding: Dp = 10.dp
    val IconSize: Dp = 16.dp
    val IconGap: Dp = 7.dp
}

@Immutable
internal data class IconButtonMetrics(val side: Dp, val icon: Dp, val spinner: Dp, val cornerRadius: Dp)

internal object IconButtonDefaults {
    val Large = IconButtonMetrics(side = 40.dp, icon = 20.dp, spinner = 18.dp, cornerRadius = HosannaRadius.M)
    val Medium = IconButtonMetrics(side = 34.dp, icon = 16.dp, spinner = 16.dp, cornerRadius = HosannaRadius.M)
    val Small = IconButtonMetrics(side = 28.dp, icon = 14.dp, spinner = 14.dp, cornerRadius = HosannaRadius.S)

    fun metrics(size: IconButtonSize): IconButtonMetrics = when (size) {
        IconButtonSize.Large -> Large
        IconButtonSize.Medium -> Medium
        IconButtonSize.Small -> Small
    }
}
