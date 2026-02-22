package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import javafx.util.Duration
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import java.awt.BorderLayout
import java.io.File
import javax.swing.JPanel

/**
 * Initialises the JavaFX toolkit exactly once for the lifetime of the process.
 * Call [initAsync] early (e.g. from main) to pre-warm on a background thread.
 */
private object JfxInit {
    @Volatile private var initialised = false
    fun ensureInit() {
        if (!initialised) {
            synchronized(this) {
                if (!initialised) {
                    initialised = true
                    JFXPanel() // boots the JavaFX application thread
                }
            }
        }
    }
    /** Pre-warms JavaFX on a daemon thread so the Media tab opens instantly. */
    fun initAsync() {
        if (initialised) return
        Thread(::ensureInit, "jfx-prewarm").apply { isDaemon = true; start() }
    }
}

/** Call once from main() to pre-warm JavaFX on a background thread. */
fun preWarmJavaFX() = JfxInit.initAsync()

/**
 * Embeds a JavaFX MediaPlayer for local video files.
 * Every time this composable enters composition (e.g. tab switch back) it
 * rebuilds the player from scratch, so the video is always available.
 */
@Composable
fun VideoPlayer(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    JfxInit.ensureInit()

    val swingContainer = remember { JPanel(BorderLayout()) }
    val jfxPanel       = remember { JFXPanel() }
    val playerHolder   = remember { PlayerHolder() }

    // Attach JFXPanel on entry; tear down player on exit
    DisposableEffect(Unit) {
        swingContainer.add(jfxPanel, BorderLayout.CENTER)
        onDispose {
            Platform.runLater {
                playerHolder.clear()
                jfxPanel.scene = null
            }
            swingContainer.remove(jfxPanel)
        }
    }

    // Rebuild player when URL changes
    LaunchedEffect(viewModel.mediaUrl) {
        val url = viewModel.mediaUrl
        Platform.runLater {
            playerHolder.clear()
            if (url.isBlank()) {
                jfxPanel.scene = null
                return@runLater
            }
            buildPlayer(url, jfxPanel, playerHolder, viewModel)
        }
    }

    LaunchedEffect(viewModel.isPlaying) {
        Platform.runLater {
            val p = playerHolder.player ?: return@runLater
            if (viewModel.isPlaying) p.play() else p.pause()
        }
    }

    LaunchedEffect(viewModel.effectiveVolume) {
        Platform.runLater {
            playerHolder.player?.volume = viewModel.effectiveVolume.toDouble()
        }
    }

    LaunchedEffect(viewModel.seekVersion) {
        Platform.runLater {
            val p = playerHolder.player ?: return@runLater
            p.seek(Duration.millis(viewModel.currentPosition.toDouble()))
        }
    }

    SwingPanel(factory = { swingContainer }, modifier = modifier)
}

private fun buildPlayer(
    url: String,
    jfxPanel: JFXPanel,
    holder: PlayerHolder,
    viewModel: MediaViewModel
) {
    val uri = try {
        val f = File(url)
        if (f.exists()) f.toURI().toString() else url
    } catch (_: Exception) { url }

    try {
        val media  = Media(uri)
        val player = MediaPlayer(media)
        val view   = MediaView(player)
        view.isPreserveRatio = true

        val root = StackPane(view)
        root.style = "-fx-background-color: black;"
        view.fitWidthProperty().bind(root.widthProperty())
        view.fitHeightProperty().bind(root.heightProperty())

        jfxPanel.scene = Scene(root, javafx.scene.paint.Color.BLACK)

        player.setOnReady {
            viewModel.setDuration(player.totalDuration.toMillis().toLong())
        }
        player.currentTimeProperty().addListener { _, _, v: Duration ->
            viewModel.setCurrentPosition(v.toMillis().toLong())
        }
        player.setOnEndOfMedia {
            viewModel.pause()
            player.seek(Duration.ZERO)
        }

        holder.player = player
        player.volume = viewModel.effectiveVolume.toDouble()
        if (viewModel.isPlaying) player.play()

    } catch (_: Exception) {
        // Unsupported format — leave blank screen
    }
}

private class PlayerHolder {
    var player: MediaPlayer? = null
    fun clear() {
        player?.stop()
        player?.dispose()
        player = null
    }
}
