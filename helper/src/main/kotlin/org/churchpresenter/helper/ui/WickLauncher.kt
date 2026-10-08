package org.churchpresenter.helper.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.resolve
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_close
import org.churchpresenter.strings.generated.resources.helper_name
import org.churchpresenter.strings.generated.resources.helper_intro_replay
import org.churchpresenter.strings.generated.resources.helper_intro_pointer
import org.churchpresenter.strings.generated.resources.helper_open
import org.churchpresenter.theme.elevationPalette
import org.jetbrains.compose.resources.stringResource

// Wick's own colours: the teal of its lamp and the gold of its flame, the same in every theme.
private val TealLight = Color(0xFF1A9A80)
private val TealDark = Color(0xFF0C5546)
private val FlameGlow = Color(0xFFF5C45A)
private val OnTeal = Color(0xFFE8FBF6)

private const val GLOW_WAITING = 0.55f
private const val GLOW_RESTING = 0.18f
private const val RING_ALPHA = 0.25f
internal const val FOCUS_RING_ALPHA = 0.3f
internal const val LINE_ALPHA = 0.5f

/** How far the flame's glow reaches past the lamp's edge, as a share of its size. */
private const val GLOW_REACH = 0.85f
private val TeaserShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 4.dp, bottomStart = 14.dp)
private const val AVATAR_GLOW_REACH = 0.75f

/** Where the teal is lightest, as a share of the height from the top: lit from above, like the flame. */
private const val LIGHT_FROM_TOP = 0.3f

/**
 * The round teal button: Wick, always. While the panel is open it bobs and two gold rings ripple
 * out from it; while closed, a badge counts what waits and the glow brightens.
 */
@Composable
internal fun Launcher(open: Boolean, waiting: Int, animate: Boolean, mood: LampMood, onClick: () -> Unit) {
    val label = stringResource(if (open) Res.string.helper_close else Res.string.helper_open)
    val glow = if (waiting > 0 && !open) GLOW_WAITING else GLOW_RESTING
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val grow by animateFloatAsState(
        when {
            pressed -> PRESSED_SCALE
            hovered -> HOVER_SCALE
            else -> 1f
        },
        tween(HOVER_MS),
        label = "launcherScale",
    )
    val moving = open && animate
    // Only composed while it shows, so a closed lamp's ripples are not ticking unseen.
    val motion = if (moving) launcherMotion() else remember { LauncherMotion(Still, Still, Still, Still) }
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(60.dp)
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                }
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(FlameGlow.copy(alpha = glow), Color.Transparent),
                            center = center,
                            radius = size.minDimension * GLOW_REACH,
                        ),
                        radius = size.minDimension * GLOW_REACH,
                    )
                    if (moving) {
                        drawRing(motion.ringA.value)
                        drawRing(motion.ringB.value)
                    }
                }
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .drawBehind { drawCircle(tealGradient(size.width, size.height)) }
                .clickable(interactionSource = interaction, indication = null, onClickLabel = label, onClick = onClick)
                .hoverable(interaction)
                .semantics { contentDescription = label }
                .testTag("helper.lamp"),
            contentAlignment = Alignment.Center,
        ) {
            LampMascot(
                size = 60.dp,
                mood = mood,
                animate = animate,
                modifier = Modifier.graphicsLayer {
                    if (moving) {
                        translationY = motion.bobY.value.dp.toPx()
                        rotationZ = motion.bobTurn.value
                    }
                },
            )
        }
        if (waiting > 0 && !open) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .heightIn(min = 19.dp)
                    .widthIn(min = 19.dp)
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(10.dp))
                    .border(2.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .padding(horizontal = 5.dp)
                    .testTag("helper.badge"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    waiting.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onError,
                )
            }
        }
    }
}

/** A gold ring [progress] of the way through its ripple: from just outside the button, growing and fading. */
private fun DrawScope.drawRing(progress: Float) {
    val radius = (size.minDimension / 2f + RING_OUTSET.toPx()) * (1f + (RING_GROWTH - 1f) * progress)
    drawCircle(
        FlameGlow.copy(alpha = RING_GOLD * RING_START_ALPHA * (1f - progress)),
        radius = radius,
        style = Stroke(RING_WIDTH.toPx()),
    )
}

private class LauncherMotion(
    val ringA: State<Float>,
    val ringB: State<Float>,
    val bobY: State<Float>,
    val bobTurn: State<Float>,
)

/** The design's `wickRing` (two, half a beat apart) and `wickBob`. */
@Composable
private fun launcherMotion(): LauncherMotion {
    val transition = rememberInfiniteTransition(label = "launcher")
    val ring = infiniteRepeatable<Float>(tween(RING_MS, easing = EaseOut))
    val ringA = transition.animateFloat(0f, 1f, ring, label = "ringA")
    val ringB = transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(RING_MS, easing = EaseOut), initialStartOffset = StartOffset(RING_MS / 2)),
        label = "ringB",
    )
    val bobY = transition.animateFloat(
        0f, 0f,
        infiniteRepeatable(
            keyframes {
                durationMillis = BOB_MS
                0f at 0 using EaseInOut
                -BOB_LIFT at BOB_MS / 4 using EaseInOut
                0f at BOB_MS / 2 using EaseInOut
                -BOB_LIFT at BOB_MS * 3 / 4 using EaseInOut
            },
        ),
        label = "bobY",
    )
    val bobTurn = transition.animateFloat(
        0f, 0f,
        infiniteRepeatable(
            keyframes {
                durationMillis = BOB_MS
                0f at 0 using EaseInOut
                -BOB_TURN at BOB_MS / 4 using EaseInOut
                0f at BOB_MS / 2 using EaseInOut
                BOB_TURN at BOB_MS * 3 / 4 using EaseInOut
            },
        ),
        label = "bobTurn",
    )
    return remember(transition) { LauncherMotion(ringA, ringB, bobY, bobTurn) }
}

private val Still: State<Float> = mutableFloatStateOf(0f)
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private const val RING_MS = 1800
private const val BOB_MS = 2400
private const val BOB_LIFT = 3f
private const val BOB_TURN = 4f
private const val RING_GROWTH = 1.32f
private const val RING_START_ALPHA = 0.9f
private const val RING_GOLD = 0.55f
private val RING_OUTSET = 4.dp
private val RING_WIDTH = 2.dp
private const val HOVER_SCALE = 1.06f
private const val PRESSED_SCALE = 0.97f
private const val HOVER_MS = 150

internal fun tealGradient(width: Float, height: Float) = Brush.radialGradient(
    listOf(TealLight, TealDark),
    center = Offset(width / 2f, height * LIGHT_FROM_TOP),
    radius = maxOf(width, height),
)

/** What Wick has waiting, shown over the closed lamp; clicking it opens the panel. */
@Composable
internal fun Teaser(text: HelperText, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = TeaserShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = LINE_ALPHA)),
        modifier = Modifier
            .padding(top = 8.dp)
            .widthIn(max = 250.dp)
            .floating(TeaserShape)
            .testTag("helper.teaser"),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
            Text(
                stringResource(Res.string.helper_name),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Just after the intro: where Wick went, and a way to see the intro again. */
@Composable
internal fun IntroPointer(onOpen: () -> Unit, onReplay: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = TeaserShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = LINE_ALPHA)),
        modifier = Modifier
            .padding(top = 8.dp)
            .widthIn(max = 250.dp)
            .floating(TeaserShape)
            .testTag("helper.introPointer"),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
            Text(stringResource(Res.string.helper_intro_pointer), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(Res.string.helper_intro_replay),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 5.dp).clickable(onClick = onReplay),
            )
        }
    }
}

/** Wick's face in the header: the lamp on its teal disc, ringed and faintly lit. */
@Composable
internal fun Avatar(animate: Boolean) {
    Box(
        Modifier
            .size(42.dp)
            .drawBehind {
                val glow = Brush.radialGradient(listOf(FlameGlow.copy(alpha = GLOW_RESTING), Color.Transparent))
                drawCircle(glow, radius = size.minDimension * AVATAR_GLOW_REACH)
            }
            .border(3.dp, TealLight.copy(alpha = RING_ALPHA), CircleShape)
            .clip(CircleShape)
            .drawBehind { drawCircle(tealGradient(size.width, size.height)) },
        contentAlignment = Alignment.Center,
    ) {
        LampMascot(size = 42.dp, animate = animate)
    }
}

/**
 * Lifted well off the page, like the design's panel: a deep, soft drop shadow, a tighter one under
 * it, and on a dark theme a faint light along the top edge.
 */
internal fun Modifier.floating(shape: Shape): Modifier = composed {
    val dark = elevationPalette().isDark
    val shade = Color.Black.copy(alpha = if (dark) DEEP_SHADOW_DARK else DEEP_SHADOW_LIGHT)
    this
        .shadow(FAR_SHADOW, shape, clip = false, ambientColor = shade, spotColor = shade)
        .shadow(NEAR_SHADOW, shape, clip = false, ambientColor = shade, spotColor = shade)
        .drawWithContent {
            drawContent()
            if (dark) {
                val inset = TOP_LIGHT_INSET.toPx()
                drawLine(
                    Color.White.copy(alpha = TOP_LIGHT_ALPHA),
                    Offset(inset, 1f),
                    Offset(size.width - inset, 1f),
                    strokeWidth = 1f,
                )
            }
        }
}

private val FAR_SHADOW = 24.dp
private val NEAR_SHADOW = 4.dp
private val TOP_LIGHT_INSET = 14.dp
private const val DEEP_SHADOW_DARK = 0.55f
private const val DEEP_SHADOW_LIGHT = 0.18f
private const val TOP_LIGHT_ALPHA = 0.05f
