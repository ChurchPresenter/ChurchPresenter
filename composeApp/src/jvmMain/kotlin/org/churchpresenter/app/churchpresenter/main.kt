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
import androidx.compose.material.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.awt.GraphicsEnvironment
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.service_schedule
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
        title = "Church Presenter",
    ) {
        NavigationTopBar(
            onExit = { exitApplication() },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Row {
                Column(modifier = Modifier.fillMaxWidth(0.20f)) {
                    Text(text = stringResource(Res.string.service_schedule))
                    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                        // TODO add schedule code here
                    }
                }
                Column(modifier = Modifier.fillMaxWidth(0.6f)) {
                    var selectedTab by rememberSaveable { mutableStateOf("") }
                    TabSection { index ->
                        selectedTab = when(Tabs.entries[index]) {
                            Tabs.BIBLE -> "Bible"
                            Tabs.SONGS -> "Songs"
                            Tabs.ANNOUNCEMENTS -> "Announcements"
                            Tabs.MEDIA -> "Media"
                            Tabs.PICTURES -> "Pictures"
                        }
                    }
                    Text(
                        text = selectedTab,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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