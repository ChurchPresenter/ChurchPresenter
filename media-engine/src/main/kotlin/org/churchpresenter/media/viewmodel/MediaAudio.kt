package org.churchpresenter.media.viewmodel

import androidx.compose.runtime.mutableStateOf

/** The clip's volume and mute, apart from everything else the player is doing. */
class MediaAudio {
    private val _volume = mutableStateOf(1.0f)
    val volume: Float get() = _volume.value

    private val _isMuted = mutableStateOf(false)
    val isMuted: Boolean get() = _isMuted.value

    /** Effective volume sent to the player (0 when muted). */
    val effectiveVolume: Float get() = if (_isMuted.value) 0f else _volume.value

    fun setVolume(v: Float) {
        _volume.value = v.coerceIn(0f, 1f)
        if (_isMuted.value && v > 0f) _isMuted.value = false
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }
}
