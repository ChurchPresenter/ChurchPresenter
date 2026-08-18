package songlibrary

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import songlibrary.ui.SongLibraryApp
import songlibrary.ui.Strings
import java.io.File

/**
 * The library on its own, for working on a folder of songs without opening the app.
 *
 * The folder is the first argument, or `~/ChurchPresenter/Songs` — which is where the app puts one
 * by default — so `./gradlew :songlibrary:run` opens something real.
 */
fun main(args: Array<String>) = application {
    val folder = File(args.firstOrNull() ?: defaultLibraryFolder())
    Window(
        onCloseRequest = ::exitApplication,
        title = Strings["window_title"],
        state = rememberWindowState(width = 1420.dp, height = 880.dp),
    ) {
        // The same wrapper the app opens this window in, so the standalone build and the hosted
        // one are the same window -- and so `MaterialTheme.semantic` is provided either way.
        AppThemeWrapper(theme = ThemeMode.SYSTEM) {
            SongLibraryApp(libraryFolder = folder)
        }
    }
}

private fun defaultLibraryFolder(): String =
    File(System.getProperty("user.home"), "ChurchPresenter/Songs").absolutePath
