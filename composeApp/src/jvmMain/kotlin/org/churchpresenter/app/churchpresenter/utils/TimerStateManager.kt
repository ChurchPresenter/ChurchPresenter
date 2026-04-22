package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.runtime.mutableStateMapOf

/**
 * Singleton that holds countdown timer runtime state (remainingSeconds, isRunning)
 * keyed by ClockSource ID, so both the canvas renderer and the properties panel
 * share the same live state.
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

    fun tick(sourceId: String, totalSeconds: Int) {
        val current = _states[sourceId] ?: return
        if (current.isRunning && current.remainingSeconds > 0) {
            val next = current.remainingSeconds - 1
            _states[sourceId] = current.copy(
                remainingSeconds = next,
                isRunning = next > 0
            )
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

