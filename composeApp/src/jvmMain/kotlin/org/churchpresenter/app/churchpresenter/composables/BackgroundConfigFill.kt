package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.dialogs.DownloadedEntry
import org.churchpresenter.app.churchpresenter.dialogs.loadThumbnailBitmap
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants
import java.io.File

/**
 * Draws a [BackgroundConfig] as far as a still tile can — the color, the gradient or the picture,
 * faded and blurred the way the presenter draws it so a preview and the screen agree.
 *
 * Shared by the Settings → Background surface rail and its stage preview, so a background looks the
 * same wherever it is shown. A clip shows as black: spinning up VLC for a thumbnail is not worth
 * what it costs.
 *
 * [blurRadius] is the blur in *this tile's* space — the config's own blur is measured against a
 * 1920×1080 output, so a caller drawing a small tile scales it down rather than passing it through.
 */
@Composable
internal fun BackgroundConfigFill(
    config: BackgroundConfig,
    modifier: Modifier,
    blurRadius: Dp = 0.dp,
) {
    val blurred = if (blurRadius > 0.dp) {
        modifier.graphicsLayer { scaleX = BLUR_OVERSCAN; scaleY = BLUR_OVERSCAN }.blur(blurRadius)
    } else {
        modifier
    }
    val shaped =
        if (config.backgroundOpacity < 1f) blurred.alpha(config.backgroundOpacity) else blurred
    when (config.backgroundType) {
        Constants.BACKGROUND_IMAGE -> {
            var bitmap by remember(config.backgroundImage) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(config.backgroundImage) {
                bitmap = withContext(Dispatchers.IO) {
                    loadThumbnailBitmap(DownloadedEntry(File(config.backgroundImage)))
                }
            }
            val shot = bitmap
            if (shot == null) Box(shaped.background(Color.Black))
            else Image(shot, null, contentScale = ContentScale.Crop, modifier = shaped)
        }
        Constants.BACKGROUND_VIDEO -> Box(shaped.background(Color.Black), Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                modifier = Modifier.size(GLYPH_SIZE),
                tint = Color.White.copy(alpha = VIDEO_GLYPH_ALPHA),
            )
        }
        // Black with a glyph, like a clip: opening a capture device to draw a 14dp chip would hold
        // the camera for as long as the settings tab is on screen, for a picture nobody can read.
        Constants.BACKGROUND_CAMERA -> Box(shaped.background(Color.Black), Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                modifier = Modifier.size(GLYPH_SIZE),
                tint = Color.White.copy(alpha = VIDEO_GLYPH_ALPHA),
            )
        }
        Constants.BACKGROUND_GRADIENT -> Box(
            shaped.background(
                Brush.verticalGradient(
                    listOf(
                        parseHexColor(config.gradientTopColor).copy(alpha = config.gradientTopOpacity),
                        parseHexColor(config.gradientBottomColor).copy(alpha = config.gradientBottomOpacity),
                    ),
                ),
            ),
        )
        Constants.BACKGROUND_TRANSPARENT -> CheckerboardFill(shaped)
        else -> Box(shaped.background(parseHexColor(config.backgroundColor)))
    }
}

/** The usual checkerboard for "nothing here" — a transparent background has no color to draw. */
@Composable
private fun CheckerboardFill(modifier: Modifier) {
    val light = MaterialTheme.colorScheme.surfaceContainer
    val dark = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier) {
        drawRect(light)
        val step = CHECKER_SQUARE_PX
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

/** The presenter overscans a blurred background by the same amount; a preview must match. */
private const val BLUR_OVERSCAN = 1.08f
/** The glyph that stands in for a picture the tile deliberately does not draw. */
private val GLYPH_SIZE = 14.dp

private const val VIDEO_GLYPH_ALPHA = 0.85f
private const val CHECKER_SQUARE_PX = 5f
