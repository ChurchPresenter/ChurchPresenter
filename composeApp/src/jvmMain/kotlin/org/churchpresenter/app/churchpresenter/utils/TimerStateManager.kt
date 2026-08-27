package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.runtime.mutableStateMapOf

/**
 * Singleton that holds countdown timer runtime state (remainingSeconds, isRunning)
 * keyed by ClockSource ID, so both the canvas renderer and the properties panel
 * share the same live state.
 *
 * A count-up source is held here too, and stores its elapsed seconds in the same
 * [TimerState.remainingSeconds] field — it is seeded at zero and driven by [tickUp].
 */
object TimerStateManager {

    data class TimerState(
        val remainingSeconds: Int,
        val isRunning: Boolean
    )

    // Compose-observable map: sourceId -> TimerState
    private val _states = mutableStateMapOf<String, TimerState>()

    fun getState(sourceId: String, totalSeconds: Int): TimerState {
        return _states.getOrPut(sourceId) { TimerState(totalSeconds, false) }
    }

    fun setRunning(sourceId: String, totalSeconds: Int, running: Boolean) {
        val current = _states[sourceId] ?: TimerState(totalSeconds, false)
        _states[sourceId] = current.copy(isRunning = running)
    }

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
        _states[sourceId] = TimerState(totalSeconds, false)
    }

    /** Call when duration settings change to reset the timer to the new total. */
    fun onDurationChanged(sourceId: String, totalSeconds: Int) {
        _states[sourceId] = TimerState(totalSeconds, false)
    }
}

