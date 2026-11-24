package org.churchpresenter.hosanna.composables.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.ProgressDefaults
import org.churchpresenter.hosanna.composables.foundation.FULL_TURN
import org.churchpresenter.hosanna.composables.foundation.Spinner
import org.churchpresenter.hosanna.composables.foundation.progressColors
import org.churchpresenter.hosanna.composables.model.ProgressSpinnerSize
import org.churchpresenter.hosanna.composables.model.ProgressStatus
import org.churchpresenter.hosanna.composables.model.percentText
import org.churchpresenter.hosanna.tabular
import org.jetbrains.compose.resources.painterResource

private const val TOP = -90f

@Composable
fun ProgressSpinner(modifier: Modifier = Modifier, size: ProgressSpinnerSize = ProgressSpinnerSize.Inline) {
    val colors = HosannaTheme.colors.progressColors(ProgressStatus.Running)
    Box(modifier) {
        Spinner(
            color = colors.fill,
            diameter = ProgressDefaults.spinner(size),
            stroke = ProgressDefaults.RingStroke,
            millis = ProgressDefaults.SPIN_MILLIS,
            track = colors.track,
        )
    }
}

@Composable
fun CircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    status: ProgressStatus = ProgressStatus.Running,
) {
    val colors = HosannaTheme.colors.progressColors(status)
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(ProgressDefaults.FILL_MILLIS))
    Box(
        modifier.size(ProgressDefaults.Ring).progressSemantics(progress.coerceIn(0f, 1f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val width = ProgressDefaults.RingStroke.toPx()
            val ring = Size(size.width - width, size.height - width)
            val topLeft = Offset(width / 2, width / 2)
            drawArc(colors.track, 0f, FULL_TURN, false, topLeft, ring, style = Stroke(width))
            val stroke = Stroke(width, cap = StrokeCap.Round)
            drawArc(colors.fill, TOP, FULL_TURN * shown, false, topLeft, ring, style = stroke)
        }
        val icon = statusIcon(status)
        if (icon != null) {
            Image(
                painterResource(icon),
                null,
                Modifier.size(ProgressDefaults.RingIcon),
                colorFilter = ColorFilter.tint(colors.status),
            )
        } else {
            BasicText(
                percentText(progress),
                style = HosannaTheme.typography.captionStrong.tabular().copy(color = colors.label),
            )
        }
    }
}
