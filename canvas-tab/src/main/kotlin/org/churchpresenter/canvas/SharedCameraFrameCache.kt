package org.churchpresenter.canvas

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
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.diagnostics.CrashReporter

private const val MAX_NULL_FRAMES_BEFORE_CLEAR = 30
private const val DECKLINK_POLL_INTERVAL_MS = 16L
private const val STDERR_TAIL_LINES = 50
private const val MAX_CONSECUTIVE_FAILURES = 5
private const val DEVICE_RELEASE_DELAY_MS = 500L
private const val RETRY_DELAY_MS = 2000L
private const val RESTART_DELAY_MS = 1000L
private const val DIMENSION_POLL_ATTEMPTS = 50
private const val DIMENSION_POLL_INTERVAL_MS = 100L
private const val PROCESS_KILL_TIMEOUT_S = 3L
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BGRA_BYTES_PER_PIXEL = 4

/**
 * Why a camera has no picture.
 *
 * A reason, not a sentence: this object has no access to Compose string resources, and the message
 * an operator reads has to be translatable. The renderer owns the wording.
 */
enum class CameraFailure {
    /** The DeckLink input would not open — most often the card is already feeding an output. */
    DECKLINK_INPUT_IN_USE,
    /** ffmpeg is not installed, or not on PATH. Nothing to retry. */
    FFMPEG_MISSING,
    /** The stored device path matches no capture scheme this OS knows. */
    UNSUPPORTED_DEVICE_PATH,
    /** ffmpeg ran but never delivered a picture — device busy, unplugged, or refusing the format. */
    DEVICE_UNAVAILABLE,
}

/** What one ffmpeg attempt produced — the success signal, and why it failed when it didn't. */
private enum class CaptureOutcome { FRAMES, NO_DIMENSIONS, NO_FRAMES }

/**
 * Shared camera frame cache — ensures only one capture process runs per device,
 * even when multiple composable instances (canvas preview + presenter output)
 * need to display the same camera.
 */
object SharedCameraFrameCache {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val entries = mutableMapOf<String, CacheEntry>()

    private class CacheEntry(
        val frame: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null),
        val error: MutableStateFlow<CameraFailure?> = MutableStateFlow(null),
        var refCount: Int = 0,
        var captureJob: Job? = null,
        var ffmpegProcess: Process? = null
    )

    /** Build a unique key for a camera source. */
    internal fun keyFor(source: SceneSource.CameraSource): String {
        return if (source.isDeckLink && source.deckLinkIndex >= 0) {
            "decklink:${source.deckLinkIndex}:${source.videoFormat}:${source.videoConnection}"
        } else {
            "ffmpeg:${source.devicePath}:${source.videoFormat}"
        }
    }

    data class CameraFlows(
        val frame: StateFlow<ImageBitmap?>,
        val error: StateFlow<CameraFailure?>
    )

    /**
     * Acquire a shared frame flow for this camera source.
     * First subscriber starts the capture; subsequent subscribers share it.
     */
    @Synchronized
    /**
     * Start (or join) capture for [source], pulling DeckLink frames through [deckLink].
     *
     * The device is a parameter rather than a property on this object: it is a singleton, and a
     * mutable field on a singleton is the seam the root `AGENT.md` bans — it leaks between tests and
     * nothing restores it. Passing it in also means the caller decides, which is what lets a test
     * hand over `CanvasDeckLink.None` and get the ffmpeg path deterministically.
     */
    fun acquire(source: SceneSource.CameraSource, deckLink: CanvasDeckLink = CanvasDeckLink.None): CameraFlows {
        val key = keyFor(source)
        val entry = entries.getOrPut(key) { CacheEntry() }
        entry.refCount++
        if (entry.refCount == 1) {
            entry.error.value = null
            // First subscriber — start capture
            entry.captureJob = scope.launch {
                try {
                    if (source.isDeckLink && source.deckLinkIndex >= 0 && deckLink.isAvailable()) {
                        runDeckLinkCapture(source, entry, deckLink)
                    } else {
                        runFfmpegCapture(source, entry)
                    }
                } catch (_: CancellationException) {
                    // Normal cleanup
                } catch (e: Exception) {
                    System.err.println("[SharedCameraFrameCache] Capture error for $key: ${e.message}")
                }
            }
        }
        return CameraFlows(entry.frame, entry.error)
    }

    /**
     * Release a shared frame flow. When the last subscriber releases,
     * capture is stopped and resources are cleaned up.
     */
    @Synchronized
    fun release(source: SceneSource.CameraSource, deckLink: CanvasDeckLink = CanvasDeckLink.None) {
        val key = keyFor(source)
        val entry = entries[key] ?: return
        entry.refCount--
        if (entry.refCount <= 0) {
            entry.captureJob?.cancel()
            entry.captureJob = null
            entry.frame.value = null
            entries.remove(key)

            // Only close the device if no other cache entry is using the same device.
            // When switching connections, the new acquire's openInput() already closed
            // the old input — calling closeInput here would kill the new one.
            if (source.isDeckLink && source.deckLinkIndex >= 0 && deckLink.isAvailable()) {
                val deviceStillActive = entries.keys.any {
                    it.startsWith("decklink:${source.deckLinkIndex}:")
                }
                if (!deviceStillActive) {
                    deckLink.closeInput(source.deckLinkIndex)
                }
            }

            // Clean up ffmpeg process
            val p = entry.ffmpegProcess
            if (p != null) {
                val devicePath = source.devicePath
                val deviceStillActive = entries.keys.any {
                    it.startsWith("ffmpeg:$devicePath:")
                }
                if (!deviceStillActive) {
                    killFfmpegProcess(p)
                }
                entry.ffmpegProcess = null
            }
        }
    }

    // ── DeckLink capture ────────────────────────────────────────────

    /** Puts one polled DeckLink frame on screen; false when the poll returned no usable frame. */
    private suspend fun showDeckLinkFrame(frameData: IntArray?, entry: CacheEntry, first: Boolean): Boolean {
        if (frameData == null || frameData.size <= 2) return false
        val w = frameData[0]
        val h = frameData[1]
        if (w <= 0 || h <= 0) return false
        val img = withContext(Dispatchers.IO) {
            val bi = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            bi.setRGB(0, 0, w, h, frameData, 2, w)
            bi
        }
        entry.frame.value = img.toComposeImageBitmap()
        if (first) System.err.println("[DeckLink Input] First frame: ${w}x${h}")
        return true
    }

    private suspend fun runDeckLinkCapture(
        source: SceneSource.CameraSource,
        entry: CacheEntry,
        deckLink: CanvasDeckLink,
    ) {
        System.err.println("[DeckLink Input] Opening device ${source.deckLinkIndex}, " +
            "format: ${source.videoFormat.ifEmpty { "auto" }}, connection: ${source.videoConnection}")

        val opened = withContext(Dispatchers.IO) {
            deckLink.openInput(source.deckLinkIndex, source.videoFormat, source.videoConnection)
        }
        if (!opened) {
            System.err.println("[DeckLink Input] Failed to open input on device ${source.deckLinkIndex}")
            CrashReporter.reportWarning(
                "DeckLink: Failed to open input on device ${source.deckLinkIndex}",
                tags = mapOf("subsystem" to "decklink")
            )
            entry.error.value = CameraFailure.DECKLINK_INPUT_IN_USE
            return
        }
        entry.error.value = null

        System.err.println("[DeckLink Input] Input opened, polling for frames...")
        var frameCount = 0
        var nullCount = 0

        while (currentCoroutineContext().isActive) {
            val frameData = withContext(Dispatchers.IO) {
                deckLink.getInputFrame(source.deckLinkIndex)
            }

            if (showDeckLinkFrame(frameData, entry, first = frameCount == 0)) {
                frameCount++
                nullCount = 0
            } else {
                nullCount++
                if (nullCount > MAX_NULL_FRAMES_BEFORE_CLEAR && entry.frame.value != null) {
                    entry.frame.value = null  // no signal — clear display
                }
            }

            delay(DECKLINK_POLL_INTERVAL_MS) // ~60fps polling
        }
    }

    // ── FFmpeg capture ──────────────────────────────────────────────


    /**
     * Reads raw BGRA frames off a running ffmpeg into [entry] until the stream ends. A frame having
     * arrived is what tells a dropped stream apart from a device that never opened; the two failing
     * outcomes are kept apart so the diagnostic can say which one it was.
     */
    private suspend fun streamFrames(process: Process, entry: CacheEntry): CaptureOutcome {
        entry.ffmpegProcess = process

        // Drain stderr and extract video dimensions from ffmpeg output
        val stderrLines = mutableListOf<String>()
        val videoDims = java.util.concurrent.atomic.AtomicReference<Pair<Int, Int>?>(null)
        val stderrJob = CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO) {
            try {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(stderrLines) {
                            stderrLines.add(line)
                            if (stderrLines.size > STDERR_TAIL_LINES) stderrLines.removeAt(0)
                        }
                        if (videoDims.get() == null) {
                            parseFfmpegVideoDimensions(line)?.let { videoDims.set(it) }
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        val resolved = awaitVideoDimensions(videoDims)
        if (resolved == null) {
            System.err.println("[Camera] Could not determine video dimensions from ffmpeg")
            stderrJob.cancel()
            withContext(Dispatchers.IO) { killFfmpegProcess(process) }
            entry.ffmpegProcess = null
            return CaptureOutcome.NO_DIMENSIONS
        }

        val (videoW, videoH) = resolved
        val frameCount = readFramesInto(process, entry, videoW, videoH)

        // Stream ended — clean up this process
        stderrJob.cancel()
        val exitCode = withContext(Dispatchers.IO) {
            try {
                killFfmpegProcess(process)
                process.exitValue()
            } catch (_: Throwable) { -1 }
        }
        entry.ffmpegProcess = null

        if (frameCount > 0) {
            System.err.println("[Camera] Stream interrupted after $frameCount frames (exit $exitCode), restarting...")
        } else {
            System.err.println("[Camera] ffmpeg exited with code $exitCode without producing any frames")
            synchronized(stderrLines) {
                stderrLines.forEach { System.err.println("[Camera] ffmpeg stderr: $it") }
            }
        }
        return if (frameCount > 0) CaptureOutcome.FRAMES else CaptureOutcome.NO_FRAMES
    }

    /** Waits up to five seconds for ffmpeg to announce the stream's size. */
    private suspend fun awaitVideoDimensions(
        videoDims: java.util.concurrent.atomic.AtomicReference<Pair<Int, Int>?>,
    ): Pair<Int, Int>? {
        repeat(DIMENSION_POLL_ATTEMPTS) {
            videoDims.get()?.let { return it }
            delay(DIMENSION_POLL_INTERVAL_MS)
        }
        return videoDims.get()
    }

    /** Frames read into [entry] until the stream stops; the count is the caller's success signal. */
    private suspend fun readFramesInto(process: Process, entry: CacheEntry, videoW: Int, videoH: Int): Int {
        val frameBytes = videoW * videoH * 4  // BGRA = 4 bytes per pixel
        System.err.println("[Camera] Capturing ${videoW}x${videoH} rawvideo BGRA ($frameBytes bytes/frame)")

        val inputStream = java.io.BufferedInputStream(process.inputStream, frameBytes * 2)
        val frameBuf = ByteArray(frameBytes)
        val pixelBuf = IntArray(videoW * videoH)
        var frameCount = 0

        while (currentCoroutineContext().isActive) {
            val ok = withContext(Dispatchers.IO) { readFullFrame(inputStream, frameBuf, frameBytes) }
            if (!ok) break

            withContext(Dispatchers.IO) { bgraBytesToArgbPixels(frameBuf, pixelBuf) }

            val img = java.awt.image.BufferedImage(videoW, videoH, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            img.setRGB(0, 0, videoW, videoH, pixelBuf, 0, videoW)
            entry.frame.value = img.toComposeImageBitmap()
            frameCount++
            if (frameCount == 1) {
                System.err.println("[Camera] First frame received (${videoW}x${videoH})")
            }
        }
        return frameCount
    }

    private fun readFullFrame(inputStream: java.io.InputStream, frameBuf: ByteArray, frameBytes: Int): Boolean =
        try {
            var read = 0
            var endOfStream = false
            while (read < frameBytes && !endOfStream) {
                val r = inputStream.read(frameBuf, read, frameBytes - read)
                if (r == -1) endOfStream = true else read += r
            }
            !endOfStream
        } catch (_: Throwable) {
            false
        }

    private suspend fun runFfmpegCapture(source: SceneSource.CameraSource, entry: CacheEntry) {
        val path = source.devicePath
        System.err.println(
            "[Camera] Starting camera capture for device: $path, format: ${source.videoFormat.ifEmpty { "auto" }}"
        )

        val command = buildFfmpegCommand(source) ?: run {
            System.err.println("[Camera] Unknown device path scheme: $path")
            // Nothing was ever going to open. Without this the source sits on the generic
            // "Camera" placeholder for ever, indistinguishable from one still starting up.
            entry.error.value = CameraFailure.UNSUPPORTED_DEVICE_PATH
            return
        }

        var consecutiveFailures = 0
        var lastOutcome = CaptureOutcome.NO_FRAMES
        var lastExitCode: Int? = null
        while (currentCoroutineContext().isActive && consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
            // Kill any lingering process and wait for the OS to release the device
            val old = entry.ffmpegProcess
            if (old != null) {
                withContext(Dispatchers.IO) { killFfmpegProcess(old) }
                entry.ffmpegProcess = null
                delay(DEVICE_RELEASE_DELAY_MS)
            }

            System.err.println(
                "[Camera] Opening device (attempt ${consecutiveFailures + 1}): ${command.joinToString(" ")}"
            )
            val process = withContext(Dispatchers.IO) {
                try {
                    ProcessBuilder(command).redirectErrorStream(false).start()
                } catch (e: Throwable) {
                    System.err.println("[Camera] Failed to start ffmpeg: ${e.message}")
                    null
                }
            }
            if (process == null) {
                // The binary isn't there — ffmpeg ships with the OS on nobody's machine, and the
                // camera picker already tells people to install it. Five more attempts two seconds
                // apart cannot change that, so stop now and say what is actually wrong.
                reportGaveUp(source, CaptureOutcome.NO_FRAMES, exitCode = null, missing = true)
                entry.error.value = CameraFailure.FFMPEG_MISSING
                return
            }
            // Check whether ffmpeg managed to open the device
            val exitedImmediately = withContext(Dispatchers.IO) {
                process.waitFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
            } && process.exitValue() != 0
            if (exitedImmediately) {
                lastExitCode = process.exitValue()
                System.err.println("[Camera] ffmpeg exited immediately with code $lastExitCode")
                withContext(Dispatchers.IO) { killFfmpegProcess(process) }
            }
            val outcome = if (exitedImmediately) CaptureOutcome.NO_FRAMES else streamFrames(process, entry)
            lastOutcome = outcome
            if (outcome == CaptureOutcome.FRAMES) {
                consecutiveFailures = 0
                entry.error.value = null
                delay(RESTART_DELAY_MS)
            } else {
                consecutiveFailures++
                delay(RETRY_DELAY_MS)
            }
        }

        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            System.err.println("[Camera] Giving up after $consecutiveFailures consecutive failures")
            reportGaveUp(source, lastOutcome, lastExitCode, missing = false)
            // The capture coroutine is about to end. Nothing else will ever set this, so without it
            // the operator watches an empty rectangle with no idea the app has stopped trying.
            entry.error.value = CameraFailure.DEVICE_UNAVAILABLE
        }
    }
}

/**
 * The diagnostic for a camera that never opened.
 *
 * The scheme and the outcome are what separate the causes this warning collapses together —
 * a missing binary, a device another app holds, a format the device refuses. The device *name*
 * is deliberately not sent: it is the one part of a capture command that identifies a person's
 * hardware, and it is not needed to tell those cases apart.
 */
private fun reportGaveUp(
    source: SceneSource.CameraSource,
    outcome: CaptureOutcome,
    exitCode: Int?,
    missing: Boolean,
) {
    val reason = if (missing) "ffmpeg_missing" else outcome.name.lowercase()
    CrashReporter.reportWarning(
        if (missing) "Camera: ffmpeg could not be started"
        else "Camera: Giving up on device after $MAX_CONSECUTIVE_FAILURES consecutive ffmpeg failures",
        tags = mapOf(
            "subsystem" to "camera",
            "camera.scheme" to source.devicePath.substringBefore("://", "unknown"),
            "failure.reason" to reason
        ),
        extras = buildMap {
            put("camera.format", source.videoFormat.ifEmpty { "auto" })
            exitCode?.let { put("ffmpeg.exit_code", it.toString()) }
        }
    )
}

/**
 * Builds the ffmpeg command line for capturing [source]'s device as raw BGRA video, or `null` when
 * [SceneSource.CameraSource.devicePath] doesn't match a recognized OS capture scheme
 * (`dshow://`, `v4l2://`, `avfoundation://`).
 */
internal fun buildFfmpegCommand(source: SceneSource.CameraSource): List<String>? {
    val path = source.devicePath

    // Parse video format into ffmpeg input args (must come before -i)
    val formatArgs = if (source.videoFormat.isNotEmpty()) {
        val match = Regex("""(\d+)x(\d+)@(\d+)""").find(source.videoFormat)
        if (match != null) {
            val (w, h, fps) = match.destructured
            listOf("-video_size", "${w}x${h}", "-framerate", fps)
        } else emptyList()
    } else emptyList()

    return when {
        path.startsWith("dshow://") -> {
            val deviceName = path.removePrefix("dshow://").removePrefix(":dshow-vdev=")
            listOf("ffmpeg", "-f", "dshow") + formatArgs + listOf("-i", "video=$deviceName",
                "-an", "-vf", "fps=30", "-pix_fmt", "bgra",
                "-f", "rawvideo", "-")
        }
        path.startsWith("v4l2://") -> {
            val device = path.removePrefix("v4l2://")
            listOf("ffmpeg", "-f", "v4l2") + formatArgs + listOf("-i", device,
                "-an", "-vf", "fps=30", "-pix_fmt", "bgra",
                "-f", "rawvideo", "-")
        }
        path.startsWith("avfoundation://") -> {
            val index = path.removePrefix("avfoundation://")
            listOf("ffmpeg", "-f", "avfoundation") + formatArgs + listOf("-i", "$index:none",
                "-an", "-vf", "fps=30", "-pix_fmt", "bgra",
                "-f", "rawvideo", "-")
        }
        else -> null
    }
}

/**
 * Parses ffmpeg's own `-list_options`/capture stderr line announcing the negotiated raw BGRA video
 * stream — e.g. `Stream #0:0: Video: rawvideo (BGRA / ...), bgra, 1280x720, ...` — into (width,
 * height), or `null` when this line isn't that announcement, or the dimensions in it are invalid.
 */
internal fun parseFfmpegVideoDimensions(stderrLine: String): Pair<Int, Int>? {
    if (!stderrLine.contains("Video:") || !stderrLine.contains("bgra")) return null
    val match = Regex("""(\d{2,5})x(\d{2,5})""").find(stderrLine.substringAfter("bgra")) ?: return null
    val w = match.groupValues[1].toIntOrNull() ?: 0
    val h = match.groupValues[2].toIntOrNull() ?: 0
    return if (w > 0 && h > 0) Pair(w, h) else null
}

/** Converts a raw BGRA frame buffer (4 bytes/pixel, as ffmpeg emits it) into [pixelBuf]'s packed
 *  ARGB ints, in place. [frameBuf] must hold at least `pixelBuf.size * 4` bytes. */
internal fun bgraBytesToArgbPixels(frameBuf: ByteArray, pixelBuf: IntArray) {
    var bi = 0
    for (pi in pixelBuf.indices) {
        val b = frameBuf[bi].toInt() and 0xFF
        val g = frameBuf[bi + 1].toInt() and 0xFF
        val r = frameBuf[bi + 2].toInt() and 0xFF
        val a = frameBuf[bi + 3].toInt() and 0xFF
        pixelBuf[pi] = (a shl ALPHA_SHIFT) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
        bi += BGRA_BYTES_PER_PIXEL
    }
}

/** Kill an ffmpeg process and ensure device handles are released.
 *  On Windows, Process.destroyForcibly() often fails to release DirectShow
 *  device handles, so we kill the process tree via taskkill. */
internal fun killFfmpegProcess(process: Process) {
    try {
        if (System.getProperty("os.name", "").lowercase().contains("win")) {
            try {
                val pid = process.pid()
                ProcessBuilder("taskkill", "/F", "/T", "/PID", pid.toString())
                    .redirectErrorStream(true).start()
                    .waitFor(PROCESS_KILL_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
            } catch (_: Throwable) {}
            try {
                ProcessBuilder("taskkill", "/F", "/IM", "ffmpeg.exe")
                    .redirectErrorStream(true).start()
                    .waitFor(PROCESS_KILL_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
            } catch (_: Throwable) {}
        } else {
            process.destroyForcibly()
        }
        process.waitFor(PROCESS_KILL_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
    } catch (_: Throwable) {
        process.destroyForcibly()
    }
}
