package org.churchpresenter.hosanna.composables.defaults

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.AppRadius
import org.churchpresenter.hosanna.composables.model.ThumbnailSize

internal data class ThumbnailMetrics(val width: Dp, val height: Dp, val radius: Dp)

internal object ThumbnailDefaults {
    val Edge: Dp = 1.dp
    val Border: Dp = 2.dp
    val CaptionGap: Dp = 6.dp
    val BadgeHeight: Dp = 18.dp
    val BadgeRadius: Dp = AppRadius.XS
    val BadgePadding: Dp = 6.dp
    val BadgeInset: Dp = 6.dp
    val LiveDot: Dp = 8.dp
    val Progress: Dp = 3.dp
    val Action: Dp = 28.dp
    val ActionIcon: Dp = 16.dp
    val ActionGap: Dp = 4.dp
    val MissingIcon: Dp = 20.dp
    val MissingGap: Dp = 4.dp
    const val SCRIM_HEIGHT: Float = 0.55f
    const val HIDDEN_ALPHA: Float = 0.35f
    const val HIDDEN_SATURATION: Float = 0.4f
    const val OFFLINE_ALPHA: Float = 0.4f
    const val SHINE_MILLIS: Int = 1200
    const val SHINE_WIDTH: Float = 0.4f

    fun metrics(size: ThumbnailSize): ThumbnailMetrics = when (size) {
        ThumbnailSize.Small -> ThumbnailMetrics(96.dp, 54.dp, AppRadius.S)
        ThumbnailSize.Medium -> ThumbnailMetrics(160.dp, 90.dp, AppRadius.M)
        ThumbnailSize.Large -> ThumbnailMetrics(240.dp, 135.dp, AppRadius.M)
    }
}
