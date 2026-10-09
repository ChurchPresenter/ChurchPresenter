package org.churchpresenter.profiles

import org.churchpresenter.sharedui.guide.GuideTarget

/**
 * One choice of a [RowSegmented]: what it stands for, what it says, its test handle, and what the
 * helper's tours call it.
 */
internal data class RowOption<T>(
    val value: T,
    val label: String,
    val testTag: String? = null,
    val guideTarget: GuideTarget? = null,
)
