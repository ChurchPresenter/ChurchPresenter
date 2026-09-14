package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.ui.PreviewGuide
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.Tokens
import org.churchpresenter.lottiegen.ui.components.LottieSlider

private val STAGE_MAX_WIDTH = 880.dp
private const val GUIDE_DASH = 4f
private const val GUIDE_GAP = 3f
private const val GUIDE_ALPHA = 0.55f
private const val PAUSE_DESCRIPTION = "Pause"
private const val PLAY_DESCRIPTION = "Play"

/**
 * The band generator's right pane: the composition on a plain stage at the band's own aspect,
 * a clock over it, and under it a scrubber marked where each phase begins.
 */
@Composable
internal fun BandPreviewPanel(viewModel: BibleLottieGenViewModel) {
    var isPlaying by remember { mutableStateOf(true) }
    var seekValue by remember { mutableStateOf(0f) }
    val cfg = viewModel.config
    val total = viewModel.timeline.totalSeconds
    Column(modifier = Modifier.fillMaxSize().background(Tokens.PreviewBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Caption(Strings.previewLabel)
            Text("${cfg.canvasW} × ${cfg.canvasH}", fontSize = 11.sp, color = Tokens.DimText, maxLines = 1)
            Box(Modifier.weight(1f))
            Text(
                "%.1fs / %.1fs".format(seekValue * total, total),
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Tokens.Accent, maxLines = 1,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.PreviewDivider))
        Box(Modifier.fillMaxWidth().weight(1f).padding(18.dp), contentAlignment = Alignment.Center) {
            Stage(
                viewModel = viewModel,
                aspectRatio = cfg.canvasW.toFloat() / cfg.canvasH.toFloat(),
                isPlaying = isPlaying,
                seekValue = seekValue,
                onProgress = { seekValue = it },
                guides = viewModel.slotGuides,
                guideWindow = viewModel.textWindow,
            )
        }
        if (viewModel.statusText.isNotEmpty()) {
            Text(
                viewModel.statusText,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                fontSize = 11.5.sp, color = Tokens.DimText, textAlign = TextAlign.Center, maxLines = 2,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.PreviewDivider))
        Transport(
            viewModel = viewModel,
            isPlaying = isPlaying,
            seekValue = seekValue,
            onPlayPause = { isPlaying = !isPlaying },
            onSeek = { seekValue = it; isPlaying = false },
        )
    }
}

/** The composition, drawn to fit the band's aspect, with the slot outlines over it while the text is up. */
@Composable
private fun Stage(
    viewModel: BibleLottieGenViewModel,
    aspectRatio: Float,
    isPlaying: Boolean,
    seekValue: Float,
    onProgress: (Float) -> Unit,
    guides: List<PreviewGuide>,
    guideWindow: ClosedFloatingPointRange<Float>,
) {
    // Clipped: a band sliding in from off-canvas is drawn past the stage otherwise, over the panel.
    Box(
        Modifier.widthIn(max = STAGE_MAX_WIDTH).fillMaxWidth().aspectRatio(aspectRatio).clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val jsonString = viewModel.generatedJson
        if (jsonString == null) {
            Text(Strings.generating, fontSize = 13.sp, color = Tokens.UnitText)
            return@Box
        }
        val composition by rememberLottieComposition(key = jsonString) { LottieCompositionSpec.JsonString(jsonString) }
        // The typewriter and the ticker are the player's to drive; the preview drives them the same way.
        val textMotion = rememberPreviewTextMotion(viewModel.config, viewModel.timeline, jsonString)
        val progress by animateLottieCompositionAsState(
            composition = composition, isPlaying = isPlaying, iterations = Int.MAX_VALUE,
        )
        LaunchedEffect(progress) { if (isPlaying) onProgress(progress) }
        val shown = if (isPlaying) progress else seekValue
        composition?.let {
            Image(
                painter = rememberLottiePainter(composition = it, progress = { shown }, dynamicProperties = textMotion),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // The box is the canvas's own aspect, so a fraction of it is a fraction of the canvas;
        // the outlines come and go with the text they stand for.
        if (guides.isNotEmpty() && shown in guideWindow) {
            val color = Tokens.Accent.copy(alpha = GUIDE_ALPHA)
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(GUIDE_DASH.dp.toPx(), GUIDE_GAP.dp.toPx())),
                )
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
    }
}

/** Play/pause, the scrubber with a tick where each phase begins, and the name of the phase at the head. */
@Composable
private fun Transport(
    viewModel: BibleLottieGenViewModel,
    isPlaying: Boolean,
    seekValue: Float,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    val timeline = viewModel.timeline
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(Tokens.Accent)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                // The same descriptions the main generator's transport carries; the tests find it by them.
                contentDescription = if (isPlaying) PAUSE_DESCRIPTION else PLAY_DESCRIPTION,
                tint = Tokens.OnAccent,
                modifier = Modifier.size(18.dp),
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            LottieSlider(
                value = seekValue,
                onValueChange = onSeek,
                valueRange = 0f..1f,
                trackHeight = 5.dp,
                knobSize = 13.dp,
                trackColor = Tokens.TransportTrack,
            )
            val tick = Tokens.LabelText.copy(alpha = GUIDE_ALPHA)
            Canvas(Modifier.fillMaxWidth().height(9.dp)) {
                BandPhase.entries.drop(1).forEach { phase ->
                    val x = phase.startFraction(timeline) * size.width
                    drawRect(tick, topLeft = Offset(x, 0f), size = Size(1.5f.dp.toPx(), size.height))
                }
            }
        }
        Text(
            BandPhase.at(timeline, seekValue).label,
            modifier = Modifier.widthIn(min = 52.dp),
            fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Tokens.LabelText,
            textAlign = TextAlign.End, maxLines = 1,
        )
    }
}
