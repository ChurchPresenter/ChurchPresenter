package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.app.churchpresenter.dialogs.songEditorBackgroundButton
import org.churchpresenter.app.churchpresenter.utils.countDeckSlides
import org.churchpresenter.bibletab.BibleBookAbbreviations
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
import org.churchpresenter.settings.AppSettings
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
        settings = appSettings.planningCenterSettings,
        services = services,
        window = planningCenterWindow(theme),
        editSong = planningCenterSongEditor(theme),
        onDismiss = onDismiss,
        onTokensRefreshed = { accessToken, refreshToken, expiresAtEpochMs ->
            onSettingsChange { settings ->
                withPlanningCenterTokens(settings, accessToken, refreshToken, expiresAtEpochMs, personName = null)
            }
        },
        onAddSong = { songNumber, title, songbook, songId ->
            scheduleViewModel.addSong(songNumber, title, songbook, songId)
        },
        onAddLabel = { text, textColor, backgroundColor ->
            scheduleViewModel.addLabel(text, textColor, backgroundColor)
        },
        onAddPresentation = { filePath, fileName, slideCount, fileType ->
            scheduleViewModel.addPresentation(filePath, fileName, slideCount, fileType)
        },
        onAddPicture = { folderPath, folderName, imageCount ->
            scheduleViewModel.addPicture(folderPath, folderName, imageCount)
        },
        onAddMedia = { mediaUrl, mediaTitle, mediaType ->
            scheduleViewModel.addMedia(mediaUrl, mediaTitle, mediaType)
        },
        onAddAnnouncement = { text ->
            scheduleViewModel.addAnnouncement(text = text)
        },
        onAddBibleVerse = { bookName, chapter, verseNumber, verseText, verseRange, bookId ->
            scheduleViewModel.addBibleVerse(bookName, chapter, verseNumber, verseText, verseRange, bookId)
        },
        onConnected = { accessToken, refreshToken, expiresAtEpochMs, personName ->
            onSettingsChange { settings ->
                withPlanningCenterTokens(settings, accessToken, refreshToken, expiresAtEpochMs, personName)
            }
        },
        onDisconnect = {
            onSettingsChange { settings -> withPlanningCenterTokens(settings, "", "", 0L, personName = "") }
        }
    )
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
        AppWindowRoot(theme = theme, content = content)
    }
}

/** The song editor a plan's unmatched song is filled in with before it is saved. */
private fun planningCenterSongEditor(theme: ThemeMode): PlanningCenterSongEditor =
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
