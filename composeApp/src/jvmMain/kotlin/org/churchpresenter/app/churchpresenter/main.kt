package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.awt.GraphicsEnvironment
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.app_name
import churchpresenter.composeapp.generated.resources.book
import churchpresenter.composeapp.generated.resources.chapter
import churchpresenter.composeapp.generated.resources.service_schedule
import churchpresenter.composeapp.generated.resources.verse
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.jetbrains.compose.resources.stringResource


fun main() = application {
    var openBlackWindow by remember { mutableStateOf(false) }

    // Get bounds of the second screen if present
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    val secondScreenBounds = if (screens.size > 1) screens[1].defaultConfiguration.bounds else null

    var showPicker by remember { mutableStateOf(false) }
    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
    ) {
        NavigationTopBar(
            onExit = { exitApplication() },
        )
        MainDesktop()
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

@Preview(showBackground = true)
@Composable
fun MainPreview() {

}