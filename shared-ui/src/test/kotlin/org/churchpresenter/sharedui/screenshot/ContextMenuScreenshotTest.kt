@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.DpOffset
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_delete
import org.churchpresenter.icons.generated.resources.ic_edit
import org.churchpresenter.icons.generated.resources.ic_go_live
import org.churchpresenter.icons.generated.resources.ic_playlist_add
import org.churchpresenter.icons.generated.resources.ic_star
import org.churchpresenter.sharedui.composables.ContextMenu
import org.churchpresenter.sharedui.composables.ContextMenuDivider
import org.churchpresenter.sharedui.composables.ContextMenuHeader
import org.churchpresenter.sharedui.composables.ContextMenuItem
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import kotlin.test.Test

class ContextMenuScreenshotTest {

    @Test
    fun `a menu with a header, a bold first item, keys and a danger item`() =
        captureComponent(SECTION, "context_menu", rootIndex = 1) {
            ContextMenu(
                expanded = true,
                onDismissRequest = {},
                offset = DpOffset.Zero,
                header = {
                    ContextMenuHeader("12", "Amazing Love", "Hymnal · 12", MaterialTheme.semantic.contentSongs)
                },
            ) {
                ContextMenuItem(
                    "Go Live", painterResource(Res.drawable.ic_go_live), MaterialTheme.colorScheme.primary,
                    onClick = {}, shortcut = "Enter", emphasized = true,
                )
                ContextMenuItem(
                    "Add to Schedule", painterResource(Res.drawable.ic_playlist_add), MaterialTheme.semantic.success,
                    onClick = {}, shortcut = "F2",
                )
                ContextMenuItem(
                    "Add to favorites", painterResource(Res.drawable.ic_star), MaterialTheme.semantic.favorite,
                    onClick = {},
                )
                ContextMenuItem(
                    "Edit Song", painterResource(Res.drawable.ic_edit), MaterialTheme.colorScheme.tertiary,
                    onClick = {},
                )
                ContextMenuDivider()
                ContextMenuItem(
                    "Delete", painterResource(Res.drawable.ic_delete), MaterialTheme.colorScheme.error,
                    onClick = {}, danger = true,
                )
            }
        }

    private companion object {
        const val SECTION = "contextMenu"
    }
}
