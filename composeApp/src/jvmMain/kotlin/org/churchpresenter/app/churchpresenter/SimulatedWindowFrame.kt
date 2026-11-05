package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPosition
import kotlin.math.roundToInt
import org.churchpresenter.settings.DisplayRect
import org.churchpresenter.settings.ScreenAssignment

/** Where a simulated output's window was left, as [ScreenAssignment] keeps it — in dp. */
internal data class SimulatedWindowFrame(val x: Int, val y: Int, val width: Int, val height: Int)

/** [assignment] remembering that its simulated window was left at [frame]. */
internal fun withSimulatedFrame(assignment: ScreenAssignment, frame: SimulatedWindowFrame): ScreenAssignment =
    assignment.copy(
        simulatedWindowX = frame.x,
        simulatedWindowY = frame.y,
        simulatedWindowWidth = frame.width,
        simulatedWindowHeight = frame.height,
    )

/**
 * Where a simulated output's window opens: where it was last left, else its cascade spot.
 *
 * The saved spot is used only while its top-left corner is on a display attached now -- restored
 * onto a monitor that has since been unplugged, the window would open where nobody can see it.
 */
internal fun simulatedWindowPositionDp(
    assignment: ScreenAssignment,
    index: Int,
    displays: List<DisplayRect>,
): Pair<Int, Int> {
    val x = assignment.simulatedWindowX
    val y = assignment.simulatedWindowY
    val onADisplay = x != Int.MIN_VALUE && y != Int.MIN_VALUE &&
        displays.any { x >= it.x && x < it.right && y >= it.y && y < it.bottom }
    return if (onADisplay) x to y else devFallbackWindowOffsetDp(index) to devFallbackWindowOffsetDp(index)
}

/** The size a simulated output's window opens at: the size it was left at, else its output's shape. */
internal fun simulatedWindowSizeDp(
    assignment: ScreenAssignment,
    outputWidth: Int,
    outputHeight: Int,
): Pair<Float, Float> =
    if (assignment.simulatedWindowWidth > 0 && assignment.simulatedWindowHeight > 0) {
        assignment.simulatedWindowWidth.toFloat() to assignment.simulatedWindowHeight.toFloat()
    } else {
        devFallbackWindowSizeDp(outputWidth, outputHeight)
    }

/** Where a simulated window now is, to save on its output — or null while the window system has not placed it. */
internal fun simulatedWindowFrame(position: WindowPosition, size: DpSize): SimulatedWindowFrame? =
    if (!position.isSpecified) {
        null
    } else {
        SimulatedWindowFrame(
            position.x.value.roundToInt(), position.y.value.roundToInt(),
            size.width.value.roundToInt(), size.height.value.roundToInt(),
        )
    }
