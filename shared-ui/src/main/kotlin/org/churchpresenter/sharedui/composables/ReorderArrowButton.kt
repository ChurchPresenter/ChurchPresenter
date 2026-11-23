package org.churchpresenter.sharedui.composables

import org.churchpresenter.theme.flatDisabled
import org.churchpresenter.theme.elevationPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/** A small up/down arrow for moving an item in a list, dimmed and inert when it can't move. */
@Composable
fun ReorderArrowButton(
    icon: Painter,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = elevationPalette()
    Box(
        modifier = Modifier
            .size(width = 22.dp, height = 16.dp)
            .then(
                if (enabled) {
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, AppShape(5.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShape(5.dp))
                } else {
                    Modifier.flatDisabled(AppShape(5.dp), palette)
                }
            )
            .then(
                if (enabled) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(10.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else palette.disabledInk,
        )
    }
}
