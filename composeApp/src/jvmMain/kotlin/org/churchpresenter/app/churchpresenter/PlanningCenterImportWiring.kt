package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.dialogs.songEditorBackgroundButton
import org.churchpresenter.app.churchpresenter.utils.countDeckSlides
import org.churchpresenter.bibletab.BibleBookAbbreviations
import org.churchpresenter.helper.ui.GuideSpotlightHost
import org.churchpresenter.planningcenter.ui.PlanningCenterImportDialog
import org.churchpresenter.planningcenter.ui.PlanningCenterSongEditor
import org.churchpresenter.planningcenter.ui.PlanningCenterWindow
import org.churchpresenter.planningcenter.ui.planningCenterServices
import org.churchpresenter.schedule.addAnnouncement
import org.churchpresenter.schedule.addBibleVerse
import org.churchpresenter.schedule.addLabel
import org.churchpresenter.schedule.addMedia
import org.churchpresenter.schedule.addPicture
import org.churchpresenter.schedule.addPresentation
import org.churchpresenter.schedule.addSong
import org.churchpresenter.schedule.ScheduleViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PlanningCenterSettings
import org.churchpresenter.sharedui.utils.AppWindowRoot
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.songs.EditSongDialog
import org.churchpresenter.theme.ThemeMode

/*
 * The Planning Center import (`:planning-center`) with what it needs from the app filled in: the
 * OAuth client this build is registered as, the abbreviation tables the scripture a plan names is
 * read with, the presentation engine's slide count, the app's windows and its song editor.
 */

/** The Planning Center import, adding what it picks to the Schedule and saving its tokens. */
@Composable
internal fun MainDesktopScope.PlanningCenterImport(isVisible: Boolean, onDismiss: () -> Unit) {
    val sink = remember(scheduleViewModel, onSettingsChange) {
        PlanningCenterImportSink(scheduleViewModel, onSettingsChange)
    }
    PlanningCenterImportPane(isVisible, theme, appSettings.planningCenterSettings, sink, onDismiss)
}

/**
 * The import with this build's services, opening its windows through [window] -- the app's dialogs
 * unless a test draws them in place.
 */
@Composable
internal fun PlanningCenterImportPane(
    isVisible: Boolean,
    theme: ThemeMode,
    settings: PlanningCenterSettings,
    sink: PlanningCenterImportSink,
    onDismiss: () -> Unit,
    window: PlanningCenterWindow = planningCenterWindow(theme),
) {
    if (!isVisible) return
    // One Bible load per open dialog, reused for every item the plan has.
    val services = remember(isVisible) {
        planningCenterServices(
            clientId = BuildConfig.PLANNING_CENTER_CLIENT_ID,
            clientSecret = BuildConfig.PLANNING_CENTER_CLIENT_SECRET,
            resolveAbbreviation = BibleBookAbbreviations::resolveBookId,
            countSlides = ::countDeckSlides,
        )
    }
    PlanningCenterImportDialog(
        isVisible = true,
        settings = settings,
        services = services,
        window = window,
        editSong = planningCenterSongEditor(theme),
        onDismiss = onDismiss,
        onTokensRefreshed = sink::tokensRefreshed,
        onAddSong = sink::addSong,
        onAddLabel = sink::addLabel,
        onAddPresentation = sink::addPresentation,
        onAddPicture = sink::addPicture,
        onAddMedia = sink::addMedia,
        onAddAnnouncement = sink::addAnnouncement,
        onAddBibleVerse = sink.addBibleVerse,
        onConnected = sink::connected,
        onDisconnect = sink::disconnected,
    )
}

/** Where what the import picks goes: the Schedule, and its tokens into the settings. */
internal class PlanningCenterImportSink(
    private val schedule: ScheduleViewModel,
    private val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    fun tokensRefreshed(accessToken: String, refreshToken: String, expiresAtEpochMs: Long) =
        onSettingsChange {
            withPlanningCenterTokens(it, accessToken, refreshToken, expiresAtEpochMs, personName = null)
        }

    fun connected(accessToken: String, refreshToken: String, expiresAtEpochMs: Long, personName: String) =
        onSettingsChange { withPlanningCenterTokens(it, accessToken, refreshToken, expiresAtEpochMs, personName) }

    fun disconnected() = onSettingsChange { withPlanningCenterTokens(it, "", "", 0L, personName = "") }

    fun addSong(songNumber: Int, title: String, songbook: String, songId: String) =
        schedule.addSong(songNumber, title, songbook, songId)

    fun addLabel(text: String, textColor: String, backgroundColor: String) =
        schedule.addLabel(text, textColor, backgroundColor)

    fun addPresentation(filePath: String, fileName: String, slideCount: Int, fileType: String) =
        schedule.addPresentation(filePath, fileName, slideCount, fileType)

    fun addPicture(folderPath: String, folderName: String, imageCount: Int) =
        schedule.addPicture(folderPath, folderName, imageCount)

    fun addMedia(mediaUrl: String, mediaTitle: String, mediaType: String) =
        schedule.addMedia(mediaUrl, mediaTitle, mediaType)

    fun addAnnouncement(text: String) = schedule.addAnnouncement(text = text)

    /** One verse, in the import's own callback shape. */
    val addBibleVerse: (String, Int, Int, String, String, Int) -> Unit =
        { bookName, chapter, verseNumber, verseText, verseRange, bookId ->
            schedule.addBibleVerse(bookName, chapter, verseNumber, verseText, verseRange, bookId)
        }
}

/**
 * [settings] with Planning Center's tokens replaced — by a refresh, a new connection or a
 * disconnect (all blank). [personName] is left as it was when null, as a refresh does.
 */
private fun withPlanningCenterTokens(
    settings: AppSettings,
    accessToken: String,
    refreshToken: String,
    expiresAtEpochMs: Long,
    personName: String?,
): AppSettings = settings.copy(
    planningCenterSettings = settings.planningCenterSettings.copy(
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenExpiresAtEpochMs = expiresAtEpochMs,
        connectedPersonName = personName ?: settings.planningCenterSettings.connectedPersonName,
    )
)

/** The import's windows: dialogs centred on the main window, in the app's [theme]. */
private fun planningCenterWindow(theme: ThemeMode): PlanningCenterWindow = { spec, content ->
    val mainWindowState = LocalMainWindowState.current
    DialogWindow(
        onCloseRequest = spec.onClose,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, spec.width, spec.height),
            width = spec.width,
            height = spec.height,
        ),
        title = spec.title,
        resizable = spec.resizable,
    ) {
        AppWindowRoot(theme = theme) {
            // This window's own spotlight: Wick's Planning Center tour rings its buttons.
            GuideSpotlightHost(Modifier.fillMaxSize()) { content() }
        }
    }
}

/** The song editor a plan's unmatched song is filled in with before it is saved. */
internal fun planningCenterSongEditor(theme: ThemeMode): PlanningCenterSongEditor =
    { song, songbook, onEditDismiss, onSave ->
        EditSongDialog(
            backgroundButton = songEditorBackgroundButton,
            isVisible = song != null,
            song = song,
            songbooks = listOf(songbook),
            isNewSong = true,
            theme = theme,
            onDismiss = onEditDismiss,
            // Tempo and capo are not offered here (showTuningFields defaults off), so they come back unset.
            onSave = { savedSong, _ -> onSave(savedSong) },
        )
    }
