package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.profileFor
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.settings.utils.Constants
import java.io.File

/*
 * The main screen's decisions about what runs off screen: the hidden media players, the stage monitor
 * lock, the remembered STT address and lower-third presets. Pure.
 */

internal fun sttUrlToPersist(settings: AppSettings, sttConnected: Boolean): String? {
    if (!sttConnected) return null
    val url = settings.sttSettings.serverUrl
    return if (settings.sttSettings.lastConnectedUrl != url) url else null
}

internal fun withSttLastConnectedUrl(settings: AppSettings, url: String): AppSettings =
    settings.copy(sttSettings = settings.sttSettings.copy(lastConnectedUrl = url))

internal fun stageMonitorScreenIndices(proj: ProjectionSettings): List<Int> =
    proj.screenAssignments.indices.filter {
        proj.profileFor(proj.screenAssignments[it])?.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR
    }

internal fun findLottiePresetFile(files: List<File>?, presetLabel: String, presetId: String): File? =
    files?.find { it.nameWithoutExtension == presetLabel || it.nameWithoutExtension == presetId }

/**
 * Whether the hidden audio player should be hosted here rather than by the Media tab.
 *
 * The two players are mutually exclusive — only one decoder may run at a time — so this and
 * [shouldHostBackgroundVideo] must never both be true. Both take the media state as nullable, so
 * "there is no media view model" is decided here rather than left as a branch at the call site.
 */
internal fun shouldHostBackgroundAudio(isAudioFile: Boolean?, isPlaying: Boolean?, currentTab: Tabs): Boolean =
    isAudioFile == true && isPlaying == true && currentTab != Tabs.MEDIA

/**
 * Whether the off-tab video decoder should be hosted here. Loaded rather than playing: it exists to
 * keep a frame on screen while the operator is elsewhere, which a paused video still needs.
 */
internal fun shouldHostBackgroundVideo(isAudioFile: Boolean?, isLoaded: Boolean?, currentTab: Tabs): Boolean =
    isAudioFile == false && isLoaded == true && currentTab != Tabs.MEDIA
