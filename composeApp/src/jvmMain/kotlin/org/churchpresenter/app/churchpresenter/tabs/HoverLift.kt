package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val HOVER_LIFT_ELEVATION = 6.dp
private val HOVER_LIFT_SHIFT = 1.dp

internal fun Modifier.hoverLift(shape: Shape): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val elevation by animateDpAsState(if (hovered) HOVER_LIFT_ELEVATION else 0.dp, label = "hoverLift")
    this
        .hoverable(interaction)
        .graphicsLayer { translationY = if (hovered) -HOVER_LIFT_SHIFT.toPx() else 0f }
        .shadow(elevation, shape, clip = false)
}
