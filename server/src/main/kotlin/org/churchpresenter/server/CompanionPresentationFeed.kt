package org.churchpresenter.server

import java.io.File
import org.churchpresenter.settings.PresentationRemoteSettings
import org.churchpresenter.settings.utils.Constants

/*
 * The presentation remote's view of the desktop: its settings, and every slide, freeze, loop and
 * live change pushed to the phones following it.
 *
 * Functions of [CompanionServer], kept beside it rather than in it so no one file holds all of
 * its behaviour; they read and write the server's own state.
 */

fun CompanionServer.updatePresentationRemoteSettings(settings: PresentationRemoteSettings, apiKey: String) {
    val wasEnabled = presentationRemoteEnabled
    presentationRemoteEnabled = settings.remoteControlEnabled
    presentationRemotePassword = apiKey
    if (wasEnabled && !presentationRemoteEnabled) clearPresentationState()
    InstanceLinkLogger.log(
        InstanceLinkLogSide.PRIMARY, "state_updated",
        mapOf("type" to "presentation_remote_settings", "remoteControlEnabled" to settings.remoteControlEnabled)
    )
}

fun CompanionServer.updateAutoScrollInterval(secs: Int) {
    if (_autoScrollInterval == secs) return
    _autoScrollInterval = secs
    broadcast(WebSocketMessage(
        type = Constants.WS_EVENT_PRESENTATION_AUTO_SCROLL_CHANGED,
        payload = """{"autoScrollInterval":$secs}"""
    ))
}

fun CompanionServer.updateLoopingState(looping: Boolean) {
    if (_presentationIsLooping == looping) return
    _presentationIsLooping = looping
    broadcast(WebSocketMessage(
        type = Constants.WS_EVENT_PRESENTATION_LOOP_CHANGED,
        payload = """{"looping":$looping}"""
    ))
}

fun CompanionServer.clearPresentationState() {
    _currentPresentationId = ""
    _currentSlideIndex = 0
    _currentSlideTotalCount = 0
    _presentationIsPlaying = false
    _presentationIsLive = false
    broadcast(WebSocketMessage(
        type = Constants.WS_EVENT_PRESENTATION_SLIDE_CHANGED,
        payload = """{"id":"","index":0,"total":0,"isPlaying":false,"isLive":false}"""
    ))
}

fun CompanionServer.updatePresentationLiveStatus(isLive: Boolean) {
    if (_presentationIsLive == isLive) return
    _presentationIsLive = isLive
    broadcast(WebSocketMessage(
        type = Constants.WS_EVENT_PRESENTATION_LIVE_CHANGED,
        payload = """{"isLive":$isLive}"""
    ))
}

fun CompanionServer.broadcastSlideChange(id: String, index: Int, total: Int, isPlaying: Boolean) {
    _currentPresentationId = id
    _currentSlideIndex = index
    _currentSlideTotalCount = total
    _presentationIsPlaying = isPlaying
    val note = presentations._presentationNotes[id]?.getOrNull(index) ?: ""
    broadcast(WebSocketMessage(
        type = Constants.WS_EVENT_PRESENTATION_SLIDE_CHANGED,
        payload = """{"id":"$id","index":$index,"total":$total,"isPlaying":$isPlaying,"isLive":""" +
            """$_presentationIsLive,"notes":"${jsonEscape(note)}"}"""
    ))
}

fun CompanionServer.broadcastFreezeChange(frozen: Boolean) {
    _presentationFrozen = frozen
    broadcast(WebSocketMessage(
        type = Constants.WS_EVENT_PRESENTATION_FREEZE_CHANGED,
        payload = """{"frozen":$frozen}"""
    ))
}

/**
 * Publishes a presentation and its slides to connected companions.
 * The work is [PresentationStore]'s; this is the API main.kt calls.
 */
fun CompanionServer.updatePresentation(
    id: String,
    filePath: String,
    fileName: String,
    fileType: String,
    slideFiles: List<File>,
    slideNotes: List<String> = emptyList()
) = presentations.updatePresentation(id, filePath, fileName, fileType, slideFiles, slideNotes)
