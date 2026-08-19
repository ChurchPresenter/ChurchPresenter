package lottiegen

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import lottiegen.editor.StyleEditorApp
import lottiegen.ui.Strings

fun main(args: Array<String>) = application {
    val editorMode = args.contains("--editor") || System.getProperty("lottiegen.editor") == "true"
    if (editorMode) {
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
            title = "Lottie Lower Third Generator",
            state = rememberWindowState(width = 1200.dp, height = 800.dp)
        ) {
            App()
        }
    }
}
