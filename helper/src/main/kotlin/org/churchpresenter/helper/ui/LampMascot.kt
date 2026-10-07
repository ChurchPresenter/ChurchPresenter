package org.churchpresenter.helper.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The flame is the character's own colour, the same in every theme — a lamp's light does not
// change with the window around it. The body follows the theme.
private val FLAME_OUTER = Color(0xFFFF9F1C)
private val FLAME_INNER = Color(0xFFFFE08A)
private val FLAME_CORE = Color(0xFFFFFBEA)
private val GLOW = Color(0xFFFFC94D)

private const val FLICKER_MS = 700
private const val SWAY_MS = 1130
private const val BOB_MS = 2400
private const val BLINK_MS = 5200
private const val BLINK_CLOSING_MS = 220
private const val BLINK_SHUT_MS = 120
private const val BLINK_SHUT = 0.1f

/**
 * The helper's lamp: a little clay oil lamp with a face, and a flame that flickers, sways and glows.
 *
 * [animate] false draws it at rest — while something is live, so it never pulls the eye during a
 * service, and in screenshots, so they are the same every time.
 */
@Composable
fun LampMascot(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    mood: LampMood = LampMood.IDLE,
    animate: Boolean = true,
) {
    val motion = if (animate) lampMotion(mood) else remember { LampMotion.rest() }
    val colors = LampColors(
        body = MaterialTheme.colorScheme.secondary,
        shade = MaterialTheme.colorScheme.onSecondaryContainer,
        face = MaterialTheme.colorScheme.onSecondary,
    )
    Canvas(modifier.size(size)) {
        translate(top = motion.bob.value * this.size.height * Lamp.BOB) {
            drawGlow(motion.glow.value)
            drawFlame(mood, motion.flicker.value, motion.sway.value)
            drawBody(colors)
            drawFace(colors.face, mood, motion.blink.value)
        }
    }
}

private class LampColors(val body: Color, val shade: Color, val face: Color)

private class LampMotion(
    val flicker: State<Float>,
    val sway: State<Float>,
    val bob: State<Float>,
    val glow: State<Float>,
    val blink: State<Float>,
) {
    companion object {
        /** Still: flame upright at full height, half glow, eyes open. */
        fun rest() = LampMotion(
            flicker = mutableFloatStateOf(1f),
            sway = mutableFloatStateOf(0f),
            bob = mutableFloatStateOf(0f),
            glow = mutableFloatStateOf(REST_GLOW),
            blink = mutableFloatStateOf(1f),
        )

        private const val REST_GLOW = 0.5f
    }
}

@Composable
private fun lampMotion(mood: LampMood): LampMotion {
    val transition = rememberInfiniteTransition(label = "lamp")
    val speed = if (mood == LampMood.THINKING) 2 else 1
    val flicker = transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(FLICKER_MS / speed, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "flicker",
    )
    val sway = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SWAY_MS / speed, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    val bob = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BOB_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val glow = transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(FLICKER_MS * 2, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )
    // Eyes open almost all the time, shut for a moment near the end of each cycle.
    val blink = transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = BLINK_MS
                1f at 0
                1f at BLINK_MS - BLINK_CLOSING_MS
                BLINK_SHUT at BLINK_MS - BLINK_SHUT_MS
                1f at BLINK_MS
            },
        ),
        label = "blink",
    )
    return remember(transition) { LampMotion(flicker, sway, bob, glow, blink) }
}

/** The lamp's proportions, as fractions of the canvas — so it draws the same at any size. */
private object Lamp {
    const val BOB = 0.03f

    const val GLOW_Y = 0.36f
    const val GLOW_RADIUS = 0.48f
    const val GLOW_MIN = 0.15f
    const val GLOW_RANGE = 0.25f

    const val FLAME_TOP = 0.04f
    const val FLAME_BASE = 0.5f
    const val FLAME_HALF_WIDTH = 0.15f
    const val CORE_HALF_WIDTH = 0.08f
    const val CORE_HEIGHT = 0.62f
    const val HAPPY_HEIGHT = 1.12f
    const val CONFUSED_HEIGHT = 0.9f
    const val CONFUSED_TILT = 14f
    const val SWAY_DEGREES = 3f

    // A teardrop's two curves: how far up each control point sits, and how far it bulges out.
    const val TIP_CONTROL_Y = 0.35f
    const val TIP_CONTROL_X = 0.4f
    const val BULGE_CONTROL_Y = 0.25f
    const val BULGE_CONTROL_X = 1.3f

    const val BOWL_LEFT = 0.12f
    const val BOWL_RIGHT = 0.88f
    const val BOWL_TOP = 0.62f
    const val BOWL_BOTTOM = 0.98f
    const val SPOUT_RIGHT = 0.62f
    const val SPOUT_LEFT = 0.38f
    const val SPOUT_Y = 0.56f
    const val SPOUT_DIP = 0.5f

    const val RIM_LEFT = 0.14f
    const val RIM_TOP = 0.6f
    const val RIM_WIDTH = 0.72f
    const val RIM_HEIGHT = 0.06f
    const val RIM_ALPHA = 0.25f

    const val HANDLE_LEFT = 0.8f
    const val HANDLE_WIDTH = 0.16f
    const val HANDLE_HEIGHT = 0.18f
    const val HANDLE_STROKE = 0.05f

    const val WICK_LEFT = 0.485f
    const val WICK_TOP = 0.48f
    const val WICK_WIDTH = 0.03f
    const val WICK_HEIGHT = 0.06f

    const val EYE_Y = 0.74f
    const val EYE_LEFT_X = 0.4f
    const val EYE_RIGHT_X = 0.6f
    const val EYE_WIDTH = 0.06f
    const val EYE_HEIGHT = 0.09f
    const val EYE_MIN_HEIGHT = 0.012f

    const val MOUTH_Y = 0.83f
    const val MOUTH_LEFT = 0.45f
    const val MOUTH_RIGHT = 0.55f
    const val SMILE = 0.03f
    const val BIG_SMILE = 0.05f
    const val MOUTH_STROKE = 0.025f
}

private fun DrawScope.drawGlow(strength: Float) {
    val center = Offset(center.x, size.height * Lamp.GLOW_Y)
    val radius = size.minDimension * Lamp.GLOW_RADIUS
    val alpha = Lamp.GLOW_MIN + Lamp.GLOW_RANGE * strength
    drawCircle(
        brush = Brush.radialGradient(listOf(GLOW.copy(alpha = alpha), Color.Transparent), center, radius),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.drawFlame(mood: LampMood, flicker: Float, sway: Float) {
    val base = Offset(center.x, size.height * Lamp.FLAME_BASE)
    val height = base.y - size.height * Lamp.FLAME_TOP
    val tall = when (mood) {
        LampMood.HAPPY -> Lamp.HAPPY_HEIGHT
        LampMood.CONFUSED -> Lamp.CONFUSED_HEIGHT
        else -> 1f
    }
    val tilt = if (mood == LampMood.CONFUSED) Lamp.CONFUSED_TILT else sway * Lamp.SWAY_DEGREES
    rotate(tilt, pivot = base) {
        scale(scaleX = 1f, scaleY = flicker * tall, pivot = base) {
            val outer = flamePath(base, size.width * Lamp.FLAME_HALF_WIDTH, height)
            drawPath(outer, Brush.verticalGradient(listOf(FLAME_INNER, FLAME_OUTER)))
            drawPath(flamePath(base, size.width * Lamp.CORE_HALF_WIDTH, height * Lamp.CORE_HEIGHT), FLAME_CORE)
        }
    }
}

/** A teardrop standing on [base]: [halfWidth] across at its widest, [height] tall. */
private fun flamePath(base: Offset, halfWidth: Float, height: Float) = Path().apply {
    val x = base.x
    val tipY = base.y - height
    val tipControlY = tipY + height * Lamp.TIP_CONTROL_Y
    val bulgeControlY = base.y - height * Lamp.BULGE_CONTROL_Y
    moveTo(x, tipY)
    val tipOut = halfWidth * Lamp.TIP_CONTROL_X
    val bulgeOut = halfWidth * Lamp.BULGE_CONTROL_X
    cubicTo(x + tipOut, tipControlY, x + bulgeOut, bulgeControlY, x, base.y)
    cubicTo(x - bulgeOut, bulgeControlY, x - tipOut, tipControlY, x, tipY)
    close()
}

private fun DrawScope.drawBody(colors: LampColors) {
    val w = size.width
    val h = size.height
    // The bowl: a wide, flat oval with a spout dipping toward the wick in the middle.
    val bowl = Path().apply {
        moveTo(w * Lamp.BOWL_LEFT, h * Lamp.BOWL_TOP)
        val left = w * Lamp.BOWL_LEFT
        val right = w * Lamp.BOWL_RIGHT
        cubicTo(left, h * Lamp.BOWL_BOTTOM, right, h * Lamp.BOWL_BOTTOM, right, h * Lamp.BOWL_TOP)
        lineTo(w * Lamp.SPOUT_RIGHT, h * Lamp.SPOUT_Y)
        quadraticTo(center.x, h * Lamp.SPOUT_DIP, w * Lamp.SPOUT_LEFT, h * Lamp.SPOUT_Y)
        close()
    }
    drawPath(bowl, colors.body)
    // The rim, a lighter band across the top of the bowl.
    drawRoundRect(
        color = colors.shade.copy(alpha = Lamp.RIM_ALPHA),
        topLeft = Offset(w * Lamp.RIM_LEFT, h * Lamp.RIM_TOP),
        size = Size(w * Lamp.RIM_WIDTH, h * Lamp.RIM_HEIGHT),
        cornerRadius = CornerRadius(h * Lamp.RIM_HEIGHT / 2),
    )
    // The handle, a loop on the right.
    drawArc(
        color = colors.body,
        startAngle = -QUARTER_TURN,
        sweepAngle = HALF_TURN,
        useCenter = false,
        topLeft = Offset(w * Lamp.HANDLE_LEFT, h * Lamp.BOWL_TOP),
        size = Size(w * Lamp.HANDLE_WIDTH, h * Lamp.HANDLE_HEIGHT),
        style = Stroke(width = w * Lamp.HANDLE_STROKE),
    )
    // The wick, where the flame stands.
    drawRect(
        colors.shade,
        topLeft = Offset(w * Lamp.WICK_LEFT, h * Lamp.WICK_TOP),
        size = Size(w * Lamp.WICK_WIDTH, h * Lamp.WICK_HEIGHT),
    )
}

private const val QUARTER_TURN = 90f
private const val HALF_TURN = 180f

private fun DrawScope.drawFace(color: Color, mood: LampMood, blink: Float) {
    val w = size.width
    val h = size.height
    val eyeY = h * Lamp.EYE_Y
    val eyeWidth = w * Lamp.EYE_WIDTH
    val eyeHeight = maxOf(h * Lamp.EYE_HEIGHT * blink, h * Lamp.EYE_MIN_HEIGHT)
    for (x in listOf(w * Lamp.EYE_LEFT_X, w * Lamp.EYE_RIGHT_X)) {
        drawOval(color, topLeft = Offset(x - eyeWidth / 2, eyeY - eyeHeight / 2), size = Size(eyeWidth, eyeHeight))
    }
    // A small smile, flattened to a line when the lamp is puzzled.
    val smile = when (mood) {
        LampMood.CONFUSED -> 0f
        LampMood.HAPPY -> Lamp.BIG_SMILE
        else -> Lamp.SMILE
    }
    val mouth = Path().apply {
        moveTo(w * Lamp.MOUTH_LEFT, h * Lamp.MOUTH_Y)
        quadraticTo(center.x, h * (Lamp.MOUTH_Y + smile), w * Lamp.MOUTH_RIGHT, h * Lamp.MOUTH_Y)
    }
    drawPath(mouth, color, style = Stroke(width = w * Lamp.MOUTH_STROKE))
}
