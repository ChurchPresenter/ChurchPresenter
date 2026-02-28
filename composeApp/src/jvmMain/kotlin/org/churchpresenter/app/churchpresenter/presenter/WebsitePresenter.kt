package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView

private var jfxInitialized = false

private fun ensureJfxInitialized() {
    if (!jfxInitialized) {
        try {
            Platform.startup {}
            jfxInitialized = true
        } catch (_: IllegalStateException) {
            // Toolkit already initialized — that's fine
            jfxInitialized = true
        }
    }
}

@Composable
fun WebsitePresenter(
    url: String,
    modifier: Modifier = Modifier
) {
    if (url.isBlank()) return

    val jfxPanel = remember { JFXPanel() }

    DisposableEffect(url) {
        ensureJfxInitialized()
        Platform.runLater {
            val webView = WebView()
            webView.engine.load(url)
            jfxPanel.scene = Scene(webView)
        }
        onDispose {
            Platform.runLater {
                jfxPanel.scene = null
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = { jfxPanel }
        )
    }
}



