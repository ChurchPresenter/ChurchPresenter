package org.churchpresenter.helper.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_name
import org.jetbrains.compose.resources.stringResource

private const val POP_DELAY_MS = 550
private const val POP_MS = 500
private const val POP_FROM_SCALE = 0.3f
private const val POP_RISE_DP = 10f
private const val BUBBLE_DELAY_MS = 800
private const val BUBBLE_MS = 350
private const val BUBBLE_RISE_DP = 6f
private const val NUDGE_MS = 2600
private const val NUDGE_REST_UNTIL = 0.72f
private const val NUDGE_PEAK = 0.80f
private const val NUDGE_BACK = 0.88f
private const val NUDGE_DP = 4f
private const val NUDGE_TILT = -10f

/** The pop's overshoot: Wick lands a little large and settles. */
private val POP_EASING = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.3f)

private val AVATAR = 32.dp
private val AVATAR_OUT = 19.dp
private val AVATAR_EDGE = 2.dp
private val BUBBLE_GAP = 18.dp
private val BUBBLE_MAX = 250.dp

/**
 * What lands with the ring: Wick on its top-right corner, nudging toward the control now and then,
 * and [hint] in a bubble under the control — above it when there is no room below, and inside its
 * top-right corner when there is room for neither. Neither takes input.
 */
@Composable
internal fun BoxScope.SpotlightCallout(target: GuideTarget, bounds: Rect, hint: String?) {
    val pop = remember(target) { Animatable(0f) }
    val bubble = remember(target) { Animatable(0f) }
    LaunchedEffect(target) { pop.animateTo(1f, tween(POP_MS, POP_DELAY_MS, LinearEasing)) }
    LaunchedEffect(target) { bubble.animateTo(1f, tween(BUBBLE_MS, BUBBLE_DELAY_MS, LinearEasing)) }
    val nudge by rememberInfiniteTransition(label = "nudge").animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = NUDGE_MS
                0f at (NUDGE_MS * NUDGE_REST_UNTIL).toInt()
                1f at (NUDGE_MS * NUDGE_PEAK).toInt()
                0f at (NUDGE_MS * NUDGE_BACK).toInt()
            },
        ),
        label = "nudge",
    )
    val edge = MaterialTheme.colorScheme.background
    Layout(
        content = {
            Box(
                Modifier
                    .graphicsLayer {
                        val p = pop.value
                        val grown = POP_FROM_SCALE + (1f - POP_FROM_SCALE) * POP_EASING.transform(p)
                        alpha = (p / NUDGE_REST_UNTIL).coerceAtMost(1f)
                        scaleX = grown
                        scaleY = grown
                        translationX = -NUDGE_DP.dp.toPx() * nudge
                        translationY = (POP_RISE_DP * (1f - p) + NUDGE_DP * nudge).dp.toPx()
                        rotationZ = NUDGE_TILT * nudge
                    }
                    .shadow(6.dp, CircleShape)
                    .border(AVATAR_EDGE, edge, CircleShape)
                    .padding(AVATAR_EDGE)
                    .size(AVATAR - AVATAR_EDGE * 2),
            ) { LampMascot(size = AVATAR - AVATAR_EDGE * 2, mood = LampMood.HAPPY) }
            if (hint != null) {
                HintBubble(
                    hint,
                    Modifier.graphicsLayer {
                        alpha = bubble.value
                        translationY = (BUBBLE_RISE_DP * (1f - bubble.value)).dp.toPx()
                    },
                )
            }
        },
        modifier = Modifier.matchParentSize(),
    ) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val avatar = measurables[0].measure(loose)
        val note = measurables.getOrNull(1)?.measure(loose)
        layout(constraints.maxWidth, constraints.maxHeight) {
            val out = AVATAR_OUT.roundToPx()
            avatar.place(bounds.right.toInt() + out - avatar.width, bounds.top.toInt() - out)
            if (note != null) {
                val gap = BUBBLE_GAP.toPx()
                val below = bounds.bottom + gap
                val above = bounds.top - gap - note.height
                val (x, y) = when {
                    below + note.height <= constraints.maxHeight -> bounds.left - SPOTLIGHT_OUTSET.toPx() to below
                    above >= 0f -> bounds.left - SPOTLIGHT_OUTSET.toPx() to above
                    else -> bounds.right - gap - note.width to bounds.top + gap
                }
                val fitted = x.coerceIn(0f, (constraints.maxWidth - note.width).coerceAtLeast(0).toFloat())
                note.place(fitted.toInt(), y.toInt())
            }
        }
    }
}

@Composable
private fun HintBubble(hint: String, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .widthIn(max = BUBBLE_MAX)
            .shadow(12.dp, shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 10.dp),
    ) {
        Text(
            stringResource(Res.string.helper_name).uppercase(),
            color = spotlightGold(),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.1.em,
        )
        Text(
            hint,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
