package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import kotlinx.coroutines.delay
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.Component
import java.io.File
import java.util.Locale
import javax.swing.SwingUtilities

/**
 * A self-contained looping video background with no audio.
 * Plays the video at [videoPath] in an infinite loop, filling the available space.
 * Uses VLCJ — requires VLC to be installed on the system.
 */
@Composable
fun LoopingVideoBackground(
    videoPath: String,
    modifier: Modifier = Modifier
) {
    if (videoPath.isBlank()) return
    val file = remember(videoPath) { File(videoPath) }
    if (!file.exists()) return
    if (!isVlcAvailable) return

    val component = remember {
        try {
            val os = System.getProperty("os.name", "generic").lowercase(Locale.ENGLISH)
            if ("mac" in os || "darwin" in os) CallbackMediaPlayerComponent()
            else EmbeddedMediaPlayerComponent()
        } catch (_: Throwable) { null }
    } ?: return

    val mp = remember(component) {
        when (component) {
            is CallbackMediaPlayerComponent -> component.mediaPlayer()
            is EmbeddedMediaPlayerComponent -> component.mediaPlayer()
            else -> null
        }
    } ?: return

    DisposableEffect(Unit) {
        onDispose {
            try {
                mp.controls().stop()
                when (component) {
                    is CallbackMediaPlayerComponent -> component.release()
                    is EmbeddedMediaPlayerComponent -> component.release()
                }
            } catch (_: Throwable) { }
        }
    }

    LaunchedEffect(videoPath) {
        delay(200) // let SwingPanel attach to window
        SwingUtilities.invokeLater {
            try {
                mp.audio().setVolume(0)
                mp.media().play(file.absolutePath, ":input-repeat=65535")
            } catch (_: Throwable) { }
        }
    }

    SwingPanel(factory = { component as Component }, modifier = modifier)
}
