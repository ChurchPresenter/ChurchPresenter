package org.churchpresenter.hosanna.composables.defaults

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object SeekBarDefaults {
    val Track: Dp = 6.dp
    val Thumb: Dp = 14.dp
    val TimeGap: Dp = 10.dp
    val TimeWidth: Dp = 36.dp
    const val STEP_SECONDS: Double = 5.0
    const val BIG_STEP_SECONDS: Double = 30.0
}

internal object VolumeDefaults {
    val Width: Dp = 152.dp
    val Gap: Dp = 4.dp
    const val FULL: Double = 100.0
}
