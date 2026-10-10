package org.churchpresenter.profiles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants
import java.io.File
import kotlin.math.ceil

/**
 * Draws a [BackgroundConfig] as far as a still tile can — the color, the gradient or the picture,
 * faded, dimmed and blurred the way the presenter draws it so a preview and the screen agree.
 *
 * Shared by the Settings → Background surface rail, its stage preview and the Profiles tab's
 * preview, so a background looks the same wherever it is shown.
 *
 * A picture is decoded at the size the tile is drawn at, never whole ([PreviewStills]). A clip and a
 * camera draw a glyph on black unless [stills] is set, when they draw a still of themselves instead:
 * the clip's first frame and one snapshot from the camera. Neither ever plays -- spinning up VLC or
 * holding a capture device open for a preview is not worth what it costs -- and the stills are
 * opt-in because a 14dp chip on the Background tab's rail is too small to show one and has no
 * business opening a camera to find out.
 *
 * [blurRadius] is the blur in *this tile's* space — the config's own blur is measured against a
 * 1920×1080 output, so a caller drawing a small tile scales it down rather than passing it through.
 */
@Composable
internal fun BackgroundConfigFill(
    config: BackgroundConfig,
    modifier: Modifier,
    blurRadius: Dp = 0.dp,
    stills: Boolean = false,
) {
    // The caller's modifier sizes and places the tile; the blur and the fade belong to what is
    // drawn inside it, so a blurred picture's overscan stays within the tile's own bounds.
    val blurred = if (blurRadius > 0.dp) {
        Modifier.graphicsLayer { scaleX = BLUR_OVERSCAN; scaleY = BLUR_OVERSCAN }.blur(blurRadius)
    } else {
        Modifier
    }
    val shaped =
        if (config.backgroundOpacity < 1f) blurred.alpha(config.backgroundOpacity) else blurred
    Box(modifier = modifier) {
        when (config.backgroundType) {
            Constants.BACKGROUND_IMAGE -> StillFill(
                shaped, Icons.Default.Movie, glyph = false, key = config.backgroundImage,
            ) { w, h ->
                PreviewStills.picture(File(config.backgroundImage), w, h)
            }
            Constants.BACKGROUND_VIDEO -> if (stills) {
                StillFill(shaped, Icons.Default.Movie, glyph = true, key = config.backgroundVideo) { w, h ->
                    PreviewStills.videoFrame(File(config.backgroundVideo), w, h)
                }
            } else {
                GlyphFill(shaped, Icons.Default.Movie)
            }
            Constants.BACKGROUND_CAMERA -> if (stills && config.camera.isSet) {
                StillFill(shaped, Icons.Default.Videocam, glyph = true, key = config.camera) { _, _ ->
                    PreviewStills.cameraSnapshot(config.camera)
                }
            } else {
                // Black with a glyph, like a clip: opening a capture device to draw a 14dp chip would
                // hold the camera for as long as the settings tab is on screen.
                GlyphFill(shaped, Icons.Default.Videocam)
            }
            Constants.BACKGROUND_GRADIENT -> Box(
                shaped.fillMaxSize().background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to parseHexColor(config.gradientTopColor).copy(alpha = config.gradientTopOpacity),
                            config.gradientPosition.coerceIn(0f, 1f) to
                                parseHexColor(config.gradientBottomColor).copy(alpha = config.gradientBottomOpacity),
                            1f to parseHexColor(config.gradientBottomColor).copy(alpha = config.gradientBottomOpacity),
                        ),
                    ),
                ),
            )
            Constants.BACKGROUND_TRANSPARENT -> CheckerboardFill(shaped.fillMaxSize())
            else -> Box(shaped.fillMaxSize().background(parseHexColor(config.backgroundColor)))
        }
        // The dim wash the presenter lays over every drawn background, never over a transparent one.
        if (config.dim > 0 && config.backgroundType != Constants.BACKGROUND_TRANSPARENT) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = config.dim / PERCENT)))
        }
    }
}

/**
 * A still loaded off the UI thread at the tile's own pixel size, black until it arrives -- with
 * [icon] over the black when [glyph] is set, so a clip or camera that yields no still still says
 * what it is.
 *
 * The size is rounded up to a step so that a column being dragged wider does not decode the
 * picture again at every pixel.
 */
@Composable
private fun StillFill(
    modifier: Modifier,
    icon: ImageVector,
    glyph: Boolean,
    /** What the still is of; a new one is loaded when it changes. */
    key: Any,
    load: suspend (width: Int, height: Int) -> ImageBitmap?,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { stepUp(maxWidth.toPx()) }
        val height = with(density) { stepUp(maxHeight.toPx()) }
        var bitmap by remember(key, width, height) { mutableStateOf<ImageBitmap?>(null) }
        // Keyed on what the still is of and its size, never on [load]: that is a fresh lambda on
        // every recomposition, and keying on it would decode the picture again each time.
        val currentLoad by rememberUpdatedState(load)
        LaunchedEffect(key, width, height) {
            bitmap = withContext(Dispatchers.IO) { currentLoad(width, height) }
        }
        val shot = bitmap
        when {
            shot != null -> Image(shot, null, contentScale = ContentScale.Crop, modifier = modifier.fillMaxSize())
            glyph -> GlyphFill(modifier, icon)
            else -> Box(modifier.fillMaxSize().background(Color.Black))
        }
    }
}

/** Black with [icon] in the middle: a background the tile deliberately does not draw. */
@Composable
private fun GlyphFill(modifier: Modifier, icon: ImageVector) {
    Box(modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(GLYPH_SIZE),
            tint = Color.White.copy(alpha = VIDEO_GLYPH_ALPHA),
        )
    }
}

/**
 * The usual checkerboard for "nothing here" — a transparent background has no color to draw.
 *
 * [square] is the side of one square: the Background tab's chips use a fine one, and the Profiles
 * preview a coarser one that still reads as a checkerboard at a few hundred dp.
 */
@Composable
internal fun CheckerboardFill(
    modifier: Modifier,
    square: Dp = CHECKER_SQUARE,
    /** The two squares' colours; the theme's surfaces when not given. */
    colors: Pair<Color, Color>? = null,
) {
    val light = colors?.first ?: MaterialTheme.colorScheme.surfaceContainer
    val dark = colors?.second ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val density = LocalDensity.current
    val step = with(density) { square.toPx() }
    Canvas(modifier) {
        drawRect(light)
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else step
            while (x < size.width) {
                drawRect(
                    color = dark,
                    topLeft = Offset(x, y),
                    size = Size(minOf(step, size.width - x), minOf(step, size.height - y)),
                )
                x += step * 2
            }
            y += step
            row++
        }
    }
}

/** [px] rounded up to the next [DECODE_STEP_PX], and never below one step. */
private fun stepUp(px: Float): Int =
    (ceil(px / DECODE_STEP_PX).toInt().coerceAtLeast(1)) * DECODE_STEP_PX

/** The presenter overscans a blurred background by the same amount; a preview must match. */
private const val BLUR_OVERSCAN = 1.08f

/** The glyph that stands in for a picture the tile deliberately does not draw. */
private val GLYPH_SIZE = 14.dp

private const val VIDEO_GLYPH_ALPHA = 0.85f
private val CHECKER_SQUARE = 5.dp
private const val DECODE_STEP_PX = 160
private const val PERCENT = 100f
