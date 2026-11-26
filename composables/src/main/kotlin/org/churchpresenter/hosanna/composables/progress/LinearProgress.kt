package org.churchpresenter.hosanna.composables.progress

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import org.churchpresenter.theme.AppTheme
import org.churchpresenter.hosanna.composables.button.StandardButton
import org.churchpresenter.hosanna.composables.defaults.ProgressDefaults
import org.churchpresenter.hosanna.composables.foundation.ProgressColors
import org.churchpresenter.hosanna.composables.foundation.progressColors
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ProgressStatus
import org.churchpresenter.theme.tabular
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_check
import org.churchpresenter.icons.generated.resources.ic_pause
import org.churchpresenter.icons.generated.resources.ic_warning
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * A progress bar with an optional label and detail, and finished, failed and paused states.
 *
 * @param progress from 0 to 1, or null when the total is not known.
 * @param modifier applied to the outermost layout.
 * @param label an optional title above the bar.
 * @param detail optional text at the end while running, such as "12 of 30".
 * @param status where the task stands, which picks the color.
 * @param statusText replaces [detail] when not running, such as "Done".
 * @param retryText turns on a Retry button while failed.
 * @param onRetry called when Retry is pressed.
 * @param retryInteractionSource drives the Retry button's looks.
 * @param thin draws a 3dp bar without text, for the top edge of a panel.
 */
@Composable
fun LinearProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    label: String? = null,
    detail: String? = null,
    status: ProgressStatus = ProgressStatus.Running,
    statusText: String? = null,
    retryText: String? = null,
    onRetry: () -> Unit = {},
    retryInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thin: Boolean = false,
) {
    val colors = AppTheme.colors.progressColors(status)
    val trailing = if (status == ProgressStatus.Running) detail else statusText
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ProgressDefaults.LabelGap)) {
        if (!thin && (label != null || trailing != null)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    label.orEmpty(),
                    Modifier.weight(1f),
                    style = AppTheme.typography.label.copy(color = colors.label),
                )
                ProgressStatusText(status, trailing, colors)
                if (status == ProgressStatus.Failed && retryText != null) {
                    StandardButton(
                        retryText, onRetry,
                        interactionSource = retryInteractionSource,
                        hierarchy = ButtonHierarchy.Tertiary,
                    )
                }
            }
        }
        ProgressBar(progress, colors, if (thin) ProgressDefaults.ThinTrack else ProgressDefaults.Track)
    }
}

@Composable
private fun ProgressStatusText(status: ProgressStatus, text: String?, colors: ProgressColors) {
    if (text == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(ProgressDefaults.StatusGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        statusIcon(status)?.let {
            Image(
                painterResource(it),
                null,
                Modifier.size(ProgressDefaults.StatusIcon),
                colorFilter = ColorFilter.tint(colors.status),
            )
        }
        val ink = if (status == ProgressStatus.Running) colors.detail else colors.status
        BasicText(text, style = AppTheme.typography.caption.tabular().copy(color = ink))
    }
}

internal fun statusIcon(status: ProgressStatus): DrawableResource? = when (status) {
    ProgressStatus.Running -> null
    ProgressStatus.Finished -> Res.drawable.ic_check
    ProgressStatus.Failed -> Res.drawable.ic_warning
    ProgressStatus.Paused -> Res.drawable.ic_pause
}

@Composable
private fun ProgressBar(progress: Float?, colors: ProgressColors, height: Dp) {
    val semantics = if (progress == null) {
        Modifier.progressSemantics()
    } else {
        Modifier.progressSemantics(progress.coerceIn(0f, 1f))
    }
    if (progress == null) {
        val t by rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(ProgressDefaults.SLIDE_MILLIS, easing = FastOutSlowInEasing),
                RepeatMode.Restart,
            ),
        )
        Canvas(semantics.fillMaxWidth().height(height)) {
            drawTrack(colors.track)
            val start = (t * (1 + ProgressDefaults.SEGMENT) - ProgressDefaults.SEGMENT) * size.width
            clipRect { drawPill(colors.fill, start, ProgressDefaults.SEGMENT * size.width) }
        }
    } else {
        val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(ProgressDefaults.FILL_MILLIS))
        Canvas(semantics.fillMaxWidth().height(height)) {
            drawTrack(colors.track)
            drawPill(colors.fill, 0f, shown * size.width)
        }
    }
}

private fun DrawScope.drawTrack(color: Color) = drawPill(color, 0f, size.width)

private fun DrawScope.drawPill(color: Color, x: Float, width: Float) {
    if (width <= 0f) return
    drawRoundRect(color, Offset(x, 0f), Size(width, size.height), CornerRadius(size.height / 2))
}
