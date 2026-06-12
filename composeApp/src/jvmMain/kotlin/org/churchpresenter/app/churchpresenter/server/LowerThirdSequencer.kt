package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.churchpresenter.app.churchpresenter.data.settings.AtemSettings

/**
 * Orchestrates the Bitfocus Companion lower-third sequence so one HTTP call does
 * the whole timed dance the app alone knows the timing for:
 *
 *   DSK cut ON → pre-roll → lower third goes live → animation duration (+ pause)
 *   → post-roll → DSK cut OFF → clear
 *
 * The DSK cuts are invisible because the app's output is transparent at both
 * moments — the lottie's own animate-in/out is all the viewer sees.
 *
 * The actual go-live/clear happen in the UI layer: main.kt collects [onShow] and
 * [onClear] next to the other CompanionServer remote-control flows. ATEM failures
 * never block the lower third itself — the sequence continues without the DSK.
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
    private var activeClient: AtemClient? = null
    private var activeKeyer: Int = -1

    /** Bumped on every run/stop so a preempted job's cleanup can tell it no longer owns the DSK. */
    private var generation = 0

    /**
     * Start the full sequence. Cancels any sequence already running.
     *
     * The DSK-on happens synchronously so the caller can report a connection
     * problem in the HTTP response; the timed remainder runs in the background.
     *
     * @param keyer   0-based DSK index to drive, or null to skip DSK control
     * @param autoEnd false = "show" mode: stay on air until [stop] is called
     * @return DSK error message, or null when the keyer went on air (or was skipped)
     */
    suspend fun run(
        name: String,
        json: String,
        durationMs: Long,
        pauseAtFrame: Boolean,
        pauseDurationMs: Long,
        keyer: Int?,
        atem: AtemSettings,
        autoEnd: Boolean = true
    ): String? = mutex.withLock {
        stopLocked()

        var dskError: String? = null
        if (keyer != null && atem.host.isNotBlank()) {
            try {
                val client = AtemClient(atem.host, atem.port)
                client.connect(collectState = false)
                client.setDownstreamKeyerOnAir(keyer, true)
                activeClient = client
                activeKeyer = keyer
            } catch (e: Exception) {
                dskError = e.message ?: "ATEM unreachable"
                System.err.println("[LowerThirdSequencer] DSK on failed: $dskError")
                activeClient?.disconnect()
                activeClient = null
                activeKeyer = -1
            }
        }

        _status.value = "running:$name"
        val totalMs = durationMs + (if (pauseAtFrame) pauseDurationMs else 0L)
        val gen = ++generation
        job = scope.launch {
            try {
                delay(atem.dskPreRollMs.toLong())
                onShow.emit(ShowRequest(json, pauseAtFrame, pauseFrame = -1f, pauseDurationMs = pauseDurationMs))
                if (autoEnd) delay(totalMs + atem.dskPostRollMs)
                else delay(Long.MAX_VALUE)   // "show" mode: on air until stop()
            } finally {
                // On natural completion only — when preempted or stopped, the new
                // run / stop() already handled the DSK and display, and this job
                // must not touch a keyer it no longer owns
                var ownsSequence = false
                mutex.withLock {
                    if (generation == gen) {
                        releaseDskLocked()
                        _status.value = "idle"
                        ownsSequence = true
                    }
                }
                if (ownsSequence) onClear.emit(Unit)
            }
        }
        dskError
    }

    /** Abort the running sequence immediately: DSK off, clear, idle. */
    suspend fun stop() = mutex.withLock { stopLocked() }

    private fun stopLocked() {
        generation++
        job?.cancel()
        job = null
        releaseDskLocked()
        _status.value = "idle"
        onClear.tryEmit(Unit)
    }

    private fun releaseDskLocked() {
        val client = activeClient ?: return
        activeClient = null
        val keyer = activeKeyer
        activeKeyer = -1
        scope.launch {
            runCatching { client.setDownstreamKeyerOnAir(keyer, false) }
                .onFailure { System.err.println("[LowerThirdSequencer] DSK off failed: ${it.message}") }
            client.disconnect()
        }
    }
}
