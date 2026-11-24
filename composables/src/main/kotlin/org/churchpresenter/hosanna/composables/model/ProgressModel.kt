package org.churchpresenter.hosanna.composables.model

import kotlin.math.roundToInt

enum class ProgressStatus { Running, Finished, Failed, Paused }

enum class ProgressSpinnerSize { Inline, Regular }

private const val PERCENT = 100

internal fun percentText(progress: Float): String = "${(progress.coerceIn(0f, 1f) * PERCENT).roundToInt()}%"
