package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val LIGHT_THEME_BEZEL_COLOR = 0xFF2B2B2B
private const val DARK_THEME_BEZEL_COLOR = 0xFF585858
private const val DEFAULT_SCREEN_COLOR = 0xFF1A1A1A

/** Below this, the surface behind the mockup counts as a dark one. */
private const val DARK_SURFACE_LUMINANCE = 0.5f

/** Gap between the bezel's outer edge and the screen, on every side. */
private val BEZEL_PADDING = 6.dp
private val BEZEL_RADIUS = 10.dp
private val SCREEN_RADIUS = 4.dp
private val NECK_WIDTH = 16.dp
private val NECK_HEIGHT = 10.dp
private val BASE_WIDTH = 56.dp
private val BASE_HEIGHT = 6.dp
private val BASE_RADIUS = 3.dp

/**
 * How much wider the whole mockup is than the screen inside it: [BEZEL_PADDING] on each side.
 *
 * Public because a caller sizing the mockup from a desired *screen* shape has to add it back --
 * see [tvScreenBoxWidthFor].
 */
val TvScreenBoxHorizontalChrome: Dp = BEZEL_PADDING * 2

/** How much taller the whole mockup is than the screen: bezel padding top and bottom, plus the stand. */
val TvScreenBoxVerticalChrome: Dp = BEZEL_PADDING * 2 + NECK_HEIGHT + BASE_HEIGHT

/**
 * The widest [TvScreenBox] whose *screen* is [screenAspectRatio] and which still fits in
 * [totalHeight] overall.
 *
 * The inverse of what `screenAspectRatio` does inside the composable, for the callers that have to
 * fit the mockup into a bounded column or cap how tall a portrait output is allowed to make it.
 * Doing that arithmetic at the call site is what put the band in the wrong place in the background
 * preview: the chrome is 28dp of height that is not screen, and it is easy to forget.
 */
fun tvScreenBoxWidthFor(totalHeight: Dp, screenAspectRatio: Float): Dp =
    (totalHeight - TvScreenBoxVerticalChrome).coerceAtLeast(0.dp) * screenAspectRatio +
        TvScreenBoxHorizontalChrome

/**
 * The bezel colour, light enough to read against the surface behind it.
 *
 * One fixed dark grey worked on the light themes and disappeared on the dark ones, where the panel
 * it sits on is nearly as dark as the bezel was: the mock TV blended into the background and read as
 * a flat rectangle with no screen in it.
 */
@Composable
private fun defaultBezelColor(): Color = Color(
    if (MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE) DARK_THEME_BEZEL_COLOR
    else LIGHT_THEME_BEZEL_COLOR
)

/**
 * A TV/monitor-styled mockup of the output screen: a bezel, an inset screen area for [content],
 * and a small stand underneath. Used by settings previews that show where on the output screen
 * something (a lower third, margins, etc.) is positioned, in place of a plain rectangle.
 *
 * [screenAspectRatio] is the shape of the **screen**, with the bezel and the stand excluded -- the
 * ratio lands on the screen node itself, so it is exact by construction and no caller has to
 * subtract the chrome. Pass the real output's ratio rather than a 16:9 guess; a mockup that is not
 * the shape of the screen it stands for misplaces everything drawn inside it.
 *
 * **With a ratio set, give this a width and no height.** The bezel and the outer column then
 * wrap their content vertically, so a fixed height either leaves dead space under the stand or
 * fights the ratio. Left null, the screen fills whatever height the caller imposes, as before.
 *
 * [modifier] always lands on the outermost node, so a RowScope/ColumnScope `weight` still works.
 */
@Composable
fun TvScreenBox(
    modifier: Modifier = Modifier,
    screenAspectRatio: Float? = null,
    bezelColor: Color = defaultBezelColor(),
    screenColor: Color = Color(DEFAULT_SCREEN_COLOR),
    content: @Composable BoxScope.() -> Unit = {}
) {
    // `modifier` (which may carry a RowScope/ColumnScope weight from the caller) must land on this
    // outermost node — a weight buried on an inner child has no effect on the caller's layout.
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Weight only in the fill-the-height mode: with a ratio the bezel takes its height
                // from the screen inside it, so the column has to wrap rather than divide.
                .then(if (screenAspectRatio == null) Modifier.weight(1f) else Modifier)
                .background(bezelColor, RoundedCornerShape(BEZEL_RADIUS))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(BEZEL_RADIUS))
                .padding(BEZEL_PADDING)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (screenAspectRatio == null) Modifier.fillMaxHeight()
                        else Modifier.aspectRatio(screenAspectRatio)
                    )
                    .background(screenColor, RoundedCornerShape(SCREEN_RADIUS))
                    .border(1.dp, Color.Black.copy(alpha = 0.4f), RoundedCornerShape(SCREEN_RADIUS)),
                contentAlignment = Alignment.Center,
                content = content
            )
        }
        Box(
            modifier = Modifier
                .width(NECK_WIDTH)
                .height(NECK_HEIGHT)
                .background(bezelColor)
        )
        Box(
            modifier = Modifier
                .width(BASE_WIDTH)
                .height(BASE_HEIGHT)
                .background(bezelColor, RoundedCornerShape(BASE_RADIUS))
        )
    }
}
