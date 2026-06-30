package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.CoroutineScope
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
import org.churchpresenter.app.churchpresenter.data.settings.AtemSettings

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
object LowerThirdSequencer {

    /** Everything the UI layer needs to put a lower third on air. */
    class ShowRequest(
        val json: String,
        val pauseAtFrame: Boolean,
        val pauseFrame: Float,
        val pauseDurationMs: Long
    )

    val onShow = MutableSharedFlow<ShowRequest>(extraBufferCapacity = 4)
    val onClear = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    private val _status = MutableStateFlow("idle")
    val status: StateFlow<String> = _status

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()
    private var job: Job? = null
    // The active key target is driven through AtemConnectionManager's keepalive
    // connection so the off (which can fire many seconds after the on) lands on a
    // live session instead of a stale short-lived socket.
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
     * @param mixEffect 0-based M/E index, or null to skip key control (ignored for downstream keys)
     * @param keyer     0-based keyer index (upstream keyer, or DSK index when [useDownstreamKey]),
     *                  or null to skip key control
     * @param useDownstreamKey drive the key as a downstream keyer (DSK) instead of upstream
     * @param autoEnd   false = "show" mode: stay on air until [stop] is called
     * @return key error message, or null when the key went on air (or was skipped)
     */
    suspend fun run(
        name: String,
        json: String,
        durationMs: Long,
        pauseAtFrame: Boolean,
        pauseDurationMs: Long,
        mixEffect: Int?,
        keyer: Int?,
        atem: AtemSettings,
        useDownstreamKey: Boolean = false,
        autoEnd: Boolean = true
    ): String? = mutex.withLock {
        stopLocked()

        var keyError: String? = null
        if (mixEffect != null && keyer != null && atem.host.isNotBlank()) {
            try {
                AtemConnectionManager.use(atem.host, atem.port, needsState = false) { client ->
                    client.setKeyOnAir(useDownstreamKey, mixEffect, keyer, true)
                }
                activeHost = atem.host
                activePort = atem.port
                activeMixEffect = mixEffect
                activeKeyer = keyer
                activeUseDsk = useDownstreamKey
            } catch (e: Exception) {
                keyError = e.message ?: "ATEM unreachable"
                System.err.println("[LowerThirdSequencer] key on failed: $keyError")
                activeHost = null
                activeMixEffect = -1
                activeKeyer = -1
            }
        }

        _status.value = "running:$name"
        val totalMs = durationMs + (if (pauseAtFrame) pauseDurationMs else 0L)
        val gen = ++generation
        job = scope.launch {
            try {
                delay(atem.keyPreRollMs.toLong())
                onShow.emit(ShowRequest(json, pauseAtFrame, pauseFrame = -1f, pauseDurationMs = pauseDurationMs))
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
            AtemConnectionManager.use(host, port) { it.setKeyOnAir(useDsk, mixEffect, keyer, false) }
        }.onFailure { System.err.println("[LowerThirdSequencer] key off failed: ${it.message}") }
    }
}
