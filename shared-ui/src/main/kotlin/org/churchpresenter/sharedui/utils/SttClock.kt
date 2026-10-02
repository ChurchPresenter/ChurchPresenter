package org.churchpresenter.sharedui.utils

import kotlin.math.roundToLong

/**
 * The STT recording's own clock, as seen from this machine: where in the session's audio -- in the
 * seconds STT's segments are timed in -- any moment on the local clock falls. [LiveHistoryLogger]
 * stamps each on-screen line with it, so SongListener can cut a song out of the recording by the
 * recording's time rather than by matching two machines' wall clocks.
 *
 * Learned from the transcription: every update says how far into the audio STT has transcribed, at
 * a moment this machine knows. Transcription runs a little behind the audio, so each observation
 * makes the recording look later than it is by that lag; the one with the least lag -- the smallest
 * difference between the local time and the audio time -- is kept, and the clock is never further
 * off than the quickest update that has arrived.
 *
 * Unknown until STT has transcribed something, and forgotten when the session changes -- a new
 * session's recording starts again at 0 -- or STT is disconnected on purpose. A connection that
 * drops and comes back keeps it: the recording has carried on meanwhile.
 */
object SttClock {

    private val lock = Any()

    // Local milliseconds minus the recording's milliseconds, at the least-lagged observation
    private var offsetMs: Long? = null

    /** STT has transcribed the audio up to [sttSeconds] as of [atMs] on the local clock. */
    fun observe(sttSeconds: Double, atMs: Long = System.currentTimeMillis()) {
        if (sttSeconds <= 0.0) return
        val offset = atMs - (sttSeconds * MS_PER_SECOND).roundToLong()
        synchronized(lock) { offsetMs = offsetMs?.let { minOf(it, offset) } ?: offset }
    }

    /** Where in the recording [atMs] on the local clock falls, in seconds, or null while unknown. */
    fun secondsAt(atMs: Long): Double? = synchronized(lock) { offsetMs }?.let { (atMs - it) / MS_PER_SECOND }

    /** Forgets the clock: a new session's recording, or none. */
    fun reset() {
        synchronized(lock) { offsetMs = null }
    }

    private const val MS_PER_SECOND = 1000.0
}
