package org.churchpresenter.hosanna.composables.defaults

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.model.ProgressSpinnerSize

internal object ProgressDefaults {
    val Track: Dp = 6.dp
    val ThinTrack: Dp = 3.dp
    val LabelGap: Dp = 6.dp
    val StatusGap: Dp = 4.dp
    val StatusIcon: Dp = 14.dp
    val RingStroke: Dp = 3.dp
    val Ring: Dp = 40.dp
    val RingIcon: Dp = 18.dp
    const val FILL_MILLIS: Int = 200
    const val SLIDE_MILLIS: Int = 1400
    const val SPIN_MILLIS: Int = 900
    const val SEGMENT: Float = 0.4f

    fun spinner(size: ProgressSpinnerSize): Dp = when (size) {
        ProgressSpinnerSize.Inline -> 20.dp
        ProgressSpinnerSize.Regular -> 32.dp
    }
}
