package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants

internal fun ScreenAssignment.forQuickDisplay(display: Int?): ScreenAssignment =
    if (display == null) this else copy(
        targetDisplay = display, targetType = "screen", targetBoundsX = Int.MIN_VALUE,
        keyTargetDisplay = Constants.KEY_TARGET_NONE,
    )

internal fun nextProjectionTarget(settings: ProjectionSettings, displayCount: Int): ProjectionSettings {
    val current = settings.quickPlayDisplay.takeUnless { settings.windowedProjection }
    val next = when {
        displayCount <= 0 -> null
        current == null -> 0
        current + 1 < displayCount -> current + 1
        else -> null
    }
    return settings.copy(windowedProjection = next == null, quickPlayDisplay = next)
}
