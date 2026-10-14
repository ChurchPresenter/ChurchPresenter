package org.churchpresenter.canvas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.diagnostics.Log

/**
 * One device's retry loop, and what it learned on the way.
 *
 * This is a class rather than a long function because the give-up report needs everything the
 * attempts saw — the last failure, the last command, the last stderr — and threading six
 * accumulating locals out of a `while` is what makes such a loop unreadable.
 */
internal class CaptureLoop(
    private val source: SceneSource.CameraSource,
    private val entry: CacheEntry,
    private val steps: CaptureSteps = FfmpegCaptureSteps,
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
            steps.releaseLingering(entry)
            val command = buildFfmpegCommand(source, override) ?: return
            lastCommand = command
            Log.info(
                "Camera",
                "Opening device (attempt ${consecutiveFailures + 1}): ${command.joinToString(" ")}"
            )

            val attempt = steps.attempt(command, entry)
            if (attempt != null) everStarted = true
            if (attempt?.exitedImmediately == true) sawImmediateExit = true

            if (attempt?.framesProduced == true) {
                entry.error.value = null
                consecutiveFailures = 0
                steps.pause(RESTART_DELAY_MS)
            } else {
                consecutiveFailures++
                recordFailure(attempt)
                if (!stoppedEarly) steps.pause(RETRY_DELAY_MS)
            }
        }
    }

    /** Classifies a failed attempt, shows it to the operator, and picks what to try next. */
    private suspend fun recordFailure(attempt: FfmpegAttempt?) {
        lastStderr = attempt?.stderrTail.orEmpty()
        lastExitCode = attempt?.exitCode ?: -1
        val classified = when {
            attempt == null -> CameraFailure.UNKNOWN
            lastStderr.isEmpty() -> CameraFailure.NO_FRAMES
            else -> classifyCameraFfmpegStderr(lastStderr, deviceScheme(source.devicePath))
                .takeIf { it != CameraFailure.UNKNOWN } ?: CameraFailure.NO_FRAMES
        }
        lastFailure = refineForWindowsPrivacy(
            refineForBlindListing(classified, steps.lastEnumeration()),
            deviceScheme(source.devicePath),
        ) { windowsCameraBlocked(::queryRegistryValue) }
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

        val formats = knownFormats ?: steps.formats(source).also { knownFormats = it }

        nextCaptureOverride(lastFailure, lastStderr, formats, tried)?.let {
            Log.warn("Camera", "Device refused the defaults; retrying with $it")
            override = it
            tried += it
        }
    }

    fun reportIfGaveUp() {
        if (consecutiveFailures < MAX_CONSECUTIVE_FAILURES && !stoppedEarly) return
        val reason = cameraGiveUpReason(everStarted, sawImmediateExit)
        Log.warn("Camera", "Giving up after $consecutiveFailures failures ($reason/$lastFailure)")
        // What enumeration found is carried alongside what capture saw, because on its own
        // "could not open" does not say whether the name we tried was one ffmpeg had offered.
        // That distinction is the whole of issue #462, and asking a reporter to run
        // `ffmpeg -list_devices` by hand was the only way to learn it.
        val facts = steps.lastEnumeration()
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

/**
 * What [CaptureLoop] does to the outside world: run one ffmpeg attempt, ask the device its formats,
 * wait, and clear the previous attempt away. [FfmpegCaptureSteps] is the real machine; a test
 * scripts the attempts and does not wait.
 */
internal interface CaptureSteps {
    suspend fun attempt(command: List<String>, entry: CacheEntry): FfmpegAttempt?
    suspend fun formats(source: SceneSource.CameraSource): List<CameraFormat>
    suspend fun pause(millis: Long)
    suspend fun releaseLingering(entry: CacheEntry)
    fun lastEnumeration(): CameraEnumerationFacts?
}

/** The real capture: ffmpeg processes, the device's own format list, and real time. */
internal object FfmpegCaptureSteps : CaptureSteps {
    override suspend fun attempt(command: List<String>, entry: CacheEntry): FfmpegAttempt? =
        SharedCameraFrameCache.attemptCapture(command, entry)

    override suspend fun formats(source: SceneSource.CameraSource): List<CameraFormat> =
        withContext(Dispatchers.IO) { listCameraFormats(source.devicePath, source.deviceName) }

    override suspend fun pause(millis: Long) = delay(millis)

    override suspend fun releaseLingering(entry: CacheEntry) = releaseLingeringProcess(entry)

    override fun lastEnumeration(): CameraEnumerationFacts? = CameraDeviceCatalog.lastEnumeration
}

private const val MAX_CONSECUTIVE_FAILURES = 5
private const val RETRY_DELAY_MS = 2000L
private const val RESTART_DELAY_MS = 1000L
