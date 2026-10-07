package org.churchpresenter.helper.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import org.churchpresenter.sharedui.guide.LocalGuideRingColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.guide.GuideTargetRegistry
import org.churchpresenter.sharedui.guide.LocalGuideSession
import org.churchpresenter.sharedui.guide.LocalGuideTargetRegistry

private const val PULSE_MS = 900

/**
 * One window's spotlight: gives [content] a target registry of its own and, while the app's guide
 * session points at a control laid out in this window, rings it with a pulsing outline.
 *
 * The ring draws over everything and takes no input, so the control under it still clicks.
 */
@Composable
fun GuideSpotlightHost(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val registry = remember { GuideTargetRegistry() }
    // Targets report in window-root coordinates; the ring draws in this box's, which need not start at 0,0.
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        CompositionLocalProvider(
            LocalGuideTargetRegistry provides registry,
            LocalGuideRingColor provides MaterialTheme.colorScheme.primary,
        ) {
            content()
        }
        SpotlightRing(registry, origin)
    }
}

@Composable
private fun BoxScope.SpotlightRing(registry: GuideTargetRegistry, origin: Offset) {
    val target = LocalGuideSession.current?.activeTarget ?: return
    val bounds = registry.boundsOf(target)?.translate(-origin) ?: return
    if (bounds.isEmpty) return
    val pulse by rememberInfiniteTransition(label = "spotlight").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.matchParentSize()) {
        val inset = 3.dp.toPx() + 4.dp.toPx() * pulse
        val corner = CornerRadius(8.dp.toPx())
        // A soft wide halo, then the crisp ring itself.
        drawRoundRect(
            color = color.copy(alpha = 0.18f + 0.22f * pulse),
            topLeft = Offset(bounds.left - inset, bounds.top - inset),
            size = Size(bounds.width + inset * 2, bounds.height + inset * 2),
            cornerRadius = corner,
            style = Stroke(width = 6.dp.toPx()),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(bounds.left - 2.dp.toPx(), bounds.top - 2.dp.toPx()),
            size = Size(bounds.width + 4.dp.toPx(), bounds.height + 4.dp.toPx()),
            cornerRadius = corner,
            style = Stroke(width = 2.5.dp.toPx()),
        )
    }
}

/** A spotlight host filling its window — the usual way to wrap a window's whole content. */
@Composable
fun GuideSpotlightWindow(content: @Composable BoxScope.() -> Unit) =
    GuideSpotlightHost(Modifier.fillMaxSize(), content)
