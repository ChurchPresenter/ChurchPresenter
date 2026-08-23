package org.churchpresenter.ui

import androidx.compose.ui.graphics.vector.ImageVector

data class SegmentedButtonItem<T>(
    val value: T,
    val label: String,
    val tooltip: String? = null,
    val icon: ImageVector? = null
)
