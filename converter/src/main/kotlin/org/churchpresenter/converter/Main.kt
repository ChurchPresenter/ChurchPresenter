package org.churchpresenter.converter

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.churchpresenter.converter.ui.App
import org.churchpresenter.converter.ui.ConverterTheme
import org.churchpresenter.converter.ui.Strings

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = Strings.windowTitle,
        state = rememberWindowState(width = 1100.dp, height = 800.dp)
    ) {
        ConverterTheme { App() }
    }
}
