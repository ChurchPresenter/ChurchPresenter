package org.churchpresenter.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.composed

/**
 * The two-sided fill of a raised control: a vertical gradient from [top] to [bottom], the [ink]
 * drawn on it, the [highlight] along its top edge and the [glow] its drop shadow is tinted with.
 */
@Immutable
data class RaisedFill(
    val top: Color,
    val bottom: Color,
    val ink: Color,
    val highlight: Color,
    val glow: Color,
)

/**
 * The elevated control look, derived from the active color scheme so every theme gets it:
 * choices sit in a sunken **well** and the chosen one, like every button, is a **raised** key.
 */
@Immutable
data class ElevationPalette(
    val isDark: Boolean,
    val wellTop: Color,
    val wellBottom: Color,
    val wellBorder: Color,
    val wellShadow: Color,
    /** An unselected, neutral key — a toolbar button, a transport button, a format toggle off. */
    val key: RaisedFill,
    /** The chosen segment of a segmented control. */
    val selected: RaisedFill,
    /** A key that is switched on, or a primary action. */
    val accent: RaisedFill,
    /**
     * A destructive action: a key tinted toward the theme's error color, its label a shade of that
     * red chosen to clear 4.5:1 on it. The raw `error` red on a dark key does not -- 2.5:1 to 4.1:1
     * across the dark themes.
     */
    val danger: RaisedFill,
    /** The 0.5dp hairline light keys carry so they read against a pale page. Transparent on dark. */
    val keyEdge: Color,
    val dropShadow: Color,
    val disabledFill: Color,
    val disabledInk: Color,
) {
    /** A raised fill in an arbitrary [color] — Go Live, Add to Schedule, a success or danger action. */
    fun tinted(color: Color, ink: Color): RaisedFill = RaisedFill(
        top = lerp(color, Color.White, if (isDark) TINT_LIFT_DARK else TINT_LIFT_LIGHT),
        bottom = color,
        ink = ink,
        highlight = Color.White.copy(alpha = if (isDark) HIGHLIGHT_TINT_DARK else HIGHLIGHT_TINT_LIGHT),
        glow = color,
    )
}

@Composable
@ReadOnlyComposable
fun elevationPalette(): ElevationPalette {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkScheme(scheme)
    val surface = scheme.surface
    return if (dark) {
        ElevationPalette(
            isDark = true,
            wellTop = lerp(surface, Color.Black, fraction = 0.36f),
            wellBottom = lerp(surface, Color.Black, fraction = 0.22f),
            wellBorder = lerp(surface, Color.Black, fraction = 0.42f),
            wellShadow = Color.Black.copy(alpha = 0.5f),
            // Lifted from the lightest container rather than the base surface: settings panels and
            // cards sit on the container shades, and a key lightened from `surface` came out level
            // with them.
            key = RaisedFill(
                top = lerp(scheme.surfaceContainerHighest, Color.White, fraction = 0.14f),
                bottom = lerp(scheme.surfaceContainerHighest, Color.White, fraction = 0.06f),
                ink = scheme.onSurface,
                highlight = Color.White.copy(alpha = 0.14f),
                glow = Color.Black,
            ),
            selected = RaisedFill(
                top = lerp(scheme.primary, Color.White, fraction = 0.3f),
                bottom = scheme.primary,
                ink = scheme.onPrimary,
                highlight = Color.White.copy(alpha = 0.45f),
                glow = scheme.primary,
            ),
            accent = RaisedFill(
                top = lerp(scheme.primary, Color.White, fraction = 0.3f),
                bottom = scheme.primary,
                ink = scheme.onPrimary,
                highlight = Color.White.copy(alpha = 0.5f),
                glow = scheme.primary,
            ),
            danger = RaisedFill(
                top = lerp(scheme.surfaceContainer, scheme.error, fraction = 0.16f),
                bottom = lerp(scheme.surfaceContainer, scheme.error, fraction = 0.10f),
                ink = lerp(scheme.error, Color.White, fraction = 0.45f),
                highlight = Color.White.copy(alpha = 0.07f),
                glow = scheme.error,
            ),
            keyEdge = Color.White.copy(alpha = 0.07f),
            dropShadow = Color.Black,
            disabledFill = lerp(surface, Color.White, fraction = 0.04f),
            disabledInk = scheme.onSurface.copy(alpha = 0.38f),
        )
    } else {
        ElevationPalette(
            isDark = false,
            wellTop = lerp(surface, Color.Black, fraction = 0.075f),
            wellBottom = lerp(surface, Color.Black, fraction = 0.045f),
            wellBorder = lerp(surface, Color.Black, fraction = 0.12f),
            wellShadow = Color.Black.copy(alpha = 0.12f),
            key = RaisedFill(
                top = Color.White,
                bottom = lerp(Color.White, surface, fraction = 0.5f),
                ink = scheme.onSurface,
                highlight = Color.White,
                glow = Color.Black,
            ),
            selected = RaisedFill(
                top = Color.White,
                bottom = lerp(Color.White, surface, fraction = 0.35f),
                ink = scheme.primary,
                highlight = Color.White,
                glow = Color.Black,
            ),
            accent = RaisedFill(
                top = lerp(scheme.primary, Color.White, fraction = 0.18f),
                bottom = scheme.primary,
                ink = scheme.onPrimary,
                highlight = Color.White.copy(alpha = 0.3f),
                glow = scheme.primary,
            ),
            danger = RaisedFill(
                top = lerp(Color.White, scheme.error, fraction = 0.04f),
                bottom = lerp(Color.White, scheme.error, fraction = 0.10f),
                ink = scheme.error,
                highlight = Color.White,
                glow = scheme.error,
            ),
            keyEdge = Color.Black.copy(alpha = 0.16f),
            dropShadow = Color.Black,
            disabledFill = lerp(surface, Color.Black, fraction = 0.05f),
            disabledInk = scheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

/**
 * A surface sunk into the page: a darker gradient, an inner shadow along the top, a 1dp [rim].
 * [fill], when given, is the well's color in place of the palette's; [rim] replaces its edge, which
 * is how a focused or failing field shows its state.
 */
fun Modifier.sunken(
    shape: Shape,
    palette: ElevationPalette,
    fill: Color = Color.Unspecified,
    rim: Color = Color.Unspecified,
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            if (fill.isSpecified) {
                listOf(lerp(fill, Color.Black, FILL_WELL_SHADE), fill)
            } else {
                listOf(palette.wellTop, palette.wellBottom)
            }
        )
    )
    .drawBehind {
        val depth = INNER_SHADOW_DEPTH.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(palette.wellShadow, Color.Transparent),
                endY = depth,
            ),
            size = Size(size.width, depth),
        )
    }
    .border(1.dp, if (rim.isSpecified) rim else palette.wellBorder, shape)

/**
 * A control raised off the page in [fill]: a top-lit gradient, a dark line along the bottom and a
 * drop shadow tinted with the fill's own color. [pressed] pushes it back in — no shadow, an inner
 * shade along the top instead.
 */
fun Modifier.raised(
    shape: Shape,
    fill: RaisedFill,
    palette: ElevationPalette,
    pressed: Boolean = false,
    hovered: Boolean = false,
    lift: Dp = RAISED_LIFT,
    /**
     * Whether the pointer moves it: a button rises a step under the pointer and sinks on a press,
     * the design's translateY(-1px) / (1px). Off for what is chosen rather than pressed -- a
     * segment, a tick, a card -- which only brightens.
     */
    moves: Boolean = true,
    /** A 2dp ring drawn over the edge -- keyboard focus, or a picker that is open or chosen. */
    ring: Color = Color.Unspecified,
): Modifier {
    val elevation = when {
        pressed -> 0.dp
        hovered -> lift + HOVER_EXTRA_LIFT
        else -> lift
    }
    val shadowTint = if (palette.isDark) fill.glow else fill.glow.copy(alpha = LIGHT_GLOW_ALPHA)
    // Hover lights the key a shade and deepens its shadow; a button also rises a step, and sinks on
    // a press. Drawn, not laid out: the shift never moves anything around it.
    val shift = when {
        !moves -> 0.dp
        pressed -> PRESS_SHIFT
        hovered -> -PRESS_SHIFT
        else -> 0.dp
    }
    val top = if (hovered && !pressed) lerp(fill.top, Color.White, fraction = HOVER_BRIGHTEN) else fill.top
    val bottom = if (hovered && !pressed) lerp(fill.bottom, Color.White, fraction = HOVER_BRIGHTEN) else fill.bottom
    return this
        .graphicsLayer { translationY = shift.toPx() }
        .shadow(elevation, shape, clip = false, ambientColor = shadowTint, spotColor = shadowTint)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .drawBehind {
            val line = 1.dp.toPx()
            if (pressed) {
                val depth = INNER_SHADOW_DEPTH.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(palette.wellShadow, Color.Transparent),
                        endY = depth,
                    ),
                    size = Size(size.width, depth),
                )
            } else {
                // No shine along the top edge: on the dark keys it read as a hard line rather than
                // a highlight. The top-lit gradient and the drop shadow carry the lift.
                drawLine(
                    Color.Black.copy(alpha = BOTTOM_EDGE_ALPHA),
                    Offset(0f, size.height - line / 2),
                    Offset(size.width, size.height - line / 2),
                    line,
                )
            }
        }
        .then(
            if (palette.keyEdge.alpha > 0f) Modifier.border(HAIRLINE, palette.keyEdge, shape) else Modifier
        )
        .then(if (ring.isSpecified) Modifier.border(RING_WIDTH, ring, shape) else Modifier)
}

/** A control that cannot be used: the neutral key, flat and at 40% -- the whole control, label too. */
fun Modifier.flatDisabled(shape: Shape, palette: ElevationPalette): Modifier = this
    .alpha(DISABLED_OPACITY)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(palette.key.top, palette.key.bottom)))

private val INNER_SHADOW_DEPTH = 4.dp
private val RAISED_LIFT = 3.dp
private val HOVER_EXTRA_LIFT = 3.dp
private const val HOVER_BRIGHTEN = 0.08f
private const val DISABLED_OPACITY = 0.4f
private val RING_WIDTH = 2.dp
/** How strong a focus or open ring is drawn. */
const val RING_ALPHA = 0.55f
private val PRESS_SHIFT = 1.dp
private val HAIRLINE = 0.5.dp
private const val FILL_WELL_SHADE = 0.08f
private const val BOTTOM_EDGE_ALPHA = 0.14f
private const val LIGHT_GLOW_ALPHA = 0.35f
private const val TINT_LIFT_DARK = 0.22f
private const val TINT_LIFT_LIGHT = 0.16f
private const val HIGHLIGHT_TINT_DARK = 0.4f
private const val HIGHLIGHT_TINT_LIGHT = 0.3f

/**
 * A half-strength accent rim while the pointer is over the control -- how a sunken field that opens
 * something (a dropdown) says it can be clicked. Its own hover tracking, so a caller adds only this.
 */
fun Modifier.hoverOutline(shape: Shape): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val rim = MaterialTheme.colorScheme.primary.copy(alpha = HOVER_RIM_ALPHA)
    this
        .hoverable(interaction)
        .then(if (hovered) Modifier.border(1.dp, rim, shape) else Modifier)
}

private const val HOVER_RIM_ALPHA = 0.55f

/**
 * [raised], tracking the pointer itself: for a hand-built button whose click handler does not share
 * an interaction source, so it still lifts under the pointer like every other button.
 */
fun Modifier.raisedHover(
    shape: Shape,
    fill: RaisedFill,
    palette: ElevationPalette,
    pressed: Boolean = false,
    lift: Dp = RAISED_LIFT,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    this
        .hoverable(interaction)
        .raised(shape, fill, palette, pressed = pressed, hovered = hovered, lift = lift)
}

/**
 * A dropdown's closed field: the same sunken well as the text fields, a half-strength accent rim
 * under the pointer and the full accent rim while its menu is [open].
 */
fun Modifier.dropdownField(shape: Shape, open: Boolean = false): Modifier = composed {
    val palette = elevationPalette()
    this
        .sunken(shape, palette, rim = if (open) MaterialTheme.colorScheme.primary else Color.Unspecified)
        .hoverOutline(shape)
}
