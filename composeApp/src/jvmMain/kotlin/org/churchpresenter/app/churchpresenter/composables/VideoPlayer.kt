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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import javax.swing.SwingUtilities
import androidx.compose.foundation.Image

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

internal fun isMacOS(): Boolean {
    val os = System.getProperty("os.name", "generic").lowercase(Locale.ENGLISH)
    return "mac" in os || "darwin" in os
}

/**
 * Singleton frame buffer written by the single master SoftwareVideoPlayer and read by
 * every SharedVideoOutputDisplay. This eliminates the need for multiple VLC decoder
 * instances when presenting on more than one screen.
 */
internal object SharedVideoOutput {
    val frame = mutableStateOf<ImageBitmap?>(null)
}

/**
 * Lightweight Compose composable that displays the latest frame from [SharedVideoOutput].
 * Uses no VLC instance — just renders the ImageBitmap written by the master SoftwareVideoPlayer.
 */
@Composable
fun SharedVideoOutputDisplay(modifier: Modifier = Modifier) {
    SharedVideoOutput.frame.value?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}

/** Custom VLC installation directory. Set from saved settings before first VLC access. */
var vlcCustomPath: String = ""

private var _vlcAvailable: Boolean? = null

/**
 * Human-readable reason VLC is unavailable; empty when VLC loaded successfully.
 * Populated by checkVlcAvailable() so the UI can show a targeted error message.
 */
var vlcUnavailableReason: String = ""

/** Returns true if VLC is installed and VLCJ can initialise. */
val isVlcAvailable: Boolean get() = _vlcAvailable ?: checkVlcAvailable().also { _vlcAvailable = it }

/**
 * True when VLC is present on disk but is the wrong CPU architecture
 * (e.g. x86_64 VLC on an Apple-Silicon Mac running an arm64 JVM).
 */
val isVlcArchMismatch: Boolean
    get() = !isVlcAvailable && "incompatible architecture" in vlcUnavailableReason.lowercase()

/** Clears the cached result and re-checks VLC availability. */
fun recheckVlcAvailability(): Boolean {
    _vlcAvailable = null
    vlcUnavailableReason = ""
    return isVlcAvailable
}

private fun checkVlcAvailable(): Boolean {
    return try {
        applyCustomVlcPath()
        if (!isVlcInstalledOnSystem()) {
            System.err.println("VLCJ: VLC not found on this system. Skipping initialisation.")
            vlcUnavailableReason = "not_found"
            return false
        }
        // Try to actually load the native library by creating a player component.
        val component = createMediaPlayerComponent()
        if (component == null) {
            // createMediaPlayerComponent() already logged the error and set vlcUnavailableReason.
            return false
        }
        when (component) {
            is CallbackMediaPlayerComponent -> component.release()
            is EmbeddedMediaPlayerComponent -> component.release()
        }
        true
    } catch (e: Throwable) {
        vlcUnavailableReason = e.message ?: "unknown error"
        false
    }
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
private fun dirContainsVlcLib(dir: Path): Boolean {
    if (!Files.isDirectory(dir)) return false
    return try {
        Files.list(dir).use { stream ->
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
                System.getenv("VLC_PLUGIN_PATH")?.let { Paths.get(it).parent },
                Paths.get(System.getenv("ProgramFiles") ?: "C:\\Program Files", "VideoLAN", "VLC"),
                Paths.get(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "VideoLAN", "VLC")
            )
            paths.firstOrNull { dirContainsVlcLib(it) }?.toString() ?: ""
        }
        "mac" in osName || "darwin" in osName -> {
            val libPath = Paths.get("/Applications/VLC.app/Contents/MacOS/lib")
            if (dirContainsVlcLib(libPath)) libPath.toString()
            else if (Files.exists(Paths.get("/Applications/VLC.app"))) "/Applications/VLC.app"
            else ""
        }
        else -> {
            val libDirs = listOf(
                Paths.get("/usr/lib"),
                Paths.get("/usr/lib64"),
                Paths.get("/usr/lib/x86_64-linux-gnu"),
                Paths.get("/usr/lib/aarch64-linux-gnu"),
                Paths.get("/snap/vlc/current/usr/lib")
            )
            libDirs.firstOrNull { dirContainsVlcLib(it) }?.toString() ?: ""
        }
    }
}

/** Checks common installation paths for the VLC native library on each OS. */
private fun isVlcInstalledOnSystem(): Boolean {
    // Check custom path first
    if (vlcCustomPath.isNotBlank()) {
        if (dirContainsVlcLib(Paths.get(vlcCustomPath))) return true
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
        val msg = e.message ?: e.toString()
        vlcUnavailableReason = msg
        System.err.println("VLCJ: Could not initialise. Is VLC installed? $msg")
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

    // True once VLC has delivered at least one frame. Used to give a 200 ms grace window
    // before auto-pausing on first load, so portrait/rotated videos have time to render
    // their first frame before the player is paused.
    val firstFrameCaptured = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        mp.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                if (newLength > 0) viewModel.setDuration(newLength)
            }
            override fun playing(mediaPlayer: MediaPlayer) {
                if (!viewModel.isPlaying) {
                    if (!firstFrameCaptured.value) {
                        // Delay pause by 200 ms so VLC can decode and render the first frame
                        // before being paused. Without this, portrait/MOV videos stay black.
                        javax.swing.Timer(200) {
                            if (!viewModel.isPlaying) mediaPlayer.controls().pause()
                        }.also { it.isRepeats = false; it.start() }
                    } else {
                        SwingUtilities.invokeLater { mediaPlayer.controls().pause() }
                    }
                }
            }
            override fun finished(mediaPlayer: MediaPlayer) {
                viewModel.markFinished()
            }
            override fun error(mediaPlayer: MediaPlayer) {
                System.err.println("VLCJ: Playback error for: ${viewModel.mediaUrl}")
                SwingUtilities.invokeLater { viewModel.pause() }
            }
            override fun videoOutput(mediaPlayer: MediaPlayer, newCount: Int) {
                // VLC confirmed a video output is present — mark first frame as captured
                // so subsequent play/pause cycles don't delay.
                if (newCount > 0) firstFrameCaptured.value = true
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
        firstFrameCaptured.value = false  // reset grace window for each new file
        mp.controls().stop()
        if (url.isBlank()) return@LaunchedEffect

        val mrl = try {
            val f = File(url)
            if (f.exists()) f.absolutePath else url
        } catch (_: Exception) { url }

        if (!audioEnabled) mp.audio().setVolume(0)
        else mp.audio().setVolume((viewModel.effectiveVolume * 100).toInt())

        mp.media().play(mrl)  // VideoPlayer is audio-only; no codec override needed
        // Auto-pause is handled by the playing() event listener above.
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

    // Poll position and, as a fallback, duration (in case lengthChanged fired too early).
    if (audioEnabled) {
        LaunchedEffect(viewModel.mediaUrl) {
            while (isActive) {
                delay(250)
                if (mp.status().isPlaying) {
                    viewModel.setCurrentPosition(mp.status().time())
                    // Fallback: pick up duration if the lengthChanged event was missed.
                    if (viewModel.duration == 0L) {
                        val len = mp.status().length()
                        if (len > 0) viewModel.setDuration(len)
                    }
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

    // On macOS, factory.mediaPlayers().newEmbeddedMediaPlayer() does NOT deliver video
    // frames to a callback surface — that requires CallbackMediaPlayerComponent.
    // On Linux/Windows, EmbeddedMediaPlayerComponent works fine.
    // createMediaPlayerComponent() already picks the right type per platform.
    val component = remember { createMediaPlayerComponent() } ?: return
    val mp: EmbeddedMediaPlayer = component.mediaPlayer()

    // A small factory used only to create the CallbackVideoSurface; the component
    // above manages its own internal factory for actual media playback.
    val surfaceFactory = remember {
        try { MediaPlayerFactory() } catch (_: Throwable) { null }
    } ?: return

    // True once the render callback has delivered at least one frame for the current URL.
    // Used to give VLC a brief window (200 ms) before auto-pausing on first load so that
    // even slow-starting or portrait/rotated videos have time to deliver their first frame.
    val firstFrameCaptured = remember { mutableStateOf(false) }

    // Set up callback video surface for software rendering
    DisposableEffect(Unit) {
        var bufferedImage: java.awt.image.BufferedImage? = null

        val bufferFormatCallback = object : uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat {
                val w = sourceWidth.coerceAtLeast(1)
                val h = sourceHeight.coerceAtLeast(1)
                bufferedImage = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
                return uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat(w, h)
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
                val bitmap = img.toComposeImageBitmap()
                currentFrame.value = bitmap
                SharedVideoOutput.frame.value = bitmap   // share to all presenter windows
                firstFrameCaptured.value = true
            } catch (_: Throwable) { }
        }

        // Setting a new video surface here replaces the component's internal surface,
        // directing all decoded frames to our renderCallback instead.
        mp.videoSurface().set(
            surfaceFactory.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, true)
        )

        mp.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                if (newLength > 0) viewModel.setDuration(newLength)
            }
            override fun playing(mediaPlayer: MediaPlayer) {
                if (!viewModel.isPlaying) {
                    if (!firstFrameCaptured.value) {
                        // Give VLC up to 200 ms to decode and deliver the first frame to the
                        // render callback before pausing. This is critical for portrait/rotated
                        // videos (e.g. iPhone MOV) where the decoder may take longer to start.
                        javax.swing.Timer(200) {
                            if (!viewModel.isPlaying) mediaPlayer.controls().pause()
                        }.also { it.isRepeats = false; it.start() }
                    } else {
                        SwingUtilities.invokeLater { mediaPlayer.controls().pause() }
                    }
                }
            }
            override fun finished(mediaPlayer: MediaPlayer) {
                viewModel.markFinished()
            }
            override fun error(mediaPlayer: MediaPlayer) {
                System.err.println("VLCJ (software): Playback error for: ${viewModel.mediaUrl}")
                SwingUtilities.invokeLater { viewModel.pause() }
            }
        })

        onDispose {
            try {
                mp.controls().stop()
                component.releasePlayer()
                surfaceFactory.release()
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
        firstFrameCaptured.value = false  // reset so next file gets the 200 ms grace window
        SharedVideoOutput.frame.value = null  // clear stale frame while new media loads
        mp.controls().stop()
        if (url.isBlank()) return@LaunchedEffect

        val mrl = try {
            val f = File(url)
            if (f.exists()) f.absolutePath else url
        } catch (_: Exception) { url }

        if (!audioEnabled) mp.audio().setVolume(0)
        else mp.audio().setVolume((viewModel.effectiveVolume * 100).toInt())

        // :codec=avcodec forces FFmpeg software decoding, bypassing VideoToolbox.
        // Required for Dolby Vision HEVC / 10-bit files where VideoToolbox outputs zero-copy
        // GPU CVPX buffers that the callback video surface cannot read (black frame).
        // :avcodec-fast reduces per-frame overhead; :clock-jitter=0 tightens frame scheduling.
        mp.media().play(mrl, ":codec=avcodec", ":avcodec-fast", ":clock-jitter=0")
        // Auto-pause is handled by the playing() event listener above.
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
                    // Fallback: pick up duration if lengthChanged was missed.
                    if (viewModel.duration == 0L) {
                        val len = mp.status().length()
                        if (len > 0) viewModel.setDuration(len)
                    }
                }
            }
        }
    }

    // Render current frame as Compose Image
    currentFrame.value?.let { frame ->
        Image(
            bitmap = frame,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}
