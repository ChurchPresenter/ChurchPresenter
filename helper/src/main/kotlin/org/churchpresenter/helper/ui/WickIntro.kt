package org.churchpresenter.helper.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_intro_ask_clear
import org.churchpresenter.strings.generated.resources.helper_intro_ask_countdown
import org.churchpresenter.strings.generated.resources.helper_intro_ask_help
import org.churchpresenter.strings.generated.resources.helper_intro_ask_phone
import org.churchpresenter.strings.generated.resources.helper_intro_ask_song
import org.churchpresenter.strings.generated.resources.helper_intro_ask_verse
import org.churchpresenter.strings.generated.resources.helper_intro_back
import org.churchpresenter.strings.generated.resources.helper_intro_body_1
import org.churchpresenter.strings.generated.resources.helper_intro_body_2
import org.churchpresenter.strings.generated.resources.helper_intro_finish
import org.churchpresenter.strings.generated.resources.helper_intro_next
import org.churchpresenter.strings.generated.resources.helper_intro_point_badge
import org.churchpresenter.strings.generated.resources.helper_intro_point_hide
import org.churchpresenter.strings.generated.resources.helper_intro_point_live
import org.churchpresenter.strings.generated.resources.helper_intro_reply_clear
import org.churchpresenter.strings.generated.resources.helper_intro_reply_countdown
import org.churchpresenter.strings.generated.resources.helper_intro_reply_help
import org.churchpresenter.strings.generated.resources.helper_intro_reply_phone
import org.churchpresenter.strings.generated.resources.helper_intro_reply_song
import org.churchpresenter.strings.generated.resources.helper_intro_reply_verse
import org.churchpresenter.strings.generated.resources.helper_intro_skip
import org.churchpresenter.strings.generated.resources.helper_intro_title_1
import org.churchpresenter.strings.generated.resources.helper_intro_title_2
import org.churchpresenter.strings.generated.resources.helper_intro_title_3
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.elevationPalette
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val STEPS = 3
private val CardShape = RoundedCornerShape(20.dp)
private val HeroTop = Color(0xFF2FB294)
private val HeroMid = Color(0xFF0F6F5C)
private val HeroEdge = Color(0xFF0A4A3E)
private val RingGold = Color(0xFFF5C45A)
private val SCRIM_DARK = Color(0xFF08090C).copy(alpha = 0.55f)
private val SCRIM_LIGHT = Color(0xFF141E2D).copy(alpha = 0.32f)
private const val SKIP_FILL = 0.12f
private const val SKIP_INK = 0.85f
private const val CHIP_TINT = 0.14f
private const val DOT_IDLE = 0.18f
private const val RING_MS = 2200
private const val BOB_MS = 2800
private const val RING_GROWTH = 1.32f
private const val RING_ALPHA = 0.5f

/** A question to try on step two, and what Wick says it would do. */
private class IntroQuestion(val ask: StringResource, val reply: StringResource)

private val QUESTIONS = listOf(
    IntroQuestion(Res.string.helper_intro_ask_song, Res.string.helper_intro_reply_song),
    IntroQuestion(Res.string.helper_intro_ask_verse, Res.string.helper_intro_reply_verse),
    IntroQuestion(Res.string.helper_intro_ask_clear, Res.string.helper_intro_reply_clear),
    IntroQuestion(Res.string.helper_intro_ask_countdown, Res.string.helper_intro_reply_countdown),
    IntroQuestion(Res.string.helper_intro_ask_phone, Res.string.helper_intro_reply_phone),
    IntroQuestion(Res.string.helper_intro_ask_help, Res.string.helper_intro_reply_help),
)

/**
 * "Meet Wick": three short steps over the dimmed window — who Wick is, a question to try, and how
 * it keeps out of the way — then [onDone], whether finished or skipped. Shown once, the first time.
 */
@Composable
fun WickIntro(onDone: () -> Unit, animate: Boolean = true, modifier: Modifier = Modifier) {
    var step by remember { mutableIntStateOf(0) }
    val scrim = if (elevationPalette().isDark) SCRIM_DARK else SCRIM_LIGHT
    Box(
        modifier
            .fillMaxSize()
            .background(scrim)
            // Swallows clicks, so the app behind waits until the intro is done.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .testTag("helper.intro"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = LINE_ALPHA)),
            modifier = Modifier.padding(20.dp).width(500.dp).floating(CardShape),
        ) {
            Column {
                IntroHero(onSkip = onDone, animate = animate)
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 178.dp).padding(start = 26.dp, end = 26.dp, top = 20.dp),
                ) {
                    when (step) {
                        0 -> IntroText(Res.string.helper_intro_title_1, Res.string.helper_intro_body_1)
                        1 -> IntroAsk(animate)
                        else -> IntroQuiet()
                    }
                }
                IntroFooter(
                    step = step,
                    onStep = { step = it },
                    onNext = { if (step == STEPS - 1) onDone() else step++ },
                )
            }
        }
    }
}

/** Wick large on its teal light, bobbing inside two rippling gold rings, with Skip in the corner. */
@Composable
private fun IntroHero(onSkip: () -> Unit, animate: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to HeroTop, 0.58f to HeroMid, 1f to HeroEdge,
                        center = Offset(size.width / 2f, size.height * 0.42f),
                        radius = size.width * 0.6f,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val motion = if (animate) heroMotion() else null
        Box(
            Modifier
                .size(92.dp)
                .drawBehind {
                    motion ?: return@drawBehind
                    listOf(motion.ringA, motion.ringB).forEach { progress ->
                        val p = progress.value
                        drawCircle(
                            RingGold.copy(alpha = RING_ALPHA * (1f - p)),
                            radius = (size.minDimension / 2f + 6.dp.toPx()) * (1f + (RING_GROWTH - 1f) * p),
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                }
                .graphicsLayer {
                    if (motion != null) {
                        translationY = motion.bobY.value.dp.toPx()
                        rotationZ = motion.bobTurn.value
                    }
                },
        ) {
            LampMascot(size = 92.dp, animate = animate)
        }
        Text(
            stringResource(Res.string.helper_intro_skip),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = SKIP_INK),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = SKIP_FILL))
                .clickable(onClick = onSkip)
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .testTag("helper.intro.skip"),
        )
    }
}

private class HeroMotion(
    val ringA: State<Float>,
    val ringB: State<Float>,
    val bobY: State<Float>,
    val bobTurn: State<Float>,
)

/** The design's slower hero beats: rings every 2.2s, half a beat apart, and a 2.8s bob. */
@Composable
private fun heroMotion(): HeroMotion {
    val transition = rememberInfiniteTransition(label = "intro")
    val easeOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val easeInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    val ringA = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(RING_MS, easing = easeOut)), label = "a")
    val ringB = transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(RING_MS, easing = easeOut), initialStartOffset = StartOffset(RING_MS / 2)),
        label = "b",
    )
    fun bob(peak: Float, alternate: Boolean) = infiniteRepeatable<Float>(
        keyframes {
            durationMillis = BOB_MS
            0f at 0 using easeInOut
            -peak at BOB_MS / 4 using easeInOut
            0f at BOB_MS / 2 using easeInOut
            (if (alternate) peak else -peak) at BOB_MS * 3 / 4 using easeInOut
        },
    )
    val bobY = transition.animateFloat(0f, 0f, bob(3f, alternate = false), label = "y")
    val bobTurn = transition.animateFloat(0f, 0f, bob(4f, alternate = true), label = "turn")
    return remember(transition) { HeroMotion(ringA, ringB, bobY, bobTurn) }
}

@Composable
private fun IntroText(title: StringResource, body: StringResource) {
    IntroTitle(title)
    Text(
        stringResource(body),
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun IntroTitle(title: StringResource) {
    Text(
        stringResource(title),
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** Step two: a few requests as chips; picking one shows what Wick would do with it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntroAsk(animate: Boolean) {
    var asked by remember { mutableStateOf<IntroQuestion?>(null) }
    val colors = MaterialTheme.colorScheme
    IntroText(Res.string.helper_intro_title_2, Res.string.helper_intro_body_2)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        QUESTIONS.forEach { question ->
            val picked = asked == question
            val shape = RoundedCornerShape(15.dp)
            Text(
                stringResource(question.ask),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (picked) colors.primary else colors.onSurface,
                modifier = Modifier
                    .clip(shape)
                    .background(if (picked) colors.primary.copy(alpha = CHIP_TINT) else lifted(CARD_LIFT))
                    .border(1.dp, if (picked) colors.primary else colors.outlineVariant, shape)
                    .clickable { asked = question }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
    asked?.let { question ->
        Row(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.size(22.dp).clip(CircleShape)) { LampMascot(size = 22.dp, animate = animate) }
            Text(
                stringResource(question.reply),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.onSurface,
                modifier = Modifier
                    .background(
                        lifted(BUBBLE_LIFT),
                        RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 12.dp),
                    )
                    .padding(horizontal = 11.dp, vertical = 8.dp),
            )
        }
    }
}

/** Step three: quiet while live, tips as a badge, and how to hide it. */
@Composable
private fun IntroQuiet() {
    IntroTitle(Res.string.helper_intro_title_3)
    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        IntroPoint(Icons.Filled.NightsStay, Res.string.helper_intro_point_live)
        IntroPoint(Icons.Filled.Circle, Res.string.helper_intro_point_badge)
        IntroPoint(Icons.Filled.VisibilityOff, Res.string.helper_intro_point_hide)
    }
}

@Composable
private fun IntroPoint(icon: ImageVector, text: StringResource) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).background(colors.primary.copy(alpha = CHIP_TINT), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
        }
        Text(
            stringResource(text),
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            color = colors.onSurface,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** The step dots, which jump to a step; Back from the second step on; Next, then Let's go. */
@Composable
private fun IntroFooter(step: Int, onStep: (Int) -> Unit, onNext: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(STEPS) { i ->
                val width by animateDpAsState(if (i == step) 20.dp else 7.dp, label = "dot")
                Box(
                    Modifier
                        .size(width = width, height = 7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i == step) colors.primary else colors.onSurface.copy(alpha = DOT_IDLE))
                        .clickable { onStep(i) },
                )
            }
        }
        if (step > 0) {
            GhostButton(
                onClick = { onStep(step - 1) },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.height(34.dp),
            ) { Text(stringResource(Res.string.helper_intro_back)) }
        }
        RaisedButton(
            onClick = onNext,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.height(34.dp).testTag("helper.intro.next"),
        ) {
            Text(stringResource(if (step == STEPS - 1) Res.string.helper_intro_finish else Res.string.helper_intro_next))
        }
    }
}
