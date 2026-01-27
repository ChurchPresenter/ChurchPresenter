package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.menu_about
import churchpresenter.composeapp.generated.resources.menu_add_to_schedule
import churchpresenter.composeapp.generated.resources.menu_clear_schedule
import churchpresenter.composeapp.generated.resources.menu_close_schedule
import churchpresenter.composeapp.generated.resources.menu_edit
import churchpresenter.composeapp.generated.resources.menu_exit
import churchpresenter.composeapp.generated.resources.menu_file
import churchpresenter.composeapp.generated.resources.menu_help
import churchpresenter.composeapp.generated.resources.menu_help_item
import churchpresenter.composeapp.generated.resources.menu_new_schedule
import churchpresenter.composeapp.generated.resources.menu_open_schedule
import churchpresenter.composeapp.generated.resources.menu_remove_from_schedule
import churchpresenter.composeapp.generated.resources.menu_save_schedule
import churchpresenter.composeapp.generated.resources.menu_save_schedule_as
import churchpresenter.composeapp.generated.resources.menu_schedule
import churchpresenter.composeapp.generated.resources.menu_settings
import org.jetbrains.compose.resources.stringResource

@Composable
fun FrameWindowScope.NavigationTopBar(
    onNewSchedule: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    onSaveSchedule: () -> Unit = {},
    onSaveScheduleAs: () -> Unit = {},
    onCloseSchedule: () -> Unit = {},
    onExit: () -> Unit = {},
    onAddToSchedule: () -> Unit = {},
    onRemoveFromSchedule: () -> Unit = {},
    onClearSchedule: () -> Unit = {},
    onSettings: () -> Unit = {},
    onAbout: () -> Unit = {},
    onHelp: () -> Unit = {}
) {
    val fileLabel = stringResource(Res.string.menu_file)
    val fileMnemonic = fileLabel.firstOrNull() ?: 'F'

    val scheduleLabel = stringResource(Res.string.menu_schedule)
    val scheduleMnemonic = scheduleLabel.firstOrNull() ?: 'S'

    val editLabel = stringResource(Res.string.menu_edit)
    val editMnemonic = editLabel.firstOrNull() ?: 'E'

    val helpLabel = stringResource(Res.string.menu_help)
    val helpMnemonic = helpLabel.firstOrNull() ?: 'H'

    MenuBar {
        Menu(fileLabel, mnemonic = fileMnemonic) {
            Item(
                stringResource(Res.string.menu_new_schedule),
                onClick = onNewSchedule,
                shortcut = KeyShortcut(ctrl = true, shift = true, key = Key.N)
            )
            Item(
                stringResource(Res.string.menu_open_schedule),
                onClick = onOpenSchedule,
                shortcut = KeyShortcut(ctrl = true, key = Key.O)
            )
            Item(
                stringResource(Res.string.menu_save_schedule),
                onClick = onSaveSchedule,
                shortcut = KeyShortcut(ctrl = true, key = Key.S)
            )
            Item(
                stringResource(Res.string.menu_save_schedule_as),
                onClick = onSaveScheduleAs
            )
            Item(
                stringResource(Res.string.menu_close_schedule),
                onClick = onCloseSchedule,
                shortcut = KeyShortcut(ctrl = true, key = Key.W)
            )
            Item(
                stringResource(Res.string.menu_exit),
                onClick = onExit,
                shortcut = KeyShortcut(ctrl = true, key = Key.Q)
            )
        }

        Menu(scheduleLabel, mnemonic = scheduleMnemonic) {
            Item(stringResource(Res.string.menu_add_to_schedule), onClick = onAddToSchedule, shortcut = KeyShortcut(key = Key.F2))
            Item(stringResource(Res.string.menu_remove_from_schedule), onClick = onRemoveFromSchedule, shortcut = KeyShortcut(key = Key.Delete))
            Item(stringResource(Res.string.menu_clear_schedule), onClick = onClearSchedule)
        }

        Menu(editLabel, mnemonic = editMnemonic) {
            Item(stringResource(Res.string.menu_settings), onClick = onSettings, shortcut = KeyShortcut(ctrl = true, key = Key.T))
        }

        Menu(helpLabel, mnemonic = helpMnemonic) {
            Item(stringResource(Res.string.menu_about), onClick = onAbout)
            Item(stringResource(Res.string.menu_help_item), onClick = onHelp, shortcut = KeyShortcut(key = Key.F1))
        }
    }
}