package org.churchpresenter.canvas

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Singleton that holds countdown timer runtime state (remainingSeconds, isRunning)
 * keyed by ClockSource ID, so both the canvas renderer and the properties panel
 * share the same live state.
 *
 * A count-up source is held here too, and stores its elapsed seconds in the same
 * [TimerState.remainingSeconds] field — it is seeded at zero and counted up instead of down.
 *
 * **The clock that advances a timer lives here, not in the renderer.** A scene is composed in
 * several places at once — the canvas editor, the live preview, every output window — and while
 * each of those drove its own one-second loop against this shared state they all advanced the same
 * timer, so it ran at one second per *renderer* per second: a stopwatch climbing five at a time,
 * and a countdown whose direction depended on which renderers happened to be showing which mode.
 * One ticker per source, started by [setRunning], cannot do that whatever is on screen.
 */
object TimerStateManager {

    data class TimerState(
        val remainingSeconds: Int,
        val isRunning: Boolean,
        /** Which way [TimerState.remainingSeconds] moves while running: up for a stopwatch. */
        val countUp: Boolean = false
    )

    // Compose-observable map: sourceId -> TimerState
    private val _states = mutableStateMapOf<String, TimerState>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tickers = mutableMapOf<String, Job>()

    fun getState(sourceId: String, totalSeconds: Int): TimerState {
        return _states.getOrPut(sourceId) { TimerState(totalSeconds, false) }
    }

    /** Starts or stops [sourceId], counting down by default and up when [countUp] is set. */
    fun setRunning(sourceId: String, totalSeconds: Int, running: Boolean, countUp: Boolean = false) {
        val current = _states[sourceId] ?: TimerState(totalSeconds, false)
        _states[sourceId] = current.copy(isRunning = running, countUp = countUp)
        tickers.remove(sourceId)?.cancel()
        if (!running) return
        tickers[sourceId] = scope.launch {
            TimerTicker().run(sourceId, countUp)
        }
    }

    /** One tick of [sourceId], whichever way it counts. */
    internal fun advance(sourceId: String, countUp: Boolean) {
        if (countUp) tickUp(sourceId) else tick(sourceId)
    }

    /** Whether [sourceId] is still meant to be advancing; the ticker stops the moment it is not. */
    internal fun isTicking(sourceId: String): Boolean = _states[sourceId]?.isRunning == true

    fun tick(sourceId: String) {
        val current = _states[sourceId] ?: return
        if (current.isRunning && current.remainingSeconds > 0) {
            val next = current.remainingSeconds - 1
            _states[sourceId] = current.copy(
                remainingSeconds = next,
                isRunning = next > 0
            )
        }
    }

    /** Counts up instead of down: no ceiling, so it never stops itself. */
    fun tickUp(sourceId: String) {
        val current = _states[sourceId] ?: return
        if (current.isRunning) {
            _states[sourceId] = current.copy(remainingSeconds = current.remainingSeconds + 1)
        }
    }

    fun reset(sourceId: String, totalSeconds: Int) {
        stop(sourceId)
        _states[sourceId] = TimerState(totalSeconds, false)
    }

    /** Call when duration settings change to reset the timer to the new total. */
    fun onDurationChanged(sourceId: String, totalSeconds: Int) {
        stop(sourceId)
        _states[sourceId] = TimerState(totalSeconds, false)
    }

    private fun stop(sourceId: String) {
        tickers.remove(sourceId)?.cancel()
    }

    /**
     * Cancels every ticker and forgets every timer.
     *
     * Nothing in the app needs this — a scene's timers are meant to outlive the panel that started
     * them, which is the whole point of holding them here. Tests do: [setRunning] launches a real
     * one-second coroutine on a process-wide scope, and nothing else ever stops it, so a test that
     * starts a countdown leaves it decrementing [_states] for the rest of the JVM. That map is
     * Compose-observable, so those stray ticks recompose whatever panel the *next* test is driving,
     * in the middle of a click or an assertion. Calling this between tests is what keeps a timer
     * inside the test that started it.
     */
    internal fun clear() {
        tickers.values.forEach { it.cancel() }
        tickers.clear()
        _states.clear()
    }
}

/** One second, and the only rate any timer runs at. */
private const val TICK_INTERVAL_MS = 1000L

/** The loop behind one running timer: wait a tick, advance it, for as long as it is still running. */
internal class TimerTicker(
    private val intervalMs: Long = TICK_INTERVAL_MS,
) {
    suspend fun run(sourceId: String, countUp: Boolean) {
        while (TimerStateManager.isTicking(sourceId)) {
            delay(intervalMs)
            TimerStateManager.advance(sourceId, countUp)
        }
    }
}
