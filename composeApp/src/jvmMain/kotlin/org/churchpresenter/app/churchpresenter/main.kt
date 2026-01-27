package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.Image
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.awt.GraphicsEnvironment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_book_1
import jdk.internal.org.jline.keymap.KeyMap.ctrl
import org.jetbrains.compose.resources.stringResource


fun main() = application {
    var openBlackWindow by remember { mutableStateOf(false) }

    // Get bounds of the second screen if present
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    val secondScreenBounds = if (screens.size > 1) screens[1].defaultConfiguration.bounds else null

    var showPicker by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Church Presenter",
    ) {
        MenuBar {
            Menu("File", mnemonic = 'F')
            {
                Item(
                    "New Schedule",
                    onClick = { /* TODO */ },
                    shortcut = KeyShortcut(ctrl = true, shift = true, key = Key.N)
                )
                Item(
                    "Open Schedule",
                    onClick = { /* TODO */ },
                    shortcut = KeyShortcut(ctrl = true, key = Key.O)
                )
                Item(
                    "Save Schedule",
                    onClick = { /* TODO */ },
                    shortcut = KeyShortcut(ctrl = true, key = Key.S)
                )
                Item(
                    "Save Schedule As...",
                    onClick = { /* TODO */ }
                )
                Item(
                    "Close Schedule",
                    onClick = { /* TODO */ },
                    shortcut = KeyShortcut(ctrl = true, key = Key.W)
                )
                Item(
                    "Print",
                    onClick = { /* TODO */ },
                    shortcut = KeyShortcut(ctrl = true, key = Key.P)
                )
                Item(
                    "Print Schedule",
                    onClick = { /* TODO */ },
                    shortcut = KeyShortcut(ctrl = true, shift = true, key = Key.P)
                )
                Item(
                    "Exit",
                    onClick = { exitApplication() },
                    shortcut = KeyShortcut(ctrl = true, key = Key.Q)
                )
            }
            Menu("Schedule", mnemonic = 'S')
            {
                Item(
                    "Add to Schedule",
                    onClick = { /* TODO */ },
                    shortcut = KeyShortcut(key = Key.F2)
                )
                Item(
                    "Remove from Schedule",
                    onClick = { /* TODO */ },
                    shortcut = KeyShortcut(key = Key.Delete)
                )
                Item(
                    "Clear Schedule",
                    onClick = { /* TODO */ }
                )
                Item(
                    "Move item to Top",
                    onClick = { /* TODO */ }
                )
                Item(
                    "Move item Up",
                    onClick = { /* TODO */ }
                )
                Item(
                    "Move item Down",
                    onClick = { /* TODO */ }
                )
                Item(
                    "Move item to Bottom",
                    onClick = { /* TODO */ }
                )
            }
            Menu("Edit", mnemonic = 'E') {
                Item("Clear Bible History List",
                    onClick = { /* TODO */ },
                    shortcut = KeyShortcut(ctrl = true, key = Key.Delete)
                )
                Item(
                    "Manage Databases",
                    onClick = { /* TODO */ },
                    shortcut = KeyShortcut(ctrl = true, key = Key.M)
                )
                Item(
                    "Settings",
                    onClick = { /* TODO */ },
                    shortcut = KeyShortcut(ctrl = true, key = Key.T)
                )
            }
            Menu("View") {
                Item(
                    "Song Counter",
                    onClick = {  },
                )
            }
            Menu("Display Screen") {
                Item(
                    "Show",
                    onClick = {  },
                    shortcut = KeyShortcut(key = Key.F4)
                )
                Item(
                    "Clear",
                    onClick = {  },
                    shortcut = KeyShortcut(key = Key.Escape, shift = true)
                )
                Item(
                    "Hide",
                    onClick = {  },
                    shortcut = KeyShortcut(key = Key.Escape)
                )
                Item(
                    "On / Off",
                    onClick = {  }
                )
            }
            Menu("Language", mnemonic = 'L') {

            }
            Menu("Help", mnemonic = 'H') {
                Item(
                    "About",
                    onClick = { /* TODO */ }
                )
                Item(
                    "Help",
                    onClick = { /* TODO */ },
                    shortcut = KeyShortcut(key = Key.F1)
                )

            }
        }
        Column {
            Button(onClick = { openBlackWindow = true }) {
                val book = stringResource(Res.string.bible_book_1)
                Text(book)
            }
            Button(onClick = { showPicker = true }) {
                Text("Open font picker ")
            }
        }
    }

    if (showPicker) {
        showSwingFontChooser()
    }

    if (openBlackWindow) {
        val windowState = remember {
            if (secondScreenBounds != null) {
                WindowState(
                    placement = WindowPlacement.Floating,
                    position = WindowPosition(
                        secondScreenBounds.x.dp, secondScreenBounds.y.dp
                    )
                )
            } else {
                WindowState(placement = WindowPlacement.Fullscreen)
            }
        }
        Window(
            onCloseRequest = { openBlackWindow = false },
            state = windowState,
        ) {
            PresenterScreen(modifier = Modifier.fillMaxSize()) {
                Text(text = "Hello world", color = Color.White)
            }
        }
    }
}