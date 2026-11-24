package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize

/** A small up/down arrow for moving an item in a list, dimmed and inert when it can't move. */
@Composable
fun ReorderArrowButton(
    icon: Painter,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = Modifier.size(width = 22.dp, height = 16.dp),
        size = IconButtonSize.Small,
        hierarchy = ButtonHierarchy.Secondary,
        intent = ButtonIntent.Neutral,
        enabled = enabled,
    )
}
