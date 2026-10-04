package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * A presenter's fade-in on its first appearance: 0 to 1 over [durationMs] when [fadeIn] is on,
 * otherwise 1 from the start. Content and its background each hold one, started together.
 */
@Composable
internal fun rememberEnterAlpha(fadeIn: Boolean, durationMs: Int): State<Float> {
    val enterAlpha = remember { mutableFloatStateOf(if (fadeIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (fadeIn && enterAlpha.floatValue < 1f) {
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(durationMillis = durationMs)) { enterAlpha.floatValue = value }
            enterAlpha.floatValue = 1f
        }
    }
    return enterAlpha
}

/**
 * The full-screen box a Bible or song slide draws in: [backdrop] painted on it when [drawsBackground]
 * (as a modifier, or as its own layers when blurred), at [alpha], with [content] on top.
 *
 * The same box draws the slide on the slide layer, with [drawsBackground] off, and the background
 * alone on the background layer, with no content -- so the two line up exactly.
 */
@Composable
internal fun FullScreenBackdropBox(
    modifier: Modifier,
    alpha: () -> Float,
    backdrop: PresenterBackdrop,
    isLowerThird: Boolean,
    drawsBackground: Boolean,
    content: @Composable BoxWithConstraintsScope.(blurRadius: Dp) -> Unit = {},
) {
    val paintsModifier = drawsBackground && !isLowerThird && !backdrop.blurred
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha() }
            .then(if (paintsModifier) backdrop.bgModifier else Modifier)
    ) {
        // The stored radius is in the 1920x1080 reference space the rest of the presenter measures in.
        val blurRadius = backgroundBlurRadius(backdrop.bgBlurReferencePx, maxWidth)
        if (drawsBackground) {
            PresenterBackgroundLayers(
                background = backdrop.resolvedBg,
                backgroundModifier = backdrop.bgModifier,
                isLowerThird = isLowerThird,
                blurRadius = blurRadius,
            )
        }
        content(blurRadius)
    }
}
