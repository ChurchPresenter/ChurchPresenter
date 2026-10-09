package org.churchpresenter.helper.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import org.churchpresenter.sharedui.guide.LocalWickCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.guide.GuideSession
import org.churchpresenter.sharedui.guide.GuideTargetRegistry
import org.churchpresenter.sharedui.guide.LocalGuideRingColor
import org.churchpresenter.sharedui.guide.LocalGuideSession
import org.churchpresenter.sharedui.guide.LocalGuideTargetRegistry
import org.churchpresenter.theme.semantic
import kotlin.math.max

private val SPARK_DIM = Color(0x26FFECBA)
private val SPARK = Color(0xFFFFF8E0)

private const val RING_ALPHA = 0.9f
private const val INNER_ALPHA = 0.3f
private const val PING_ALPHA = 0.8f
private const val LAND_MS = 550
private const val PING_DELAY_MS = 500
private const val PING_MS = 900
private const val BREATHE_MS = 1200
private const val SPIN_MS = 2400
private const val LAND_FROM_SCALE = 1.35f
private const val LAND_VISIBLE_AT = 0.6f
private const val PING_GROWTH = 0.22f
private const val FULL_TURN = 360f

/** The landing's overshoot: it drops in a touch past its place and settles. */
private val LAND_EASING = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.25f)

internal val SPOTLIGHT_OUTSET = 6.dp
private val RING_CORNER = 12.dp
private val RING_WIDTH = 2.dp

/** How far a dialog's lamp sits above its corner: clear of its Cancel / OK row. */
private val WICK_LIFT = 56.dp

/** The smallest window that carries the lamp: room for the bubble above it and beside the content. */
private val WICK_MIN_WIDTH = 480.dp
private val WICK_MIN_HEIGHT = 420.dp

/**
 * One window's spotlight: gives [content] a target registry of its own and, while the app's guide
 * session points at a control laid out in this window, rings it in gold — the ring lands, pings once,
 * then breathes while a spark of light travels around its edge.
 *
 * The ring draws over everything and takes no input, so the control under it still clicks. A press
 * anywhere inside the ring counts as pressing the ringed control — watched here, over the whole
 * window, so nothing laid over the control can keep it from counting. With [wick], the window also
 * carries Wick's lamp in its corner ([LocalWickCorner]), above the buttons a dialog keeps along its
 * bottom edge; the main window, which places its own, passes false.
 */
@Composable
fun GuideSpotlightHost(
    modifier: Modifier = Modifier,
    wick: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val registry = remember { GuideTargetRegistry() }
    val session by rememberUpdatedState(LocalGuideSession.current)
    // Targets report in window-root coordinates; the ring draws in this box's, which need not start at 0,0.
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(registry) { watchRingPresses(registry, { session }, { origin }) },
    ) {
        CompositionLocalProvider(
            LocalGuideTargetRegistry provides registry,
            // Faint: inside the ring it only shows where the ring is clipped or covered.
            LocalGuideRingColor provides spotlightGold().copy(alpha = INNER_ALPHA),
        ) {
            content()
        }
        SpotlightRing(registry, origin)
        val lamp = LocalWickCorner.current.takeIf { wick }
        // A window too small for the bubble (Planning Center's Connect) gets no lamp: it would only cover the
        // window, and Wick's tours still ring its controls.
        if (lamp != null) WickCornerIfRoom(lamp)
    }
}

@Composable
private fun BoxScope.WickCornerIfRoom(lamp: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        if (maxWidth < WICK_MIN_WIDTH || maxHeight < WICK_MIN_HEIGHT) return@BoxWithConstraints
        val session = LocalGuideSession.current
        DisposableEffect(session) {
            session?.let { it.otherLamps++ }
            onDispose { session?.let { it.otherLamps-- } }
        }
        lamp(Modifier.align(Alignment.BottomEnd).padding(bottom = WICK_LIFT))
    }
}

/** Tells the session whenever a press lands inside the ring, observed without consuming it. */
private suspend fun PointerInputScope.watchRingPresses(
    registry: GuideTargetRegistry,
    session: () -> GuideSession?,
    origin: () -> Offset,
) = awaitPointerEventScope {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val current = session().takeIf { event.type == PointerEventType.Press }
        val target = current?.activeTarget
        val ring = target?.let(registry::boundsOf)?.inflate(SPOTLIGHT_OUTSET.toPx())
        if (ring != null && event.changes.any { ring.contains(it.position + origin()) }) current.pressed(target)
    }
}

/**
 * The theme's gold — the favorite star's, deeper on a light theme and brighter on a dark one — so the
 * ring never reads as keyboard focus and still sits in the operator's theme.
 */
@Composable
internal fun spotlightGold(): Color = MaterialTheme.semantic.favorite

@Composable
private fun BoxScope.SpotlightRing(registry: GuideTargetRegistry, origin: Offset) {
    val session = LocalGuideSession.current ?: return
    val target = session.activeTarget ?: return
    val bounds = registry.boundsOf(target)?.translate(-origin) ?: return
    if (bounds.isEmpty) return
    val land = remember(target) { Animatable(0f) }
    val ping = remember(target) { Animatable(0f) }
    LaunchedEffect(target) { land.animateTo(1f, tween(LAND_MS, easing = LinearEasing)) }
    LaunchedEffect(target) {
        ping.animateTo(1f, tween(PING_MS, delayMillis = PING_DELAY_MS, easing = LinearOutSlowInEasing))
    }
    val gold = spotlightGold()
    val loop = rememberInfiniteTransition(label = "spotlight")
    val breathe by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATHE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val spin by loop.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)),
        label = "spin",
    )
    Canvas(Modifier.matchParentSize()) {
        val ring = bounds.inflate(SPOTLIGHT_OUTSET.toPx())
        val landed = land.value
        val alpha = (landed / LAND_VISIBLE_AT).coerceAtMost(1f)
        val scale = LAND_FROM_SCALE - (LAND_FROM_SCALE - 1f) * LAND_EASING.transform(landed)
        // The glow breathes only once the ring has landed.
        if (landed >= 1f) glow(ring, breathe, gold)
        roundRing(ring.scaledBy(scale), gold.copy(alpha = RING_ALPHA * alpha))
        val pinged = ping.value
        if (pinged > 0f && pinged < 1f) {
            roundRing(ring.scaledBy(1f + PING_GROWTH * pinged), gold.copy(alpha = PING_ALPHA * (1f - pinged)))
        }
        if (landed >= 1f) spark(ring, spin)
    }
    SpotlightCallout(target, bounds, session.activeHint?.invoke())
}

private fun Rect.scaledBy(scale: Float): Rect {
    val halfWidth = width * scale / 2f
    val halfHeight = height * scale / 2f
    return Rect(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
}

private fun DrawScope.roundRing(rect: Rect, color: Color) {
    val half = RING_WIDTH.toPx() / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(rect.left + half, rect.top + half),
        size = rect.deflate(half).size,
        cornerRadius = CornerRadius(RING_CORNER.toPx()),
        style = Stroke(RING_WIDTH.toPx()),
    )
}

/** A tight band and a wide soft halo, swelling and fading together: 3dp at 16% to 6dp at 8%, the halo 22% to 36%. */
private fun DrawScope.glow(ring: Rect, breathe: Float, gold: Color) {
    val band = (3 + 3 * breathe).dp.toPx()
    drawRoundRect(
        color = gold.copy(alpha = 0.16f - 0.08f * breathe),
        topLeft = ring.topLeft - Offset(band / 2f, band / 2f),
        size = ring.inflate(band / 2f).size,
        cornerRadius = CornerRadius(RING_CORNER.toPx() + band / 2f),
        style = Stroke(band),
    )
    val halo = (16 + 14 * breathe).dp.toPx()
    val haloAlpha = 0.22f + 0.14f * breathe
    // Rings of falling strength stand in for a blur.
    val layers = 6
    for (i in 1..layers) {
        val spread = halo * i / layers
        drawRoundRect(
            color = gold.copy(alpha = haloAlpha * (1f - i.toFloat() / (layers + 1)) / layers),
            topLeft = ring.topLeft - Offset(spread, spread),
            size = ring.inflate(spread).size,
            cornerRadius = CornerRadius(RING_CORNER.toPx() + spread),
            style = Stroke(halo / layers),
        )
    }
}

/** A spark of light running round the ring's edge: a sweep turned behind a cut-out of the border. */
private fun DrawScope.spark(ring: Rect, degrees: Float) {
    val corner = CornerRadius(RING_CORNER.toPx())
    val inner = ring.deflate(RING_WIDTH.toPx())
    val border = Path().apply {
        op(
            Path().apply { addRoundRect(RoundRect(ring, corner)) },
            Path().apply { addRoundRect(RoundRect(inner, CornerRadius(max(0f, corner.x - RING_WIDTH.toPx())))) },
            PathOperation.Difference,
        )
    }
    val sweep = Brush.sweepGradient(
        0f to Color.Transparent,
        0.75f to Color.Transparent,
        0.833f to SPARK_DIM,
        0.958f to SPARK,
        1f to Color.Transparent,
        center = ring.center,
    )
    clipPath(border) {
        rotate(degrees, ring.center) {
            val reach = max(ring.width, ring.height)
            drawRect(sweep, topLeft = ring.center - Offset(reach, reach), size = Size(reach * 2, reach * 2))
        }
    }
}

/** A spotlight host filling its window — the usual way to wrap a window's whole content. */
@Composable
fun GuideSpotlightWindow(content: @Composable BoxScope.() -> Unit) =
    GuideSpotlightHost(Modifier.fillMaxSize(), content = content)
