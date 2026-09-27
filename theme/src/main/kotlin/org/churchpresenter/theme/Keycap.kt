package org.churchpresenter.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * A keycap in [color]: a lit face standing on a darker side along the bottom, with a soft gloss over
 * its upper half. [pressed] sinks the face onto a thinner side. The face is shorter than the
 * control, so its content goes inside [keycapFacePadding] to stay centred on it.
 */
fun Modifier.keycap(
    shape: Shape,
    color: Color,
    palette: ElevationPalette,
    pressed: Boolean = false,
    hovered: Boolean = false,
    /** A 2dp ring drawn over the edge -- keyboard focus. */
    ring: Color = Color.Unspecified,
    /** Off, the key keeps its shape but lies unlit: no drop shadow, no gloss, no hover or press. */
    enabled: Boolean = true,
): Modifier {
    val lit = enabled && hovered && !pressed
    val base = if (lit) lerp(color, Color.White, fraction = HOVER_BRIGHTEN) else color
    val elevation = when {
        !enabled || pressed -> 0.dp
        hovered -> RAISED_LIFT + HOVER_EXTRA_LIFT
        else -> RAISED_LIFT
    }
    val shadowTint = if (palette.isDark) Color.Black else Color.Black.copy(alpha = LIGHT_GLOW_ALPHA)
    val faceTop = if (enabled && pressed) KEYCAP_SIDE - KEYCAP_SIDE_PRESSED else 0.dp
    val gloss = when {
        !enabled -> 0f
        palette.isDark -> KEYCAP_GLOSS_DARK
        else -> KEYCAP_GLOSS_LIGHT
    }
    return this
        .shadow(elevation, shape, clip = false, ambientColor = shadowTint, spotColor = shadowTint)
        .clip(shape)
        .background(lerp(base, Color.Black, fraction = KEYCAP_SIDE_SHADE))
        .drawBehind {
            val faceSize = Size(size.width, size.height - KEYCAP_SIDE.toPx())
            val face = shape.createOutline(faceSize, layoutDirection, this)
            translate(top = faceTop.toPx()) {
                drawOutline(
                    face,
                    Brush.verticalGradient(
                        listOf(
                            lerp(base, Color.White, fraction = KEYCAP_FACE_LIFT),
                            lerp(base, Color.Black, fraction = KEYCAP_FACE_SHADE),
                        ),
                        endY = faceSize.height,
                    ),
                )
                drawOutline(
                    face,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = gloss), Color.Transparent),
                        endY = faceSize.height * KEYCAP_GLOSS_DEPTH,
                    ),
                )
            }
        }
        .then(
            if (palette.keyEdge.alpha > 0f) Modifier.border(HAIRLINE, palette.keyEdge, shape) else Modifier
        )
        .then(if (ring.isSpecified) Modifier.border(RING_WIDTH, ring, shape) else Modifier)
}

/** A [keycap] that cannot be used: the neutral key, unlit and at 40% -- the whole control, label too. */
fun Modifier.keycapDisabled(shape: Shape, palette: ElevationPalette): Modifier = this
    .alpha(DISABLED_OPACITY)
    .keycap(shape, palette.key.bottom, palette, enabled = false)

/** Where a [keycap]'s content goes so it sits centred on the face, and sinks with it. */
fun keycapFacePadding(pressed: Boolean): PaddingValues {
    val faceTop = if (pressed) KEYCAP_SIDE - KEYCAP_SIDE_PRESSED else 0.dp
    return PaddingValues(top = faceTop, bottom = KEYCAP_SIDE - faceTop)
}

private val KEYCAP_SIDE = 3.dp
private val KEYCAP_SIDE_PRESSED = 1.dp
private const val KEYCAP_SIDE_SHADE = 0.38f
private const val KEYCAP_FACE_LIFT = 0.24f
private const val KEYCAP_FACE_SHADE = 0.06f
private const val KEYCAP_GLOSS_DARK = 0.18f
private const val KEYCAP_GLOSS_LIGHT = 0.28f
private const val KEYCAP_GLOSS_DEPTH = 0.5f
