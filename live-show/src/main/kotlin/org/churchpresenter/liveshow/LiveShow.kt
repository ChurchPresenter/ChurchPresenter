package org.churchpresenter.liveshow

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/**
 * What is on air and what is cued, one cue per layer.
 *
 * [program] is what every output draws; [preview] is what is cued and not yet on air. Direct Go
 * Live is [set]; with preview mode on, Go Live is [cue] and [take] puts it on air.
 */
class LiveShow {
    private val _program = mutableStateOf<Map<Layer, Cue>>(emptyMap())
    val program: State<Map<Layer, Cue>> = _program

    private val _preview = mutableStateOf<Map<Layer, Cue>>(emptyMap())
    val preview: State<Map<Layer, Cue>> = _preview

    /** Puts [cue] straight on air on its layer, replacing what was there -- and, for a message, everything else. */
    fun set(cue: Cue) {
        if (_program.value[cue.layer] != cue) _program.value = _program.value.goingLive(mapOf(cue.layer to cue))
    }

    /** Cues [cue] on its layer, without touching what is on air. */
    fun cue(cue: Cue) {
        if (_preview.value[cue.layer] != cue) _preview.value = _preview.value + (cue.layer to cue)
    }

    /**
     * Puts what is cued on air: on [layer] only, or on every cued layer when it is null. Each layer
     * taken leaves preview.
     */
    fun take(layer: Layer? = null) {
        val taken = if (layer == null) _preview.value else _preview.value.filterKeys { it == layer }
        if (taken.isEmpty()) return
        _program.value = _program.value.goingLive(taken)
        _preview.value = _preview.value - taken.keys
    }

    /** Takes [layer] off air. */
    fun clear(layer: Layer) {
        if (layer in _program.value) _program.value = _program.value - layer
    }

    /** Takes everything off air, except the background when [keepBackground]. */
    fun clearAll(keepBackground: Boolean = true) {
        val kept = if (keepBackground) _program.value.filterKeys { it == Layer.BACKGROUND } else emptyMap()
        if (kept != _program.value) _program.value = kept
    }
}

/** What is on air once [live] goes up over [this]: beside it, unless a message clears the rest first. */
private fun Map<Layer, Cue>.goingLive(live: Map<Layer, Cue>): Map<Layer, Cue> =
    live[Layer.MESSAGES]?.let { mapOf(Layer.MESSAGES to it) } ?: (this + live)
