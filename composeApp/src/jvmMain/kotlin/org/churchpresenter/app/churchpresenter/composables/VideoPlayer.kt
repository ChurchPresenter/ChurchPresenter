package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import org.churchpresenter.diagnostics.CrashReporter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.SubtitleTrack
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import androidx.compose.runtime.MutableState
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.awt.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import javax.swing.SwingUtilities
import androidx.compose.foundation.Image
import org.churchpresenter.app.churchpresenter.utils.DevFlags

private const val POSITION_POLL_MS = 200
private const val VOLUME_PERCENT_SCALE = 100
private const val STATE_SETTLE_MS = 250L
private const val FRAME_INTERVAL_MS = 16L

/** RV32 is four bytes to the pixel, which is how many pixels a native buffer holds. */
private const val RV32_BYTES_PER_PIXEL = 4

// libvlc media options, passed to play() one argument at a time rather than as an array: play() is
// a Java vararg, so handing it an array makes the compiler copy that array at every call -- which
// is exactly what detekt's SpreadOperator rule is there to stop.
private const val VLC_OPT_SOFTWARE_CODEC = ":codec=avcodec"
private const val VLC_OPT_FAST_DECODE = ":avcodec-fast"
private const val VLC_OPT_TIGHT_CLOCK = ":clock-jitter=0"
private const val VLC_OPT_NO_AUDIO = ":no-audio"
private const val VLC_OPT_SUB_FILE = ":sub-file="

/** The media option that hands VLC an external subtitle file, or nothing when there is none. */
internal fun subtitleMediaOptions(subtitleUrl: String): Array<String> =
    if (subtitleUrl.isBlank()) emptyArray() else arrayOf(VLC_OPT_SUB_FILE + subtitleUrl)

/** The subtitle tracks VLC lists for the playing media, without its own "Disable" entry (id -1). */
private fun MediaPlayer.subtitleTracks(): List<SubtitleTrack> =
    subpictures().trackDescriptions()
        .filter { it.id() >= 0 }
        .map { SubtitleTrack(it.id(), it.description()) }

/**
 * Initialises the JavaFX toolkit exactly once for the lifetime of the process.
 * Still needed for WebView (WebsitePresenter).
 */
internal fun isJavaFxScreenReconfigRace(throwable: Throwable): Boolean =
    throwable is NullPointerException &&
        throwable.stackTrace.any {
            it.className.startsWith("com.sun.glass.ui.Screen") ||
                it.className.startsWith("com.sun.javafx.tk.quantum.QuantumToolkit")
        }

private object JfxInit {
    @Volatile private var initialised = false

    /** False once the toolkit has been tried and refused to start; see [ensureInit]. */
    @Volatile var available = true
        private set

    fun ensureInit() {
        if (!initialised) {
            synchronized(this) {
                if (!initialised) {
                    initialised = true
                    // Suppress "unnamed module" warning — JavaFX is intentionally loaded
                    // from the classpath in this Compose Desktop build configuration
                    java.util.logging.Logger.getLogger("com.sun.javafx.application.PlatformImpl")
                        .level = java.util.logging.Level.SEVERE
                    // A machine whose JavaFX natives cannot start — no Prism pipeline, a headless
                    // or restricted session, an incomplete install — throws out of the JFXPanel
                    // constructor as `RuntimeException: No toolkit found`, or as a linkage error
                    // from the native load. This runs on `main` before the window exists, so an
                    // escape is a silent failure to launch at all. JavaFX drives nothing the app
                    // cannot do without, so the toolkit is marked unavailable and startup carries
                    // on. Throwable, not Exception: the native failures are Errors. A
                    // VirtualMachineError is rethrown — the JVM is out of headroom, and carrying on
                    // only moves the crash somewhere unrelated.
                    try {
                        JFXPanel()
                    } catch (vme: VirtualMachineError) {
                        throw vme
                    } catch (t: Throwable) {
                        available = false
                        CrashReporter.reportWarning(
                            "JavaFX toolkit unavailable (continuing without it)",
                            throwable = t,
                            tags = mapOf("subsystem" to "javafx_init")
                        )
                        return
                    }
                    // Screen.notifySettingsChanged -> QuantumToolkit.assignScreensAdapters can NPE
                    // deep inside Prism/Glass when the OS reports a display change (monitor
                    // plugged/unplugged) while Prism's GraphicsPipeline isn't fully initialised.
                    // The whole stack is JavaFX-internal with no app frames, so it can't be guarded
                    // with a try/catch at a call site — install a thread-local handler instead that
                    // downgrades just this known race to a warning and defers everything else to
                    // the JVM's default handler.
                    Platform.runLater {
                        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
                        Thread.currentThread().uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
                            if (isJavaFxScreenReconfigRace(throwable)) {
                                CrashReporter.reportWarning(
                                    "JavaFX screen-reconfiguration NPE (suppressed, known Prism/Glass race)",
                                    throwable = throwable,
                                    tags = mapOf("subsystem" to "javafx_screen")
                                )
                            } else {
                                defaultHandler?.uncaughtException(thread, throwable)
                            }
                        }
                    }
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

/**
 * False when the toolkit refused to start. Diagnostics only — it is reported as a crash-service tag
 * so these machines are identifiable, and nothing branches on it, because nothing in the app depends
 * on JavaFX being alive. A future JavaFX consumer would be the thing that has to consult it.
 */
internal fun isJavaFxAvailable(): Boolean = JfxInit.available

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
 * A 1×1 transparent stand-in, drawn by [SharedVideoOutputDisplay] before any real frame exists.
 *
 * Every output window that can show media -- the presenter window, an NDI/Browser Source output --
 * composes this the moment it opens, long before a clip is ever loaded. Skipping the `Image` call
 * entirely until the first real frame (as this used to) meant that window's graphics surface had
 * never actually drawn a bitmap through Skia until the moment Go Live handed it its first one --
 * texture upload and shader compilation for that surface were still cold, paid for exactly when a
 * decoder was also actively converting and delivering frames, and the two together were the
 * stutter on the very first clip of a session. Drawing this placeholder as soon as the window
 * exists moves that one-time cost there instead, where nothing else is competing for it.
 */
private val emptyFramePlaceholder: ImageBitmap by lazy {
    BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).toComposeImageBitmap()
}

/**
 * Lightweight Compose composable that displays the latest frame from [SharedVideoOutput].
 * Uses no VLC instance — just renders the ImageBitmap written by the master SoftwareVideoPlayer.
 */
@Composable
fun SharedVideoOutputDisplay(modifier: Modifier = Modifier) {
    Image(
        bitmap = SharedVideoOutput.frame.value ?: emptyFramePlaceholder,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
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
    get() = vlcArchMismatchFrom(isVlcAvailable, vlcUnavailableReason)

/**
 * The arch-mismatch decision itself, over the two values [isVlcArchMismatch] reads from globals.
 *
 * Split out because those globals cannot be driven from a test: `isVlcAvailable` caches the result of
 * actually loading libvlc on the machine running the suite, so on a developer's box with VLC
 * installed the getter can only ever answer `false` and the branch that matters is unreachable.
 */
internal fun vlcArchMismatchFrom(available: Boolean, reason: String): Boolean =
    !available && "incompatible architecture" in reason.lowercase()

/**
 * True when a VLC installation was detected on disk but the native library still
 * failed to load (e.g. a corrupted/partial install, or on Windows a missing
 * dependency such as the Visual C++ Redistributable — LoadLibrary reports this as
 * "The specified module could not be found" even though libvlc.dll itself exists).
 * Distinct from "not installed" so the UI doesn't tell the user to install something
 * that is already there.
 */
val isVlcLoadFailed: Boolean
    get() = vlcLoadFailedFrom(isVlcAvailable, vlcUnavailableReason)

/**
 * The load-failed decision itself, over the two values [isVlcLoadFailed] reads from globals — split
 * out for the same reason as [vlcArchMismatchFrom].
 *
 * The three exclusions are what separate "installed but broken" from the two conditions with their
 * own messaging: a blank reason or the literal `not_found` means VLC simply isn't there, and an arch
 * mismatch is reported as itself rather than as a generic load failure.
 */
internal fun vlcLoadFailedFrom(available: Boolean, reason: String): Boolean =
    !available && reason.isNotBlank() && reason != "not_found" &&
        !vlcArchMismatchFrom(available, reason)

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
internal fun applyCustomVlcPath() {
    if (vlcCustomPath.isBlank()) return
    val dir = File(vlcCustomPath)
    if (!dir.isDirectory) return
    val current = System.getProperty("jna.library.path", "")
    if (current.contains(vlcCustomPath)) return
    val newPath = if (current.isBlank()) vlcCustomPath else "$vlcCustomPath${File.pathSeparator}$current"
    System.setProperty("jna.library.path", newPath)
}

/** Checks whether a directory contains a VLC native library (libvlc.dll / .so* / .dylib). */
internal fun dirContainsVlcLib(dir: Path): Boolean {
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
fun detectVlcInstallPath(): String = detectVlcInstallPathFor(System.getProperty("os.name", "").lowercase())

/**
 * The auto-detected VLC directory for [osName], or empty when VLC isn't in any of the usual places.
 *
 * [osName] is a parameter rather than read from `os.name` here so a test can walk all three platforms'
 * candidate lists without swapping the system property — skiko latches that JVM-wide and would take
 * every later Compose test in the same JVM down with it.
 *
 * The macOS branch is the one that does not simply return the first hit: when `VLC.app` is present but
 * its `MacOS/lib` holds no libvlc, the bundle root is returned anyway, because that is still where the
 * user installed VLC and JNA may yet find the library through it.
 */
internal fun detectVlcInstallPathFor(
    osName: String,
    /**
     * Whether a directory holds libvlc. A parameter for the same reason [osName] is one: left
     * reading the real filesystem, every assertion below becomes a statement about whether the
     * developer happens to have VLC installed. `detectVlcInstallPath finds nothing on a forced
     * Windows OS name` asserted exactly that and failed on any machine with VLC in Program Files.
     */
    hasVlcLib: (Path) -> Boolean = ::dirContainsVlcLib,
    /** Whether a path exists at all — only the macOS bundle fallback needs it. */
    pathExists: (Path) -> Boolean = Files::exists,
): String {
    return when {
        "win" in osName -> {
            val paths = listOfNotNull(
                System.getenv("VLC_PLUGIN_PATH")?.let { Paths.get(it).parent },
                Paths.get(System.getenv("ProgramFiles") ?: "C:\\Program Files", "VideoLAN", "VLC"),
                Paths.get(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "VideoLAN", "VLC")
            )
            paths.firstOrNull { hasVlcLib(it) }?.toString() ?: ""
        }
        "mac" in osName || "darwin" in osName -> {
            val libPath = Paths.get("/Applications/VLC.app/Contents/MacOS/lib")
            if (hasVlcLib(libPath)) libPath.toString()
            else if (pathExists(Paths.get("/Applications/VLC.app"))) "/Applications/VLC.app"
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
            libDirs.firstOrNull { hasVlcLib(it) }?.toString() ?: ""
        }
    }
}

/** Checks common installation paths for the VLC native library on each OS. */
private fun isVlcInstalledOnSystem(): Boolean =
    vlcInstalledOn(System.getProperty("os.name", "").lowercase(), vlcCustomPath, ::readCommandOutput)

/**
 * Whether VLC is installed, given the OS, the user's configured [customPath] and a way to run
 * `which`.
 *
 * The custom path is consulted before the well-known ones so a deliberately chosen install always
 * wins over whatever else happens to be on the machine. `which vlc` is a Linux-only last resort:
 * it proves the *player* is on PATH, not that libvlc is anywhere JNA will look, so it is worth
 * trying only where distributions reliably ship the two together.
 */
internal fun vlcInstalledOn(osName: String, customPath: String, run: CommandRunner): Boolean {
    val custom = customPath.takeIf { it.isNotBlank() }
        ?.let { try { Paths.get(it) } catch (_: Exception) { null } }
    if (custom != null && dirContainsVlcLib(custom)) return true
    if (detectVlcInstallPathFor(osName).isNotBlank()) return true
    val unixLike = "win" !in osName && "mac" !in osName && "darwin" !in osName
    return unixLike && run(listOf("which", "vlc"), 0L).exitCode == 0
}

data class VlcAudioDevice(val id: String, val description: String)

/**
 * Drops VLC's own "use whatever the system is using" entry, which it reports with an **empty**
 * device id and a description in VLC's language rather than the app's ("Default" in English).
 *
 * The app already offers that choice itself — the first item in the dropdown, labelled with the
 * localized `audio_output_default` string — and stores it as an empty `audioOutputDeviceId`.
 * Keeping VLC's copy as well put two rows meaning the same thing in the menu, and because the
 * stored id is `""` it also *matched* VLC's entry, so the closed button showed VLC's untranslated
 * description instead of the app's string on every machine whose VLC reports a default device
 * (which is every machine with a working audio output).
 */
internal fun withoutVlcDefaultDevice(devices: List<VlcAudioDevice>): List<VlcAudioDevice> =
    devices.filter { it.id.isNotBlank() }

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
        withoutVlcDefaultDevice(devices)
    } catch (_: Throwable) { emptyList() }
}

/**
 * Creates a platform-appropriate VLCJ component.
 * macOS requires CallbackMediaPlayerComponent; Linux/Windows use EmbeddedMediaPlayerComponent.
 * See https://github.com/caprica/vlcj/issues/887#issuecomment-503288294
 */
internal fun createMediaPlayerComponent(): Component? {
    return try {
        if (isMacOS()) CallbackMediaPlayerComponent()
        else EmbeddedMediaPlayerComponent()
    } catch (e: Throwable) {
        val msg = e.message ?: e.toString()
        vlcUnavailableReason = msg
        System.err.println("VLCJ: Could not initialise. Is VLC installed? $msg")
        // Not reported to CrashReporter: this is a known, expected condition (missing VLC,
        // arch mismatch, or a missing Windows dependency like the VC++ Redistributable) with
        // its own dedicated UI messaging (isVlcArchMismatch / isVlcLoadFailed) — it's already
        // "handled" and surfaced to the user, not an unexpected app crash worth telemetry noise.
        null
    }
}

/** Extracts the EmbeddedMediaPlayer from either component type. */
internal fun Component.mediaPlayer(): EmbeddedMediaPlayer = when (this) {
    is CallbackMediaPlayerComponent -> mediaPlayer()
    is EmbeddedMediaPlayerComponent -> mediaPlayer()
    else -> error("Unexpected component type")
}

/** Releases the component. */
internal fun Component.releasePlayer() = when (this) {
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

    // Native-handle lifetime guard and the deferred pause it protects — see PlayerReleaseGate.
    val gate = remember { PlayerReleaseGate() }
    val pauseTimer = remember { mutableStateOf<javax.swing.Timer?>(null) }

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
                        // The timer is held so onDispose can stop it: switching media disposes
                        // this composable well inside those 200 ms, and the callback would
                        // otherwise pause a player that has already been released.
                        pauseTimer.value?.stop()
                        pauseTimer.value = javax.swing.Timer(POSITION_POLL_MS) {
                            gate.ifLive {
                                if (!viewModel.isPlaying) {
                                    mediaPlayer.controls().pause()
                                    // Rewinds to the start of the file this load-grace decode
                                    // ran ahead into, so the first real Go Live resumes from an
                                    // explicit seek -- exactly what Stop does before a second
                                    // Play -- rather than from wherever those ~200ms of decode
                                    // happened to land. An operator-initiated pause mid-playback
                                    // never reaches this branch (firstFrameCaptured is already
                                    // true by then), so nothing here touches a real pause.
                                    mediaPlayer.controls().setTime(0)
                                }
                            }
                        }.also { it.isRepeats = false; it.start() }
                    } else {
                        SwingUtilities.invokeLater { gate.ifLive { mediaPlayer.controls().pause() } }
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
            // Before releasePlayer(), so anything already queued sees the player as gone.
            gate.release()
            pauseTimer.value?.stop()
            try {
                mp.controls().stop()
                component.releasePlayer()
            } catch (_: Throwable) { }
        }
    }

    // Apply audio output device
    LaunchedEffect(audioDeviceId) {
        if (audioDeviceId.isNotBlank()) {
            mp.audio().setOutputDevice(null, audioDeviceId)
        }
    }

    // Load media when the URL changes, and again on every loop restart: once VLC has reached the
    // end of a file it is in the Ended state, where setTime()/play() alone will not start it over.
    LaunchedEffect(viewModel.mediaUrl, viewModel.loopRestartVersion) {
        val url = viewModel.mediaUrl
        firstFrameCaptured.value = false  // reset grace window for each new file
        mp.controls().stop()
        if (url.isBlank()) return@LaunchedEffect

        val mrl = try {
            val f = File(url)
            if (f.exists()) f.absolutePath else url
        } catch (_: Exception) { url }

        // Stay muted until playback is actually requested. Loading always briefly starts the
        // VLC pipeline to capture a first frame (see playing() below), and without this guard
        // that grace window would be audible even though the file is meant to load paused.
        //
        // Muted, not silenced by volume: the real volume is set here too, so libVLC's audio
        // output device is asked to exist during this load grace window rather than for the
        // first time at Go Live -- see the isPlaying effect below for why that ordering matters.
        if (audioEnabled) mp.audio().setVolume((viewModel.effectiveVolume * VOLUME_PERCENT_SCALE).toInt())
        mp.audio().setMute(!audioEnabled || !viewModel.isPlaying)

        // When the caller has determined this instance must never produce audio (e.g. a
        // background decoder mounted only to keep rendering a paused frame), disable the
        // audio track outright with :no-audio.
        if (!audioEnabled) mp.media().play(mrl, ":no-audio")
        else mp.media().play(mrl)  // VideoPlayer is audio-only; no codec override needed
        // Auto-pause is handled by the playing() event listener above.
    }

    // Play / pause sync
    // We always send the command unconditionally: mp.status().isPlaying() can return a stale
    // value on the EDT (VLC may be buffering/transitioning) and guard-skipping the call is what
    // causes the button to get stuck. libvlc play/pause on an already-playing/paused player
    // is a documented no-op, so this is safe.
    LaunchedEffect(viewModel.isPlaying) {
        SwingUtilities.invokeLater {
            if (viewModel.isPlaying) {
                // Lifts the mute the load effect set for the first-frame grace window — the
                // audio device itself has been live since load, so this is not the first time
                // it's asked to exist.
                if (audioEnabled) mp.audio().setMute(false)
                mp.controls().play()
            } else {
                if (audioEnabled) mp.audio().setMute(true)
                mp.controls().pause()
            }
        }
    }

    // Volume sync
    LaunchedEffect(viewModel.effectiveVolume) {
        if (audioEnabled) {
            mp.audio().setVolume((viewModel.effectiveVolume * VOLUME_PERCENT_SCALE).toInt())
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
                delay(STATE_SETTLE_MS)
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
 * A factory used only to create the CallbackVideoSurface; the media player component manages its
 * own internal factory for the playback itself. Null when VLC cannot be initialised at all.
 */
@Composable
private fun rememberSurfaceFactory(): MediaPlayerFactory? = remember {
    try { MediaPlayerFactory() } catch (t: Throwable) {
        CrashReporter.reportException(t, "VideoPlayer: VLC MediaPlayerFactory init failed"); null
    }
}

/**
 * Turns each captured frame into an [ImageBitmap] rather than on VLC's render thread, which must
 * not be blocked or the audio pipeline stutters. Capped at ~60fps.
 *
 * The actual conversion runs on [Dispatchers.Default], not on the polling loop's own coroutine:
 * `LaunchedEffect` otherwise runs on the composition's dispatcher, the same one that drives
 * Compose's own recomposition and layout -- and `toComposeImageBitmap()` is real work, a full-frame
 * pixel copy done up to 60 times a second. Left there, it competed directly with the rest of the
 * app's UI work on the one thread both needed, which is what read as stutter across the whole
 * window, video included, not only the video.
 */
@Composable
private fun ConvertFramesOffRenderThread(
    frameVersion: MutableState<Long>,
    holder: MutableState<BufferedImage?>,
    out: MutableState<ImageBitmap?>,
) {
    LaunchedEffect(Unit) {
        var lastVersion = 0L
        while (isActive) {
            val v = frameVersion.value
            if (v != lastVersion) {
                lastVersion = v
                val img = holder.value
                if (img != null) {
                    val bitmap = withContext(Dispatchers.Default) { img.toComposeImageBitmap() }
                    out.value = bitmap
                    SharedVideoOutput.frame.value = bitmap
                }
            }
            delay(FRAME_INTERVAL_MS)
        }
    }
}

/**
 * Two same-sized [BufferedImage]s so VLC's render thread and the composable's own conversion
 * coroutine ([ConvertFramesOffRenderThread]) are never touching the same one at once.
 *
 * Before this, both sides shared one [BufferedImage] and mutated/read its backing `int[]` in
 * place: VLC could start copying frame N+1's pixels into that array while the coroutine was still
 * mid-read of frame N for `toComposeImageBitmap()`, tearing the frame -- a torn frame reads on
 * screen as a glitch, and since neither side is throttled to the other's pace, it recurred rather
 * than being a one-off. [writeTarget] is always the buffer nobody is reading: the previous
 * [completeWrite] handed the just-finished one off for display and only then flipped which one
 * VLC writes into next, so a buffer is never both the current write target and the current display
 * source at the same time.
 */
internal class FramePingPong(width: Int, height: Int) {
    private val bufferA = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    private val bufferB = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    private var nextIsA = true

    /** Where the render callback copies the next frame's pixels. */
    val writeTarget: BufferedImage get() = if (nextIsA) bufferA else bufferB

    /** Called once that copy finishes: hands back the now-complete buffer and flips the target. */
    fun completeWrite(): BufferedImage {
        val completed = writeTarget
        nextIsA = !nextIsA
        return completed
    }
}

/**
 * The RV32 buffer format VLC renders into, re-allocating the [FramePingPong] pair whenever the
 * source size changes.
 */
private fun rv32BufferFormatCallback(pingPong: MutableState<FramePingPong?>) =
    object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            val w = sourceWidth.coerceAtLeast(1)
            val h = sourceHeight.coerceAtLeast(1)
            pingPong.value = FramePingPong(w, h)
            return RV32BufferFormat(w, h)
        }

        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
    }

/**
 * Copies each decoded frame into [pingPong]'s current write target, publishes the completed buffer
 * to [displayHolder], and bumps [frameVersion]. Runs on VLC's own render thread, so it does no
 * conversion work: that happens in the composable's frame loop.
 */
private fun frameRenderCallback(
    pingPong: MutableState<FramePingPong?>,
    displayHolder: MutableState<BufferedImage?>,
    firstFrameCaptured: MutableState<Boolean>,
    frameVersion: MutableState<Long>,
) = RenderCallback { _, nativeBuffers, _ ->
    val pp = pingPong.value ?: return@RenderCallback
    if (nativeBuffers == null || nativeBuffers.isEmpty()) return@RenderCallback
    val target = pp.writeTarget
    val pixelData = (target.raster.dataBuffer as? DataBufferInt)?.data ?: return@RenderCallback
    try {
        val buf = nativeBuffers[0] ?: return@RenderCallback
        buf.rewind()
        buf.asIntBuffer().get(pixelData, 0, pixelData.size.coerceAtMost(buf.remaining() / RV32_BYTES_PER_PIXEL))
        displayHolder.value = pp.completeWrite()
        firstFrameCaptured.value = true
        frameVersion.value++  // signal new frame available, conversion happens off-thread
    } catch (_: Throwable) { }
}

/**
 * The software player's VLC event listener. [reportsPlaybackEnd] is what decides whether this
 * decoder is the one that tells the view model the file ended — see the parameter on
 * [SoftwareVideoPlayer].
 */
internal fun softwarePlayerEvents(
    viewModel: MediaViewModel,
    firstFrameCaptured: MutableState<Boolean>,
    gate: PlayerReleaseGate,
    pauseTimer: MutableState<javax.swing.Timer?>,
    reportsPlaybackEnd: Boolean,
) = object : MediaPlayerEventAdapter() {
    override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
        if (newLength > 0) viewModel.setDuration(newLength)
    }

    // The tracks are the same for a mirror, so only the decoder that owns the end of the file
    // reports them; the mirror just applies whichever one the view model has selected.
    override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
        if (reportsPlaybackEnd) viewModel.setSubtitleTracks(mediaPlayer.subtitleTracks())
    }

    // An external file is added to the player after the media is ready, so it arrives here.
    override fun elementaryStreamAdded(mediaPlayer: MediaPlayer, type: TrackType, id: Int) {
        if (reportsPlaybackEnd && type == TrackType.TEXT) viewModel.setSubtitleTracks(mediaPlayer.subtitleTracks())
    }

    override fun playing(mediaPlayer: MediaPlayer) {
        if (viewModel.isPlaying) return
        if (!firstFrameCaptured.value) {
            // Give VLC up to 200 ms to decode and deliver the first frame to the render callback
            // before pausing. This is critical for portrait/rotated videos (e.g. iPhone MOV) where
            // the decoder may take longer to start. Held so onDispose can stop it — see the
            // embedded player above.
            pauseTimer.value?.stop()
            pauseTimer.value = javax.swing.Timer(POSITION_POLL_MS) {
                gate.ifLive {
                    if (!viewModel.isPlaying) {
                        mediaPlayer.controls().pause()
                        // Rewinds to the start of the file this load-grace decode ran ahead
                        // into, so the first real Go Live resumes from an explicit seek --
                        // exactly what Stop does before a second Play -- rather than from
                        // wherever those ~200ms of decode happened to land. An operator-
                        // initiated pause mid-playback never reaches this branch
                        // (firstFrameCaptured is already true by then), so nothing here
                        // touches a real pause.
                        mediaPlayer.controls().setTime(0)
                    }
                }
            }.also { it.isRepeats = false; it.start() }
        } else {
            SwingUtilities.invokeLater { gate.ifLive { mediaPlayer.controls().pause() } }
        }
    }

    override fun finished(mediaPlayer: MediaPlayer) {
        if (reportsPlaybackEnd) viewModel.markFinished()
    }

    override fun error(mediaPlayer: MediaPlayer) {
        System.err.println("VLCJ (software): Playback error for: ${viewModel.mediaUrl}")
        SwingUtilities.invokeLater { viewModel.pause() }
    }
}

/**
 * The media options the software player loads [subtitleUrl] and its audio setting with.
 *
 * [appRendersSubtitles] is true when `MediaViewModel.subtitleCues` parsed something out of
 * [subtitleUrl] -- the app is drawing that file itself (`SubtitleOverlay`), so VLC must not also
 * be handed `:sub-file=`, or the same text would be burned into the frame a second time.
 */
internal fun softwarePlayOptions(
    audioEnabled: Boolean,
    subtitleUrl: String,
    appRendersSubtitles: Boolean = false,
    forceAvcodec: Boolean = DevFlags.forceAvcodec,
): Array<String> {
    val codec = if (forceAvcodec) arrayOf(VLC_OPT_SOFTWARE_CODEC) else emptyArray()
    val base = if (audioEnabled) {
        codec + arrayOf(VLC_OPT_FAST_DECODE, VLC_OPT_TIGHT_CLOCK)
    } else {
        codec + arrayOf(VLC_OPT_FAST_DECODE, VLC_OPT_TIGHT_CLOCK, VLC_OPT_NO_AUDIO)
    }
    return if (appRendersSubtitles) base else base + subtitleMediaOptions(subtitleUrl)
}

private fun MediaPlayer.playSoftware(
    mrl: String,
    audioEnabled: Boolean,
    subtitleUrl: String,
    appRendersSubtitles: Boolean,
) {
    val options = softwarePlayOptions(audioEnabled, subtitleUrl, appRendersSubtitles)
    // media().play takes its options as a vararg; this is the one call that spreads them.
    @Suppress("SpreadOperator")
    media().play(mrl, *options)
}

/**
 * Applies the view model's chosen subtitle track live, without reloading the media.
 *
 * Skipped entirely when the app is rendering the subtitles itself (`subtitleCues` non-empty) --
 * VLC was never handed that file, so it has no track for it to select.
 */
@Composable
private fun SubtitleTrackSync(viewModel: MediaViewModel, mp: MediaPlayer, gate: PlayerReleaseGate) {
    if (viewModel.subtitleCues.isNotEmpty()) return
    // Keyed on the resolved selection alone, not on `subtitleTracks` too: that list is reassigned
    // once per embedded track VLC reports (`elementaryStreamAdded` fires per track), and keying on
    // it re-sent the *same* selection to VLC on every one of those -- `setTrack` is not a no-op
    // when the id is unchanged, so a file with several embedded tracks flashed the subtitle
    // renderer once per track while it was still being discovered. Nothing here needs the list
    // itself: `selectedSubtitleTrack` already flips from `SUBTITLES_UNDECIDED` to a resolved value
    // (see `setSubtitleTracks`) the moment there's something to resolve it with.
    LaunchedEffect(viewModel.selectedSubtitleTrack) {
        val track = viewModel.selectedSubtitleTrack
        if (track != MediaViewModel.SUBTITLES_UNDECIDED) {
            SwingUtilities.invokeLater { gate.ifLive { mp.subpictures().setTrack(track) } }
        }
    }
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
    audioDeviceId: String = "",
    // Exactly one decoder may report the end of the file: with looping armed, the end is what
    // spends a repeat, so a mirror mounted on the same view model reporting it too would spend
    // two. The Media tab's decoder and MainDesktop's are already mutually exclusive; a mirror
    // (the stage monitor) passes false.
    reportsPlaybackEnd: Boolean = true,
) {
    if (!isVlcAvailable) return

    val currentFrame = remember { mutableStateOf<ImageBitmap?>(null) }
    val frameVersion = remember { mutableStateOf(0L) }
    // The last COMPLETE frame -- what ConvertFramesOffRenderThread reads. The pair it was copied
    // from is FramePingPong's own concern; nothing here reads a buffer VLC might still be writing.
    val bufferedImageHolder = remember { mutableStateOf<BufferedImage?>(null) }
    val framePingPong = remember { mutableStateOf<FramePingPong?>(null) }

    // On macOS, factory.mediaPlayers().newEmbeddedMediaPlayer() does NOT deliver video
    // frames to a callback surface — that requires CallbackMediaPlayerComponent.
    // On Linux/Windows, EmbeddedMediaPlayerComponent works fine.
    // createMediaPlayerComponent() already picks the right type per platform.
    val component = remember { createMediaPlayerComponent() } ?: return
    val mp: EmbeddedMediaPlayer = component.mediaPlayer()

    val surfaceFactory = rememberSurfaceFactory() ?: return

    // True once the render callback has delivered at least one frame for the current URL.
    // Used to give VLC a brief window (200 ms) before auto-pausing on first load so that
    // even slow-starting or portrait/rotated videos have time to deliver their first frame.
    val firstFrameCaptured = remember { mutableStateOf(false) }

    ConvertFramesOffRenderThread(frameVersion, bufferedImageHolder, currentFrame)

    // Native-handle lifetime guard and the deferred pause it protects — see PlayerReleaseGate.
    val gate = remember { PlayerReleaseGate() }
    val pauseTimer = remember { mutableStateOf<javax.swing.Timer?>(null) }

    // Set up callback video surface for software rendering
    DisposableEffect(Unit) {
        val bufferFormatCallback = rv32BufferFormatCallback(framePingPong)
        val renderCallback = frameRenderCallback(framePingPong, bufferedImageHolder, firstFrameCaptured, frameVersion)

        // Setting a new video surface here replaces the component's internal surface,
        // directing all decoded frames to our renderCallback instead.
        mp.videoSurface().set(
            surfaceFactory.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, true)
        )

        mp.events().addMediaPlayerEventListener(
            softwarePlayerEvents(viewModel, firstFrameCaptured, gate, pauseTimer, reportsPlaybackEnd)
        )

        onDispose {
            // Before releasePlayer(), so anything already queued sees the player as gone.
            gate.release()
            pauseTimer.value?.stop()
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

    // Load media when the URL changes, and again on every loop restart: once VLC has reached the
    // end of a file it is in the Ended state, where setTime()/play() alone will not start it over.
    LaunchedEffect(viewModel.mediaUrl, viewModel.loopRestartVersion, viewModel.subtitleUrl) {
        val url = viewModel.mediaUrl
        firstFrameCaptured.value = false  // reset so next file gets the 200 ms grace window
        SharedVideoOutput.frame.value = null  // clear stale frame while new media loads
        mp.controls().stop()
        if (url.isBlank()) return@LaunchedEffect

        val mrl = try {
            val f = File(url)
            if (f.exists()) f.absolutePath else url
        } catch (_: Exception) { url }

        // Stay muted until playback is actually requested. Loading always briefly starts the
        // VLC pipeline to capture a first frame (see playing() below), and without this guard
        // that grace window would be audible even though the video is meant to load paused.
        //
        // Muted, not silenced by volume: the real volume is set here too, so libVLC's audio
        // output device is asked to exist -- and negotiate with the OS -- during this load
        // grace window rather than for the first time at Go Live. That negotiation is the
        // asynchronous part the comment below already flags; setting volume 0 at load and only
        // setting the real volume once Go Live is pressed meant Go Live was the first moment
        // that device was ever actually needed, which is a plausible stall of its own layered
        // on top of decode ramping up -- the isPlaying effect below only ever lifts a mute now,
        // it does not ask for a device for the first time.
        if (audioEnabled) mp.audio().setVolume((viewModel.effectiveVolume * VOLUME_PERCENT_SCALE).toInt())
        mp.audio().setMute(!audioEnabled || !viewModel.isPlaying)

        // :codec=avcodec forces FFmpeg software decoding, bypassing VideoToolbox.
        // Required for Dolby Vision HEVC / 10-bit files where VideoToolbox outputs zero-copy
        // GPU CVPX buffers that the callback video surface cannot read (black frame).
        // :avcodec-fast reduces per-frame overhead; :clock-jitter=0 tightens frame scheduling.
        // When the caller has determined this instance must never produce audio (e.g. a
        // background decoder mounted only to keep rendering a paused frame), :no-audio
        // disables the audio track outright.
        mp.playSoftware(mrl, audioEnabled, viewModel.subtitleUrl, viewModel.subtitleCues.isNotEmpty())
        // Auto-pause is handled by the playing() event listener above.
    }

    SubtitleTrackSync(viewModel, mp, gate)

    // Play / pause sync
    // We always send the command unconditionally: mp.status().isPlaying() can return a stale
    // value on the EDT (VLC may be buffering/transitioning) and guard-skipping the call is what
    // causes the button to get stuck. libvlc play/pause on an already-playing/paused player
    // is a documented no-op, so this is safe.
    LaunchedEffect(viewModel.isPlaying) {
        SwingUtilities.invokeLater {
            if (viewModel.isPlaying) {
                // Lifts the mute the load effect set for the first-frame grace window — the
                // audio device itself has been live since load, so this is not the first time
                // it's asked to exist.
                if (audioEnabled) mp.audio().setMute(false)
                mp.controls().play()
            } else {
                if (audioEnabled) mp.audio().setMute(true)
                mp.controls().pause()
            }
        }
    }

    // Volume sync
    LaunchedEffect(viewModel.effectiveVolume) {
        if (audioEnabled) {
            mp.audio().setVolume((viewModel.effectiveVolume * VOLUME_PERCENT_SCALE).toInt())
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
                delay(STATE_SETTLE_MS)
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
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}
