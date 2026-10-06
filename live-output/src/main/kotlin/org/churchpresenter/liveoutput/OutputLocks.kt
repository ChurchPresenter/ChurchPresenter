package org.churchpresenter.liveoutput

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.churchpresenter.sharedui.models.Presenting

/** How long an output shows its "Identify" number. */
private const val IDENTIFY_MS = 5_000L

/** One octave either way: past that a transpose is the same key again. */
private const val MAX_TRANSPOSE_STEPS = 11

/**
 * Each output's own hold on what it shows, apart from the live mode: the per-output locks, the
 * Browser Source chord transpose, and the brief "Identify" number. Part of [PresenterManager].
 */
interface OutputLocks {
    /** Per-screen lock: screen slot index -> locked mode. A missing entry follows the live mode. */
    val screenLocks: State<Map<Int, Presenting>>

    /** Per-Browser-Source lock, in that list's own 0-based index space. */
    val browserSourceLocks: State<Map<Int, Presenting>>

    /**
     * How many semitones each Browser Source output moves the chords it draws, for musicians reading
     * a Stage Monitor off a tablet. Runtime only: it survives a change of song -- a capo does not come
     * off between songs -- but not a restart, and it never touches the song or any other output. A
     * flow rather than Compose state because the server pushes it to the musician pages as well.
     */
    val browserSourceTranspose: StateFlow<Map<Int, Int>>

    /** Per-NDI-output lock: NDI output 0 and Browser Source output 0 are different outputs. */
    val ndiLocks: State<Map<Int, Presenting>>

    /** Per-OMT-output lock, a fourth independent index space. */
    val omtLocks: State<Map<Int, Presenting>>

    /** Browser Source outputs showing their "Identify" number -- there is no window to flash. */
    val browserSourceIdentifying: State<Set<Int>>

    /** The same for NDI outputs; flashing one must not flash the Browser Source of that index. */
    val ndiIdentifying: State<Set<Int>>

    /** The same for OMT outputs. */
    val omtIdentifying: State<Set<Int>>

    fun setScreenLock(screenIndex: Int, mode: Presenting?)
    fun setBrowserSourceLock(index: Int, mode: Presenting?)
    fun setBrowserSourceTranspose(index: Int, steps: Int)
    fun stepBrowserSourceTranspose(index: Int, delta: Int)
    fun setNdiLock(index: Int, mode: Presenting?)
    fun setOmtLock(index: Int, mode: Presenting?)
    fun identifyBrowserSourceOutput(index: Int)
    fun identifyNdiOutput(index: Int)
    fun identifyOmtOutput(index: Int)
}

internal class OutputLocksState(private val context: PresenterContext) : OutputLocks {

    override val screenLocks: State<Map<Int, Presenting>> = context.screenLocks

    private val _browserSourceLocks = mutableStateOf<Map<Int, Presenting>>(emptyMap())
    override val browserSourceLocks: State<Map<Int, Presenting>> = _browserSourceLocks

    private val _browserSourceTranspose = MutableStateFlow<Map<Int, Int>>(emptyMap())
    override val browserSourceTranspose: StateFlow<Map<Int, Int>> = _browserSourceTranspose.asStateFlow()

    private val _ndiLocks = mutableStateOf<Map<Int, Presenting>>(emptyMap())
    override val ndiLocks: State<Map<Int, Presenting>> = _ndiLocks

    private val _omtLocks = mutableStateOf<Map<Int, Presenting>>(emptyMap())
    override val omtLocks: State<Map<Int, Presenting>> = _omtLocks

    private val _browserSourceIdentifying = mutableStateOf<Set<Int>>(emptySet())
    override val browserSourceIdentifying: State<Set<Int>> = _browserSourceIdentifying

    private val _ndiIdentifying = mutableStateOf<Set<Int>>(emptySet())
    override val ndiIdentifying: State<Set<Int>> = _ndiIdentifying

    private val _omtIdentifying = mutableStateOf<Set<Int>>(emptySet())
    override val omtIdentifying: State<Set<Int>> = _omtIdentifying

    override fun setScreenLock(screenIndex: Int, mode: Presenting?) {
        context.screenLocks.value = context.screenLocks.value.locking(screenIndex, mode)
    }

    override fun setBrowserSourceLock(index: Int, mode: Presenting?) {
        _browserSourceLocks.value = _browserSourceLocks.value.locking(index, mode)
    }

    override fun setBrowserSourceTranspose(index: Int, steps: Int) {
        val clamped = steps.coerceIn(-MAX_TRANSPOSE_STEPS, MAX_TRANSPOSE_STEPS)
        _browserSourceTranspose.update { if (clamped == 0) it - index else it + (index to clamped) }
    }

    override fun stepBrowserSourceTranspose(index: Int, delta: Int) =
        setBrowserSourceTranspose(index, (_browserSourceTranspose.value[index] ?: 0) + delta)

    override fun setNdiLock(index: Int, mode: Presenting?) {
        _ndiLocks.value = _ndiLocks.value.locking(index, mode)
    }

    override fun setOmtLock(index: Int, mode: Presenting?) {
        _omtLocks.value = _omtLocks.value.locking(index, mode)
    }

    override fun identifyBrowserSourceOutput(index: Int) = flash(_browserSourceIdentifying, index)

    override fun identifyNdiOutput(index: Int) = flash(_ndiIdentifying, index)

    override fun identifyOmtOutput(index: Int) = flash(_omtIdentifying, index)

    /** Adds [index] to [identifying] for [IDENTIFY_MS], then takes it out again. */
    private fun flash(identifying: MutableState<Set<Int>>, index: Int) {
        identifying.value = identifying.value + index
        context.scope.launch {
            delay(IDENTIFY_MS)
            identifying.value = identifying.value - index
        }
    }
}

/** [this] with [index] locked to [mode], or released when [mode] is null. */
private fun Map<Int, Presenting>.locking(index: Int, mode: Presenting?): Map<Int, Presenting> =
    if (mode == null) this - index else this + (index to mode)
