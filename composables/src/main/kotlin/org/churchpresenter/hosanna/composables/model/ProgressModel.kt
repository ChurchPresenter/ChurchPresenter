package org.churchpresenter.hosanna.composables.model

import kotlin.math.roundToInt

/**
 * Where a task stands, which picks the progress color and icon: [Running], [Finished], [Failed] or [Paused].
 */
enum class ProgressStatus { Running, Finished, Failed, Paused }

/**
 * The size of a [org.churchpresenter.hosanna.composables.progress.ProgressSpinner]: [Inline] (20dp) or
 * [Regular] (32dp).
 */
enum class ProgressSpinnerSize { Inline, Regular }

private const val PERCENT = 100

internal fun percentText(progress: Float): String = "${(progress.coerceIn(0f, 1f) * PERCENT).roundToInt()}%"
