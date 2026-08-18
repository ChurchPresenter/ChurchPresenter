import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ui.App
import ui.ConverterTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ChurchPresenter Converter",
        state = rememberWindowState(width = 1100.dp, height = 800.dp)
    ) {
        ConverterTheme { App() }
    }
}
