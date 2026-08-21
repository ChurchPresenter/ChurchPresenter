package org.churchpresenter.crossword

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.churchpresenter.crossword.ui.AdminApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ChurchPresenter Cross Editor",
        state = rememberWindowState(width = 1100.dp, height = 700.dp)
    ) {
        AdminApp()
    }
}
