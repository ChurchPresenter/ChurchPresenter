package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import org.churchpresenter.converter.ui.ConverterTab
import org.churchpresenter.helper.action.describe
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.churchpresenter.app.churchpresenter.dialogs.optionsTabIndexOf
import org.churchpresenter.app.churchpresenter.dialogs.tabs.DisplayOption
import org.churchpresenter.app.churchpresenter.dialogs.tabs.detectScreensFromAwt
import org.churchpresenter.app.churchpresenter.dialogs.tabs.withPrimaryTarget
import org.churchpresenter.app.churchpresenter.utils.hasAudienceOutput
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.helper.HelperActionExecutor
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.action.ContentScope
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.action.Persistence
import org.churchpresenter.helper.action.UndoEntry
import org.churchpresenter.helper.action.changedSections
import org.churchpresenter.helper.action.revertSections
import org.churchpresenter.helper.action.scopeName
import org.churchpresenter.helper.action.withBackgroundColor
import org.churchpresenter.helper.action.withFontStep
import org.churchpresenter.helper.display.HelperScreen
import org.churchpresenter.helper.display.screenLabel
import org.churchpresenter.helper.helperText
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.helper.intent.helperTabName
import org.churchpresenter.helper.suggest.HelperSignals
import org.churchpresenter.helper.suggest.suggestionsFor
import org.churchpresenter.helper.ui.HelperInputs
import org.churchpresenter.helper.ui.HelperOverlay
import org.churchpresenter.liveoutput.deckLinkOutputCount
import org.churchpresenter.canvas.DeckLinkManager
import org.churchpresenter.server.SelectBibleVerseRequest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.HelperSettings
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.screenKey
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.utils.rememberScreenDevices
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_done_assign
import org.churchpresenter.strings.generated.resources.helper_done_bg
import org.churchpresenter.strings.generated.resources.helper_done_bg_service
import org.churchpresenter.strings.generated.resources.helper_done_font
import org.churchpresenter.strings.generated.resources.helper_refused_follower
import org.churchpresenter.strings.generated.resources.helper_refused_live
import org.churchpresenter.strings.generated.resources.helper_refused_no_preview
import org.churchpresenter.strings.generated.resources.helper_refused_nothing_to_step
import org.churchpresenter.strings.generated.resources.helper_refused_settings_open
import org.churchpresenter.strings.generated.resources.helper_undo_label_bg
import org.churchpresenter.strings.generated.resources.helper_undo_label_font
import org.churchpresenter.strings.generated.resources.helper_undo_label_screen
import org.churchpresenter.strings.generated.resources.helper_undo_label_tab

/** The helper lamp in the main window's corner, fed from the app's own state. */
@Composable
internal fun MainWindowScope.HelperHost(modifier: Modifier) {
    // Wick is still being shaped: only development builds and an unlocked developer menu show it.
    if (!root.isDevMode) return
    with(root) {
        val devices = rememberScreenDevices()
        val projection = appSettings.projectionSettings
        val screens = remember(devices.size, projection.screenAssignments, projection.screenNames) {
            helperScreens(appSettings)
        }
        val anythingLive = presenterManager.liveContent.value.isNotEmpty()
        val signals = HelperSignals(
            screenCount = devices.size,
            hasAudienceOutput = hasAudienceOutput(projection.screenAssignments, devices.size, deckLinkCountForUsage),
            outputWindowsShown = presenterManager.showPresenterWindow.value,
            primaryBibleMissing = appSettings.bibleSettings.primaryBible.isBlank(),
            songLibraryEmpty = helperSongCount == 0,
            scheduleEmpty = currentScheduleItems.isEmpty(),
            anythingLive = anythingLive,
            settingsOpen = showOptionsDialog,
            firstRunDone = !showSetupWizard,
        )
        val executor = remember(this) { AppHelperExecutor(this) }
        val resolver = remember { RuleIntentResolver() }
        val now = System.currentTimeMillis()
        HelperOverlay(
            state = helperState,
            inputs = HelperInputs(
                settings = appSettings.helper,
                onSettingsChange = ::saveHelperSettings,
                suggestions = suggestionsFor(signals, appSettings.helper, now),
                screens = screens,
                context = ResolveContext(
                    currentTab = helperCurrentTab,
                    visibleTabs = Tabs.entries.filter { it.name !in appSettings.hiddenTabs }.toSet(),
                ),
                anythingLive = anythingLive,
            ),
            executor = executor,
            resolver = resolver,
            modifier = modifier,
        )
    }
}

/** Writes the helper's own settings. */
internal fun AppRootState.saveHelperSettings(helper: HelperSettings) {
    appSettings = appSettings.copy(helper = helper)
    settingsManager.saveSettings(appSettings)
}

/** Help → Show Helper: puts the lamp back if it was hidden, and opens it. */
internal fun AppRootState.showHelper() {
    if (!appSettings.helper.enabled) saveHelperSettings(appSettings.helper.copy(enabled = true))
    helperState.isOpen = true
}

/** The connected screens as the helper shows them, the one output 1 uses marked. */
internal fun helperScreens(settings: AppSettings): List<HelperScreen> {
    val projection = settings.projectionSettings
    val first = projection.screenAssignments.firstOrNull()
    return runCatching { detectScreensFromAwt() }.getOrDefault(emptyList()).map { screen ->
        HelperScreen(
            index = screen.index,
            isPrimary = screen.isPrimary,
            x = screen.boundsX,
            y = screen.boundsY,
            width = screen.boundsW,
            height = screen.boundsH,
            name = projection.screenNames[screen.key].orEmpty(),
            isAudience = first != null && first.isOn(screen.boundsX, screen.boundsY, screen.boundsW, screen.boundsH),
        )
    }
}

private fun ScreenAssignment.isOn(x: Int, y: Int, w: Int, h: Int): Boolean =
    targetType == Constants.TARGET_TYPE_SCREEN && targetScreenKey == screenKey(x, y, w, h)

/**
 * What the helper's actions do in this app. Each is already confirmed by the operator when it
 * gets here; this decides whether it can be done now, does it, and says how to take it back.
 *
 * Live actions go through the same flows a remote control's do, so the helper drives the output
 * exactly as a phone would — nothing here reaches into a view model.
 */
internal class AppHelperExecutor(private val root: AppRootState) : HelperActionExecutor {

    @Suppress("CyclomaticComplexMethod")
    override fun execute(action: HelperAction): ActionOutcome = when (action) {
        is HelperAction.ShowBibleVerse -> {
            val range = if (action.lastVerse > action.verse) "${action.verse}-${action.lastVerse}" else ""
            root.companionServer.onSelectBibleVerse.tryEmit(
                SelectBibleVerseRequest(
                    bookName = action.book,
                    chapter = action.chapter,
                    verseNumber = action.verse,
                    verseRange = range,
                ),
            )
            ActionOutcome.Done()
        }
        HelperAction.NextSlide -> step(forward = true)
        HelperAction.PreviousSlide -> step(forward = false)
        HelperAction.ClearOutput -> {
            root.companionServer.onClear.tryEmit(Unit)
            ActionOutcome.Done()
        }
        HelperAction.Take -> take()
        is HelperAction.SetBackgroundColor -> backgroundColor(action)
        is HelperAction.ChangeFontSize -> fontSize(action)
        is HelperAction.OpenSettings -> {
            root.openOptionsDialog(optionsTabIndexOf(action.page))
            ActionOutcome.Done()
        }
        HelperAction.OpenSetupWizard -> {
            root.showSetupWizard = true
            ActionOutcome.Done()
        }
        is HelperAction.OpenConverter -> {
            root.converterInitialTab = ConverterTab.SONGS
            root.converterInitialSource = action.sourceId
            root.showConverterWindow = true
            ActionOutcome.Done(message = action.describe())
        }
        HelperAction.OpenSongLibrary -> {
            root.showSongLibraryWindow = true
            ActionOutcome.Done(message = action.describe())
        }
        HelperAction.OpenKeyboardShortcuts -> {
            root.showKeyboardShortcutsDialog = true
            ActionOutcome.Done()
        }
        HelperAction.StartDisplaySetup -> ActionOutcome.DisplaySetup
        is HelperAction.AssignAudienceScreen -> assign(action.screen)
        HelperAction.IdentifyScreens -> identify()
        HelperAction.ToggleOutputWindows -> {
            root.presenterManager.togglePresenterWindow()
            ActionOutcome.Done()
        }
        is HelperAction.SelectTab -> {
            root.helperSelectTabFlow.tryEmit(action.tab)
            ActionOutcome.Done()
        }
        is HelperAction.ShowTab -> showTab(action.tab)
        // Handled by the helper itself; never sent here.
        is HelperAction.Highlight, is HelperAction.ShowShortcut, HelperAction.UndoLast,
        HelperAction.Greet, HelperAction.Thanks -> ActionOutcome.Done()
    }

    /** Slides and pictures step from here; songs and the Bible by their own tab's keys. */
    private fun step(forward: Boolean): ActionOutcome {
        val server = root.companionServer
        val flow = when (root.presenterManager.slideContent.value) {
            Presenting.PRESENTATION -> if (forward) server.onNextSlide else server.onPreviousSlide
            Presenting.PICTURES -> if (forward) server.onNextPicture else server.onPreviousPicture
            else -> return ActionOutcome.Refused(helperText(Res.string.helper_refused_nothing_to_step))
        }
        flow.tryEmit(Unit)
        return ActionOutcome.Done()
    }

    private fun take(): ActionOutcome {
        val bus = root.presenterManager.previewBus
        if (!root.appSettings.projectionSettings.previewModeEnabled || !bus.anythingCued) {
            return ActionOutcome.Refused(helperText(Res.string.helper_refused_no_preview))
        }
        bus.take()
        return ActionOutcome.Done()
    }

    private fun backgroundColor(action: HelperAction.SetBackgroundColor): ActionOutcome {
        if (root.mirroredBackgroundSettings != null) {
            return ActionOutcome.Refused(helperText(Res.string.helper_refused_follower))
        }
        if (action.persistence == Persistence.THIS_SERVICE) return quickBackground(action)
        return changeSettings(
            label = helperText(Res.string.helper_undo_label_bg, scopeName(action.scope)),
            done = helperText(Res.string.helper_done_bg, scopeName(action.scope), action.colorName),
        ) { withBackgroundColor(it, action.scope, action.hex) }
    }

    /** A colour behind everything for now, the way the quick-background tray does it — nothing saved. */
    private fun quickBackground(action: HelperAction.SetBackgroundColor): ActionOutcome {
        val previous = root.activeQuickBackground
        val colour = SongBackground(type = SongBackgroundType.COLOR, color = action.hex)
        val mine = QuickBackground(
            id = "helper",
            label = action.colorName,
            background = colour,
            lowerThirdBackground = colour,
        )
        root.activeQuickBackground = mine
        val undo = UndoEntry(helperText(Res.string.helper_undo_label_bg, scopeName(ContentScope.ALL))) {
            if (root.activeQuickBackground != mine) return@UndoEntry false
            root.activeQuickBackground = previous
            true
        }
        return ActionOutcome.Done(helperText(Res.string.helper_done_bg_service, action.colorName), undo)
    }

    private fun fontSize(action: HelperAction.ChangeFontSize): ActionOutcome = changeSettings(
        label = helperText(Res.string.helper_undo_label_font, scopeName(action.scope)),
        done = helperText(Res.string.helper_done_font, scopeName(action.scope)),
    ) { withFontStep(it, action.scope, action.direction) }

    private fun assign(screen: HelperScreen): ActionOutcome {
        val nonPrimary = runCatching { detectScreensFromAwt() }.getOrDefault(emptyList()).count { !it.isPrimary }
        val deckLinks = deckLinkOutputCount(DeckLinkManager.isAvailable()) { DeckLinkManager.listDevices().size }
        val slots = maxOf(1, presenterWindowCount(nonPrimary, deckLinks))
        val option = DisplayOption(
            label = "",
            targetDisplay = screen.index,
            targetType = Constants.TARGET_TYPE_SCREEN,
            boundsX = screen.x,
            boundsY = screen.y,
            boundsW = screen.width,
            boundsH = screen.height,
        )
        return changeSettings(
            label = helperText(Res.string.helper_undo_label_screen),
            done = helperText(Res.string.helper_done_assign, screen.screenLabel()),
        ) { s ->
            val projection = s.projectionSettings
            s.copy(projectionSettings = withPrimaryTarget(projection, 0, projection.getAssignment(0), option, slots))
        }
    }

    private fun identify(): ActionOutcome {
        if (root.presenterManager.liveContent.value.isNotEmpty()) {
            return ActionOutcome.Refused(helperText(Res.string.helper_refused_live))
        }
        root.presenterManager.setShowPresenterWindow(true)
        root.identifyScreens()
        return ActionOutcome.Done()
    }

    private fun showTab(tab: Tabs): ActionOutcome {
        val outcome = changeSettings(
            label = helperText(Res.string.helper_undo_label_tab, helperTabName(tab)),
            done = null,
        ) { it.copy(hiddenTabs = it.hiddenTabs - tab.name) }
        if (outcome is ActionOutcome.Done) root.helperSelectTabFlow.tryEmit(tab)
        return outcome
    }

    /**
     * Applies [edit] to the settings and saves them, with an undo that puts back exactly the parts it
     * changed — unless the operator has changed those parts again since.
     */
    private fun changeSettings(
        label: HelperText,
        done: HelperText?,
        edit: (AppSettings) -> AppSettings,
    ): ActionOutcome {
        // The Settings window edits a copy of its own and writes it all back on OK, which would
        // quietly undo this. Ask for it to be closed instead.
        if (root.showOptionsDialog) return ActionOutcome.Refused(helperText(Res.string.helper_refused_settings_open))
        val before = root.appSettings
        val after = edit(before)
        val sections = changedSections(before, after)
        save(after)
        val undo = UndoEntry(label) {
            if (root.showOptionsDialog) return@UndoEntry false
            val restored = revertSections(root.appSettings, before, after, sections) ?: return@UndoEntry false
            save(restored)
            true
        }
        return ActionOutcome.Done(done, undo)
    }

    private fun save(settings: AppSettings) {
        root.appSettings = settings
        root.settingsManager.saveSettings(settings)
    }
}
