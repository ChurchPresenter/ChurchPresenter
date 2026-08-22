package org.churchpresenter.lottiegen.ui

import org.churchpresenter.lottiegen.lottie.PERCENT_SCALE
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.lottiegen.ui.components.LottieSlider

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
private fun PlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) Tokens.AccentHover else Tokens.Accent, label = "playBtn")

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg)
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Tokens.OnAccent,
            modifier = Modifier.size(17.dp)
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
) {
    var isPlaying by remember { mutableStateOf(true) }
    var seekValue by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier.fillMaxSize().background(Tokens.PreviewBg)
    ) {
        // ── Header ──
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
            // Live badge: elapsed / total, driven by the scrub position.
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
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.PreviewDivider))

        // ── Canvas ──
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(26.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(aspectRatio)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Tokens.CardBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                CheckerBoard(Modifier.fillMaxSize())

                if (jsonString != null) {
                    val composition by rememberLottieComposition(key = jsonString) {
                        LottieCompositionSpec.JsonString(jsonString)
                    }
                    val progress by animateLottieCompositionAsState(
                        composition = composition,
                        isPlaying = isPlaying,
                        iterations = Int.MAX_VALUE
                    )

                    LaunchedEffect(progress) {
                        if (isPlaying) seekValue = progress
                    }

                    composition?.let {
                        Image(
                            painter = rememberLottiePainter(
                                composition = it,
                                progress = { if (isPlaying) progress else seekValue }
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Text(Strings.generating, fontSize = 13.sp, color = Tokens.UnitText)
                }
            }
        }

        // ── Status ──
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

        // ── Transport ──
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.PreviewDivider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 13.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PlayButton(isPlaying) { isPlaying = !isPlaying }
            LottieSlider(
                value = seekValue,
                onValueChange = { seekValue = it; isPlaying = false },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                trackHeight = 6.dp,
                knobSize = 15.dp,
                trackColor = Tokens.TransportTrack
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
}
