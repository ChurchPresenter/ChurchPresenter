package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
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
private const val IMMEDIATE_EXIT_WINDOW_MS = 2000L
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BGRA_BYTES_PER_PIXEL = 4

/**
 * Shared camera frame cache — ensures only one capture process runs per device,
 * even when multiple composable instances (canvas preview + presenter output)
 * need to display the same camera.
 */
object SharedCameraFrameCache {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val entries = mutableMapOf<String, CacheEntry>()

    /**
     * How many devices are being captured right now — read-only, and the one thing a test can ask
     * that distinguishes a released capture from a leaked one.
     *
     * A leak here is not a wasted object: the entry owns a live ffmpeg process holding the device
     * open, so the next acquire of that camera fails with `device_busy` and the operator's canvas
     * goes black on hardware nothing else is using.
     */
    @get:Synchronized
    internal val liveCaptureCount: Int get() = entries.size

    /** Build a unique key for a camera source. */
    internal fun keyFor(source: SceneSource.CameraSource): String {
        return if (source.isDeckLink && source.deckLinkIndex >= 0) {
            "decklink:${source.deckLinkIndex}:${source.videoFormat}:${source.videoConnection}"
        } else {
            "ffmpeg:${source.devicePath}:${source.videoFormat}"
        }
    }

    internal data class CameraFlows(
        val frame: StateFlow<ImageBitmap?>,
        val error: StateFlow<CameraFailure?>
    )

    /**
     * Acquire a shared frame flow for this camera source.
     * First subscriber starts the capture; subsequent subscribers share it.
     */
    @Synchronized
    internal fun acquire(source: SceneSource.CameraSource): CameraFlows {
        val key = keyFor(source)
        val entry = entries.getOrPut(key) { CacheEntry() }
        ResourceCensus.record(SharedResource.CAMERA_CAPTURE, entries.size)
        entry.refCount++
        if (entry.refCount == 1) {
            entry.error.value = null
            // First subscriber — start capture
            entry.captureJob = scope.launch {
                try {
                    if (source.isDeckLink && source.deckLinkIndex >= 0 && DeckLinkManager.isAvailable()) {
                        runDeckLinkCapture(source, entry)
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
    fun release(source: SceneSource.CameraSource) {
        val key = keyFor(source)
        val entry = entries[key] ?: return
        entry.refCount--
        if (entry.refCount <= 0) {
            entry.captureJob?.cancel()
            entry.captureJob = null
            entry.frame.value = null
            entries.remove(key)

            // Only close the device if no other cache entry is using the same device — two scene
            // sources on one camera are one capture, and the first of them to go must not take the
            // picture away from the second.
            //
            // A *switch* — another format, another connection — no longer reaches that guard. It
            // used to: acquire ran from a `remember` block, so the incoming entry existed before
            // the outgoing one was released, and this skipped the close on the strength of the new
            // `openInput` having displaced the old one. Acquire now runs from the same
            // `DisposableEffect` that releases, and Compose disposes the old effect before running
            // the new one, so a switch closes the device and then reopens it. That is the order
            // this cache wants: an ffmpeg process still holding a device when the next one asks for
            // it is exactly the `device_busy` failure the release path exists to prevent.
            if (source.isDeckLink && source.deckLinkIndex >= 0 && DeckLinkManager.isAvailable()) {
                val deviceStillActive = entries.keys.any {
                    it.startsWith("decklink:${source.deckLinkIndex}:")
                }
                if (!deviceStillActive) {
                    DeckLinkManager.closeInput(source.deckLinkIndex)
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

    /** Bounds the ffmpeg-missing report to one per process — see [runFfmpegCapture]. */
    private val ffmpegMissingReport = ReportOnce()

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

    private suspend fun runDeckLinkCapture(source: SceneSource.CameraSource, entry: CacheEntry) {
        System.err.println("[DeckLink Input] Opening device ${source.deckLinkIndex}, " +
            "format: ${source.videoFormat.ifEmpty { "auto" }}, connection: ${source.videoConnection}")

        val opened = withContext(Dispatchers.IO) {
            DeckLinkManager.openInput(source.deckLinkIndex, source.videoFormat, source.videoConnection)
        }
        if (!opened) {
            System.err.println("[DeckLink Input] Failed to open input on device ${source.deckLinkIndex}")
            CrashReporter.reportWarning(
                "DeckLink: Failed to open input on device",
                tags = mapOf(
                    "subsystem" to "decklink",
                    "decklink_index" to source.deckLinkIndex.toString()
                )
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
                DeckLinkManager.getInputFrame(source.deckLinkIndex)
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


    /** Reads raw BGRA frames off an already-draining ffmpeg into [entry] until the stream ends. */
    private suspend fun streamFrames(process: Process, entry: CacheEntry, drain: StderrDrain): FfmpegAttempt {
        entry.ffmpegProcess = process

        val resolved = awaitVideoDimensions(drain.videoDims)
        if (resolved == null) {
            System.err.println("[Camera] Could not determine video dimensions from ffmpeg")
            val tail = drain.tail()
            drain.job.cancel()
            withContext(Dispatchers.IO) { killFfmpegProcess(process) }
            entry.ffmpegProcess = null
            return FfmpegAttempt(framesProduced = false, exitCode = -1, stderrTail = tail)
        }

        val (videoW, videoH) = resolved
        val frameCount = readFramesInto(process, entry, videoW, videoH)

        // Stream ended — clean up this process
        val tail = drain.tail()
        drain.job.cancel()
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
            tail.forEach { System.err.println("[Camera] ffmpeg stderr: $it") }
        }
        return FfmpegAttempt(frameCount > 0, exitCode, tail)
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

    /**
     * Runs [command] once, returning what it observed — or `null` when the process never started.
     *
     * Its stderr is drained from the first instant, before the two-second window that decides
     * whether the device opened at all, so an attempt that exits straight back out still carries
     * the reason it exited.
     */
    private suspend fun attemptCapture(command: List<String>, entry: CacheEntry): FfmpegAttempt? =
        coroutineScope {
            val process = withContext(Dispatchers.IO) {
                try {
                    ProcessBuilder(command).redirectErrorStream(false).start()
                } catch (e: Throwable) {
                    System.err.println("[Camera] Failed to start ffmpeg: ${e.message}")
                    null
                }
            } ?: return@coroutineScope null

            val drain = startStderrDrain(process)
            val exitedImmediately = withContext(Dispatchers.IO) {
                process.waitFor(IMMEDIATE_EXIT_WINDOW_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            } && process.exitValue() != 0

            if (!exitedImmediately) return@coroutineScope streamFrames(process, entry, drain)

            val exitCode = process.exitValue()
            System.err.println("[Camera] ffmpeg exited immediately with code $exitCode")
            val tail = drain.tail()
            tail.forEach { System.err.println("[Camera] ffmpeg stderr: $it") }
            drain.job.cancel()
            withContext(Dispatchers.IO) { killFfmpegProcess(process) }
            FfmpegAttempt(framesProduced = false, exitCode = exitCode, stderrTail = tail)
        }

    private suspend fun runFfmpegCapture(source: SceneSource.CameraSource, entry: CacheEntry) {
        val path = source.devicePath
        System.err.println(
            "[Camera] Starting camera capture for device: $path, format: ${source.videoFormat.ifEmpty { "auto" }}"
        )

        if (buildFfmpegCommand(source) == null) {
            System.err.println("[Camera] Unknown device path scheme: $path")
            return
        }

        // ffmpeg is an optional external tool, and a machine without it fails every attempt for the
        // same knowable reason. Discovering that five times over ten seconds tells the operator
        // nothing, so the retry loop does not run.
        //
        // It is reported, though, which it did not used to be. The old reasoning — a tool the user
        // has not installed is not a fault in the app — is right about a *tool* and wrong about this
        // state: the app listed this device in its own picker, the operator chose it, and the app
        // then produced nothing. On Windows the picker deliberately offers unopenable names beside a
        // hint saying what to install, so whether that hint is doing its job is a question about our
        // own UI. Issue #462 is the evidence that it was not, and the reason we could not see it is
        // that this path was silent.
        //
        // Bounded hard: `FfmpegBinary.isAvailable` is a `by lazy`, so a second report in the same
        // process would carry nothing the first did not.
        if (!withContext(Dispatchers.IO) { isFfmpegAvailable() }) {
            System.err.println("[Camera] ffmpeg is not on PATH — cannot capture $path")
            entry.error.value = CameraFailure.FFMPEG_MISSING
            reportCameraFfmpegMissing(source, CameraDeviceCatalog.lastEnumeration, ffmpegMissingReport)
            return
        }

        val loop = CaptureLoop(source, entry)
        loop.run()
        loop.reportIfGaveUp()
    }

    /**
     * One device's retry loop, and what it learned on the way.
     *
     * This is a class rather than a long function because the give-up report needs everything the
     * attempts saw — the last failure, the last command, the last stderr — and threading six
     * accumulating locals out of a `while` is what makes such a loop unreadable.
     */
    private class CaptureLoop(
        private val source: SceneSource.CameraSource,
        private val entry: CacheEntry,
    ) {
        private var consecutiveFailures = 0
        private var everStarted = false
        private var sawImmediateExit = false
        private var stoppedEarly = false

        private var override = CaptureOverride.NONE
        private val tried = mutableSetOf(CaptureOverride.NONE)
        private var knownFormats: List<CameraFormat>? = null

        private var lastFailure = CameraFailure.UNKNOWN
        private var lastCommand: List<String> = emptyList()
        private var lastStderr: List<String> = emptyList()
        private var lastExitCode = -1

        suspend fun run() {
            while (currentCoroutineContext().isActive && !stoppedEarly &&
                consecutiveFailures < MAX_CONSECUTIVE_FAILURES
            ) {
                releaseLingeringProcess(entry)
                val command = buildFfmpegCommand(source, override) ?: return
                lastCommand = command
                System.err.println(
                    "[Camera] Opening device (attempt ${consecutiveFailures + 1}): ${command.joinToString(" ")}"
                )

                val attempt = attemptCapture(command, entry)
                if (attempt != null) everStarted = true
                if (attempt != null && attempt.exitCode > 0 && !attempt.framesProduced) sawImmediateExit = true

                if (attempt?.framesProduced == true) {
                    entry.error.value = null
                    consecutiveFailures = 0
                    delay(RESTART_DELAY_MS)
                } else {
                    consecutiveFailures++
                    recordFailure(attempt)
                    if (!stoppedEarly) delay(RETRY_DELAY_MS)
                }
            }
        }

        /** Classifies a failed attempt, shows it to the operator, and picks what to try next. */
        private suspend fun recordFailure(attempt: FfmpegAttempt?) {
            lastStderr = attempt?.stderrTail.orEmpty()
            lastExitCode = attempt?.exitCode ?: -1
            lastFailure = when {
                attempt == null -> CameraFailure.UNKNOWN
                lastStderr.isEmpty() -> CameraFailure.NO_FRAMES
                else -> classifyCameraFfmpegStderr(lastStderr, deviceScheme(source.devicePath))
                    .takeIf { it != CameraFailure.UNKNOWN } ?: CameraFailure.NO_FRAMES
            }
            entry.error.value = lastFailure

            // A privacy refusal is the operator's to resolve in System Settings; four more attempts
            // over eight seconds change nothing and only delay telling them so. The macOS pair is
            // here for the same reason: whichever of its two causes applies, neither is something a
            // retry two seconds later resolves.
            if (lastFailure == CameraFailure.PERMISSION_DENIED ||
                lastFailure == CameraFailure.PERMISSION_OR_UNAVAILABLE
            ) {
                stoppedEarly = true
                return
            }

            val formats = knownFormats ?: withContext(Dispatchers.IO) {
                listCameraFormats(source.devicePath, source.deviceName)
            }.also { knownFormats = it }

            nextCaptureOverride(lastFailure, lastStderr, formats, tried)?.let {
                System.err.println("[Camera] Device refused the defaults; retrying with $it")
                override = it
                tried += it
            }
        }

        fun reportIfGaveUp() {
            if (consecutiveFailures < MAX_CONSECUTIVE_FAILURES && !stoppedEarly) return
            val reason = cameraGiveUpReason(everStarted, sawImmediateExit)
            System.err.println("[Camera] Giving up after $consecutiveFailures failures ($reason/$lastFailure)")
            // What enumeration found is carried alongside what capture saw, because on its own
            // "could not open" does not say whether the name we tried was one ffmpeg had offered.
            // That distinction is the whole of issue #462, and asking a reporter to run
            // `ffmpeg -list_devices` by hand was the only way to learn it.
            val facts = CameraDeviceCatalog.lastEnumeration
            CrashReporter.reportWarning(
                "Camera: Giving up on device after repeated ffmpeg failures",
                tags = mapOf(
                    "subsystem" to "camera",
                    "give_up_reason" to reason,
                    "device_scheme" to deviceScheme(source.devicePath),
                    "failure_cause" to lastFailure.name.lowercase(),
                    "attempts" to consecutiveFailures.toString()
                ) + cameraEnumerationTags(facts, source.deviceName, ffmpegAvailable = true),
                extras = mapOf(
                    "ffmpeg_stderr_tail" to redactedFfmpegStderr(lastStderr, source.deviceName),
                    "ffmpeg_command" to redactedFfmpegCommand(lastCommand),
                    "exit_code" to lastExitCode.toString(),
                    "camera_enumeration" to
                        cameraEnumerationExtra(facts, source.deviceName, ffmpegAvailable = true)
                )
            )
        }
    }
}

/** One camera's shared state: the frames on screen, why they stopped, and who is still watching. */
private class CacheEntry(
    val frame: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null),
    val error: MutableStateFlow<CameraFailure?> = MutableStateFlow(null),
    var refCount: Int = 0,
    var captureJob: Job? = null,
    var ffmpegProcess: Process? = null
)

/**
 * What one attempt at opening the device observed.
 *
 * [framesProduced] is what tells a dropped stream apart from a device that never opened, and
 * [stderrTail] is why it never opened — ffmpeg says so itself, in the output this used to print to
 * `System.err` and discard. A packaged `.app` has no stderr to print to, which is how 43 Sentry
 * warnings arrived carrying nothing but the fact of the failure.
 */
private class FfmpegAttempt(
    val framesProduced: Boolean,
    val exitCode: Int,
    val stderrTail: List<String>,
)

/** ffmpeg's stderr as it arrives: the retained tail, and the first frame size announced in it. */
private class StderrDrain(
    val job: Job,
    private val lines: MutableList<String>,
    val videoDims: java.util.concurrent.atomic.AtomicReference<Pair<Int, Int>?>,
) {
    fun tail(): List<String> = synchronized(lines) { lines.toList() }
}

/**
 * Starts draining [process]'s stderr immediately, keeping the last [STDERR_TAIL_LINES] lines.
 *
 * This must run from the moment the process starts rather than only once frames are expected. A
 * process nobody is reading fills its stderr pipe and then blocks forever, and — the reason this
 * was moved — the attempts that exit straight back out are exactly the ones whose stderr names the
 * cause, so the old code threw away its own diagnosis on the one path that had one.
 */
private fun CoroutineScope.startStderrDrain(process: Process): StderrDrain {
    val lines = mutableListOf<String>()
    val videoDims = java.util.concurrent.atomic.AtomicReference<Pair<Int, Int>?>(null)
    val job = launch(Dispatchers.IO) {
        try {
            process.errorStream.bufferedReader().useLines { stream ->
                stream.forEach { line ->
                    synchronized(lines) {
                        lines.add(line)
                        if (lines.size > STDERR_TAIL_LINES) lines.removeAt(0)
                    }
                    if (videoDims.get() == null) {
                        parseFfmpegVideoDimensions(line)?.let { videoDims.set(it) }
                    }
                }
            }
        } catch (_: Throwable) {}
    }
    return StderrDrain(job, lines, videoDims)
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

/** Kills whatever is left of the previous attempt and lets the OS hand the device back. */
private suspend fun releaseLingeringProcess(entry: CacheEntry) {
    val old = entry.ffmpegProcess ?: return
    withContext(Dispatchers.IO) { killFfmpegProcess(old) }
    entry.ffmpegProcess = null
    delay(DEVICE_RELEASE_DELAY_MS)
}

/**
 * Why the capture loop gave up, from what its attempts actually observed.
 *
 * The warning this tags used to carry `subsystem=camera` and nothing else, which cannot separate
 * the three things that end the loop — and they have nothing in common. Each of these points at a
 * different fix, and the first two are the user's environment rather than a defect:
 *
 *  * `ffmpeg_not_launchable` — ffmpeg answered `-version` but every attempt to run the capture
 *    command failed to start a process at all. A PATH or permissions problem on the machine.
 *  * `device_unavailable` — ffmpeg started and exited straight back out. The device is held by
 *    another application, or was unplugged between enumeration and capture.
 *  * `no_frames` — ffmpeg ran and stayed running without ever producing a frame. The one shape
 *    here that suggests the command this code builds is wrong for the device.
 */
internal fun cameraGiveUpReason(everStarted: Boolean, sawImmediateExit: Boolean): String = when {
    !everStarted -> "ffmpeg_not_launchable"
    sawImmediateExit -> "device_unavailable"
    else -> "no_frames"
}

/**
 * The capture scheme [devicePath] names, or "unknown" — low-cardinality, so it can be a tag.
 *
 * Which OS capture API was in play separates a dshow problem from a v4l2 one without carrying the
 * device path itself, which names the user's hardware.
 */
internal fun deviceScheme(devicePath: String): String =
    devicePath.substringBefore("://", missingDelimiterValue = "").ifEmpty { "unknown" }

/**
 * Merges [override] into the `-video_size`/`-framerate` args [requested] by the chosen format.
 *
 * [CaptureOverride.DEVICE_DEFAULTS] discards [requested] outright rather than merging into it: it
 * is the attempt that asks the device for nothing, so the size and rate the source asked for are
 * exactly what has to go.
 *
 * `-framerate` is replaced rather than appended: ffmpeg takes the last occurrence of an input
 * option, but two of them in one argv is a command nobody can read in a bug report. `-pixel_format`
 * has no counterpart in the requested args, so it is simply added.
 */
internal fun applyCaptureOverride(requested: List<String>, override: CaptureOverride): List<String> {
    if (override.useDeviceDefaults) return emptyList()
    val withoutFramerate = if (override.framerate == null) requested else buildList {
        var i = 0
        while (i < requested.size) {
            if (requested[i] == "-framerate") i += 2 else add(requested[i++])
        }
    }
    return withoutFramerate +
        (override.framerate?.let { listOf("-framerate", it) } ?: emptyList()) +
        (override.pixelFormat?.let { listOf("-pixel_format", it) } ?: emptyList())
}

/**
 * Builds the ffmpeg command line for capturing [source]'s device as raw BGRA video, or `null` when
 * [SceneSource.CameraSource.devicePath] doesn't match a recognized OS capture scheme
 * (`dshow://`, `v4l2://`, `avfoundation://`).
 *
 * [override] carries the input flags a previous attempt learned the device actually wants, and
 * wins over what [SceneSource.CameraSource.videoFormat] asked for: the device rejecting a frame
 * rate is better evidence than the format list that suggested it. [CaptureOverride.NONE] — the
 * default, and every attempt before a device has complained — leaves the argv exactly as it was.
 */
internal fun buildFfmpegCommand(
    source: SceneSource.CameraSource,
    override: CaptureOverride = CaptureOverride.NONE,
): List<String>? {
    val path = source.devicePath

    // Parse video format into ffmpeg input args (must come before -i)
    val requested = if (source.videoFormat.isNotEmpty()) {
        val match = Regex("""(\d+)x(\d+)@(\d+)""").find(source.videoFormat)
        if (match != null) {
            val (w, h, fps) = match.destructured
            listOf("-video_size", "${w}x${h}", "-framerate", fps)
        } else emptyList()
    } else emptyList()

    val formatArgs = applyCaptureOverride(requested, override)

    return when {
        path.startsWith("dshow://") -> {
            val deviceName = path.removePrefix("dshow://").removePrefix(":dshow-vdev=")
            listOf(FfmpegBinary.path, "-f", "dshow") + formatArgs + listOf("-i", "video=$deviceName",
                "-an", "-vf", "fps=30", "-pix_fmt", "bgra",
                "-f", "rawvideo", "-")
        }
        path.startsWith("v4l2://") -> {
            val device = path.removePrefix("v4l2://")
            listOf(FfmpegBinary.path, "-f", "v4l2") + formatArgs + listOf("-i", device,
                "-an", "-vf", "fps=30", "-pix_fmt", "bgra",
                "-f", "rawvideo", "-")
        }
        path.startsWith("avfoundation://") -> {
            val index = path.removePrefix("avfoundation://")
            listOf(FfmpegBinary.path, "-f", "avfoundation") + formatArgs + listOf("-i", "$index:none",
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
