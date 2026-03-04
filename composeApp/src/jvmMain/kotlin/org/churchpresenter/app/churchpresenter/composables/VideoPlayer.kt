package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import javafx.embed.swing.JFXPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.AudioDevice
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Component
import java.io.File
import java.util.Locale
import javax.swing.SwingUtilities

/**
 * Initialises the JavaFX toolkit exactly once for the lifetime of the process.
 * Still needed for WebView (WebsitePresenter, LowerThirdSettingsTab).
 */
private object JfxInit {
    @Volatile private var initialised = false
    fun ensureInit() {
        if (!initialised) {
            synchronized(this) {
                if (!initialised) {
                    initialised = true
                    JFXPanel()
                }
            }
        }
    }
    fun initAsync() {
        if (initialised) return
        Thread(::ensureInit, "jfx-prewarm").apply { isDaemon = true; start() }
    }
}

/** Call once from main() to pre-warm JavaFX on a background thread. */
fun preWarmJavaFX() = JfxInit.initAsync()

private fun isMacOS(): Boolean {
    val os = System.getProperty("os.name", "generic").lowercase(Locale.ENGLISH)
    return "mac" in os || "darwin" in os
}

/** Returns true if VLC is installed and VLCJ can initialise. */
val isVlcAvailable: Boolean by lazy {
    try {
        createMediaPlayerComponent()?.let {
            when (it) {
                is CallbackMediaPlayerComponent -> it.release()
                is EmbeddedMediaPlayerComponent -> it.release()
            }
        }
        true
    } catch (_: Throwable) { false }
}

data class VlcAudioDevice(val id: String, val description: String)

/** Lists available audio output devices via VLCJ. */
fun listVlcAudioDevices(): List<VlcAudioDevice> {
    if (!isVlcAvailable) return emptyList()
    return try {
        val factory = MediaPlayerFactory()
        val mp = factory.mediaPlayers().newMediaPlayer()
        val devices = mp.audio().outputDevices()
            .map { VlcAudioDevice(it.deviceId, it.longName) }
        mp.release()
        factory.release()
        devices
    } catch (_: Throwable) { emptyList() }
}

/**
 * Creates a platform-appropriate VLCJ component.
 * macOS requires CallbackMediaPlayerComponent; Linux/Windows use EmbeddedMediaPlayerComponent.
 * See https://github.com/caprica/vlcj/issues/887#issuecomment-503288294
 */
private fun createMediaPlayerComponent(): Component? {
    return try {
        if (isMacOS()) CallbackMediaPlayerComponent()
        else EmbeddedMediaPlayerComponent()
    } catch (e: Throwable) {
        System.err.println("VLCJ: Could not initialise. Is VLC installed? ${e.message}")
        null
    }
}

/** Extracts the EmbeddedMediaPlayer from either component type. */
private fun Component.mediaPlayer(): EmbeddedMediaPlayer = when (this) {
    is CallbackMediaPlayerComponent -> mediaPlayer()
    is EmbeddedMediaPlayerComponent -> mediaPlayer()
    else -> error("Unexpected component type")
}

/** Releases the component. */
private fun Component.releasePlayer() = when (this) {
    is CallbackMediaPlayerComponent -> release()
    is EmbeddedMediaPlayerComponent -> release()
    else -> {}
}

/**
 * Embeds a VLCJ media player for local video/audio files.
 * Requires VLC to be installed on the system.
 * Works cross-platform: Linux, Windows, and macOS.
 */
@Composable
fun VideoPlayer(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier,
    audioEnabled: Boolean = true,
    audioDeviceId: String = ""
) {
    val component = remember { createMediaPlayerComponent() } ?: return
    val mp: EmbeddedMediaPlayer = component.mediaPlayer()

    DisposableEffect(Unit) {
        mp.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                viewModel.setDuration(newLength)
            }
            override fun finished(mediaPlayer: MediaPlayer) {
                if (audioEnabled) viewModel.pause()
            }
            override fun error(mediaPlayer: MediaPlayer) {
                System.err.println("VLCJ: Playback error")
            }
        })
        onDispose {
            mp.controls().stop()
            component.releasePlayer()
        }
    }

    // Apply audio output device
    LaunchedEffect(audioDeviceId) {
        if (audioDeviceId.isNotBlank()) {
            mp.audio().setOutputDevice(null, audioDeviceId)
        }
    }

    // Load media when URL changes
    LaunchedEffect(viewModel.mediaUrl) {
        val url = viewModel.mediaUrl
        mp.controls().stop()
        if (url.isBlank()) return@LaunchedEffect

        val mrl = try {
            val f = File(url)
            if (f.exists()) f.absolutePath else url
        } catch (_: Exception) { url }

        if (!audioEnabled) mp.audio().setVolume(0)
        else mp.audio().setVolume((viewModel.effectiveVolume * 100).toInt())

        mp.media().play(mrl)
        // If viewModel says paused, pause after a brief start
        if (!viewModel.isPlaying) {
            delay(150)
            SwingUtilities.invokeLater {
                if (!viewModel.isPlaying && mp.status().isPlaying) {
                    mp.controls().pause()
                }
            }
        }
    }

    // Play / pause sync
    LaunchedEffect(viewModel.isPlaying) {
        SwingUtilities.invokeLater {
            if (viewModel.isPlaying) {
                if (!mp.status().isPlaying) mp.controls().play()
            } else {
                if (mp.status().isPlaying) mp.controls().pause()
            }
        }
    }

    // Volume sync
    LaunchedEffect(viewModel.effectiveVolume) {
        if (audioEnabled) {
            mp.audio().setVolume((viewModel.effectiveVolume * 100).toInt())
        }
    }

    // Seek sync
    LaunchedEffect(viewModel.seekVersion) {
        if (viewModel.currentPosition >= 0) {
            mp.controls().setTime(viewModel.currentPosition)
        }
    }

    // Poll position for progress bar updates
    if (audioEnabled) {
        LaunchedEffect(viewModel.mediaUrl) {
            while (isActive) {
                delay(250)
                if (mp.status().isPlaying) {
                    viewModel.setCurrentPosition(mp.status().time())
                }
            }
        }
    }

    SwingPanel(factory = { component }, modifier = modifier)
}
