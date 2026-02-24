package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.AppSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.delay

/**
 * Displays a Lottie animation in the presenter window.
 *
 * If [pauseAtFrame] is true and [pauseFrame] is in 0..1, the animation plays to
 * [pauseFrame], pauses for [pauseDurationMs] ms, then continues to the end.
 * Otherwise it plays straight to the end and stops.
 */
@Composable
fun LowerThirdPresenter(
    jsonContent: String,
    pauseAtFrame: Boolean,
    pauseFrame: Float,
    pauseDurationMs: Long,
    trigger: Int = 0,
    appSettings: AppSettings
) {
    if (jsonContent.isBlank()) return

    val composition by rememberLottieComposition(key = jsonContent) {
        LottieCompositionSpec.JsonString(jsonContent)
    }

    val progress = remember(jsonContent) { Animatable(0f) }

    // trigger is included as a key so every Go Live press restarts the animation,
    // even when all other parameters are identical.
    LaunchedEffect(composition, pauseAtFrame, pauseFrame, pauseDurationMs, trigger) {
        val comp = composition ?: return@LaunchedEffect
        val totalDurMs = ((comp.durationFrames / comp.frameRate) * 1000f).toLong().coerceAtLeast(1L)
        val hasPause = pauseAtFrame && pauseFrame in 0f..1f

        progress.snapTo(0f)

        if (hasPause) {
            val toPauseDur = (totalDurMs * pauseFrame).toInt().coerceAtLeast(1)
            progress.animateTo(
                targetValue = pauseFrame,
                animationSpec = tween(durationMillis = toPauseDur, easing = LinearEasing)
            )
            if (pauseDurationMs > 0) {
                delay(pauseDurationMs)
                val remainDur = (totalDurMs * (1f - pauseFrame)).toInt().coerceAtLeast(1)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = remainDur, easing = LinearEasing)
                )
            }
            // If pauseDurationMs == 0, stay frozen at pauseFrame
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = totalDurMs.toInt(), easing = LinearEasing)
            )
        }
    }

    val s = appSettings.streamingSettings
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = s.windowLeft.dp,
                end = s.windowRight.dp,
                top = s.windowTop.dp,
                bottom = s.windowBottom.dp
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Image(
            painter = rememberLottiePainter(
                composition = composition,
                progress = { progress.value }
            ),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.333f)
        )
    }
}
