package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.settings.PropCorner
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.settings.PropKind
import org.churchpresenter.slides.utils.PictureDecoder
import java.io.File
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The props that are up, each in its corner of the output -- several in one corner stack down from
 * it -- at its own height in percent of the output's (`docs/SHOW_CONTROL.md`, Props).
 */
@Composable
internal fun PropsCue(cue: Cue.Props, surface: OutputSurface) {
    val definitions = surface.appSettings.props.filter { it.id in cue.on }
    if (definitions.isEmpty()) return
    val clockFormat = surface.appSettings.announcementsSettings.liveClockFormat
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val outputHeight = maxHeight
        val margin = outputHeight * PROP_MARGIN_FRACTION
        definitions.groupBy { it.corner }.forEach { (corner, inCorner) ->
            Column(
                modifier = Modifier.align(corner.alignment).padding(margin),
                verticalArrangement = Arrangement.spacedBy(margin / 2),
                horizontalAlignment = corner.horizontal,
            ) {
                inCorner.forEach { prop ->
                    // Keyed, so a prop keeps its own state when one above it in the corner comes down.
                    key(prop.id) {
                        Prop(prop, outputHeight * prop.sizePercent.coerceIn(1, MAX_PROP_PERCENT) / PERCENT, clockFormat)
                    }
                }
            }
        }
    }
}

@Composable
private fun Prop(prop: PropDefinition, height: Dp, clockFormat: String) {
    when (prop.kind) {
        PropKind.IMAGE -> PropImage(prop.imagePath, height)
        PropKind.CLOCK -> PropText(ticking { clockText(it, clockFormat) }, height)
        PropKind.COUNTDOWN -> PropText(ticking { now -> countdownText(now, prop.countdownTo) }, height)
        PropKind.BADGE -> PropText(prop.text, height, plate = BADGE_COLOR)
    }
}

/** [text] at [height], white with a shadow so it reads over any content -- on a plate, if given one. */
@Composable
private fun PropText(text: String, height: Dp, plate: Color? = null) {
    if (text.isEmpty()) return
    val fontSize = with(LocalDensity.current) { (height * TEXT_FRACTION).toSp() }
    val style = TextStyle(
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        shadow = if (plate == null) Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 6f) else null,
    )
    val shape = RoundedCornerShape(height * PLATE_CORNER_FRACTION)
    Box(
        modifier = Modifier.height(height)
            .then(if (plate != null) Modifier.background(plate, shape).padding(horizontal = height / 3) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, maxLines = 1)
    }
}

/** A picture prop, decoded off the composition thread and fitted to [height]. */
@Composable
private fun PropImage(path: String, height: Dp) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    val maxPixels = with(LocalDensity.current) { (height * 4).roundToPx() }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            File(path).takeIf { path.isNotEmpty() && it.exists() }
                ?.let { PictureDecoder.decodeScaledOrNull(it, maxPixels * IMAGE_ASPECT_LIMIT, maxPixels) }
                ?.toComposeImageBitmap()
        }
    }
    bitmap?.let {
        Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.height(height))
    }
}

/** [format] of the time, redone every second -- with the latest [format], so an edited prop shows at once. */
@Composable
private fun ticking(format: (LocalTime) -> String): String {
    val latest by rememberUpdatedState(format)
    val text by produceState(format(LocalTime.now())) {
        while (true) {
            value = latest(LocalTime.now())
            delay(TICK_MILLIS)
        }
    }
    return text
}

/** The time of day in [pattern], or as hours and minutes when the pattern is no pattern at all. */
internal fun clockText(now: LocalTime, pattern: String): String =
    runCatching { now.format(DateTimeFormatter.ofPattern(pattern)) }
        .getOrElse { now.format(DateTimeFormatter.ofPattern("HH:mm")) }

/**
 * What is left from [now] until [target], "HH:mm": "m:ss" under an hour and "h:mm:ss" over it, and
 * "0:00" once it has passed or when [target] is no time at all.
 */
internal fun countdownText(now: LocalTime, target: String): String {
    val until = runCatching { LocalTime.parse(target.trim()) }.getOrNull() ?: return "0:00"
    val left = Duration.between(now, until).seconds.coerceAtLeast(0)
    val hours = left / SECONDS_PER_HOUR
    val minutes = left % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = left % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private val PropCorner.alignment: Alignment
    get() = when (this) {
        PropCorner.TOP_LEFT -> Alignment.TopStart
        PropCorner.TOP_RIGHT -> Alignment.TopEnd
        PropCorner.BOTTOM_LEFT -> Alignment.BottomStart
        PropCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
    }

private val PropCorner.horizontal: Alignment.Horizontal
    get() = if (this == PropCorner.TOP_LEFT || this == PropCorner.BOTTOM_LEFT) Alignment.Start else Alignment.End

private const val PROP_MARGIN_FRACTION = 0.03f
private const val MAX_PROP_PERCENT = 50
private const val PERCENT = 100f
private const val TEXT_FRACTION = 0.6f
private const val PLATE_CORNER_FRACTION = 0.2f
private const val IMAGE_ASPECT_LIMIT = 4
private const val TICK_MILLIS = 1000L
private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_MINUTE = 60L
private val BADGE_COLOR = Color(0xFFD32F2F)
