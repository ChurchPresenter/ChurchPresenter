package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.companionsurface.CompanionSurfacePanel
import org.churchpresenter.liveoutput.clearFromOperator
import org.churchpresenter.liveoutput.clearGroup
import org.churchpresenter.schedule.redo
import org.churchpresenter.schedule.undo
import org.churchpresenter.sharedui.models.Presenting

/*
 * The main screen's layout pieces filled in from its scope: the state each one shows, the callbacks
 * its controls run, and the panes it places. The pieces themselves take no view model.
 */

/** What the three panels hold. Remembered per scope by the caller, so the slots stay stable. */
internal fun MainDesktopScope.panelSlots() = MainDesktopPanelSlots(
    scheduleSidebar = { modifier ->
        ScheduleSidebar(
            modifier = modifier,
            link = link,
            connections = appSettings.companionSatelliteConnections,
            schedule = { ScheduleTabPane() },
            companionSurface = companionSurfaceSlot(companionSatelliteViewModel),
        )
    },
    mainTabArea = { modifier ->
        MainTabArea(
            modifier = modifier,
            tabBar = TabBarState(
                visibleTabs = visibleTabs,
                selectedTabIndex = effectiveTabIndex,
                labelStyle = appSettings.tabLabelStyle,
                labelMargin = appSettings.tabLabelMargin,
                hiddenTabs = appSettings.hiddenTabs,
            ),
            actions = TabBarActions(
                onTabSelected = { state.selectedTabIndex = it },
                onToggleTabHidden = { tab ->
                    onSettingsChange { s -> s.copy(hiddenTabs = toggleHiddenTabs(s.hiddenTabs, tab)) }
                },
                onShowBackgroundSettings = onShowBackgroundSettings,
                onShowSettings = onShowSettings,
            ),
            currentTab = currentTab,
            tabContent = { tab -> TabContentPane(tab) },
        )
    },
    previewSidebar = { geometry -> PreviewSidebarPane(geometry) },
)

@Composable
private fun MainDesktopScope.PreviewSidebarPane(geometry: PreviewPanelGeometry) {
    PreviewSidebar(
        geometry = geometry,
        state = PreviewSidebarState(
            appSettings = appSettings,
            livePreviewAppSettings = livePreviewAppSettings,
            activeQuickBackground = activeQuickBackground,
            serverUrl = web.serverUrl,
            qaDisplayUrl = web.qaDisplayUrl,
            showControl = live.showControlFor(scheduleViewModel.scheduleItems),
        ),
        actions = PreviewSidebarActions(
            onClearDisplay = {
                mediaViewModel?.pause()
                presenterManager.clearFromOperator()
                link.sendClear?.invoke()
            },
            onQuickBackgroundPicked = onQuickBackgroundPicked,
            onSettingsChange = onSettingsChange,
        ),
        presenterManager = presenterManager,
        sttManager = sttManager,
        companionSurface = companionSurfaceSlot(companionSatelliteViewModel),
    )
}

/** What the main window's keys are judged against and run, as the key arrives. */
internal fun MainDesktopScope.keyContext() = MainDesktopKeyContext(
    shortcuts = shortcuts,
    devMode = live.devMode,
    settings = appSettings,
    sequences = state,
    actions = MainDesktopKeyActions(
        undo = { scheduleViewModel.undo() },
        redo = { scheduleViewModel.redo() },
        pickQuickBackground = onQuickBackgroundPicked,
        clearOutput = ::clearOutput,
        runMacro = live.onRunMacro,
        clearGroup = { presenterManager.clearGroup(it) },
        take = { presenterManager.previewBus.take() },
        clickPresentation = { forward ->
            clickerScope.launch {
                clickPresentationSlide(forward, presentationViewModel.slideCursor(link), presenterManager)
            }
        },
        selectTab = ::selectTab,
        unlockDeveloperMenu = onRequestDeveloperMenuUnlock,
        presentationLive = { slideContent == Presenting.PRESENTATION },
        anythingLive = { presenterManager.anythingLive },
    ),
)

/**
 * A sidebar's Companion surface, drawn from [viewModel] — the surface panel is `:companion-surface`'s
 * own and takes its view model, so it is handed in here rather than to the sidebars.
 */
private fun companionSurfaceSlot(viewModel: CompanionSatelliteViewModel): CompanionSurfaceSlot =
    { connection, placement ->
        CompanionSurfacePanel(
            connection = connection,
            placement = placement,
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth(),
            sizeToContent = true
        )
    }
