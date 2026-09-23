package org.churchpresenter.lottiegen.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.RaisedFill
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.flatDisabled
import org.churchpresenter.theme.raised

/**
 * The app's raised key, clickable: [fill] lit from above, lifting under the pointer and pressing in
 * on click; flat and dimmed while not [enabled]. The same key every other ChurchPresenter window
 * draws its buttons with. Adds no size of its own -- the caller's height and padding stay exactly as
 * they were.
 */
@Composable
internal fun Modifier.raisedKey(
    shape: Shape,
    fill: RaisedFill,
    enabled: Boolean = true,
    role: Role? = null,
    lift: Dp = 2.dp,
    onClick: () -> Unit,
): Modifier {
    val palette = elevationPalette()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    return this
        .then(
            if (enabled) {
                Modifier.raised(shape, fill, palette, pressed = pressed, hovered = hovered, lift = lift)
            } else {
                Modifier.flatDisabled(shape, palette)
            }
        )
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}
