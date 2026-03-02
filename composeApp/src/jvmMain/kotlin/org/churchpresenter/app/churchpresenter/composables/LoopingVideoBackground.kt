package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import java.awt.BorderLayout
import java.io.File
import javax.swing.JPanel

/**
 * A self-contained looping video background with no audio.
 * Plays the video at [videoPath] in an infinite loop, filling the available space.
 */
@Composable
fun LoopingVideoBackground(
    videoPath: String,
    modifier: Modifier = Modifier
) {
    if (videoPath.isBlank()) return
    val file = remember(videoPath) { File(videoPath) }
    if (!file.exists()) return

    // Ensure JavaFX is initialised (shared with VideoPlayer)
    preWarmJavaFX()

    val swingContainer = remember { JPanel(BorderLayout()) }
    val jfxPanel = remember { JFXPanel() }

    DisposableEffect(videoPath) {
        swingContainer.add(jfxPanel, BorderLayout.CENTER)

        Platform.runLater {
            try {
                val media = Media(file.toURI().toString())
                val player = MediaPlayer(media).apply {
                    cycleCount = MediaPlayer.INDEFINITE
                    volume = 0.0
                    isAutoPlay = true
                }
                val view = MediaView(player).apply {
                    isPreserveRatio = false
                }
                val root = StackPane(view).apply {
                    style = "-fx-background-color: black;"
                }
                view.fitWidthProperty().bind(root.widthProperty())
                view.fitHeightProperty().bind(root.heightProperty())
                jfxPanel.scene = Scene(root, javafx.scene.paint.Color.BLACK)

                // Store player ref for cleanup
                jfxPanel.putClientProperty("bgPlayer", player)
            } catch (_: Exception) {
                // Unsupported format — leave blank
            }
        }

        onDispose {
            Platform.runLater {
                val player = jfxPanel.getClientProperty("bgPlayer") as? MediaPlayer
                player?.stop()
                player?.dispose()
                jfxPanel.scene = null
                jfxPanel.putClientProperty("bgPlayer", null)
            }
            swingContainer.remove(jfxPanel)
        }
    }

    SwingPanel(factory = { swingContainer }, modifier = modifier)
}
