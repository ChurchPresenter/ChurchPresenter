package org.churchpresenter.sharedui.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import org.churchpresenter.sharedui.utils.slidePassMs
import org.churchpresenter.sharedui.utils.slideTravelPx

/**
 * [content] sliding across this box from one edge to the other, the way an announcement moves:
 * it starts just off one edge and its leading edge reaches the other edge in [crossingMs], however
 * long the text is -- a longer line moves at the same speed and only takes longer to leave
 * ([slidePassMs]). [horizontal] slides along the width, otherwise along the height;
 * [movesPositive] goes left to right or top to bottom. [loopCount] passes and then [onFinished], or
 * for ever when it is 0. [alignment] places the content across the direction it moves.
 *
 * The content is measured before it moves, and drawn only once it has been, so it never shows in
 * the middle of the screen for a frame while its size is still unknown.
 */
@Composable
fun SlideAcross(
    crossingMs: Int,
    horizontal: Boolean,
    movesPositive: Boolean,
    alignment: Alignment,
    modifier: Modifier = Modifier,
    loopCount: Int = 0,
    onFinished: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val containerPx = (if (horizontal) constraints.maxWidth else constraints.maxHeight).toFloat()
        var contentPx by remember { mutableIntStateOf(0) }
        val measured = contentPx > 0
        val travel = slideTravelPx(containerPx, contentPx.toFloat())
        val passMs = slidePassMs(crossingMs, containerPx, contentPx.toFloat())
        val from = if (movesPositive) -travel else travel
        val currentOnFinished by rememberUpdatedState(onFinished)
        // A new pass length -- a new speed, size or text -- starts the slide over from its edge.
        val progress = remember(passMs, from, loopCount) { Animatable(0f) }
        LaunchedEffect(progress, measured) {
            if (!measured) return@LaunchedEffect
            val pass = tween<Float>(durationMillis = passMs, easing = LinearEasing)
            if (loopCount > 0) {
                progress.animateTo(1f, repeatable(loopCount, pass, RepeatMode.Restart))
                currentOnFinished()
            } else {
                progress.animateTo(1f, infiniteRepeatable(pass, RepeatMode.Restart))
            }
        }
        Box(
            modifier = Modifier
                .align(alignment)
                .then(
                    if (horizontal) Modifier.wrapContentWidth(unbounded = true)
                    else Modifier.wrapContentHeight(unbounded = true),
                )
                .onSizeChanged { contentPx = if (horizontal) it.width else it.height }
                .graphicsLayer {
                    val offset = from - 2f * from * progress.value
                    if (horizontal) translationX = offset else translationY = offset
                    alpha = if (measured) 1f else 0f
                },
        ) { content() }
    }
}
