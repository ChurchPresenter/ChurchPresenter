package org.churchpresenter.slides.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.churchpresenter.sharedui.composables.ValueDialog
import org.churchpresenter.sharedui.composables.ValueDialogSpec
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.auto_scroll_interval
import org.churchpresenter.strings.generated.resources.auto_scroll_interval_hint
import org.churchpresenter.strings.generated.resources.transition_duration
import org.churchpresenter.strings.generated.resources.transition_duration_hint
import org.churchpresenter.strings.generated.resources.unit_ms
import org.churchpresenter.strings.generated.resources.unit_s
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

private val AUTO_SCROLL_PRESETS_SECONDS = listOf(3, 5, 10, 15)
private val TRANSITION_PRESETS_MS = listOf(250, 500, 750, 1000)
private const val TRANSITION_STEP_MS = 50

@Composable
internal fun AutoScrollIntervalDialog(initial: Int, maxSeconds: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    ValueDialog(
        title = stringResource(Res.string.auto_scroll_interval).removeSuffix(":"),
        hint = stringResource(Res.string.auto_scroll_interval_hint),
        icon = rememberVectorPainter(Icons.Default.Timer),
        accent = MaterialTheme.semantic.success,
        initial = initial,
        spec = ValueDialogSpec(1, maxSeconds, 1, AUTO_SCROLL_PRESETS_SECONDS, stringResource(Res.string.unit_s)),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun TransitionDurationDialog(
    initial: Int,
    minMs: Int,
    maxMs: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ValueDialog(
        title = stringResource(Res.string.transition_duration).removeSuffix(":"),
        hint = stringResource(Res.string.transition_duration_hint),
        icon = rememberVectorPainter(Icons.Default.Animation),
        accent = MaterialTheme.semantic.info,
        initial = initial,
        spec = ValueDialogSpec(
            minMs, maxMs, TRANSITION_STEP_MS, TRANSITION_PRESETS_MS, stringResource(Res.string.unit_ms),
        ),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
