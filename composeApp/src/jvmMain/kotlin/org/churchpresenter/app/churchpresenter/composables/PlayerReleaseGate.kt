package org.churchpresenter.app.churchpresenter.composables

/**
 * Guards deferred native calls against a vlcj player that has already been released.
 *
 * A `MediaPlayer` handle is native memory owned by libvlc. Once `releasePlayer()` has run, calling
 * anything on it — `controls().pause()` is the one that reached production — dereferences freed
 * memory, and libvlc answers with `java.lang.Error: Invalid memory access`, which kills the app
 * rather than the playback. Anything deferred can land in that window: a `javax.swing.Timer` firing
 * 200 ms later, or a block queued with `SwingUtilities.invokeLater`.
 *
 * So every deferred native call goes through [ifLive]. [release] must be called **before**
 * `releasePlayer()`, so the flag is already set for anything the release itself races.
 *
 * The catch is `Throwable`, not `Exception`: the failure this exists for is an `Error`, and a
 * `catch (_: Exception)` would let it straight through.
 */
internal class PlayerReleaseGate {

    @Volatile
    private var released = false

    /** Whether [release] has been called. */
    val isReleased: Boolean get() = released

    /** Marks the player released. Call before `releasePlayer()`; safe to call more than once. */
    fun release() {
        released = true
    }

    /**
     * Runs [block] only while the player is still alive, swallowing anything it throws.
     *
     * Returns whether it ran — for tests and for callers that want to know they were too late; the
     * production call sites ignore it.
     */
    fun ifLive(block: () -> Unit): Boolean {
        if (released) return false
        return try {
            block()
            true
        } catch (_: Throwable) {
            false
        }
    }
}
