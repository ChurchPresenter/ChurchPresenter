package org.churchpresenter.lottiegen

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.churchpresenter.lottiegen.band.BibleLottieGenApp
import org.churchpresenter.lottiegen.editor.StyleEditorApp
import org.churchpresenter.lottiegen.ui.Strings

fun main(args: Array<String>) = application {
    val editorMode = args.contains("--editor") || System.getProperty("lottiegen.editor") == "true"
    val bandMode = args.contains("--band") || System.getProperty("lottiegen.band") == "true"
    if (bandMode) {
        // The band generator on its own, saving nowhere: for looking at it without the app.
        Window(
            onCloseRequest = ::exitApplication,
            title = Strings.bandAppTitle,
            state = rememberWindowState(width = 1320.dp, height = 760.dp)
        ) {
            BibleLottieGenApp(outputDir = null, onFileSaved = null, embedded = false)
        }
    } else if (editorMode) {
        Window(
            onCloseRequest = ::exitApplication,
            title = Strings.editorWindowTitle,
            state = rememberWindowState(width = 1500.dp, height = 950.dp)
        ) {
            StyleEditorApp(standalone = true)
        }
    } else {
        Window(
            onCloseRequest = ::exitApplication,
            title = Strings.appTitle,
            state = rememberWindowState(width = 1200.dp, height = 800.dp)
        ) {
            App()
        }
    }
}
