package org.churchpresenter.app.churchpresenter

import org.churchpresenter.bibletab.BibleTab
import org.churchpresenter.bibletab.BibleVerseStatistics
import org.churchpresenter.app.churchpresenter.tabs.recordBibleWentLive
import org.churchpresenter.app.churchpresenter.data.sharedCrossReferences
import org.churchpresenter.songs.SongsTab
import org.churchpresenter.app.churchpresenter.tabs.recordSongWentLive
import org.churchpresenter.statistics.asDurationRow
import org.churchpresenter.presenter.titleSlideSection
import org.churchpresenter.app.churchpresenter.tabs.AppSongEditor
import org.churchpresenter.songs.SongPlayCounts
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.sharedui.models.Presenting

/*
 * The Bible and Songs tabs as the main screen composes them — the two whose panes carry the most of
 * its wiring: the statistics, the went-live hooks and Instance Link.
 */

@Composable
internal fun MainDesktopScope.BibleTabPane() {
    BibleTab(
        modifier = Modifier.fillMaxSize(),
        hostWindow = hostWindow,
        viewModel = bibleViewModel,
        appSettings = appSettings,
        onSettingsChange = onSettingsChange,
        onAddToSchedule = { bookName, chapter, verseNumber, verseText, verseRange, bookId ->
            currentScheduleActions.addBibleVerse(bookName, chapter, verseNumber, verseText, verseRange, bookId)
        },
        selectedVerseItem = state.selectedBibleVerseItem,
        selectedVerseItemVersion = state.selectedBibleVerseItemVersion,
        selectedVerseItemGoLive = state.selectedBibleVerseItemGoLive,
        onVerseSelected = live.onVerseSelected,
        onInstanceLinkSendVerse = link.sendVerse,
        onInstanceLinkSendBibleHold = link.sendBibleHold,
        onPresenting = live.presenting,
        isPresenting = slideContent == Presenting.BIBLE,
        bibleOutput = presenterManager,
        // :statistics knows nothing of :bible-tab, so the tab's seam is adapted here.
        verseStatistics = remember(statisticsManager) {
            statisticsManager?.let { BibleVerseStatistics(it::recordVerseDisplay) }
        },
        onVerseWentLive = { presenterManager.previewBus.onAir(Presenting.BIBLE) { recordBibleWentLive(appSettings) } },
        verseSequenceLog = verseSequenceLog,
        crossReferences = sharedCrossReferences,
        dialogDismissSignal = dialogDismissSignal,
        sttManager = sttManager,
        engineStatus = bibleEngineClient
    )
}

@Composable
internal fun MainDesktopScope.SongsTabPane() {
    SongsTab(
        modifier = Modifier.fillMaxSize(),
        hostWindow = hostWindow,
        viewModel = songsViewModel,
        appSettings = appSettings,
        typicalSongSeconds = service.typicalSongSeconds,
        // Counted when the song reaches the air: on Take, while preview mode cues it.
        onSongWentLive = { song ->
            presenterManager.previewBus.onAir(Presenting.LYRICS) {
                recordSongWentLive(song, appSettings, statisticsManager)
                live.onRowWentLive(song.asDurationRow())
            }
        },
        titleSlideFor = ::titleSlideSection,
        songEditor = { request -> AppSongEditor(request, theme, appSettings) },
        onSettingsChange = onSettingsChange,
        onAddToSchedule = { songNumber, title, songbook, songId ->
            currentScheduleActions.addSong(songNumber, title, songbook, songId)
        },
        onInstanceLinkSendProject = link.sendProject,
        onInstanceLinkSendSongSection = link.sendSongSection,
        selectedSongItem = state.selectedSongItem,
        selectedSongItemVersion = state.selectedSongItemVersion,
        selectedSongItemAction = state.selectedSongItemAction,
        selectedSongItemSource = state.selectedSongItemSource,
        onSongItemSelected = live.onSongItemSelected,
        onAllSectionsChanged = live.onAllSectionsChanged,
        onSectionIndexChanged = live.onSectionIndexChanged,
        onLineIndexChanged = live.onLineIndexChanged,
        onPresenting = live.presenting,
        isPresenting = slideContent == Presenting.LYRICS,
        // Likewise :songs' seam.
        playCounts = remember(statisticsManager) { statisticsManager?.let { SongPlayCounts(it::getSongPlayCount) } },
        dialogDismissSignal = dialogDismissSignal
    )
}
