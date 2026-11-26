package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.churchpresenter.theme.ThemeColors
import org.churchpresenter.hosanna.composables.model.ProgressStatus

@Immutable
internal data class ProgressColors(
    val track: Color,
    val fill: Color,
    val status: Color,
    val label: Color,
    val detail: Color,
)

internal fun ThemeColors.progressColors(status: ProgressStatus): ProgressColors {
    val fill = when (status) {
        ProgressStatus.Running -> primary
        ProgressStatus.Finished -> success
        ProgressStatus.Failed -> error
        ProgressStatus.Paused -> onSurfaceFaint
    }
    return ProgressColors(
        track = outlineVariant,
        fill = fill,
        status = if (status == ProgressStatus.Paused) onSurfaceVariant else fill,
        label = onSurface,
        detail = onSurfaceVariant,
    )
}
