package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants

/*
 * Monitors marked "Don't use": the app never opens an output window, a key window or a merged
 * picture on one, and never hands one to an output by position.
 *
 * Keyed by [screenKey] -- the monitor's geometry -- for the reason [ProjectionSettings.screenNames]
 * is: a device index is a position the window system reorders, and the mark has to follow the
 * hardware. A blank key (a row driving no monitor) is never stored.
 */

/** Whether the monitor [key] names is marked unused. A blank key names no monitor, so never. */
fun ProjectionSettings.isScreenUnused(key: String): Boolean = key.isNotEmpty() && key in unusedScreens

/** Whether the monitor with these bounds is marked unused -- see [isScreenUnused]. */
fun ProjectionSettings.isScreenUnused(boundsX: Int, boundsY: Int, boundsW: Int, boundsH: Int): Boolean =
    isScreenUnused(screenKey(boundsX, boundsY, boundsW, boundsH))

/**
 * Whether [assignment] drives nothing: its target is None, or the monitor it names is marked unused.
 * What every place that lists or counts the outputs on air asks, so a row pointing at an unused
 * monitor reads exactly as a None row does.
 */
fun ProjectionSettings.drivesNothing(assignment: ScreenAssignment): Boolean =
    assignment.targetDisplay == Constants.KEY_TARGET_NONE || isScreenUnused(assignment.targetScreenKey)

/**
 * Marks the monitor [key] unused, and takes it off every screen output's picture and key that
 * pointed at it -- those rows are set to None -- so nothing left in the settings still names it.
 * A blank key changes nothing.
 */
fun ProjectionSettings.withScreenUnused(key: String): ProjectionSettings {
    if (key.isEmpty()) return this
    return copy(
        unusedScreens = if (key in unusedScreens) unusedScreens else unusedScreens + key,
        screenAssignments = screenAssignments.map { it.withoutScreen(key) },
    )
}

/** Clears the unused mark on the monitor [key], so outputs may be pointed at it again. */
fun ProjectionSettings.withScreenUsed(key: String): ProjectionSettings =
    if (key in unusedScreens) copy(unusedScreens = unusedScreens - key) else this

/** [this] with its picture and its key taken off the monitor [key], wherever either named it. */
private fun ScreenAssignment.withoutScreen(key: String): ScreenAssignment {
    var result = this
    if (targetScreenKey == key) {
        result = result.copy(
            targetDisplay = Constants.KEY_TARGET_NONE,
            targetType = Constants.TARGET_TYPE_SCREEN,
            targetBoundsX = Int.MIN_VALUE,
            targetBoundsY = Int.MIN_VALUE,
            targetBoundsW = 0,
            targetBoundsH = 0,
        )
    }
    if (keyTargetScreenKey == key) {
        result = result.copy(
            keyTargetDisplay = Constants.KEY_TARGET_NONE,
            keyTargetType = Constants.TARGET_TYPE_SCREEN,
            keyTargetBoundsX = Int.MIN_VALUE,
            keyTargetBoundsY = Int.MIN_VALUE,
            keyTargetBoundsW = 0,
            keyTargetBoundsH = 0,
        )
    }
    return result
}
