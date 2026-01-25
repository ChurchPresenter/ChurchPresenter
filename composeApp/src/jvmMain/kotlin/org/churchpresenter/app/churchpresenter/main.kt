package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

fun main() = application {
    var openBlackWindow by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "churchpresenter",
    ) {
        App()
        Button(onClick = { openBlackWindow = true }) {
            Text("Open Black Window")
        }
    }

    if (openBlackWindow) {
        Window(
            onCloseRequest = { openBlackWindow = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
    }
}