package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.runtime.Composable
import org.churchpresenter.helper.ui.GuideSpotlightHost
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import org.churchpresenter.statistics.StatisticsManager
import org.churchpresenter.app.churchpresenter.dialogs.songEditorBackgroundButton
import org.churchpresenter.songs.EditSongDialog
import org.churchpresenter.liveoutput.lottieBandPath
import org.churchpresenter.profiles.stageMonitorScreenIndices
import org.churchpresenter.app.churchpresenter.utils.isChordChartPresentation
import org.churchpresenter.app.churchpresenter.utils.isLiveOutput
import org.churchpresenter.app.churchpresenter.utils.isSplitScreenSong
import org.churchpresenter.app.churchpresenter.utils.songLanguageEvent
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.profileFor
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.songs.SongEditorRequest
import org.churchpresenter.theme.ThemeMode

/**
 * The `:songs` tab's editor: [EditSongDialog] for [request]. The metronome tempo is only ever read
 * by the stage monitor, so the field that sets it is offered only when one is configured.
 */
@Composable
fun AppSongEditor(request: SongEditorRequest, theme: ThemeMode, appSettings: AppSettings) {
    EditSongDialog(
        backgroundButton = songEditorBackgroundButton,
        isVisible = request.isVisible,
        song = request.song,
        songbooks = request.songbooks,
        existingSongs = request.existingSongs,
        isNewSong = request.isNewSong,
        theme = theme,
        tuning = request.tuning,
        showTuningFields = stageMonitorScreenIndices(appSettings.projectionSettings).isNotEmpty(),
        chordsVisible = request.chordsVisible,
        typicalSeconds = request.typicalSeconds,
        onChordsVisibleChange = request.onChordsVisibleChange,
        onApplyBackgroundToSongbook = request.onApplyBackgroundToSongbook,
        languageNames = request.languageNames,
        onLanguageNamesChange = request.onLanguageNamesChange,
        onDismiss = request.onDismiss,
        onSave = request.onSave,
        // The editor's own spotlight: Wick's song translation and chords tours ring its controls.
        windowContent = { content -> GuideSpotlightHost(Modifier.fillMaxSize()) { content() } },
    )
}

/**
 * A different song has just gone live from the Songs tab: it is counted for the usage report, and
 * the bilingual, split-screen, lottie-band and chord-chart presentations it went out in are
 * recorded — bilingual worship actually happening, rather than merely being configured.
 */
fun recordSongWentLive(song: SongItem, appSettings: AppSettings, statisticsManager: StatisticsManager?) {
    statisticsManager?.recordSongDisplay(
        songId = song.songId,
        songNumber = song.number.toIntOrNull() ?: 0,
        title = song.title,
        songbook = song.songbook,
        author = song.author
    )
    val proj = appSettings.projectionSettings
    val outputs = proj.screenAssignments
        .filter { it.isLiveOutput(proj.unusedScreens) }
        .mapNotNull { proj.profileFor(it) }
    songLanguageEvent(song, outputs)?.let { UsageEvents.record(it) }
    if (isSplitScreenSong(outputs)) UsageEvents.record(UsageEvent.SONG_SPLIT_SCREEN)
    if (lottieBandPath(appSettings, Presenting.LYRICS) != null) {
        UsageEvents.record(UsageEvent.SONG_LOTTIE_BAND)
    }
    if (isChordChartPresentation(song, outputs)) {
        UsageEvents.record(UsageEvent.SONG_CHORD_CHART)
    }
}
