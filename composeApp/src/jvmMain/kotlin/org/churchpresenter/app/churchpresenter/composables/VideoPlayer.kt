package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
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

/** Call once from main() to initialise JavaFX before other native toolkits (JCEF). */
fun preWarmJavaFX() = JfxInit.ensureInit()

private fun isMacOS(): Boolean {
    val os = System.getProperty("os.name", "generic").lowercase(Locale.ENGLISH)
    return "mac" in os || "darwin" in os
}

/** Custom VLC installation directory. Set from saved settings before first VLC access. */
var vlcCustomPath: String = ""

private var _vlcAvailable: Boolean? = null

/** Returns true if VLC is installed and VLCJ can initialise. */
val isVlcAvailable: Boolean get() = _vlcAvailable ?: checkVlcAvailable().also { _vlcAvailable = it }

/** Clears the cached result and re-checks VLC availability. */
fun recheckVlcAvailability(): Boolean {
    _vlcAvailable = null
    return isVlcAvailable
}

private fun checkVlcAvailable(): Boolean {
    return try {
        applyCustomVlcPath()
        if (!isVlcInstalledOnSystem()) {
            System.err.println("VLCJ: VLC not found on this system. Skipping initialisation.")
            return false
        }
        createMediaPlayerComponent()?.let {
            when (it) {
                is CallbackMediaPlayerComponent -> it.release()
                is EmbeddedMediaPlayerComponent -> it.release()
            }
        }
        true
    } catch (_: Throwable) { false }
}

/** If a custom VLC path is set and valid, adds it to jna.library.path so VLCJ/JNA can find native libs. */
private fun applyCustomVlcPath() {
    if (vlcCustomPath.isBlank()) return
    val dir = File(vlcCustomPath)
    if (!dir.isDirectory) return
    val current = System.getProperty("jna.library.path", "")
    if (current.contains(vlcCustomPath)) return
    val newPath = if (current.isBlank()) vlcCustomPath else "$vlcCustomPath${File.pathSeparator}$current"
    System.setProperty("jna.library.path", newPath)
}

/** Checks whether a directory contains a VLC native library (libvlc.dll / .so* / .dylib). */
private fun dirContainsVlcLib(dir: java.nio.file.Path): Boolean {
    if (!java.nio.file.Files.isDirectory(dir)) return false
    return try {
        java.nio.file.Files.list(dir).use { stream ->
            stream.anyMatch { path ->
                val name = path.fileName.toString()
                name == "libvlc.dll" || name == "libvlc.dylib" ||
                        name == "libvlc.so" || (name.startsWith("libvlc.so.") && !name.startsWith("libvlccore"))
            }
        }
    } catch (_: Exception) { false }
}

/** Returns the auto-detected VLC installation directory, or empty string if not found. */
fun detectVlcInstallPath(): String {
    val osName = System.getProperty("os.name", "").lowercase()
    return when {
        "win" in osName -> {
            val paths = listOfNotNull(
                System.getenv("VLC_PLUGIN_PATH")?.let { java.nio.file.Paths.get(it).parent },
                java.nio.file.Paths.get(System.getenv("ProgramFiles") ?: "C:\\Program Files", "VideoLAN", "VLC"),
                java.nio.file.Paths.get(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "VideoLAN", "VLC")
            )
            paths.firstOrNull { dirContainsVlcLib(it) }?.toString() ?: ""
        }
        "mac" in osName || "darwin" in osName -> {
            val libPath = java.nio.file.Paths.get("/Applications/VLC.app/Contents/MacOS/lib")
            if (dirContainsVlcLib(libPath)) libPath.toString()
            else if (java.nio.file.Files.exists(java.nio.file.Paths.get("/Applications/VLC.app"))) "/Applications/VLC.app"
            else ""
        }
        else -> {
            val libDirs = listOf(
                java.nio.file.Paths.get("/usr/lib"),
                java.nio.file.Paths.get("/usr/lib64"),
                java.nio.file.Paths.get("/usr/lib/x86_64-linux-gnu"),
                java.nio.file.Paths.get("/usr/lib/aarch64-linux-gnu"),
                java.nio.file.Paths.get("/snap/vlc/current/usr/lib")
            )
            libDirs.firstOrNull { dirContainsVlcLib(it) }?.toString() ?: ""
        }
    }
}

/** Checks common installation paths for the VLC native library on each OS. */
private fun isVlcInstalledOnSystem(): Boolean {
    // Check custom path first
    if (vlcCustomPath.isNotBlank()) {
        if (dirContainsVlcLib(java.nio.file.Paths.get(vlcCustomPath))) return true
    }
    if (detectVlcInstallPath().isNotBlank()) return true
    // Fallback for Linux: check if vlc is on PATH
    val osName = System.getProperty("os.name", "").lowercase()
    if ("win" !in osName && "mac" !in osName && "darwin" !in osName) {
        return try {
            val proc = ProcessBuilder("which", "vlc").start()
            proc.waitFor() == 0
        } catch (_: Exception) { false }
    }
    return false
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
                viewModel.markFinished()
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

/**
 * Software-rendering video player that works in any context (including offscreen DeckLink).
 * Uses VLCJ's CallbackVideoSurface to capture frames as BufferedImage → Compose Image.
 * Integrates with MediaViewModel for full playback control.
 */
@Composable
fun SoftwareVideoPlayer(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier,
    audioEnabled: Boolean = true,
    audioDeviceId: String = ""
) {
    if (!isVlcAvailable) return

    val currentFrame = remember { mutableStateOf<ImageBitmap?>(null) }

    val factory = remember {
        try { MediaPlayerFactory() } catch (_: Throwable) { null }
    } ?: return

    val mp = remember(factory) {
        try { factory.mediaPlayers().newEmbeddedMediaPlayer() } catch (_: Throwable) { null }
    } ?: return

    // Set up callback video surface for software rendering
    DisposableEffect(Unit) {
        var bufferedImage: java.awt.image.BufferedImage? = null

        val bufferFormatCallback = object : uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat {
                bufferedImage = java.awt.image.BufferedImage(sourceWidth, sourceHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                return uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat(sourceWidth, sourceHeight)
            }
            override fun allocatedBuffers(buffers: Array<out java.nio.ByteBuffer>) { }
        }

        val renderCallback = uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback { _, nativeBuffers, _ ->
            val img = bufferedImage ?: return@RenderCallback
            if (nativeBuffers == null || nativeBuffers.isEmpty()) return@RenderCallback
            val pixelData = (img.raster.dataBuffer as? java.awt.image.DataBufferInt)?.data ?: return@RenderCallback
            try {
                val buf = nativeBuffers[0] ?: return@RenderCallback
                buf.rewind()
                buf.asIntBuffer().get(pixelData, 0, pixelData.size.coerceAtMost(buf.remaining() / 4))
                currentFrame.value = img.toComposeImageBitmap()
            } catch (_: Throwable) { }
        }

        mp.videoSurface().set(
            factory.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, true)
        )

        mp.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                viewModel.setDuration(newLength)
            }
            override fun finished(mediaPlayer: MediaPlayer) {
                viewModel.markFinished()
            }
            override fun error(mediaPlayer: MediaPlayer) {
                System.err.println("VLCJ (software): Playback error")
            }
        })

        onDispose {
            try {
                mp.controls().stop()
                mp.release()
                factory.release()
            } catch (_: Throwable) { }
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

    // Render current frame as Compose Image
    currentFrame.value?.let { frame ->
        androidx.compose.foundation.Image(
            bitmap = frame,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}
