package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants

/**
 * The overlay a Lottie band draws between the band the file paints and the text on it: the
 * three treatments the classic backdrop takes, applied to the band alone so the text stays crisp.
 */
internal data class BandLook(
    val opacity: Float,
    /** Percent of black washed over the band, 0–100. */
    val dimPercent: Int,
    /** Blur radius in the 1920×1080 reference space the presenters measure in. */
    val blurReferencePx: Int,
) {
    val isBlurred: Boolean get() = blurReferencePx > 0

    /**
     * The dim as a filter rather than a black box: scaling the colour and leaving the alpha keeps
     * a band that does not fill its rectangle — one still sliding in, one with a shaped edge —
     * from being washed where there is no band.
     */
    fun dimFilter(): ColorFilter? {
        if (dimPercent <= 0) return null
        val keep = 1f - (dimPercent / PERCENT).coerceIn(0f, 1f)
        return ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(keep, keep, keep, 1f) })
    }
}

/** The overlay this Lottie band is configured with, or null when it is off or this is not a Lottie band. */
internal fun BackgroundConfig.lottieBandLook(): BandLook? =
    if (backgroundType == Constants.BACKGROUND_LOTTIE && lottieOverlay) {
        BandLook(opacity = backgroundOpacity, dimPercent = dim, blurReferencePx = blur)
    } else null

/**
 * The band under its overlay: [content] drawn at [look]'s opacity and blur, clipped to the band.
 * A blurred pass bleeds past the band's edges by the same margin the classic band uses, so the
 * blur's own fade lands outside the clip rather than along the band's top edge.
 */
@Composable
internal fun BandBackdrop(look: BandLook, modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val blurRadius = backgroundBlurRadius(look.blurReferencePx, maxWidth)
        val bleed = if (look.isBlurred) blurRadius * BLUR_EDGE_BLEED else 0.dp
        content(
            Modifier
                .requiredSize(width = maxWidth + bleed * 2, height = maxHeight + bleed * 2)
                .alpha(look.opacity.coerceIn(0f, 1f))
                .then(if (look.isBlurred) Modifier.blur(blurRadius) else Modifier),
        )
    }
}
