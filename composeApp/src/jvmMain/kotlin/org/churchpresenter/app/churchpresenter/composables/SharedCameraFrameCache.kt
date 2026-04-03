package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.churchpresenter.app.churchpresenter.models.SceneSource

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
        val error: MutableStateFlow<String?> = MutableStateFlow(null),
        var refCount: Int = 0,
        var captureJob: Job? = null,
        var ffmpegProcess: Process? = null
    )

    /** Build a unique key for a camera source. */
    private fun keyFor(source: SceneSource.CameraSource): String {
        return if (source.isDeckLink && source.deckLinkIndex >= 0) {
            "decklink:${source.deckLinkIndex}:${source.videoFormat}:${source.videoConnection}"
        } else {
            "ffmpeg:${source.devicePath}:${source.videoFormat}"
        }
    }

    data class CameraFlows(
        val frame: StateFlow<ImageBitmap?>,
        val error: StateFlow<String?>
    )

    /**
     * Acquire a shared frame flow for this camera source.
     * First subscriber starts the capture; subsequent subscribers share it.
     */
    @Synchronized
    fun acquire(source: SceneSource.CameraSource): CameraFlows {
        val key = keyFor(source)
        val entry = entries.getOrPut(key) { CacheEntry() }
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

            // Only close the device if no other cache entry is using the same device.
            // When switching connections, the new acquire's openInput() already closed
            // the old input — calling closeInput here would kill the new one.
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

    // ── DeckLink capture ────────────────────────────────────────────

    private suspend fun runDeckLinkCapture(source: SceneSource.CameraSource, entry: CacheEntry) {
        System.err.println("[DeckLink Input] Opening device ${source.deckLinkIndex}, " +
            "format: ${source.videoFormat.ifEmpty { "auto" }}, connection: ${source.videoConnection}")

        val opened = withContext(Dispatchers.IO) {
            DeckLinkManager.openInput(source.deckLinkIndex, source.videoFormat, source.videoConnection)
        }
        if (!opened) {
            System.err.println("[DeckLink Input] Failed to open input on device ${source.deckLinkIndex}")
            entry.error.value = "Cannot open input — device may already be in use for output"
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

            if (frameData != null && frameData.size > 2) {
                val w = frameData[0]
                val h = frameData[1]
                if (w > 0 && h > 0) {
                    val img = withContext(Dispatchers.IO) {
                        val bi = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                        bi.setRGB(0, 0, w, h, frameData, 2, w)
                        bi
                    }
                    entry.frame.value = img.toComposeImageBitmap()
                    frameCount++
                    nullCount = 0
                    if (frameCount == 1) {
                        System.err.println("[DeckLink Input] First frame: ${w}x${h}")
                    }
                }
            } else {
                nullCount++
                if (nullCount > 30 && entry.frame.value != null) {
                    entry.frame.value = null  // no signal — clear display
                }
            }

            delay(16) // ~60fps polling
        }
    }

    // ── FFmpeg capture ──────────────────────────────────────────────

    private suspend fun runFfmpegCapture(source: SceneSource.CameraSource, entry: CacheEntry) {
        val path = source.devicePath
        System.err.println("[Camera] Starting camera capture for device: $path, format: ${source.videoFormat.ifEmpty { "auto" }}")

        // Parse video format into ffmpeg input args (must come before -i)
        val formatArgs = if (source.videoFormat.isNotEmpty()) {
            val match = Regex("""(\d+)x(\d+)@(\d+)""").find(source.videoFormat)
            if (match != null) {
                val (w, h, fps) = match.destructured
                listOf("-video_size", "${w}x${h}", "-framerate", fps)
            } else emptyList()
        } else emptyList()

        val command = when {
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
            else -> {
                System.err.println("[Camera] Unknown device path scheme: $path")
                return
            }
        }

        var consecutiveFailures = 0
        while (currentCoroutineContext().isActive && consecutiveFailures < 5) {
            // Kill any lingering process and wait for the OS to release the device
            val old = entry.ffmpegProcess
            if (old != null) {
                withContext(Dispatchers.IO) { killFfmpegProcess(old) }
                entry.ffmpegProcess = null
                delay(500)
            }

            System.err.println("[Camera] Opening device (attempt ${consecutiveFailures + 1}): ${command.joinToString(" ")}")
            val process = withContext(Dispatchers.IO) {
                try {
                    ProcessBuilder(command).redirectErrorStream(false).start()
                } catch (e: Throwable) {
                    System.err.println("[Camera] Failed to start ffmpeg: ${e.message}")
                    null
                }
            }
            if (process == null) {
                consecutiveFailures++
                delay(2000)
                continue
            }

            // Check whether ffmpeg managed to open the device
            val exited = withContext(Dispatchers.IO) { process.waitFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS) }
            if (exited && process.exitValue() != 0) {
                System.err.println("[Camera] ffmpeg exited immediately with code ${process.exitValue()}")
                withContext(Dispatchers.IO) { killFfmpegProcess(process) }
                consecutiveFailures++
                delay(2000)
                continue
            }
            entry.ffmpegProcess = process

            // Drain stderr and extract video dimensions from ffmpeg output
            val stderrLines = mutableListOf<String>()
            val videoDims = java.util.concurrent.atomic.AtomicReference<Pair<Int, Int>?>(null)
            val stderrJob = CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO) {
                try {
                    val dimPattern = Regex("""(\d{2,5})x(\d{2,5})""")
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            synchronized(stderrLines) {
                                stderrLines.add(line)
                                if (stderrLines.size > 50) stderrLines.removeAt(0)
                            }
                            if (videoDims.get() == null && line.contains("Video:") && line.contains("bgra")) {
                                val m = dimPattern.find(line.substringAfter("bgra"))
                                if (m != null) {
                                    val w = m.groupValues[1].toIntOrNull() ?: 0
                                    val h = m.groupValues[2].toIntOrNull() ?: 0
                                    if (w > 0 && h > 0) videoDims.set(Pair(w, h))
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            // Wait for dimensions (up to 5 seconds)
            var dims: Pair<Int, Int>? = null
            for (i in 1..50) {
                dims = videoDims.get()
                if (dims != null) break
                delay(100)
            }
            if (dims == null) {
                System.err.println("[Camera] Could not determine video dimensions from ffmpeg")
                stderrJob.cancel()
                withContext(Dispatchers.IO) { killFfmpegProcess(process) }
                entry.ffmpegProcess = null
                consecutiveFailures++
                delay(2000)
                continue
            }

            val (videoW, videoH) = dims
            val frameBytes = videoW * videoH * 4  // BGRA = 4 bytes per pixel
            System.err.println("[Camera] Capturing ${videoW}x${videoH} rawvideo BGRA ($frameBytes bytes/frame)")

            val inputStream = java.io.BufferedInputStream(process.inputStream, frameBytes * 2)
            val frameBuf = ByteArray(frameBytes)
            val pixelBuf = IntArray(videoW * videoH)
            var frameCount = 0

            while (currentCoroutineContext().isActive) {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        var read = 0
                        while (read < frameBytes) {
                            val r = inputStream.read(frameBuf, read, frameBytes - read)
                            if (r == -1) return@withContext false
                            read += r
                        }
                        true
                    } catch (_: Throwable) { false }
                }
                if (!ok) break

                withContext(Dispatchers.IO) {
                    var bi = 0
                    for (pi in pixelBuf.indices) {
                        val b = frameBuf[bi].toInt() and 0xFF
                        val g = frameBuf[bi + 1].toInt() and 0xFF
                        val r = frameBuf[bi + 2].toInt() and 0xFF
                        val a = frameBuf[bi + 3].toInt() and 0xFF
                        pixelBuf[pi] = (a shl 24) or (r shl 16) or (g shl 8) or b
                        bi += 4
                    }
                }

                val img = java.awt.image.BufferedImage(videoW, videoH, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                img.setRGB(0, 0, videoW, videoH, pixelBuf, 0, videoW)
                entry.frame.value = img.toComposeImageBitmap()
                frameCount++
                if (frameCount == 1) {
                    System.err.println("[Camera] First frame received (${videoW}x${videoH})")
                }
            }

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
                consecutiveFailures = 0
                delay(1000)
            } else {
                System.err.println("[Camera] ffmpeg exited with code $exitCode without producing any frames")
                synchronized(stderrLines) {
                    stderrLines.forEach { System.err.println("[Camera] ffmpeg stderr: $it") }
                }
                consecutiveFailures++
                delay(2000)
            }
        }

        if (consecutiveFailures >= 5) {
            System.err.println("[Camera] Giving up after $consecutiveFailures consecutive failures")
        }
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
                    .waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            } catch (_: Throwable) {}
            try {
                ProcessBuilder("taskkill", "/F", "/IM", "ffmpeg.exe")
                    .redirectErrorStream(true).start()
                    .waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            } catch (_: Throwable) {}
        } else {
            process.destroyForcibly()
        }
        process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
    } catch (_: Throwable) {
        process.destroyForcibly()
    }
}
