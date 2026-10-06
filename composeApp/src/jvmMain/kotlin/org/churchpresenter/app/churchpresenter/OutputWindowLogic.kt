package org.churchpresenter.app.churchpresenter

import org.churchpresenter.liveoutput.isScreenIndexValid
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.ScreenAssignment

private const val DEV_WINDOW_BASE_OFFSET_DP = 40

/** The box a dev fallback window is fitted into, whatever shape the output it stands for is. */
private const val DEV_WINDOW_MAX_WIDTH_DP = 960f
private const val DEV_WINDOW_MAX_HEIGHT_DP = 540f
private const val DEV_WINDOW_STEP_DP = 48

/**
 * Whether output windows have to fall back to ordinary windows on this machine.
 *
 * Only when there is genuinely nowhere else to put them — no second display and no DeckLink device
 * — and only in a build that asked for it, so a release install with one monitor shows nothing
 * extra rather than a stray window over the operator's screen.
 */
internal fun isDevWindowedFallback(
    isRelease: Boolean,
    forceDevWindow: Boolean,
    realWindowCount: Int,
): Boolean = (!isRelease || forceDevWindow) && realWindowCount == 0

/** How many fallback windows to open — at least one whenever they are used at all. */
internal fun devFallbackWindowCount(devWindowedFallback: Boolean, configured: Int): Int =
    if (devWindowedFallback) configured.coerceAtLeast(1) else 0

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

/** Which fallback window a slot is, counting from the first one after the real outputs. */
internal fun fallbackSlotIndex(index: Int, realWindowCount: Int): Int = index - realWindowCount

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
