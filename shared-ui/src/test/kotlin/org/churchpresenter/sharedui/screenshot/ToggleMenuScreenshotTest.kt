@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_add
import org.churchpresenter.icons.generated.resources.ic_delete
import org.churchpresenter.icons.generated.resources.ic_folder
import org.churchpresenter.icons.generated.resources.ic_save
import org.churchpresenter.sharedui.composables.ContextMenu
import org.churchpresenter.sharedui.composables.ContextMenuDivider
import org.churchpresenter.sharedui.composables.ToggleMenuHeader
import org.churchpresenter.sharedui.composables.ToggleMenuItem
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import kotlin.test.Test

class ToggleMenuScreenshotTest {

    /** Icons on the left, one item hidden so it dims and Show all appears, the danger hue kept. */
    @Test
    fun `a menu of buttons, one of them hidden`() =
        captureComponent(SECTION, "toggle_menu", rootIndex = 1) {
            ContextMenu(expanded = true, onDismissRequest = {}, width = 300.dp) {
                ToggleMenuHeader(title = "Buttons", shown = 3, total = 4, onShowAll = {})
                val file = MaterialTheme.semantic.success
                ToggleMenuItem("New Schedule", true, {}, icon = painterResource(Res.drawable.ic_add), accent = file)
                ToggleMenuItem(
                    "Open Schedule", false, {}, icon = painterResource(Res.drawable.ic_folder), accent = file,
                )
                ToggleMenuItem("Save Schedule", true, {}, icon = painterResource(Res.drawable.ic_save), accent = file)
                ContextMenuDivider()
                ToggleMenuItem(
                    "Clear Schedule", true, {},
                    icon = painterResource(Res.drawable.ic_delete), accent = MaterialTheme.colorScheme.error,
                )
            }
        }

    /** The tab list: no icons, two tabs hidden, and the one still showing locked on. */
    @Test
    fun `a plain list with a locked item`() =
        captureComponent(SECTION, "toggle_menu_plain", rootIndex = 1) {
            ContextMenu(expanded = true, onDismissRequest = {}, width = 220.dp) {
                ToggleMenuHeader(title = "Tabs", shown = 1, total = 3, onShowAll = {})
                ToggleMenuItem("Bible", true, {}, enabled = false)
                ToggleMenuItem("Songs", false, {})
                ToggleMenuItem("Pictures", false, {})
            }
        }

    private companion object {
        const val SECTION = "toggleMenu"
    }
}
