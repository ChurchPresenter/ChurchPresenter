package org.churchpresenter.core.models.statistics

/**
 * One column of the activity chart: how much was shown in one bucket of the selected range.
 *
 * [label] is already formatted for the axis — the bucket size (weekly, monthly or yearly) is chosen
 * from the range length, so the caller draws what it is given rather than deciding a scale.
 */
data class ActivityPoint(
    val label: String,
    val songCount: Int,
    val verseCount: Int
)
