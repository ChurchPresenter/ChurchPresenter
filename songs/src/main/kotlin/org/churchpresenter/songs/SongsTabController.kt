package org.churchpresenter.songs

import org.churchpresenter.settings.SongColumnId
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.utils.LiveHistoryLogger
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.operatorSongSettings
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Density
import org.churchpresenter.sharedui.composables.FocusLostRescueState

/**
 * The Songs tab's state and behaviour between compositions: what is live, which dialog is open,
 * the panel sizes, and every push to the output. The tab composes it, assigns the settings and
 * callbacks it was given on each composition, and draws its pieces as extensions of it.
 */
/** Adds a song to the schedule: its number, title, songbook and id. */
internal typealias AddSongToSchedule = (songNumber: Int, title: String, songbook: String, songId: String) -> Unit

@Stable
internal class SongsTabController(
    val viewModel: SongsViewModel,
    val dialogs: SongDialogRequests,
    val live: SongLiveState,
) {
    // Read while composing, so held as state: the pieces are extensions of this one stable object and
    // are skipped unless something they read has changed.
    var appSettings by mutableStateOf(AppSettings())
    var isPresenting by mutableStateOf(false)
    var onAddToSchedule by mutableStateOf<AddSongToSchedule?>(null)
    var onPresenting by mutableStateOf<(Presenting) -> Unit>({})
    var typicalSongSeconds by mutableStateOf<(SongItem) -> Int?>({ null })
    var playCounts by mutableStateOf<SongPlayCounts?>(null)
    var songEditor by mutableStateOf<@Composable (SongEditorRequest) -> Unit>({})
    var density by mutableStateOf(Density(1f))
    var isMaximized by mutableStateOf(false)
    private val columnsState = mutableStateOf<SongTableColumns?>(null)
    var columns: SongTableColumns
        get() = checkNotNull(columnsState.value)
        set(value) { columnsState.value = value }
    private val focusRescueState = mutableStateOf<FocusLostRescueState?>(null)
    var focusRescue: FocusLostRescueState
        get() = checkNotNull(focusRescueState.value)
        set(value) { focusRescueState.value = value }
    var favPanelHeight by mutableStateOf(mutableStateOf(0f))
    var lyricsPanel by mutableStateOf(mutableStateOf(0f))

    // Only called, never drawn from, so plain fields: a handler reads the latest when it runs.
    lateinit var titleSlideFor: (SongItem, SongTuning, SongSettings) -> LyricSection
    var onSongWentLive: (SongItem) -> Unit = {}
    var onInstanceLinkSendProject: ((ScheduleItem) -> Unit)? = null
    var onInstanceLinkSendSongSection: ((number: String, section: Int, lineIndex: Int) -> Unit)? = null
    var onSongItemSelected: (LyricSection) -> Unit = {}
    var onAllSectionsChanged: (List<LyricSection>) -> Unit = {}
    var onSectionIndexChanged: (Int) -> Unit = {}
    var onLineIndexChanged: (Int) -> Unit = {}
    var onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {}

    val tabFocusRequester = FocusRequester()
    // True while the caret is in the song search field — the tab's key handler stands down for it.
    var searchFieldFocused by mutableStateOf(false)
    var favoritesExpanded by mutableStateOf(true)
    var rowTotalWidth by mutableStateOf(0f)

    var favPanelHeightPx: Float
        get() = favPanelHeight.value
        set(value) { favPanelHeight.value = value }

    // Panel split — lyrics panel width in px; 0 means "not yet set, use half of row"
    var lyricsPanelPx: Float
        get() = lyricsPanel.value
        set(value) { lyricsPanel.value = value }

    // Helper: push current viewModel selection to presenter and track as live.
    // goLive=true marks this call as an explicit "go live" action so statistics are
    // recorded even though the isPresenting flag has not yet propagated.
    //
    // The title slide is not one of the view model's sections -- the panel puts it in front of
    // them -- so while it is the selection, it is what goes out, ahead of the song's own sections.
    // Every push comes through here, Go Live and the arrow keys included; a path that read the
    // view model's selection directly sent verse 1 out from under a staged title slide.
    fun sendToPresenter(goLive: Boolean = false) {
        val idx = viewModel.selectedSongIndex.value
        val items = viewModel.filteredSongItems.value
        val song = items.getOrNull(idx)
        val tuning = song?.let { appSettings.tuningFor(it.songId) } ?: SongTuning()
        val songs = appSettings.operatorSongSettings()
        val titleSlide = song?.takeIf { live.titleSlideSelected && songs.titleSlideEnabled }
            ?.let { titleSlideFor(it, tuning, songs) }
        // Before the push, so the section's history line already carries the row it came from.
        if ((goLive || isPresenting) && song != null) {
            LiveHistoryLogger.noteLiveSong(
                song.songId, song.songbook, song.number.toIntOrNull() ?: 0, song.title, "manual",
            )
        }
        pushSections(titleSlide, tuning)
        // Record song display for statistics — only when the song is actually live
        // (or being sent live), and only when a different song is presented.
        // Against the last song that went live, not live.songId: a schedule row's preview push sets
        // that, and the Go Live after it would otherwise look like the same song and go uncounted.
        val isDifferentSong = items.getOrNull(idx)?.songId?.let { it != live.wentLiveSongId } ?: false
        val wentLive = (goLive || isPresenting) && isDifferentSong
        if (wentLive && idx in items.indices) {
            live.wentLiveSongId = items[idx].songId
            // The start of the song's measurement, its statistics and its telemetry, under one
            // guard: every way of going live from this tab ends up here, and a section change on
            // a song already up is not a new song.
            onSongWentLive(items[idx])
        }
        // Instance Link Controller mode: a genuine go-live with a *different* song needs the primary
        // to actually load it first (sendProject, approval-gated the first time); a go-live that's
        // just a section/line change on the *same* already-live song can use the lighter, always-
        // instant sendSongSection instead — reusing the isDifferentSong condition already computed above.
        if (goLive && idx in items.indices) sendToInstanceLink(items[idx], isDifferentSong)
        live.songId = song?.songId
        live.sectionIndex = if (titleSlide != null) -1 else viewModel.selectedSectionIndex.value
        live.lineIndex = if (titleSlide != null) 0 else viewModel.selectedLineIndex.value
    }

    /** The sections, the selection and the section itself, led by [titleSlide] while it is the selection. */
    private fun pushSections(titleSlide: LyricSection?, tuning: SongTuning) {
        if (titleSlide != null) {
            onAllSectionsChanged(listOf(titleSlide) + viewModel.getLyricSections())
            onSectionIndexChanged(0)
            onLineIndexChanged(0)
            onSongItemSelected(titleSlide)
        } else {
            onAllSectionsChanged(viewModel.getLyricSections())
            onSectionIndexChanged(viewModel.selectedSectionIndex.value)
            onLineIndexChanged(viewModel.selectedLineIndex.value)
            viewModel.getSelectedLyricSection()?.let {
                onSongItemSelected(it.copy(bpm = tuning.bpm, capo = tuning.capo))
            }
        }
    }

    private fun sendToInstanceLink(song: SongItem, isDifferentSong: Boolean) {
        if (isDifferentSong) {
            onInstanceLinkSendProject?.invoke(
                ScheduleItem.SongItem(
                    id = java.util.UUID.randomUUID().toString(),
                    songNumber = song.number.toIntOrNull() ?: 0,
                    title = song.title,
                    songbook = song.songbook,
                    songId = song.songId
                )
            )
        } else {
            onInstanceLinkSendSongSection?.invoke(
                song.number,
                viewModel.selectedSectionIndex.value,
                viewModel.selectedLineIndex.value
            )
        }
    }

    /**
     * Steps off the title slide onto the song's first section. False when it was not selected, so
     * the caller moves the view model instead.
     */
    fun leaveTitleSlide(): Boolean {
        if (!live.titleSlideSelected) return false
        live.titleSlideSelected = false
        viewModel.selectSection(-1)
        viewModel.navigateNextSection()
        return true
    }

    /** Steps back onto the title slide from the song's first section, when there is one to step onto. */
    fun backToTitleSlide(): Boolean {
        val offered = appSettings.operatorSongSettings().titleSlideEnabled &&
            viewModel.selectedSongIndex.value in viewModel.filteredSongItems.value.indices
        if (!offered || live.titleSlideSelected) return false
        live.titleSlideSelected = true
        return true
    }

    // Re-pushes freshly-edited content to the presenter when the just-saved song is the one
    // currently live. Sourced directly from `editedSong` (the dialog's just-saved SongItem)
    // rather than viewModel.getSelectedLyricSection() — the catalog reload triggered by
    // updateSong() is async, so the viewModel's own selection state isn't guaranteed fresh yet.
    // [tuning] is passed in rather than read back from settings: the editor saves tempo, capo and
    // song together, and the settings write has not reached `appSettings` yet in that same frame.
    fun sendEditedSongToPresenter(editedSong: SongItem, tuning: SongTuning) {
        val sections = viewModel.getLyricSections(editedSong)
        val push = resolveEditedSongPush(sections, live.sectionIndex, live.lineIndex, editedSong, tuning)
        onAllSectionsChanged(sections)
        onSectionIndexChanged(push.sectionIndex)
        onLineIndexChanged(push.lineIndex)
        onSongItemSelected(push.section)
        live.sectionIndex = push.sectionIndex
        live.lineIndex = push.lineIndex
    }

    fun saveColWidths() {
        val dp = columns.widthsInDp()
        onSettingsChange { s ->
            s.copy(
                songSettings = s.songSettings.copy(
                    colWidthNumber      = dp[SongColumnId.NUMBER] ?: s.songSettings.colWidthNumber,
                    colWidthTitle       = dp[SongColumnId.TITLE] ?: s.songSettings.colWidthTitle,
                    colWidthSongbook    = dp[SongColumnId.SONGBOOK] ?: s.songSettings.colWidthSongbook,
                    colWidthTune        = dp[SongColumnId.TUNE] ?: s.songSettings.colWidthTune,
                    colWidthPlayCount   = dp[SongColumnId.PLAY_COUNT] ?: s.songSettings.colWidthPlayCount,
                    colWidthAuthor      = dp[SongColumnId.AUTHOR] ?: s.songSettings.colWidthAuthor,
                    colWidthComposer    = dp[SongColumnId.COMPOSER] ?: s.songSettings.colWidthComposer,
                ),
                songColOrder = columns.order,
                songHiddenCols = columns.hidden,
            )
        }
    }

    fun saveLyricsPanelWidth() {
        val widthDp = with(density) { lyricsPanelPx.toDp().value.toInt() }
        onSettingsChange { s ->
            if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(lyricsPanelWidthDp = widthDp))
            else s.copy(windowedLayout = s.windowedLayout.copy(lyricsPanelWidthDp = widthDp))
        }
    }
}
