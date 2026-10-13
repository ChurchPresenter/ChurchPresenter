package org.churchpresenter.media.viewmodel

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** Whether a clip repeats, how often, and how many repeats it has already played. */
class MediaLooping {
    private val _isLooping = mutableStateOf(false)
    val isLooping: Boolean get() = _isLooping.value

    /** How many times to repeat after the first play. 0 means repeat forever. */
    private val _loopCount = mutableIntStateOf(0)
    val loopCount: Int get() = _loopCount.intValue

    /** Repeats already played back for the current media. */
    private val _loopsPlayed = mutableIntStateOf(0)
    val loopsPlayed: Int get() = _loopsPlayed.intValue

    /**
     * Incremented every time a loop restarts the media. VideoPlayer observes this to re-issue
     * the play command: once VLC has reached the end, seeking alone will not start it again.
     */
    private val _loopRestartVersion = mutableIntStateOf(0)
    val loopRestartVersion: Int get() = _loopRestartVersion.intValue

    fun toggleLooping() {
        _isLooping.value = !_isLooping.value
        _loopsPlayed.intValue = 0
    }

    /** Sets looping outright — a calendar cue's Once / Loop / N times, rather than the tab's toggle. */
    fun setLooping(looping: Boolean) {
        _isLooping.value = looping
        _loopsPlayed.intValue = 0
    }

    fun setLoopCount(count: Int) {
        _loopCount.intValue = count.coerceAtLeast(0)
        _loopsPlayed.intValue = 0
    }

    /** 1 once, 0 for ever, N times -- the same counting a schedule row's `repeats` uses. */
    internal fun setPlays(plays: Int) {
        _isLooping.value = plays != 1
        _loopCount.intValue = if (plays == 0) 0 else plays - 1
        _loopsPlayed.intValue = 0
    }

    /** Spends one repeat if one is still owed, and says whether it did. */
    internal fun spendRepeat(): Boolean {
        if (!_isLooping.value || (_loopCount.intValue != 0 && _loopsPlayed.intValue >= _loopCount.intValue)) {
            return false
        }
        _loopsPlayed.intValue++
        _loopRestartVersion.intValue++
        return true
    }

    /** A new play from the top: no repeats spent yet. */
    internal fun restart() {
        _loopsPlayed.intValue = 0
    }
}
