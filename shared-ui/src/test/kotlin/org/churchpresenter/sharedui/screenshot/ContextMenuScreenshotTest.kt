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
import org.churchpresenter.sharedui.composables.BackgroundTarget
import org.churchpresenter.sharedui.composables.SetAsBackgroundItems
import org.churchpresenter.settings.BackgroundContent
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

    @Test
    fun `Set as Background, open, with the Songs background it already is marked`() =
        captureComponent(SECTION, "context_menu_set_as_background", rootIndex = 1) {
            ContextMenu(
                expanded = true,
                onDismissRequest = {},
                offset = DpOffset.Zero,
                header = {
                    ContextMenuHeader("3", "Sanctuary sunrise", "Welcome loop", MaterialTheme.semantic.contentPictures)
                },
            ) {
                ContextMenuItem(
                    "Go Live", painterResource(Res.drawable.ic_go_live), MaterialTheme.colorScheme.primary,
                    onClick = {}, emphasized = true,
                )
                ContextMenuItem(
                    "Add to Schedule", painterResource(Res.drawable.ic_playlist_add), MaterialTheme.semantic.success,
                    onClick = {},
                )
                ContextMenuDivider()
                SetAsBackgroundItems(
                    targets = listOf(
                        BackgroundTarget("main", "Screen 1 + NDI Output 1"),
                        BackgroundTarget("stage", "Stage Monitor"),
                    ),
                    startOpen = true,
                    isCurrent = { id, content -> id == "main" && content == BackgroundContent.SONG },
                    onSet = { _, _ -> },
                )
            }
        }

    private companion object {
        const val SECTION = "contextMenu"
    }
}
