package org.churchpresenter.hosanna.composables.defaults

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.HosannaRadius

internal object TooltipDefaults {
    val MinHeight: Dp = 24.dp
    val PaddingX: Dp = 8.dp
    val PaddingY: Dp = 4.dp
    val Radius: Dp = HosannaRadius.S
    val MaxWidth: Dp = 240.dp
    val ShortcutGap: Dp = 6.dp
    val Gap: Dp = 6.dp
    val Margin: Dp = 8.dp
    val Slide: Dp = 2.dp
    const val SHOW_DELAY_MILLIS: Long = 400
    const val HIDE_DELAY_MILLIS: Long = 100
    const val WARM_MILLIS: Long = 300
    const val FADE_IN_MILLIS: Int = 120
    const val FADE_OUT_MILLIS: Int = 80
}

internal object RichTooltipDefaults {
    val PaddingX: Dp = 14.dp
    val PaddingY: Dp = 12.dp
    val MaxWidth: Dp = 300.dp
    val TitleGap: Dp = 4.dp
    val ActionGap: Dp = 6.dp
    const val OPEN_DELAY_MILLIS: Long = 500
    const val CLOSE_DELAY_MILLIS: Long = 300
}
