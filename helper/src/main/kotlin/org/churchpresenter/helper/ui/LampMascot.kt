package org.churchpresenter.helper.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Wick: a teal cup-lamp with a face, on its own teal disc — the design's mascot, drawn from its SVG
 * (a 256-unit square) with the same animations: the flame's three layers flicker on their own beats,
 * the halo breathes, the eyes blink, and every few seconds the smile becomes a grin and it blushes.
 *
 * [animate] false draws it at rest — while something is live, so it never pulls the eye during a
 * service, and in screenshots, so they are the same every time. [mood] HAPPY holds the grin;
 * THINKING quickens the flame.
 */
@Composable
fun LampMascot(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    mood: LampMood = LampMood.IDLE,
    animate: Boolean = true,
) {
    val clock = lampClock(animate)
    Canvas(modifier.size(size)) {
        val ms = clock()
        val pose = Pose(ms, mood)
        scale(this.size.minDimension / VIEW, pivot = Offset.Zero) {
            clipPath(Shapes.disc) { drawLamp(pose) }
        }
    }
}

/** Milliseconds since the lamp first drew, ticking every frame while [animate]; 0 at rest. */
@Composable
private fun lampClock(animate: Boolean): () -> Long {
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(animate) {
        if (!animate) {
            now = 0L
            return@LaunchedEffect
        }
        val start = withFrameMillis { it }
        while (true) withFrameMillis { now = it - start }
    }
    return { now }
}

/** Every animated value for one frame, from the design's keyframes. */
private class Pose(ms: Long, mood: LampMood) {
    private val flameSpeed = if (mood == LampMood.THINKING) THINKING_SPEED else 1f
    private val flameMs = (ms * flameSpeed).toLong()

    val haloScale = Tracks.haloScale.at(ms, HALO_MS)
    val haloAlpha = Tracks.haloAlpha.at(ms, HALO_MS)
    val outerRotate = Tracks.outerRotate.at(flameMs, OUTER_MS)
    val outerX = Tracks.outerX.at(flameMs, OUTER_MS)
    val outerY = Tracks.outerY.at(flameMs, OUTER_MS)
    val midRotate = Tracks.midRotate.at(flameMs + MID_DELAY_MS, MID_MS)
    val midX = Tracks.midX.at(flameMs + MID_DELAY_MS, MID_MS)
    val midY = Tracks.midY.at(flameMs + MID_DELAY_MS, MID_MS)
    val coreX = Tracks.coreX.at(flameMs + CORE_DELAY_MS, CORE_MS)
    val coreY = Tracks.coreY.at(flameMs + CORE_DELAY_MS, CORE_MS)
    val coreAlpha = Tracks.coreAlpha.at(flameMs + CORE_DELAY_MS, CORE_MS)
    val eyeOpen = Tracks.blink.at(ms, BLINK_MS)
    val grin = if (mood == LampMood.HAPPY) 1f else Tracks.grin.at(ms, GRIN_MS)
    val cheek = CHEEK_REST + (CHEEK_GRIN - CHEEK_REST) * grin
}

private fun DrawScope.drawLamp(p: Pose) {
    drawCircle(LampPaint.background, radius = HALF, center = Geo.middle)
    drawCircle(Geo.edgeColor, radius = Geo.EDGE_RADIUS, center = Geo.middle, style = Geo.edgeStroke)
    scale(p.haloScale, pivot = Geo.haloCenter) {
        drawCircle(LampPaint.halo, radius = Geo.HALO_RADIUS, center = Geo.haloCenter, alpha = p.haloAlpha)
    }
    // The shadow under the cup: a round glow squashed flat.
    scale(1f, SHADE_SQUASH, pivot = Geo.shadeCenter) {
        drawCircle(LampPaint.shade, radius = Geo.SHADE_RADIUS, center = Geo.shadeCenter)
    }
    drawPath(Shapes.handle, Teal.deep, style = Geo.handleStroke)
    drawPath(Shapes.handleShine, Teal.light, alpha = Geo.HANDLE_SHINE_ALPHA, style = Geo.handleShineStroke)
    drawPath(Shapes.body, LampPaint.body)
    drawPath(Shapes.gloss, LampPaint.gloss)
    drawOval(LampPaint.rim, topLeft = Geo.rimTopLeft, size = Geo.rimSize)
    drawOval(Teal.deep, topLeft = Geo.wellTopLeft, size = Geo.wellSize, alpha = Geo.WELL_ALPHA)
    drawLine(Wick, Geo.wickFoot, Geo.wickTip, strokeWidth = Geo.WICK_WIDTH, cap = StrokeCap.Round)
    drawFlame(p)
    drawFace(p)
}

private fun DrawScope.drawFlame(p: Pose) {
    flameLayer(Geo.outerFoot, p.outerRotate, p.outerX, p.outerY) {
        drawPath(Shapes.flameOuter, LampPaint.flameOuter)
    }
    flameLayer(Geo.midFoot, p.midRotate, p.midX, p.midY) { drawPath(Shapes.flameMid, LampPaint.flameMid) }
    flameLayer(Geo.coreFoot, 0f, p.coreX, p.coreY) {
        drawPath(Shapes.flameCore, FlameCore, alpha = p.coreAlpha)
    }
}

/** One layer of the flame, turned and stretched about its foot, the way the CSS transform-origin does. */
private fun DrawScope.flameLayer(foot: Offset, degrees: Float, sx: Float, sy: Float, draw: DrawScope.() -> Unit) {
    withTransform({
        rotate(degrees, pivot = foot)
        scale(sx, sy, pivot = foot)
    }) { draw() }
}

private fun DrawScope.drawFace(p: Pose) {
    for (cx in Geo.eyeCenters) {
        scale(1f, p.eyeOpen, pivot = Offset(cx, Geo.EYE_Y)) {
            drawOval(Ink, topLeft = Offset(cx - Geo.EYE_HALF_WIDTH, Geo.EYE_TOP), size = Geo.eyeSize)
        }
        // The glint goes out with the eye.
        if (p.eyeOpen > GLINT_SHOWN) {
            drawCircle(Color.White, radius = Geo.GLINT_RADIUS, center = Offset(cx + Geo.GLINT_SHIFT, Geo.GLINT_Y))
        }
    }
    for (cx in Geo.cheekCenters) {
        drawOval(
            Cheek,
            topLeft = Offset(cx - Geo.CHEEK_HALF_WIDTH, Geo.CHEEK_TOP),
            size = Geo.cheekSize,
            alpha = p.cheek,
        )
    }
    drawPath(Shapes.smile, Ink, alpha = 1f - p.grin, style = Geo.smileStroke)
    if (p.grin > 0f) {
        drawPath(Shapes.grin, Ink, alpha = p.grin, style = Fill)
        drawPath(Shapes.grin, Ink, alpha = p.grin, style = Geo.grinStroke)
        drawPath(Shapes.tongue, Tongue, alpha = p.grin, style = Geo.tongueStroke)
    }
}

/** Where everything sits in the design's 256-unit square, by name — its SVG's own numbers. */
private object Geo {
    val middle = Offset(HALF, HALF)
    val edgeColor = Color.White.copy(alpha = 0.08f)
    const val EDGE_RADIUS = 126f
    val edgeStroke = Stroke(3f)
    val haloCenter = Offset(HALF, 84f)
    const val HALO_RADIUS = 70f
    val shadeCenter = Offset(HALF, 198f)
    const val SHADE_RADIUS = 64f
    val handleStroke = Stroke(11f, cap = StrokeCap.Round)
    val handleShineStroke = Stroke(4f, cap = StrokeCap.Round)
    const val HANDLE_SHINE_ALPHA = 0.7f
    val rimTopLeft = Offset(60f, 127f)
    val rimSize = Size(136f, 22f)
    val wellTopLeft = Offset(72f, 130.5f)
    val wellSize = Size(112f, 13f)
    const val WELL_ALPHA = 0.55f
    val wickFoot = Offset(HALF, 132f)
    val wickTip = Offset(HALF, 118f)
    const val WICK_WIDTH = 5f
    val outerFoot = Offset(HALF, 120f)
    val midFoot = Offset(HALF, 116f)
    val coreFoot = Offset(HALF, 110f)
    val eyeCenters = listOf(108f, 148f)
    const val EYE_Y = 162f
    const val EYE_TOP = 154f
    const val EYE_HALF_WIDTH = 6.5f
    val eyeSize = Size(13f, 16f)
    const val GLINT_RADIUS = 2.4f
    const val GLINT_SHIFT = 2.5f
    const val GLINT_Y = 158.5f
    val cheekCenters = listOf(94f, 162f)
    const val CHEEK_TOP = 169f
    const val CHEEK_HALF_WIDTH = 8f
    val cheekSize = Size(16f, 10f)
    val smileStroke = Stroke(4.5f, cap = StrokeCap.Round)
    val grinStroke = Stroke(3f, join = StrokeJoin.Round)
    val tongueStroke = Stroke(3f, cap = StrokeCap.Round)
}

// Wick's own colors — the same in every theme, since a lamp's light does not change with the window.
private val Ink = Color(0xFF123B33)
private val Cheek = Color(0xFFFF9E8A)
private val Tongue = Color(0xFFFF8F86)
private val Wick = Color(0xFF3A2A1E)
private val FlameCore = Color(0xFFFFFBE8)

private object Teal {
    val deep = Color(0xFF2C9A85)
    val light = Color(0xFF7FE3CF)
}

/** The design's gradients, placed in the 256-unit square the way SVG lays them over each shape's box. */
private object LampPaint {
    val background = Brush.radialGradient(
        0f to Color(0xFF1FA186), 0.6f to Color(0xFF0F6F5C), 1f to Color(0xFF0A4A3E),
        center = Offset(HALF, VIEW * 0.38f),
        radius = VIEW * 0.68f,
    )
    val halo = Brush.radialGradient(
        0f to Color(0xFFFFD27A).copy(alpha = 0.55f),
        0.45f to Color(0xFFFFB84D).copy(alpha = 0.18f),
        1f to Color(0xFFFFB84D).copy(alpha = 0f),
        center = Offset(HALF, 84f),
        radius = 70f,
    )
    val shade = Brush.radialGradient(
        0f to Color(0xFF03231D).copy(alpha = 0.45f), 1f to Color(0xFF03231D).copy(alpha = 0f),
        center = Offset(HALF, 198f),
        radius = 64f,
    )
    val body = Brush.verticalGradient(
        0f to Color(0xFF7FE3CF), 0.5f to Color(0xFF4CC4AD), 1f to Color(0xFF2C9A85),
        startY = 138f,
        endY = 196f,
    )
    val gloss = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.5f), 1f to Color.White.copy(alpha = 0f),
        startY = 146f,
        endY = 186f,
    )
    val rim = Brush.verticalGradient(listOf(Color(0xFFB6F3E6), Color(0xFF5FD0B9)), startY = 127f, endY = 149f)
    val flameOuter = Brush.verticalGradient(
        0f to Color(0xFFFFD36B), 0.55f to Color(0xFFFF9A2E), 1f to Color(0xFFF26A1B),
        startY = 40f,
        endY = 120f,
    )
    val flameMid = Brush.verticalGradient(listOf(Color(0xFFFFF1B0), Color(0xFFFFC24A)), startY = 62f, endY = 114f)
}

/** The design's SVG paths, word for word. */
private object Shapes {
    val disc = Path().apply { addOval(Rect(0f, 0f, VIEW, VIEW)) }
    val handle = svg("M182 146 C206 140 214 160 202 174 C195 182 184 182 176 178")
    val handleShine = svg("M182 146 C204 141 210 158 200 170")
    val body = svg("M62 138 H194 C194 172 166 196 128 196 C90 196 62 172 62 138 Z")
    val gloss = svg("M76 146 C80 168 98 182 116 186 C96 178 84 164 82 146 Z")
    val flameOuter = svg(
        "M128 40 C146 62 156 80 152 98 C149 112 139 120 128 120 C117 120 107 112 104 98 C100 80 110 62 128 40 Z",
    )
    val flameMid = svg(
        "M128 62 C139 77 143 89 140 101 C138 109 133 114 128 114 C123 114 118 109 116 101 C113 89 117 77 128 62 Z",
    )
    val flameCore = svg(
        "M128 84 C133 91 135 98 133 104 C132 108 130 110 128 110 C126 110 124 108 123 104 C121 98 123 91 128 84 Z",
    )
    val smile = svg("M119 175 C124 181 132 181 137 175")
    val grin = svg("M116 173 C120 186 136 186 140 173 Z")
    val tongue = svg("M123 179 C126 183 130 183 133 179")

    private fun svg(d: String): Path = PathParser().parsePathString(d).toPath()
}

/**
 * A CSS keyframe track: [stops] are the percentages as fractions, [values] the value at each, and
 * [easing] runs between neighbours — CSS applies the timing function per segment, not overall.
 */
private class Track(val stops: FloatArray, val values: FloatArray, val easing: Easing = EaseInOut) {
    fun at(ms: Long, periodMs: Long): Float {
        val t = (ms.mod(periodMs)).toFloat() / periodMs
        val i = (stops.indexOfLast { it <= t }).coerceIn(0, stops.size - 2)
        val span = stops[i + 1] - stops[i]
        val local = if (span == 0f) 1f else ((t - stops[i]) / span).coerceIn(0f, 1f)
        return values[i] + (values[i + 1] - values[i]) * easing.transform(local)
    }
}

private fun track(vararg pairs: Pair<Float, Float>, easing: Easing = EaseInOut) =
    Track(pairs.map { it.first }.toFloatArray(), pairs.map { it.second }.toFloatArray(), easing)

/** CSS's `ease-in-out`. */
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private object Tracks {
    val haloScale = track(0f to 1f, 0.35f to 1.07f, 0.65f to 0.95f, 1f to 1f)
    val haloAlpha = track(0f to 0.85f, 0.35f to 1f, 0.65f to 0.75f, 1f to 0.85f)
    val outerRotate = track(0f to 0f, 0.25f to -5f, 0.5f to 2f, 0.75f to 5f, 1f to 0f)
    val outerX = track(0f to 1f, 0.25f to 0.97f, 0.5f to 1.03f, 0.75f to 0.98f, 1f to 1f)
    val outerY = track(0f to 1f, 0.25f to 1.04f, 0.5f to 0.95f, 0.75f to 1.03f, 1f to 1f)
    val midRotate = track(0f to 0f, 0.3f to -4f, 0.6f to 3f, 0.8f to 4f, 1f to 0f)
    val midX = track(0f to 1f, 0.3f to 0.96f, 0.6f to 1.04f, 0.8f to 0.97f, 1f to 1f)
    val midY = track(0f to 1f, 0.3f to 1.06f, 0.6f to 0.94f, 0.8f to 1.03f, 1f to 1f)
    val coreX = track(0f to 1f, 0.4f to 0.92f, 0.7f to 1.06f, 1f to 1f)
    val coreY = track(0f to 1f, 0.4f to 1.08f, 0.7f to 0.94f, 1f to 1f)
    val coreAlpha = track(0f to 1f, 0.4f to 0.85f, 0.7f to 0.95f, 1f to 1f)

    /** Open, a blink at 43%, then a double blink at 86% and 94%. */
    val blink = track(
        0f to 1f, 0.40f to 1f, 0.43f to BLINK_SHUT, 0.46f to 1f, 0.84f to 1f, 0.86f to BLINK_SHUT, 0.88f to 1f,
        0.92f to 1f, 0.94f to BLINK_SHUT, 0.96f to 1f, 1f to 1f,
        easing = LinearEasing,
    )

    /** 0 is the smile, 1 the grin: it breaks into one for a while every cycle. */
    val grin = track(0f to 0f, 0.28f to 0f, 0.34f to 1f, 0.54f to 1f, 0.60f to 0f, 1f to 0f)
}

private const val VIEW = 256f
private const val HALF = VIEW / 2f
private const val SHADE_SQUASH = 9f / 64f
private const val BLINK_SHUT = 0.08f
private const val GLINT_SHOWN = 0.5f
private const val CHEEK_REST = 0.4f
private const val CHEEK_GRIN = 0.68f
private const val THINKING_SPEED = 1.6f

private const val HALO_MS = 2600L
private const val OUTER_MS = 1900L
private const val MID_MS = 1700L
private const val MID_DELAY_MS = 300L
private const val CORE_MS = 1300L
private const val CORE_DELAY_MS = 200L
private const val BLINK_MS = 4600L
private const val GRIN_MS = 6000L
