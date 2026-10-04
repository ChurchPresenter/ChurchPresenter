package org.churchpresenter.server

import kotlinx.coroutines.flow.SharedFlow
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants

/*
 * What the outputs and the media player tell the server: media state, the ATEM's settings, the
 * Browser Source outputs and their transposes, and the clear and section changes the phones mirror.
 *
 * Functions of [CompanionServer], kept beside it rather than in it so no one file holds all of
 * its behaviour; they read and write the server's own state.
 */

/**
 * Broadcasts the desktop media player's playback state to companions (mobile Media tab).
 * Position ticks continuously, so callers poll this on a fixed cadence.
 */
fun CompanionServer.broadcastMediaState(state: MediaPlaybackState) = with(state) {
    broadcast(WebSocketMessage(
        type = Constants.WS_EVENT_MEDIA_STATE_CHANGED,
        payload = """{"isLive":$isLive,"isLoaded":$isLoaded,"isPlaying":$isPlaying,""" +
            """"title":"${jsonEscape(title)}","positionMs":$positionMs,"durationMs":$durationMs,""" +
            """"volume":$volume,"muted":$muted,"mediaType":"${jsonEscape(mediaType)}",""" +
            """"source":"${jsonEscape(source)}"}"""
    ))
}

fun CompanionServer.updateAtemConfig(atem: AtemSettings, lowerThirdFolder: String) {
    this.atem.updateConfig(atem, lowerThirdFolder)
    InstanceLinkLogger.log(InstanceLinkLogSide.PRIMARY, "state_updated", mapOf("type" to "atem_config"))
}

// ── Browser Source outputs (OBS/vMix overlay) ─────────────────────────────
// Content is rendered off-screen in main.kt (BrowserSourceVideoRenderer, the same
// BiblePresenter/SongPresenter/etc composables used everywhere else) and streamed here over
// a WebSocket as binary-framed PNG deltas — this class only owns serving, never
// PresenterManager/content state. (Previously HTTP multipart/x-mixed-replace; switched to
// WebSocket because that legacy MIME type turned out to be unreliable in both directions —
// Chrome's <img> support for it is inconsistent, and Safari's fetch()/ReadableStream failed
// outright with "Load failed" for this exact indefinitely-long streaming response pattern,
// even on localhost. WebSocket is what the rest of this server already uses for real-time
// push, and has none of that legacy baggage.)

/** Publishes the configured Browser Source outputs. Called from main.kt. */
fun CompanionServer.updateBrowserSourceOutputs(outputs: List<ScreenAssignment>) {
    browserSource.updateBrowserSourceOutputs(outputs)
    InstanceLinkLogger.log(
        InstanceLinkLogSide.PRIMARY, "state_updated",
        mapOf("type" to "browser_source_outputs", "count" to outputs.size)
    )
}

/** The output configured at [index], or null. */
fun CompanionServer.browserSourceOutput(index: Int): ScreenAssignment? = browserSource.browserSourceOutput(index)

/** Registers the frame flow an output's renderer produces. Called from main.kt. */
fun CompanionServer.registerBrowserSourceFrames(index: Int, frames: SharedFlow<BrowserSourceFrame>) =
    browserSource.registerBrowserSourceFrames(index, frames)

/** Publishes each output's current transpose to the pages that show it. Called from main.kt. */
fun CompanionServer.updateBrowserSourceTranspose(transposes: Map<Int, Int>) {
    browserSource.transposes.value = transposes
}

/** Publishes which outputs' pages offer the transpose buttons at all. Called from main.kt. */
fun CompanionServer.updateTransposeControls(indices: Set<Int>) {
    browserSource.transposeControls.value = indices
}

// ── Public API ────────────────────────────────────────────────────────────

/** Broadcasts a display_cleared event to all connected mobile clients. */
fun CompanionServer.broadcastDisplayCleared() {
    broadcast(WebSocketMessage(type = Constants.WS_EVENT_DISPLAY_CLEARED, payload = ""))
}

/** Broadcasts the currently active song section index to all connected mobile clients. */
fun CompanionServer.broadcastSongSectionSelected(sectionIndex: Int) {
    broadcast(WebSocketMessage(type = Constants.WS_EVENT_SONG_SECTION_SELECTED, payload = sectionIndex.toString()))
}
