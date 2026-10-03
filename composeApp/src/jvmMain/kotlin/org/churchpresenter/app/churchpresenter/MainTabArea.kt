package org.churchpresenter.app.churchpresenter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.background
import org.churchpresenter.icons.generated.resources.ic_settings
import org.churchpresenter.strings.generated.resources.tab_visibility
import org.churchpresenter.strings.generated.resources.tooltip_settings
import org.churchpresenter.app.churchpresenter.composables.ToolbarKey
import org.churchpresenter.app.churchpresenter.composables.ToolbarKeyStyle
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.app.churchpresenter.tabs.BibleTab
import org.churchpresenter.app.churchpresenter.tabs.CompanionSurfaceTab
import org.churchpresenter.crosswordtab.CrosswordTab
import org.churchpresenter.app.churchpresenter.tabs.AppSTTTab
import org.churchpresenter.songs.SongsTab
import org.churchpresenter.app.churchpresenter.tabs.AppSongEditor
import org.churchpresenter.app.churchpresenter.tabs.recordSongWentLive
import org.churchpresenter.app.churchpresenter.viewmodel.titleSlideSection
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.app.churchpresenter.tabs.getStringName
import org.churchpresenter.app.churchpresenter.data.asDurationRow
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedCheckbox
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.tabs.AppQATab

private const val CONTENT_CROSSFADE_MS = 120
private val TOOLBAR_KEY_SIZE = 40.dp

/** The tab bar's bottom corners, matching the cards below it; the divider under it stops where they start. */
private val TAB_BAR_CORNER_RADIUS = 14.dp
private val TAB_BAR_SHAPE = AppShape(bottomStart = TAB_BAR_CORNER_RADIUS, bottomEnd = TAB_BAR_CORNER_RADIUS)

/** The middle of the main screen: the tab bar above the showing tab. */
@Composable
internal fun MainDesktopScope.MainTabArea(modifier: Modifier) {
    Column(modifier = modifier) {
        TabBar()
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = TAB_BAR_CORNER_RADIUS),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
        )
        TabContent(Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun MainDesktopScope.TabBar() {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(TAB_BAR_SHAPE)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabSection(
            modifier = Modifier.weight(1f),
            visibleTabs = visibleTabs,
            selectedTabIndex = effectiveTabIndex,
            labelStyle = appSettings.tabLabelStyle,
            labelMargin = appSettings.tabLabelMargin,
            onTabSelected = { state.selectedTabIndex = it }
        )
        TabVisibilityMenu()
        ToolbarKey(
            painter = rememberVectorPainter(Icons.Default.Wallpaper),
            text = stringResource(Res.string.background),
            onClick = onShowBackgroundSettings,
            style = ToolbarKeyStyle.PANEL_TOGGLE,
            buttonSize = TOOLBAR_KEY_SIZE,
        )
        ToolbarKey(
            painter = painterResource(IconRes.drawable.ic_settings),
            text = stringResource(Res.string.tooltip_settings),
            onClick = onShowSettings,
            style = ToolbarKeyStyle.PANEL_TOGGLE,
            buttonSize = TOOLBAR_KEY_SIZE,
        )
    }
}

/** The tab bar's show/hide menu: every tab but the crossword, never letting the last one go. */
@Composable
private fun MainDesktopScope.TabVisibilityMenu() {
    var showTabVisibilityMenu by remember { mutableStateOf(false) }
    Box {
        ToolbarKey(
            painter = rememberVectorPainter(Icons.Default.Tune),
            text = stringResource(Res.string.tab_visibility),
            onClick = { showTabVisibilityMenu = true },
            style = ToolbarKeyStyle.PANEL_TOGGLE,
            open = showTabVisibilityMenu,
            buttonSize = TOOLBAR_KEY_SIZE,
        )
        DropdownMenu(
            expanded = showTabVisibilityMenu,
            onDismissRequest = { showTabVisibilityMenu = false }
        ) {
            val visibleCount = visibleTabCount(appSettings.hiddenTabs)
            Tabs.entries.filter { it != Tabs.CROSSWORD }.forEach { tab ->
                val isVisible = tab.name !in appSettings.hiddenTabs
                val isOnlyVisible = isOnlyVisibleTab(tab, appSettings.hiddenTabs, visibleCount)
                DropdownMenuItem(
                    text = { Text(getStringName(tab)) },
                    onClick = {
                        if (!isOnlyVisible) {
                            onSettingsChange { s -> s.copy(hiddenTabs = toggleHiddenTabs(s.hiddenTabs, tab)) }
                        }
                    },
                    leadingIcon = {
                        RaisedCheckbox(
                            checked = isVisible,
                            onCheckedChange = null,
                            enabled = !isOnlyVisible
                        )
                    },
                    enabled = !isOnlyVisible
                )
            }
        }
    }
}

@Composable
private fun MainDesktopScope.TabContent(modifier: Modifier) {
    AnimatedContent(
        targetState = currentTab,
        transitionSpec = { fadeIn(tween(CONTENT_CROSSFADE_MS)) togetherWith fadeOut(tween(CONTENT_CROSSFADE_MS)) },
        modifier = modifier,
        label = "tab_content"
    ) { tab ->
        when (tab) {
            Tabs.BIBLE -> BibleTabPane()
            Tabs.SONGS -> SongsTabPane()
            Tabs.PICTURES -> PicturesTabPane()
            Tabs.PRESENTATION -> PresentationTabPane()
            Tabs.MEDIA -> MediaTabPane()
            Tabs.LOWER_THIRD -> LowerThirdTabPane()
            Tabs.ANNOUNCEMENTS -> AnnouncementsTabPane()
            Tabs.WEB -> WebTabPane()
            Tabs.CANVAS -> CanvasTabPane()
            Tabs.QA -> if (qaManager != null) {
                AppQATab(
                    modifier = Modifier.fillMaxSize(),
                    qaManager = qaManager,
                    presenterManager = presenterManager,
                    serverUrl = web.serverUrl,
                    presenting = live.presenting,
                    appSettings = appSettings,
                    onSettingsChange = onSettingsChange,
                    tunnelStatus = web.tunnelStatus,
                    tunnelUrl = web.tunnelUrl,
                    onStartTunnel = web.onStartTunnel,
                    onStopTunnel = web.onStopTunnel,
                    qaDisplayUrl = web.qaDisplayUrl,
                    onQaDisplayUrlChanged = web.onQaDisplayUrlChanged,
                )
            }
            Tabs.STT -> if (sttManager != null) {
                AppSTTTab(
                    modifier = Modifier.fillMaxSize(),
                    sttManager = sttManager,
                    presenterManager = presenterManager,
                    presenting = live.presenting,
                    appSettings = appSettings,
                    onSettingsChange = onSettingsChange
                )
            }
            Tabs.CROSSWORD -> CrosswordTab(
                modifier = Modifier.fillMaxSize(),
                appSettings = appSettings,
                onSettingsChange = onSettingsChange
            )
            Tabs.COMPANION_SURFACE -> CompanionSurfaceTab(
                modifier = Modifier.fillMaxSize(),
                appSettings = appSettings,
                viewModel = companionSatelliteViewModel
            )
            Tabs.DICTIONARY -> DictionaryTabPane()
        }
    }
}

@Composable
private fun MainDesktopScope.BibleTabPane() {
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
        onVerseSelected = live.onVerseSelected,
        onInstanceLinkSendVerse = link.sendVerse,
        onInstanceLinkSendBibleHold = link.sendBibleHold,
        onPresenting = live.presenting,
        isPresenting = presentingMode == Presenting.BIBLE,
        presenterManager = presenterManager,
        statisticsManager = statisticsManager,
        verseSequenceLog = verseSequenceLog,
        dialogDismissSignal = dialogDismissSignal,
        sttManager = sttManager,
        bibleEngineClient = bibleEngineClient
    )
}

@Composable
private fun MainDesktopScope.SongsTabPane() {
    SongsTab(
        modifier = Modifier.fillMaxSize(),
        hostWindow = hostWindow,
        viewModel = songsViewModel,
        appSettings = appSettings,
        typicalSongSeconds = service.typicalSongSeconds,
        onSongWentLive = { song ->
            recordSongWentLive(song, appSettings, statisticsManager)
            live.onRowWentLive(song.asDurationRow())
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
        onSongItemSelected = live.onSongItemSelected,
        onAllSectionsChanged = live.onAllSectionsChanged,
        onSectionIndexChanged = live.onSectionIndexChanged,
        onLineIndexChanged = live.onLineIndexChanged,
        onPresenting = live.presenting,
        isPresenting = presentingMode == Presenting.LYRICS,
        playCounts = statisticsManager,
        dialogDismissSignal = dialogDismissSignal
    )
}
