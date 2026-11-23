package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The rounded track a player's transport keys sit on -- back, play, stop, forward -- so they read as
 * one control. The Media, Pictures and Presentation tabs all draw their transport on it.
 */
@Composable
fun Modifier.transportPill(): Modifier = this
    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = TRANSPORT_PILL_ALPHA), CircleShape)
    .padding(4.dp)

private const val TRANSPORT_PILL_ALPHA = 0.06f
