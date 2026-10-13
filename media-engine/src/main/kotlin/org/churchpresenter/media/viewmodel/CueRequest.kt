package org.churchpresenter.media.viewmodel

/**
 * A cue asking a clip to play, kept until the clip it names has finished loading.
 *
 * The load is what the Media tab does when it is handed a row, and it deliberately leaves the
 * clip paused -- an operator going live by hand presses play. Automation has nobody to press it,
 * so a row that fired on its own sat on a blank output. The url is part of the request so it
 * cannot start whatever clip happened to be loaded a moment earlier.
 *
 * [isReady] says whether that url is the one loaded; [start] plays it the requested number of
 * times.
 */
class CueRequest internal constructor(
    private val isReady: (url: String) -> Boolean,
    private val start: (plays: Int) -> Unit,
) {
    private var pendingPlayUrl: String? = null
    private var pendingPlays: Int = 1

    fun requestPlayback(plays: Int, url: String) {
        pendingPlayUrl = url
        pendingPlays = plays
        applyIfReady()
    }

    /** Starts the waiting request once its clip is the one loaded; otherwise keeps waiting. */
    internal fun applyIfReady() {
        val wanted = pendingPlayUrl ?: return
        if (!isReady(wanted)) return
        pendingPlayUrl = null
        start(pendingPlays)
    }
}
