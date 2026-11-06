package org.churchpresenter.sharedui.utils

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether outputs with nothing real behind them open as windows on the operator's screen: dev mode
 * is on and Developer → Display → Simulate Missing Outputs is ticked. Provided by the app; the saved
 * setting alone does nothing, so it cannot keep opening windows once dev mode is off.
 */
val LocalSimulateMissingOutputs = compositionLocalOf { false }

/**
 * How many output slots open as simulated windows, after the [realWindowCount] real ones.
 *
 * Slots are numbered across both: simulated window `realWindowCount + k` is output
 * `realWindowCount + k`, with that output's own assignment, profile and size. So plugging a monitor
 * in takes the first simulated slot over as a real one, and unplugging it hands the slot back.
 *
 * [configured] is `ProjectionSettings.devWindowCount`, the number of outputs this machine should
 * have. [developmentFallback] is the older rule a development build (or `forceDevWindow`) keeps:
 * simulate only when there is no real output at all. [simulateMissing] is
 * [LocalSimulateMissingOutputs].
 */
fun simulatedOutputCount(
    realWindowCount: Int,
    configured: Int,
    developmentFallback: Boolean,
    simulateMissing: Boolean,
): Int {
    if (!simulatesOutputs(realWindowCount, developmentFallback, simulateMissing)) return 0
    return (configured.coerceAtLeast(1) - realWindowCount).coerceAtLeast(0)
}

/** Whether the Simulate Outputs count can be set on this machine now — see [simulatedOutputCount]. */
fun simulatesOutputs(realWindowCount: Int, developmentFallback: Boolean, simulateMissing: Boolean): Boolean =
    simulateMissing || (developmentFallback && realWindowCount == 0)
