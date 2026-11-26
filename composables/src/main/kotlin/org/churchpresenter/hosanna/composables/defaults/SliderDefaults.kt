package org.churchpresenter.hosanna.composables.defaults

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.AppRadius
import org.churchpresenter.hosanna.composables.model.NumberRange

internal object SliderDefaults {
    val Percent = NumberRange(0.0, 100.0)
    val Track: Dp = 4.dp
    val HitHeight: Dp = 24.dp
    val Thumb: Dp = 16.dp
    val ThumbRing: Dp = 2.dp
    val ThumbEdge: Dp = 1.dp
    val HoverHalo: Dp = 8.dp
    val DragHalo: Dp = 10.dp
    val Dot: Dp = 4.dp
    val CenterMark: Dp = 2.dp
    val CenterMarkHeight: Dp = 12.dp
    val BubbleGap: Dp = 6.dp
    val BubbleRadius: Dp = AppRadius.S
    val BubblePaddingX: Dp = 8.dp
    val BubblePaddingY: Dp = 3.dp
    val ValueWidth: Dp = 44.dp
    val SignedValueWidth: Dp = 58.dp
    val Gap: Dp = 12.dp
    val LabelGap: Dp = 8.dp
    val MinWidth: Dp = 96.dp
    const val FILL_MILLIS: Int = 200
    const val MAX_DOTS: Int = 10
    const val ORIGIN_SNAP: Double = 0.02
}
