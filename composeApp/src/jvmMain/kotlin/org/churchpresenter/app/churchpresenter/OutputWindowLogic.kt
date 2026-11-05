package org.churchpresenter.app.churchpresenter

import org.churchpresenter.liveoutput.isScreenIndexValid
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.DisplayRect
import org.churchpresenter.settings.ScreenAssignment

private const val DEV_WINDOW_BASE_OFFSET_DP = 40

/** The box a dev fallback window is fitted into, whatever shape the output it stands for is. */
private const val DEV_WINDOW_MAX_WIDTH_DP = 960f
private const val DEV_WINDOW_MAX_HEIGHT_DP = 540f
private const val DEV_WINDOW_STEP_DP = 48

/**
 * The on-screen size of a dev fallback window standing in for a [width] x [height] output.
 *
 * Scaled down to fit [DEV_WINDOW_MAX_WIDTH_DP] x [DEV_WINDOW_MAX_HEIGHT_DP] while keeping the
 * output's shape, so a 4:3 projector and a portrait confidence display are simulated at the shape
 * they actually are rather than all being drawn in one 16:9 box. 1920x1080 still lands on exactly
 * the 960x540 this used to hardcode.
 *
 * A degenerate size falls back to 16:9 rather than dividing by zero.
 */
internal fun devFallbackWindowSizeDp(width: Int, height: Int): Pair<Float, Float> {
    if (width <= 0 || height <= 0) return DEV_WINDOW_MAX_WIDTH_DP to DEV_WINDOW_MAX_HEIGHT_DP
    val scale = minOf(DEV_WINDOW_MAX_WIDTH_DP / width, DEV_WINDOW_MAX_HEIGHT_DP / height)
    return width * scale to height * scale
}

/** How far each extra fallback window is cascaded, so several do not stack exactly on each other. */
internal fun devFallbackWindowOffsetDp(index: Int): Int = DEV_WINDOW_BASE_OFFSET_DP + index * DEV_WINDOW_STEP_DP

/** The positions in [all] that are not [primary] — the displays an output may be opened on. */
internal fun <T> nonPrimaryIndices(all: List<T>, primary: T): List<Int> =
    all.indices.filter { all[it] != primary }

/** How many real outputs there are to drive: every non-primary display, plus every SDI device. */
internal fun presenterWindowCount(nonPrimaryScreens: Int, deckLinkDevices: Int): Int =
    nonPrimaryScreens + deckLinkDevices

/** Whether the slot at [index] is one of the fallback windows rather than a real output. */
internal fun isFallbackWindowSlot(
    devWindowedFallback: Boolean,
    index: Int,
    realWindowCount: Int,
): Boolean = devWindowedFallback && index >= realWindowCount

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
internal fun simulatedWindowSizeDp(assignment: ScreenAssignment, outputWidth: Int, outputHeight: Int): Pair<Float, Float> =
    if (assignment.simulatedWindowWidth > 0 && assignment.simulatedWindowHeight > 0) {
        assignment.simulatedWindowWidth.toFloat() to assignment.simulatedWindowHeight.toFloat()
    } else {
        devFallbackWindowSizeDp(outputWidth, outputHeight)
    }

/** Whether this output has no display chosen for it at all. */
internal fun hasNoPrimaryTarget(assignment: ScreenAssignment): Boolean =
    assignment.targetDisplay == Constants.KEY_TARGET_NONE

/**
 * Which display this output opens on, or none when nothing usable is left.
 *
 * Three attempts in order of how much they can be trusted: the bounds saved with the assignment,
 * which survive the OS reordering display indices; the saved index, but only if it still names an
 * attached display; and finally this output's own position in the list of non-primary displays, so
 * a configuration written on a different machine still lands somewhere rather than nowhere.
 */
internal fun primaryOutputScreenIndex(
    matchedByBounds: Int?,
    savedDisplay: Int,
    screenCount: Int,
    positionalFallback: Int?,
): Int? = matchedByBounds
    ?: savedDisplay.takeIf { isScreenIndexValid(it, screenCount) }
    ?: positionalFallback
