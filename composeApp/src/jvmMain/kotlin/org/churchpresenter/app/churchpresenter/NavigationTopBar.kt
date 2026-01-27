package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar

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
    MenuBar {
        Menu("File", mnemonic = 'F') {
            Item(
                "New Schedule",
                onClick = onNewSchedule,
                shortcut = KeyShortcut(ctrl = true, shift = true, key = Key.N)
            )
            Item(
                "Open Schedule",
                onClick = onOpenSchedule,
                shortcut = KeyShortcut(ctrl = true, key = Key.O)
            )
            Item(
                "Save Schedule",
                onClick = onSaveSchedule,
                shortcut = KeyShortcut(ctrl = true, key = Key.S)
            )
            Item(
                "Save Schedule As...",
                onClick = onSaveScheduleAs
            )
            Item(
                "Close Schedule",
                onClick = onCloseSchedule,
                shortcut = KeyShortcut(ctrl = true, key = Key.W)
            )
            Item(
                "Exit",
                onClick = onExit,
                shortcut = KeyShortcut(ctrl = true, key = Key.Q)
            )
        }

        Menu("Schedule", mnemonic = 'S') {
            Item("Add to Schedule", onClick = onAddToSchedule, shortcut = KeyShortcut(key = Key.F2))
            Item("Remove from Schedule", onClick = onRemoveFromSchedule, shortcut = KeyShortcut(key = Key.Delete))
            Item("Clear Schedule", onClick = onClearSchedule)
        }

        Menu("Edit", mnemonic = 'E') {
            Item("Settings", onClick = onSettings, shortcut = KeyShortcut(ctrl = true, key = Key.T))
        }

        Menu("Help", mnemonic = 'H') {
            Item("About", onClick = onAbout)
            Item("Help", onClick = onHelp, shortcut = KeyShortcut(key = Key.F1))
        }
    }
}