package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

private const val NANOS_PER_SECOND = 1_000_000_000f

/**
 * Where a loop that started on [holdProgress] is after [elapsedNanos] of a file [durationNanos]
 * long: it runs on past the end and wraps to the start. A file with no duration stays put.
 */
internal fun loopedProgress(holdProgress: Float, elapsedNanos: Long, durationNanos: Float): Float {
    if (durationNanos <= 0f) return holdProgress
    return (holdProgress + (elapsedNanos / durationNanos)).mod(1f)
}

/**
 * A template playing on a loop, with the sample text it was generated with, for the Background
 * tab's stage. It starts on its hold frame — the band up and the words on it — and runs from
 * there through the exit, the entrance and back, so the first frame is the look and the rest is
 * the motion. A file that is missing or is not a template draws nothing.
 *
 * The loop is an infinite-animation frame wait, so under a test clock it never advances and the
 * stage stays on the hold frame the screenshots were recorded with.
 */
@Composable
internal fun BibleLottieStillFrame(path: String, modifier: Modifier = Modifier) {
    val template by rememberBibleLottieTemplate(path)
    val loaded = template ?: return
    val composition by rememberLottieComposition(loaded.json) { LottieCompositionSpec.JsonString(loaded.json) }
    val holdProgress = loaded.progressAt(BibleBandClock())
    var progress by remember(loaded) { mutableStateOf(holdProgress) }
    LaunchedEffect(loaded) {
        val durationNanos = loaded.totalFrames / loaded.frameRate * NANOS_PER_SECOND
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                progress = loopedProgress(holdProgress, now - start, durationNanos)
            }
        }
    }
    // No font manager: the sample is drawn from the glyph outlines the generator embedded, which
    // is exactly how the generator's own preview draws it, so the two agree.
    val painter = rememberLottiePainter(
        composition = composition,
        progress = { progress },
    )
    Image(painter = painter, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = modifier)
}
