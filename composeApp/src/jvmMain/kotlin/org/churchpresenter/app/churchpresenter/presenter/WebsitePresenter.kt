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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.embed.swing.JFXPanel
import javafx.embed.swing.SwingFXUtils
import javafx.event.ActionEvent
import javafx.scene.Scene
import javafx.scene.web.WebView
import javafx.util.Duration

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

/**
 * Embeds a JavaFX WebView in a Compose SwingPanel.
 *
 * [onUrlChanged] — called on the JavaFX thread whenever the page URL changes.
 * [onSnapshot]  — called every ~200 ms with an [ImageBitmap] of the current
 *                 WebView frame, so other Compose surfaces can mirror it.
 *                 Pass null to disable snapshotting (saves CPU).
 */
@Composable
fun EmbeddedWebView(
    url: String,
    modifier: Modifier = Modifier,
    onUrlChanged: ((String) -> Unit)? = null,
    onTitleChanged: ((String) -> Unit)? = null,
    onSnapshot: ((ImageBitmap) -> Unit)? = null
) {
    if (url.isBlank()) return

    val jfxPanel = remember { JFXPanel() }

    DisposableEffect(url) {
        ensureJfxInitialized()
        var locationListener: ChangeListener<String>? = null
        var titleListener: ChangeListener<String>? = null
        var snapshotTimeline: Timeline? = null

        Platform.runLater {
            val webView = WebView()

            // URL change listener
            if (onUrlChanged != null) {
                locationListener = ChangeListener { _, _, newUrl ->
                    if (!newUrl.isNullOrBlank()) onUrlChanged(newUrl)
                }
                webView.engine.locationProperty().addListener(locationListener)
            }

            // Page title listener
            if (onTitleChanged != null) {
                titleListener = ChangeListener { _, _, newTitle ->
                    onTitleChanged(newTitle ?: "")
                }
                webView.engine.titleProperty().addListener(titleListener)
            }

            // Snapshot timer — fires every 200 ms while the WebView is live
            if (onSnapshot != null) {
                snapshotTimeline = Timeline(
                    KeyFrame(Duration.millis(200.0), { _: ActionEvent ->
                        webView.snapshot(
                            { result ->
                                val writableImage = result.image
                                if (writableImage != null) {
                                    val buffered = SwingFXUtils.fromFXImage(writableImage, null)
                                    if (buffered != null) {
                                        onSnapshot(buffered.toComposeImageBitmap())
                                    }
                                }
                                null
                            },
                            null, null
                        )
                    })
                ).also {
                    it.cycleCount = Timeline.INDEFINITE
                    it.play()
                }
            }

            webView.engine.load(url)
            jfxPanel.scene = Scene(webView)
        }

        onDispose {
            Platform.runLater {
                snapshotTimeline?.stop()
                locationListener?.let { listener ->
                    (jfxPanel.scene?.root as? WebView)
                        ?.engine?.locationProperty()?.removeListener(listener)
                }
                titleListener?.let { listener ->
                    (jfxPanel.scene?.root as? WebView)
                        ?.engine?.titleProperty()?.removeListener(listener)
                }
                jfxPanel.scene = null
            }
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = { jfxPanel }
        )
    }
}

/** Full-screen presenter variant — no callbacks needed. */
@Composable
fun WebsitePresenter(
    url: String,
    modifier: Modifier = Modifier
) {
    EmbeddedWebView(url = url, modifier = modifier.fillMaxSize())
}
