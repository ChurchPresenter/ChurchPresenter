package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.churchpresenter.app.churchpresenter.utils.WindowsWindowCapture
import org.churchpresenter.app.churchpresenter.utils.X11WindowCapture
import org.churchpresenter.core.models.scene.SceneSource
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage

/** The floor on how often a capture repeats, whatever the source asks for. */
private const val MIN_CAPTURE_INTERVAL_MS = 33L

/** Capture mode naming a window rather than a region of the screen. */
internal const val CAPTURE_MODE_WINDOW = "window"

/**
 * One screen-capture configuration: everything that decides what a grab returns.
 *
 * A data class because it is the cache key. Two layers capturing the same region at the same rate
 * are one capture; two layers capturing different regions are two, and folding those together
 * would put one layer's picture on the other.
 */
internal data class ScreenCaptureSpec(
    val mode: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val intervalMs: Long,
    val windowTitle: String,
    val windowId: String,
) {
    companion object {
        fun of(source: SceneSource.ScreenCaptureSource) = ScreenCaptureSpec(
            mode = source.captureMode,
            x = source.captureX,
            y = source.captureY,
            width = source.captureWidth,
            height = source.captureHeight,
            intervalMs = source.captureInterval.toLong().coerceAtLeast(MIN_CAPTURE_INTERVAL_MS),
            windowTitle = source.windowTitle,
            windowId = source.windowId,
        )
    }
}

/**
 * One screen grab loop per distinct capture configuration, however many layers are drawing it.
 *
 * The same shape and the same reason as [SharedCameraFrameCache] and [NdiFrameCache], and it was
 * the last canvas source type without one. A screen-capture layer is composed once in the canvas
 * editor, once in each sidebar live preview, and once on each presenter output — and each of those
 * ran **its own** `Robot.createScreenCapture` loop at up to 30fps over the same pixels. Grabbing
 * the framebuffer is work the window server does, not the app, which is why an operator sees it as
 * the *compositor* pegged rather than ChurchPresenter: the reported machine had WindowServer at
 * 91-95% alongside the app's own 160%.
 *
 * Reference counted: the first layer to want a configuration starts the loop, the last one to let
 * go stops it.
 *
 * A class rather than the object it is reached through, so the whole of it is testable: [grab] is
 * the seam, and a test supplies one that returns a constructed image without ever touching
 * `java.awt.Robot` — which throws in the headless test JVM. That is a constructor parameter, not
 * the ad-hoc mutable `internal var` on a singleton that AGENT.md rules out.
 */
internal open class ScreenCaptureCache(
    private val grab: (ScreenCaptureSpec) -> BufferedImage?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val entries = mutableMapOf<ScreenCaptureSpec, CacheEntry>()

    private class CacheEntry {
        val frame = MutableStateFlow<ImageBitmap?>(null)
        var refCount = 0
        var captureJob: Job? = null
    }

    /**
     * Frames for [source], starting the loop on the first caller and sharing it afterwards.
     *
     * Every acquire must be matched by a [release], from the same `DisposableEffect` that made it —
     * acquiring from a `remember` block leaks the entry when a composition is abandoned.
     */
    @Synchronized
    fun acquire(source: SceneSource.ScreenCaptureSource): StateFlow<ImageBitmap?> {
        val spec = ScreenCaptureSpec.of(source)
        val entry = entries.getOrPut(spec) { CacheEntry() }
        entry.refCount++
        if (entry.refCount == 1) {
            entry.captureJob = scope.launch { capture(spec, entry) }
        }
        return entry.frame
    }

    /** Lets go of [source]. The grab loop stops when the last layer does. */
    @Synchronized
    fun release(source: SceneSource.ScreenCaptureSource) {
        val spec = ScreenCaptureSpec.of(source)
        val entry = entries[spec] ?: return
        entry.refCount--
        if (entry.refCount > 0) return
        entry.captureJob?.cancel()
        entry.captureJob = null
        entry.frame.value = null
        entries.remove(spec)
    }

    /** How many grab loops are running — how a test sees the sharing, and the release. */
    @get:Synchronized
    internal val liveCaptureCount: Int get() = entries.size

    // TooGenericExceptionCaught, narrowly: `Robot` and the two native window-capture helpers throw
    // across a range this code cannot enumerate — headless, a display that went away, a window
    // closed mid-grab. A layer that stops capturing is a black rectangle; the same throw uncaught
    // takes down the coroutine and leaves the entry live but frozen.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun capture(spec: ScreenCaptureSpec, entry: CacheEntry) {
        try {
            while (currentCoroutineContext().isActive) {
                val image = withContext(Dispatchers.IO) { grab(spec) }
                if (image != null) entry.frame.value = image.toComposeImageBitmap()
                delay(spec.intervalMs)
            }
        } catch (_: CancellationException) {
            // Ordinary teardown — the last layer let go.
        } catch (e: Exception) {
            System.err.println("[Screen Capture] ${spec.mode}: ${e.message}")
        }
    }
}

/**
 * The robot the real grabs go through, built once.
 *
 * Constructing one reaches `GraphicsEnvironment`, which throws in a headless JVM — so it is
 * resolved lazily and to null rather than at class-init, and a machine that cannot provide one
 * captures nothing instead of failing to load the class.
 */
private val screenRobot: Robot? by lazy {
    try {
        Robot()
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        System.err.println("[Screen Capture] no Robot available: ${e.message}")
        null
    }
}

/**
 * One grab of what [spec] names, or null when there is nothing to grab.
 *
 * Window mode prefers the platform's own occluded-window capture — which can read a window that is
 * behind another one — and falls back to grabbing the screen rectangle the window currently
 * occupies, which cannot. Region mode is the rectangle as given.
 */
internal fun grabScreen(spec: ScreenCaptureSpec): BufferedImage? {
    val robot = screenRobot ?: return null
    return when {
        spec.mode == CAPTURE_MODE_WINDOW && spec.windowId.isNotBlank() -> {
            val wid = spec.windowId.removePrefix("0x").toLongOrNull(16) ?: 0L
            WindowsWindowCapture.captureWindow(wid)
                ?: X11WindowCapture.captureWindow(wid)
                ?: robot.grabOrNull(findWindowBounds(spec.windowTitle))
        }
        spec.mode == CAPTURE_MODE_WINDOW && spec.windowTitle.isNotBlank() ->
            robot.grabOrNull(findWindowBounds(spec.windowTitle))
        else -> robot.grabOrNull(Rectangle(spec.x, spec.y, spec.width, spec.height))
    }
}

/** Grabs [rect], or null when it names no area — `createScreenCapture` throws on an empty one. */
private fun Robot.grabOrNull(rect: Rectangle?): BufferedImage? =
    rect?.takeIf { it.width > 0 && it.height > 0 }?.let { createScreenCapture(it) }

/**
 * The one cache the app draws screen-capture layers from.
 *
 * Holds no logic of its own — everything it does is [ScreenCaptureCache]'s, which is an ordinary
 * class a test builds over a stand-in grab.
 */
internal object SharedScreenCaptureCache : ScreenCaptureCache(grab = ::grabScreen)
