package org.churchpresenter.songs

import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.churchpresenter.sharedui.composables.rememberFocusLostRescue
import java.awt.Window as AwtWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.composables.initialPassCombinedClickable
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.settings.SongSettings


/** The toolbar button that adds the *selected* song, as opposed to any other "Add to Schedule". */
internal const val SONGS_ADD_SELECTED_TAG = "songs_addSelectedToSchedule"


@Composable
fun SongsTab(
    modifier: Modifier = Modifier,
    /** The hosting AWT window — used by the focus-lost rescue to heal AWT focus (see
     *  composables/FocusLostRescue.kt). */
    hostWindow: AwtWindow? = null,
    viewModel: SongsViewModel,
    appSettings: AppSettings,
    /** How long a song usually stays on screen here, measured -- see `LiveDurationLog`. */
    typicalSongSeconds: (SongItem) -> Int? = { null },
    /**
     * The song that has just gone live from this tab -- which is where songs are actually
     * presented from, rather than from a schedule row. The app records it for the usage report
     * and the telemetry here.
     */
    onSongWentLive: (SongItem) -> Unit = {},
    /** The title slide for a song at a tuning, under the operator's song settings; the app's. */
    titleSlideFor: (SongItem, SongTuning, SongSettings) -> LyricSection,
    /** Draws the song editor; the app's is `EditSongDialog`. */
    songEditor: @Composable (SongEditorRequest) -> Unit = {},
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: ((songNumber: Int, title: String, songbook: String, songId: String) -> Unit)? = null,
    /** Instance Link Controller mode — non-null only when connected and controlling. Go-live with a
     *  *new* song (approval-gated the first time on the primary, instant afterwards). */
    onInstanceLinkSendProject: ((ScheduleItem) -> Unit)? = null,
    /** Instance Link Controller mode — section navigation *within the same already-live song*
     *  (always instant on the primary, no approval gate). [lineIndex] carries "one line at a time"
     *  display-mode navigation (-1 = section-level only). */
    onInstanceLinkSendSongSection: ((number: String, section: Int, lineIndex: Int) -> Unit)? = null,
    selectedSongItem: ScheduleItem.SongItem? = null,
    selectedSongItemVersion: Int = 0,
    onSongItemSelected: (LyricSection) -> Unit,
    onAllSectionsChanged: (List<LyricSection>) -> Unit = {},
    onSectionIndexChanged: (Int) -> Unit = {},
    onLineIndexChanged: (Int) -> Unit = {},
    onPresenting: (Presenting) -> Unit = { Presenting.NONE },
    isPresenting: Boolean = false,
    playCounts: SongPlayCounts? = null,
    dialogDismissSignal: Int = 0,
) {

    // Edit Song Dialog state (pure UI state — fine to keep here)
    val dialogs = rememberSongDialogRequests()
    // Track which song/section/line is currently live on the presenter.
    // live.songId is the song's stable songId (not a list index) so it survives the
    // filtered list being rebuilt by search — see AGENT.md's "Song Edit While Live" note.
    val live = rememberSongLiveState()
    val density = LocalDensity.current
    val songbooks by viewModel.songbooks
    val windowState = LocalMainWindowState.current
    val isMaximized = windowState?.placement != WindowPlacement.Floating
    val currentLayout = if (isMaximized) appSettings.maximizedLayout else appSettings.windowedLayout

    val controller = remember(viewModel, dialogs, live) { SongsTabController(viewModel, dialogs, live) }
    controller.appSettings = appSettings
    controller.isPresenting = isPresenting
    controller.titleSlideFor = titleSlideFor
    controller.onSongWentLive = onSongWentLive
    controller.onInstanceLinkSendProject = onInstanceLinkSendProject
    controller.onInstanceLinkSendSongSection = onInstanceLinkSendSongSection
    controller.onSongItemSelected = onSongItemSelected
    controller.onAllSectionsChanged = onAllSectionsChanged
    controller.onSectionIndexChanged = onSectionIndexChanged
    controller.onLineIndexChanged = onLineIndexChanged
    controller.onSettingsChange = onSettingsChange
    controller.onAddToSchedule = onAddToSchedule
    controller.onPresenting = onPresenting
    controller.typicalSongSeconds = typicalSongSeconds
    controller.playCounts = playCounts
    controller.songEditor = songEditor
    controller.density = density
    controller.isMaximized = isMaximized
    controller.columns = rememberSongTableColumns(
        settings = appSettings,
        density = density,
        availableColumns = availableSongColumns(songbooks.size, hasAddToSchedule = onAddToSchedule != null),
    )
    // Favorites panel height in px
    controller.favPanelHeight = remember(appSettings.songFavoritesPanelHeightDp) {
        mutableStateOf(with(density) { appSettings.songFavoritesPanelHeightDp.dp.toPx() })
    }
    controller.lyricsPanel = remember(currentLayout.lyricsPanelWidthDp, isMaximized) {
        val saved = currentLayout.lyricsPanelWidthDp
        mutableStateOf(if (saved > 0) with(density) { saved.dp.toPx() } else 0f)
    }
    // Focus-lost rescue: arrow-key song/section/line navigation only works while the tab
    // holds keyboard focus AND the window is focused — full machinery in
    // composables/FocusLostRescue.kt (shared with Presentation/Bible).
    controller.focusRescue = rememberFocusLostRescue(hostWindow, controller.tabFocusRequester)

    controller.SongsTabEffects(
        playCounts, selectedSongItem, selectedSongItemVersion, dialogDismissSignal,
    )
    controller.SongsTabPanes(modifier)
    controller.SongEditorDialogs()
    controller.DeleteSongDialog()
}

@Composable
internal fun LyricLines(
    lines: List<String>,
    textColor: Color,
    activeLineIndex: Int = -1,
    onLineClick: ((Int) -> Unit)? = null,
    onLineDoubleClick: ((Int) -> Unit)? = null,
) {
    lines.forEachIndexed { lineIndex, line ->
        val isActiveLine = activeLineIndex >= 0 && lineIndex == activeLineIndex
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal,
            color = if (isActiveLine) MaterialTheme.colorScheme.primary else textColor,
            modifier = Modifier
                .padding(vertical = 2.dp)
                .then(
                    if (onLineClick != null) Modifier.initialPassCombinedClickable(
                        onClick = { onLineClick(lineIndex) },
                        onDoubleClick = { onLineDoubleClick?.invoke(lineIndex) }
                    ) else Modifier
                )
        )
    }
}
