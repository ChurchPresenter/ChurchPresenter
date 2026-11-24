@file:OptIn(ExperimentalComposeUiApi::class)

package org.churchpresenter.hosanna.composables.defaults

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.HosannaRadius

internal object FieldDefaults {
    val LabeledHeight: Dp = 44.dp
    val CompactHeight: Dp = 36.dp
    val MultilineMinHeight: Dp = 72.dp
    val MultilineMaxHeight: Dp = 200.dp
    val HorizontalPadding: Dp = 12.dp
    val MultilineVerticalPadding: Dp = 10.dp
    val LabelGap: Dp = 2.dp
    val Icon: Dp = 16.dp
    val IconGap: Dp = 8.dp
    val ClearCircle: Dp = 20.dp
    val ClearGlyph: Dp = 10.dp
    val Border: Dp = 1.dp
    val SupportingGap: Dp = 6.dp
    val SupportingIcon: Dp = 13.dp
    val SupportingIconGap: Dp = 4.dp
    val Dash: Dp = 4.dp
    val DashGap: Dp = 3.dp
    const val TRANSITION_MILLIS: Int = 120
    val SearchHeight: Dp = 40.dp
    val SearchCompactHeight: Dp = 32.dp
    val SearchClear: Dp = 28.dp
    val Spinner: Dp = 14.dp
    val KeyCapHeight: Dp = 20.dp
    val KeyCapPadding: Dp = 6.dp
    val DetectionHeight: Dp = 22.dp
    val DetectionPadding: Dp = 8.dp
}

internal object MenuDefaults {
    val Padding: Dp = 6.dp
    val RowPadding: Dp = 10.dp
    val RowHeight: Dp = 36.dp
    val TwoLineRowHeight: Dp = 46.dp
    val Check: Dp = 14.dp
    val Icon: Dp = 16.dp
    val Gap: Dp = 6.dp
    val MaxHeight: Dp = 314.dp
    val KeyboardRing: Dp = 1.5.dp
    val HeaderTop: Dp = 10.dp
    val HeaderBottom: Dp = 5.dp
    val DividerMargin: Dp = 5.dp
    val BadgeHeight: Dp = 20.dp
    val BadgePadding: Dp = 6.dp
    val ScrollbarThickness: Dp = 6.dp
    const val SEARCH_AFTER: Int = 10
    const val OPEN_MILLIS: Int = 120
    val OpenSlide: Dp = 4.dp
}

internal object TimeFieldDefaults {
    val Width12: Dp = 112.dp
    val Width24: Dp = 96.dp
    val PartPadding: Dp = 2.dp
    val ArrowWidth: Dp = 16.dp
    val ArrowHeight: Dp = 15.dp
    val Arrow: Dp = 10.dp
    val TagHeight: Dp = 18.dp
    val TagPadding: Dp = 6.dp
    val EndPadding: Dp = 4.dp
}

internal object TimeSpinnerDefaults {
    val Padding: Dp = 8.dp
    val ColumnWidth: Dp = 52.dp
    val ColumnGap: Dp = 4.dp
    val ArrowHeight: Dp = 28.dp
    val RowHeight: Dp = 32.dp
    val Arrow: Dp = 12.dp
}

internal object TimerSpinnerDefaults {
    val Width: Dp = 84.dp
    val ButtonHeight: Dp = 36.dp
    val NumberHeight: Dp = 60.dp
    val Icon: Dp = 16.dp
    val LabelGap: Dp = 6.dp
    val Dot: Dp = 5.dp
    val DotGap: Dp = 12.dp
    val ColonPadding: Dp = 14.dp
    val PresetGap: Dp = 12.dp
    val ChipGap: Dp = 6.dp
}

internal object ColorFieldDefaults {
    val Width: Dp = 160.dp
    val WidthWithOpacity: Dp = 200.dp
    val Swatch: Dp = 20.dp
    val PickerWidth: Dp = 256.dp
    val PickerPadding: Dp = 12.dp
    val AreaHeight: Dp = 148.dp
    val AreaThumb: Dp = 14.dp
    val Ring: Dp = 2.dp
    val SliderHeight: Dp = 12.dp
    val SliderThumb: Dp = 16.dp
    val InputHeight: Dp = 32.dp
    val OpacityInputWidth: Dp = 64.dp
    val PaletteSwatch: Dp = 24.dp
    val SwatchGap: Dp = 6.dp
    val Gap: Dp = 10.dp
    val Check: Dp = 5.dp
    const val SWATCHES_PER_ROW: Int = 8
    const val KEY_STEP: Float = 0.01f
    const val KEY_STEP_LARGE: Float = 0.1f
}

internal object NumberFieldDefaults {
    val ArrowWidth: Dp = 20.dp
    val ArrowHeight: Dp = 16.dp
    val Arrow: Dp = 10.dp
    val ArrowInset: Dp = 4.dp
    val UnitWidth: Dp = 76.dp
    val UnitGap: Dp = 4.dp
    const val BIG_STEP: Int = 10
}

internal class PositionMetrics(
    val width: Dp,
    val height: Dp,
    val inset: Dp,
    val radius: Dp,
    val dot: Dp,
    val selectedDot: Dp,
)

internal object PositionPickerDefaults {
    val Regular = PositionMetrics(
        width = 224.dp,
        height = 126.dp,
        inset = 3.dp,
        radius = HosannaRadius.S,
        dot = 6.dp,
        selectedDot = 8.dp,
    )
    val Compact = PositionMetrics(
        width = 112.dp,
        height = 63.dp,
        inset = 2.dp,
        radius = HosannaRadius.XS,
        dot = 4.dp,
        selectedDot = 6.dp,
    )
    val LabelGap: Dp = 8.dp
    val Line: Dp = 1.dp
    val Dash: Dp = 1.5.dp
    val DashOn: Dp = 4.dp
    val DashOff: Dp = 3.dp

    fun metrics(compact: Boolean): PositionMetrics = if (compact) Compact else Regular
}

internal object HoldRepeatDefaults {
    const val DELAY_MILLIS: Long = 400
    const val REPEAT_MILLIS: Long = 80
    const val FAST_AFTER_MILLIS: Long = 2000
}
