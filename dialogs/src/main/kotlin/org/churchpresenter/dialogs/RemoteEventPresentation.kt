package org.churchpresenter.dialogs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/** What the remote-event dialog shows for the item at the front of the queue. */
data class RemoteEventPresentation(
    val actionLabel: String,
    val typeIcon: ImageVector,
    val typeAccent: Color,
    val bodyTitle: String,
    val remaining: Int,
    val showAllowPermanently: Boolean,
    val dialogTitle: String,
    val dialogHeight: Dp,
)
