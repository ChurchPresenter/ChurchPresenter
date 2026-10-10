package org.churchpresenter.lowerthird

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.churchpresenter.atem.AtemConnectionManager
import org.churchpresenter.atem.AtemKey
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.settings.AtemSettings
import java.io.IOException

/**
 * Orchestrates the Bitfocus Companion lower-third sequence so one HTTP call does
 * the whole timed dance the app alone knows the timing for:
 *
 *   key cut ON → pre-roll → lower third goes live → animation duration (+ pause)
 *   → post-roll → key cut OFF → clear
 *
 * The key cuts are invisible because the app's output is transparent at both
 * moments — the lottie's own animate-in/out is all the viewer sees.
 *
 * The actual go-live/clear happen in the UI layer: main.kt collects [onShow] and
 * [onClear] next to the other CompanionServer remote-control flows. ATEM failures
 * never block the lower third itself — the sequence continues without the key.
 */
/** A lower third to run: its name, its lottie JSON, and how long it plays and holds. */
data class LowerThirdClip(
    val name: String,
    val json: String,
    val durationMs: Long,
    val pauseAtFrame: Boolean,
    val pauseDurationMs: Long,
)

/**
 * The ATEM key a lower third goes on air on.
 *
 * @param mixEffect 0-based M/E index, or null to skip key control (ignored for downstream keys)
 * @param keyer     0-based keyer index (upstream keyer, or DSK index when [useDownstreamKey]), or
 *                  null to skip key control
 * @param useDownstreamKey drive the key as a downstream keyer (DSK) instead of upstream
 */
data class LowerThirdKey(val mixEffect: Int?, val keyer: Int?, val useDownstreamKey: Boolean = false)

/** Drives one ATEM key on or off air at a switcher. */
fun interface AtemKeyDriver {
    suspend fun setOnAir(host: String, port: Int, key: AtemKey, onAir: Boolean)
}

/**
 * Through AtemConnectionManager's keepalive connection, so the off (which can fire many seconds
 * after the on) lands on a live session instead of a stale short-lived socket.
 */
val connectionManagerKeyDriver = AtemKeyDriver { host, port, key, onAir ->
    if (onAir) {
        AtemConnectionManager.use(host, port, needsState = false) { it.setKeyOnAir(key, true) }
    } else {
        AtemConnectionManager.use(host, port) { it.setKeyOnAir(key, false) }
    }
}

object LowerThirdSequencer {

    /** Everything the UI layer needs to put a lower third on air. */
    class ShowRequest(
        val json: String,
        val pauseAtFrame: Boolean,
        val pauseFrame: Float,
        val pauseDurationMs: Long,
        val name: String
    )

    val onShow = MutableSharedFlow<ShowRequest>(extraBufferCapacity = 4)
    val onClear = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    private val _status = MutableStateFlow("idle")
    val status: StateFlow<String> = _status

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()
    private var job: Job? = null

    // The active key target, and the driver that put it on air, which takes it off again.
    private var activeDriver: AtemKeyDriver = connectionManagerKeyDriver
    private var activeHost: String? = null
    private var activePort: Int = 9910
    private var activeMixEffect: Int = -1
    private var activeKeyer: Int = -1
    private var activeUseDsk: Boolean = false

    /** Bumped on every run/stop so a preempted job's cleanup can tell it no longer owns the key. */
    private var generation = 0

    /**
     * Start the full sequence. Cancels any sequence already running.
     *
     * The key-on happens synchronously so the caller can report a connection
     * problem in the HTTP response; the timed remainder runs in the background.
     *
     * @param clip    what is run, and for how long
     * @param key     which ATEM key takes it on air
     * @param autoEnd false = "show" mode: stay on air until [stop] is called
     * @param driver  how the key is driven; the switcher's keepalive connection unless a test says
     * @return key error message, or null when the key went on air (or was skipped)
     */
    suspend fun run(
        clip: LowerThirdClip,
        key: LowerThirdKey,
        atem: AtemSettings,
        autoEnd: Boolean = true,
        driver: AtemKeyDriver = connectionManagerKeyDriver,
    ): String? = mutex.withLock {
        val name = clip.name
        val mixEffect = key.mixEffect
        val keyer = key.keyer
        val useDownstreamKey = key.useDownstreamKey
        stopLocked()

        var keyError: String? = null
        if (mixEffect != null && keyer != null && atem.host.isNotBlank()) {
            try {
                driver.setOnAir(atem.host, atem.port, AtemKey(useDownstreamKey, mixEffect, keyer), true)
                activeDriver = driver
                activeHost = atem.host
                activePort = atem.port
                activeMixEffect = mixEffect
                activeKeyer = keyer
                activeUseDsk = useDownstreamKey
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // The ATEM link: AtemProtocolException is one.
                keyError = keyOnFailed(e)
            } catch (e: IllegalStateException) {
                keyError = keyOnFailed(e)
            }
        }

        _status.value = "running:$name"
        val totalMs = clip.durationMs + (if (clip.pauseAtFrame) clip.pauseDurationMs else 0L)
        val gen = ++generation
        job = scope.launch {
            try {
                delay(atem.keyPreRollMs.toLong())
                onShow.emit(
                    ShowRequest(
                        clip.json,
                        clip.pauseAtFrame,
                        pauseFrame = -1f,
                        pauseDurationMs = clip.pauseDurationMs,
                        name = name,
                    )
                )
                if (autoEnd) delay(totalMs + atem.keyPostRollMs)
                else delay(Long.MAX_VALUE)   // "show" mode: on air until stop()
            } finally {
                // On natural completion only — when preempted or stopped, the new
                // run / stop() already handled the DSK and display, and this job
                // must not touch a keyer it no longer owns
                var ownsSequence = false
                mutex.withLock {
                    if (generation == gen) {
                        releaseKeyLocked()
                        _status.value = "idle"
                        ownsSequence = true
                    }
                }
                if (ownsSequence) onClear.emit(Unit)
            }
        }
        keyError
    }

    /** Reports a key that would not go on air, forgets the target, and returns the reason. */
    private fun keyOnFailed(e: Exception): String {
        val reason = e.message ?: "ATEM unreachable"
        Log.warn("LowerThirdSequencer", "key on failed: $reason")
        CrashReporter.reportWarning(
            "LowerThirdSequencer: ATEM key on failed",
            throwable = e,
            tags = mapOf("subsystem" to "atem")
        )
        activeHost = null
        activeMixEffect = -1
        activeKeyer = -1
        return reason
    }

    /** Abort the running sequence immediately: key off, clear, idle. */
    suspend fun stop() = mutex.withLock { stopLocked() }

    private suspend fun stopLocked() {
        generation++
        job?.cancel()
        job = null
        releaseKeyLocked()
        _status.value = "idle"
        onClear.tryEmit(Unit)
    }

    /**
     * Turn off the active key (if any) through the keepalive-managed connection and
     * await it, so preemption is deterministic: a previous key is off before the next
     * one is turned on, and the natural-end off lands on the live session.
     */
    private suspend fun releaseKeyLocked() {
        val host = activeHost ?: return
        val port = activePort
        val mixEffect = activeMixEffect
        val keyer = activeKeyer
        val useDsk = activeUseDsk
        activeHost = null
        activeMixEffect = -1
        activeKeyer = -1
        activeUseDsk = false
        runCatching {
            activeDriver.setOnAir(host, port, AtemKey(useDsk, mixEffect, keyer), false)
        }.onFailure {
            Log.warn("LowerThirdSequencer", "key off failed: ${it.message}")
            CrashReporter.reportWarning(
                "LowerThirdSequencer: ATEM key off failed",
                throwable = it,
                tags = mapOf("subsystem" to "atem")
            )
        }
    }
}
