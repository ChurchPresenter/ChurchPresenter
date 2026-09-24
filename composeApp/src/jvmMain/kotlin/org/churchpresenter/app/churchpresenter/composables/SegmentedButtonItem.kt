package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

data class SegmentedButtonItem<T>(
    val value: T,
    val label: String,
    val tooltip: String? = null,
    val icon: ImageVector? = null,
    /**
     * A test handle for this one segment.
     *
     * A segmented control is a single composable, so a caller that replaced a row of individually
     * tagged chips with one has nowhere else to put the tag its tests reach the segment by.
     */
    val testTag: String? = null,
    val width: Dp? = null,
)
