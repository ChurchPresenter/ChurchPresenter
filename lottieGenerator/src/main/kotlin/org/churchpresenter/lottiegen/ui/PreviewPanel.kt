package org.churchpresenter.lottiegen.ui

import org.churchpresenter.lottiegen.lottie.PERCENT_SCALE
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.lottiegen.ui.components.LottieSlider
import org.churchpresenter.lottiegen.ui.components.raisedKey
import org.churchpresenter.theme.elevationPalette

/** Dotted outlines over the composition: where the host's text will go, so the margins can be judged. */
@Composable
private fun GuideOverlay(guides: List<PreviewGuide>, modifier: Modifier = Modifier) {
    val color = Tokens.Accent
    Canvas(modifier = modifier) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(GUIDE_DASH_PX.dp.toPx(), GUIDE_GAP_PX.dp.toPx()))
        val stroke = Stroke(width = GUIDE_STROKE_PX.dp.toPx(), pathEffect = dash)
        guides.forEach { g ->
            drawRect(
                color = color,
                topLeft = Offset(g.left * size.width, g.top * size.height),
                size = Size(g.width * size.width, g.height * size.height),
                style = stroke,
            )
        }
    }
}

private const val GUIDE_DASH_PX = 4f
private const val GUIDE_GAP_PX = 3f
private const val GUIDE_STROKE_PX = 1f

/** The transparency checkerboard behind the composition. */
@Composable
private fun CheckerBoard(modifier: Modifier = Modifier) {
    // Read in composable scope: the draw block below is not composable, so it cannot resolve the
    // ambient palette itself.
    val background = Tokens.CanvasBg
    val checker = Tokens.CanvasChecker
    Canvas(modifier = modifier) {
        val cell = 11.dp.toPx()
        drawRect(background)
        var row = 0
        var y = 0f
        while (y < size.height) {
            var col = 0
            var x = 0f
            while (x < size.width) {
                if ((row + col) % 2 == 0) {
                    drawRect(
                        color = checker,
                        topLeft = Offset(x, y),
                        size = Size(
                            minOf(cell, size.width - x),
                            minOf(cell, size.height - y)
                        )
                    )
                }
                x += cell; col++
            }
            y += cell; row++
        }
    }
}

/** Round accent play/pause button on the transport. */
@Composable
internal fun PlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    size: Dp = 36.dp,
    iconSize: Dp = 17.dp,
) {
    val fill = elevationPalette().accent
    Box(
        modifier = Modifier
            .size(size)
            .raisedKey(CircleShape, fill, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = fill.ink,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun PreviewPanel(
    jsonString: String?,
    aspectRatio: Float,
    statusText: String,
    canvasW: Int = 0,
    canvasH: Int = 0,
    durationSeconds: Float = 0f,
    /** The canvas card's corner. A full-band template wants 0 so its own corners are what shows. */
    canvasCornerRadius: Dp = PREVIEW_CANVAS_RADIUS,
    /** Regions to outline over the composition; none by default. */
    guides: List<PreviewGuide> = emptyList(),
    /** The stretch of the timeline, as fractions, the outlines are shown for. */
    guideWindow: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    var isPlaying by remember { mutableStateOf(true) }
    var seekValue by remember { mutableStateOf(0f) }

    Column(modifier = Modifier.fillMaxSize().background(Tokens.PreviewBg)) {
        PreviewHeader(canvasW, canvasH, durationSeconds, seekValue)
        Divider()
        PreviewCanvas(
            jsonString = jsonString,
            aspectRatio = aspectRatio,
            isPlaying = isPlaying,
            seekValue = seekValue,
            onProgress = { seekValue = it },
            cornerRadius = canvasCornerRadius,
            guides = guides,
            guideWindow = guideWindow,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(26.dp),
        )
        if (statusText.isNotEmpty()) {
            Text(
                statusText,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                fontSize = 11.5.sp,
                color = Tokens.DimText,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
        Divider()
        TransportBar(
            isPlaying = isPlaying,
            seekValue = seekValue,
            onPlayPause = { isPlaying = !isPlaying },
            onSeek = { seekValue = it; isPlaying = false },
        )
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.PreviewDivider))
}

/** The caption, the canvas size, and a live elapsed/total badge driven by the scrub position. */
@Composable
private fun PreviewHeader(canvasW: Int, canvasH: Int, durationSeconds: Float, seekValue: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Tokens.HeaderHeight)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            Strings.previewLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.99.sp,
            color = Tokens.HintText
        )
        if (canvasW > 0 && canvasH > 0) {
            Text("$canvasW × $canvasH", fontSize = 11.5.sp, color = Tokens.DimText, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (durationSeconds > 0f) {
            Row(
                modifier = Modifier
                    .clip(Tokens.ChipShape)
                    .background(Tokens.BadgeBg)
                    .border(1.dp, Tokens.BadgeBorder, Tokens.ChipShape)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Tokens.LiveDot))
                Text(
                    "%.1fs / %.1fs".format(seekValue * durationSeconds, durationSeconds),
                    fontSize = 11.sp,
                    color = Tokens.ValueText,
                    maxLines = 1
                )
            }
        }
    }
}

/** The checkerboard, and the composition drawn on it once one has been generated. */
@Composable
private fun PreviewCanvas(
    jsonString: String?,
    aspectRatio: Float,
    isPlaying: Boolean,
    seekValue: Float,
    onProgress: (Float) -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = PREVIEW_CANVAS_RADIUS,
    guides: List<PreviewGuide> = emptyList(),
    guideWindow: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .aspectRatio(aspectRatio)
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
                .border(1.dp, Tokens.CardBorder, RoundedCornerShape(cornerRadius)),
            contentAlignment = Alignment.Center
        ) {
            CheckerBoard(Modifier.fillMaxSize())
            if (jsonString == null) {
                Text(Strings.generating, fontSize = 13.sp, color = Tokens.UnitText)
                return@Box
            }
            val composition by rememberLottieComposition(key = jsonString) {
                LottieCompositionSpec.JsonString(jsonString)
            }
            val progress by animateLottieCompositionAsState(
                composition = composition,
                isPlaying = isPlaying,
                iterations = Int.MAX_VALUE
            )
            LaunchedEffect(progress) {
                if (isPlaying) onProgress(progress)
            }
            val shown = if (isPlaying) progress else seekValue
            composition?.let {
                Image(
                    painter = rememberLottiePainter(
                        composition = it,
                        progress = { shown }
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // The box is the canvas's own aspect, so a fraction of it is a fraction of the canvas.
            // The outlines come and go with the text they stand for.
            if (guides.isNotEmpty() && shown in guideWindow) GuideOverlay(guides, Modifier.fillMaxSize())
        }
    }
}

/** Play/pause, the scrub bar, and the position as a percentage. */
@Composable
private fun TransportBar(
    isPlaying: Boolean,
    seekValue: Float,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 13.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PlayButton(isPlaying, onPlayPause)
        LottieSlider(
            value = seekValue,
            onValueChange = onSeek,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
            trackHeight = 6.dp,
            knobSize = 15.dp
        )
        Text(
            "%.0f%%".format(seekValue * PERCENT_SCALE),
            modifier = Modifier.widthIn(min = 42.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Tokens.HexText,
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

private val PREVIEW_CANVAS_RADIUS = 12.dp
