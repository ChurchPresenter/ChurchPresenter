package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import org.churchpresenter.app.churchpresenter.composables.SectionLabelRow
import org.churchpresenter.app.churchpresenter.composables.ActionIconButton
import org.churchpresenter.app.churchpresenter.composables.AddToScheduleButton
import org.churchpresenter.app.churchpresenter.composables.FocusLostBanner
import org.churchpresenter.app.churchpresenter.composables.GoLiveButton
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import churchpresenter.composeapp.generated.resources.songs_no_db_title
import churchpresenter.composeapp.generated.resources.songs_no_db_hint
import churchpresenter.composeapp.generated.resources.songs_no_db_step
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.edit_song
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_add
import churchpresenter.composeapp.generated.resources.ic_note
import churchpresenter.composeapp.generated.resources.ic_edit
import churchpresenter.composeapp.generated.resources.no_lyrics_available
import churchpresenter.composeapp.generated.resources.tab_focus_lost
import churchpresenter.composeapp.generated.resources.number
import churchpresenter.composeapp.generated.resources.song_title_slide
import churchpresenter.composeapp.generated.resources.title
import org.churchpresenter.app.churchpresenter.composables.initialPassCombinedClickable
import org.churchpresenter.app.churchpresenter.composables.finalPassCombinedClickable
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.isSongLineMode
import org.churchpresenter.app.churchpresenter.viewmodel.songCreditLine
import org.churchpresenter.app.churchpresenter.viewmodel.songTitleLine
import org.churchpresenter.app.churchpresenter.viewmodel.titleSlideSection
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.composables.FocusLostRescueState
import androidx.compose.foundation.layout.RowScope

/**
 * The lyrics panel down the right of the Songs tab.
 *
 * The actions for the selected song, then its sections and — in per-line mode — the lines within
 * them, each clickable to stage or to go live. Reads its state from the two holders and reports
 * everything else through callbacks; it owns nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RowScope.SongLyricsPanel(
    lyricsPanelPx: Float,
    appSettings: AppSettings,
    filteredSongs: List<SongItem>,
    selectedSongIndex: Int,
    selectedSectionIndex: Int,
    selectedLineIndex: Int,
    searchQuery: String,
    isPresenting: Boolean,
    live: SongLiveState,
    dialogs: SongDialogRequests,
    backToLiveStr: String,
    lineNavHintStr: String,
    newSongStr: String,
    focusRescue: FocusLostRescueState,
    tabFocusRequester: FocusRequester,
    lyricSections: () -> List<LyricSection>,
    onSectionSelected: (Int) -> Unit,
    onLineSelected: (Int) -> Unit,
    onBackToLiveSong: () -> Unit,
    onSectionIndexChanged: (Int) -> Unit,
    onLineIndexChanged: (Int) -> Unit,
    onAllSectionsChanged: (List<LyricSection>) -> Unit,
    onSongItemSelected: (LyricSection) -> Unit,
    onAddToSchedule: ((Int, String, String, String) -> Unit)?,
    onPresenting: (Presenting) -> Unit,
    sendToPresenter: (goLive: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    // Right panel — Lyrics display (fixed width, resizable via drag handle)
    Column(
        modifier = Modifier
            .width(with(density) { lyricsPanelPx.toDp() })
            .fillMaxHeight()
    ) {
        // Header row with action buttons — switches to icon-only when width is tight
        val editSongStr    = stringResource(Res.string.edit_song)
        val goLiveStr      = stringResource(Res.string.go_live)
        val addScheduleStr = stringResource(Res.string.add_to_schedule)

        val hasSongSelected = selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size && selectedSectionIndex >= 0
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                ActionIconButton(
                    onClick = { dialogs.edit(filteredSongs[selectedSongIndex]); tabFocusRequester.requestFocus() },
                    tooltipText = editSongStr,
                    painter = painterResource(Res.drawable.ic_edit),
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            }

            // New Song button
            ActionIconButton(
                onClick = { dialogs.createNew(); tabFocusRequester.requestFocus() },
                tooltipText = newSongStr,
                painter = painterResource(Res.drawable.ic_add),
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )

            if (onAddToSchedule != null && selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                AddToScheduleButton(
                    onClick = {
                        filteredSongs.getOrNull(selectedSongIndex)?.let { item ->
                            onAddToSchedule(item.number.toIntOrNull() ?: 0, item.title, item.songbook, item.songId)
                        }
                        tabFocusRequester.requestFocus()
                    },
                    tooltipText = addScheduleStr,
                    // Tagged because "Add to Schedule" is the right name for several controls
                    // here — this one, the per-row buttons and the favourites panel's — and only
                    // this one adds the *selected* song. The name is shared on purpose; the tag
                    // is how a test says which of them it means.
                    modifier = Modifier.testTag(SONGS_ADD_SELECTED_TAG)
                )
            }

            GoLiveButton(
                onClick = { sendToPresenter(true); onPresenting(Presenting.LYRICS); tabFocusRequester.requestFocus() },
                enabled = hasSongSelected,
                tooltipText = goLiveStr
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FocusLostBanner(focusRescue, stringResource(Res.string.tab_focus_lost))

        // "Back to Live" button — shown when browsing a different song than what's live.
        // Compares songId, not index/position, so this stays correct even when the live
        // song has been filtered out of the visible list by a search.
        val currentSongIdForLiveCheck = filteredSongs.getOrNull(selectedSongIndex)?.songId
        if (isPresenting && live.songId != null && currentSongIdForLiveCheck != live.songId) {
            Button(
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                onClick = {
                    onBackToLiveSong()
                    onSectionSelected(live.sectionIndex)
                    onLineSelected(live.lineIndex)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(backToLiveStr,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onError,
                    maxLines = 1)
            }
        }

        // Navigation hint — only in line mode, and drawn from the live bindings so a rebind is
        // reflected here. Hidden when both pairs are unbound: a sentence naming keys that do
        // nothing is worse than no hint.
        val isLineModeHint = isSongLineMode(appSettings.songSettings)
        if (isLineModeHint && lineNavHintStr.isNotEmpty()) {
            Text(
                text = lineNavHintStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
            )
        }

        // Lyrics content
        val noSongsLoaded = filteredSongs.isEmpty() && searchQuery.isBlank()
        if (noSongsLoaded) {
            // ── Empty state: no song database configured ──────────────
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 320.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_note),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = stringResource(Res.string.songs_no_db_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = stringResource(Res.string.songs_no_db_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(Res.string.songs_no_db_step),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
        // ── Normal lyrics view ────────────────────────────────────
        Box {
            val lyricsListState = rememberLazyListState()
            val titleSlideEnabled = appSettings.songSettings.titleSlideEnabled
            val currentSong = filteredSongs.getOrNull(selectedSongIndex)

            LaunchedEffect(selectedSectionIndex, live.titleSlideSelected) {
                if (live.titleSlideSelected) {
                    lyricsListState.animateScrollToItem(0)
                } else if (selectedSectionIndex >= 0) {
                    val offset = if (titleSlideEnabled && currentSong != null) 1 else 0
                    lyricsListState.animateScrollToItem(selectedSectionIndex + offset)
                }
            }

            // Get lyric sections from ViewModel — no parsing in UI
            val sections = if (selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                lyricSections()
            } else emptyList()

            LazyColumn(
                state = lyricsListState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
            ) {
                // ── Title slide entry ────────────────────────────────────
                if (titleSlideEnabled && currentSong != null && sections.isNotEmpty()) {
                    item {
                        val titleLine = songTitleLine(
                            currentSong,
                            appSettings.songSettings.titleSlideShowSongNumber,
                        )
                        val creditLine = songCreditLine(currentSong)

                        fun buildTitleSection() =
                            titleSlideSection(
                                currentSong,
                                appSettings.tuningFor(currentSong.songId),
                                appSettings.songSettings.titleSlideShowSongNumber,
                            )

                        fun sendTitleSlide() {
                            val ts = buildTitleSection()
                            val allSections = listOf(ts) + lyricSections()
                            onAllSectionsChanged(allSections)
                            onSectionIndexChanged(0)
                            onLineIndexChanged(0)
                            onSongItemSelected(ts)
                            live.titleSlideSelected = true
                            live.songId = currentSong.songId
                            live.sectionIndex = -1
                            live.lineIndex = 0
                        }

                        val contentColor = if (live.titleSlideSelected)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (live.titleSlideSelected)
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    else Color.Transparent
                                )
                                .initialPassCombinedClickable(
                                    onClick = { sendTitleSlide() },
                                    onDoubleClick = { sendTitleSlide(); onPresenting(Presenting.LYRICS) }
                                )
                                .padding(8.dp)
                        ) {
                            // Same chip the lyric sections use, so the title slide reads as
                            // one more entry in the list rather than a differently-styled one.
                            SectionLabelRow(
                                label = stringResource(Res.string.song_title_slide),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            Text(
                                text = titleLine,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            if (creditLine.isNotBlank()) {
                                Text(
                                    text = creditLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                // ── Regular lyric sections ───────────────────────────────
                if (sections.isNotEmpty()) {
                    itemsIndexed(sections) { sectionIndex, section ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (!live.titleSlideSelected && sectionIndex == selectedSectionIndex)
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    else Color.Transparent
                                )
                                .finalPassCombinedClickable(
                                    onClick = {
                                        onSectionSelected(sectionIndex)
                                        live.titleSlideSelected = false
                                        sendToPresenter(isPresenting)
                                    },
                                    onDoubleClick = {
                                        onSectionSelected(sectionIndex)
                                        live.titleSlideSelected = false
                                        sendToPresenter(true)
                                        onPresenting(Presenting.LYRICS)
                                    }
                                )
                                .padding(8.dp)
                        ) {
                            val textColor = if (!live.titleSlideSelected && sectionIndex == selectedSectionIndex)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurface

                            val isPerLineMode = isSongLineMode(appSettings.songSettings)
                            val activeLineIndex = if (isPerLineMode && sectionIndex == selectedSectionIndex)
                                selectedLineIndex else -1

                            // Render section header if present — the same chip the editor's
                            // preview uses, so a verse is recognised the same way in both.
                            section.header?.let { header ->
                                SectionLabelRow(
                                    label = header.trim().trim('[', ']', '{', '}').trim(),
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }

                            // Lyrics panel always shows both — language filtering only applies to presenter
                            val langDisplay = Constants.SONG_LANG_BOTH
                            val showPrimary = langDisplay != Constants.SONG_LANG_SECONDARY
                            val showSecondary = langDisplay != Constants.SONG_LANG_PRIMARY && section.secondaryLines.isNotEmpty()

                            val lineClickHandler: ((Int) -> Unit)? = if (isPerLineMode) { lineIdx ->
                                onSectionSelected(sectionIndex)
                                onLineSelected(lineIdx)
                                live.titleSlideSelected = false
                                sendToPresenter(isPresenting)
                            } else null
                            // Double-click on the words goes live too — at the clicked LINE,
                            // so per-line display mode stays line-accurate (the section's own
                            // double-click below the text still covers the background).
                            val lineDoubleClickHandler: ((Int) -> Unit)? = if (isPerLineMode) { lineIdx ->
                                onSectionSelected(sectionIndex)
                                onLineSelected(lineIdx)
                                live.titleSlideSelected = false
                                sendToPresenter(true)
                                onPresenting(Presenting.LYRICS)
                            } else null

                            if (showPrimary && showSecondary) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        LyricLines(section.lines,
                                            textColor,
                                            activeLineIndex,
                                            lineClickHandler,
                                            lineDoubleClickHandler)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        LyricLines(section.secondaryLines,
                                            textColor,
                                            activeLineIndex,
                                            lineClickHandler,
                                            lineDoubleClickHandler)
                                    }
                                }
                            } else if (showSecondary) {
                                LyricLines(section.secondaryLines,
                                    textColor,
                                    activeLineIndex,
                                    lineClickHandler,
                                    lineDoubleClickHandler)
                            } else {
                                LyricLines(section.lines,
                                    textColor,
                                    activeLineIndex,
                                    lineClickHandler,
                                    lineDoubleClickHandler)
                            }
                        }
                        // No separator between sections: each one opens with its own labelled
                        // chip and rule, which is what divides them now.
                    }
                } else {
                    item {
                        Text(
                            text = stringResource(Res.string.no_lyrics_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = lyricsListState)
            )
        }
        } // end else (songs loaded)
    }
}
