package org.churchpresenter.media.viewmodel

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * Where playback is and how long the clip runs, in milliseconds.
 *
 * [onSeek] is the view model's: an explicit seek starts a new play as far as end-of-file is
 * concerned, so a late end event for the old position is not taken for the end of this one.
 */
class MediaPosition(private val onSeek: () -> Unit) {
    private val _currentPosition = mutableStateOf(0L)
    val currentPosition: Long get() = _currentPosition.value

    private val _duration = mutableStateOf(0L)
    val duration: Long get() = _duration.value

    /**
     * Incremented every time the user explicitly seeks (seekTo/seekForward/seekBackward).
     * VideoPlayer observes this to avoid a feedback loop with setCurrentPosition().
     */
    private val _seekVersion = mutableIntStateOf(0)
    val seekVersion: Int get() = _seekVersion.intValue

    fun seekForward(ms: Long = 10_000L) {
        if (_duration.value > 0) {
            _currentPosition.value = (_currentPosition.value + ms).coerceAtMost(_duration.value)
            seeked()
        }
    }

    fun seekBackward(ms: Long = 10_000L) {
        _currentPosition.value = (_currentPosition.value - ms).coerceAtLeast(0L)
        seeked()
    }

    fun seekTo(ms: Long) {
        _currentPosition.value = ms.coerceIn(0L, _duration.value.takeIf { it > 0 } ?: Long.MAX_VALUE)
        seeked()
    }

    /** Called by VideoPlayer once the media is ready. */
    fun setDuration(ms: Long) {
        _duration.value = ms
    }

    /** Called by VideoPlayer to keep the progress in sync (does NOT bump seekVersion). */
    fun setCurrentPosition(ms: Long) {
        _currentPosition.value = ms
    }

    /** Back to the start, and the player told so -- a stop, an unload, the end of the last play. */
    internal fun rewind() {
        _currentPosition.value = 0L
        _seekVersion.intValue++
    }

    /** A new clip: no position and no length until the player reports one. */
    internal fun clear() {
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    private fun seeked() {
        onSeek()
        _seekVersion.intValue++
    }
}
