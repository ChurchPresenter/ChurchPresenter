package org.churchpresenter.theme.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.elevationPalette
import kotlin.math.abs

/** The track height and handle size of an ordinary slider, and of a compact one (volume, panels). */
val SLIDER_TRACK: Dp = 6.dp
val SLIDER_THUMB: Dp = 14.dp
val COMPACT_SLIDER_TRACK: Dp = 4.dp
val COMPACT_SLIDER_THUMB: Dp = 12.dp

/**
 * The colors every slider in the app is drawn in: a flat neutral [track], the accent for the
 * [fill]ed part, and a white [thumb] ringed in the accent. Read once in composition and handed to
 * [drawSlider], which cannot read the theme itself.
 */
@Immutable
data class SliderLook(
    val track: Color,
    val buffered: Color,
    val fill: Color,
    val thumb: Color,
    val ring: Color,
    val shadow: Color,
)

@Composable
fun sliderLook(): SliderLook {
    val scheme = MaterialTheme.colorScheme
    val dark = elevationPalette().isDark
    return SliderLook(
        track = scheme.onSurface.copy(alpha = if (dark) TRACK_ALPHA_DARK else TRACK_ALPHA_LIGHT),
        buffered = scheme.onSurface.copy(alpha = BUFFERED_ALPHA),
        fill = scheme.primary,
        thumb = Color.White,
        ring = scheme.primary,
        shadow = Color.Black.copy(alpha = THUMB_SHADOW_ALPHA),
    )
}

/**
 * Draws a slider across this scope's width, centred in its height: the track, the fill between
 * [originFraction] and [fraction], an optional [bufferedFraction] under the fill, and the handle at
 * [fraction], grown by [thumbScale].
 */
fun DrawScope.drawSlider(
    look: SliderLook,
    fraction: Float,
    originFraction: Float = 0f,
    bufferedFraction: Float? = null,
    track: Dp = SLIDER_TRACK,
    thumb: Dp = SLIDER_THUMB,
    thumbScale: Float = 1f,
) {
    val trackH = track.toPx()
    val cy = size.height / 2f
    val top = cy - trackH / 2f
    val radius = CornerRadius(trackH / 2f, trackH / 2f)
    drawRoundRect(look.track, Offset(0f, top), Size(size.width, trackH), radius)
    if (bufferedFraction != null && bufferedFraction > 0f) {
        drawRoundRect(look.buffered, Offset(0f, top), Size(size.width * bufferedFraction, trackH), radius)
    }
    if (fraction != originFraction) {
        val fillStart = size.width * minOf(originFraction, fraction)
        val fillW = size.width * abs(fraction - originFraction)
        drawRoundRect(look.fill, Offset(fillStart, top), Size(fillW, trackH), radius)
    }
    val hx = (size.width * fraction).coerceIn(0f, size.width)
    val r = thumb.toPx() / 2f * thumbScale
    val ringW = THUMB_RING.toPx()
    drawCircle(look.shadow, radius = r + 0.5.dp.toPx(), center = Offset(hx, cy + 1.dp.toPx()))
    drawCircle(look.thumb, radius = r, center = Offset(hx, cy))
    drawCircle(look.ring, radius = r - ringW / 2f, center = Offset(hx, cy), style = Stroke(ringW))
}

private val THUMB_RING = 2.dp
private const val TRACK_ALPHA_LIGHT = 0.09f
private const val TRACK_ALPHA_DARK = 0.12f
private const val BUFFERED_ALPHA = 0.18f
private const val THUMB_SHADOW_ALPHA = 0.25f
