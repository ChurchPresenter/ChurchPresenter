package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.app.churchpresenter.data.PlanningCenterPrimaryBible
import org.churchpresenter.app.churchpresenter.utils.AppWindowRoot
import org.churchpresenter.planningcenter.ui.PlanningCenterImportServices
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.PresentationLoader
import org.churchpresenter.settings.PlanningCenterSettings
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.theme.ThemeMode
import java.io.File
import org.churchpresenter.planningcenter.ui.PlanningCenterImportDialog as PlanningCenterImportWindows

/**
 * The Planning Center import (`:planning-center`) with what it needs from the app filled in: the
 * OAuth client this build is registered as, the operator's primary Bible for the scripture a plan
 * names, the presentation engine's slide count, the app's windows and its song editor.
 */
@Composable
fun PlanningCenterImportDialog(
    isVisible: Boolean,
    theme: ThemeMode,
    settings: PlanningCenterSettings,
    onDismiss: () -> Unit,
    onTokensRefreshed: (accessToken: String, refreshToken: String, expiresAtEpochMs: Long) -> Unit,
    onAddSong: (songNumber: Int, title: String, songbook: String, songId: String) -> Unit,
    onAddLabel: (text: String, textColor: String, backgroundColor: String) -> Unit,
    onAddPresentation: (filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit,
    onAddPicture: (folderPath: String, folderName: String, imageCount: Int) -> Unit,
    onAddMedia: (mediaUrl: String, mediaTitle: String, mediaType: String) -> Unit,
    onAddAnnouncement: (text: String) -> Unit,
    onAddBibleVerse: (bookName: String, chapter: Int, verseNumber: Int, verseText: String,
        verseRange: String, bookId: Int) -> Unit,
    onConnected: (accessToken: String, refreshToken: String, expiresAtEpochMs: Long, personName: String) -> Unit,
    onDisconnect: () -> Unit
) {
    if (!isVisible) return
    // One Bible load per open dialog, reused for every item the plan has.
    val services = remember(isVisible) { appPlanningCenterServices(PlanningCenterPrimaryBible()) }
    val mainWindowState = LocalMainWindowState.current
    PlanningCenterImportWindows(
        isVisible = true,
        settings = settings,
        services = services,
        window = { spec, content ->
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
        },
        editSong = { song, songbook, onEditDismiss, onSave ->
            EditSongDialog(
                isVisible = song != null,
                song = song,
                songbooks = listOf(songbook),
                isNewSong = true,
                theme = theme,
                onDismiss = onEditDismiss,
                // Tempo and capo are not offered here (showTuningFields defaults off), so they come back unset.
                onSave = { savedSong, _ -> onSave(savedSong) },
            )
        },
        onDismiss = onDismiss,
        onTokensRefreshed = onTokensRefreshed,
        onAddSong = onAddSong,
        onAddLabel = onAddLabel,
        onAddPresentation = onAddPresentation,
        onAddPicture = onAddPicture,
        onAddMedia = onAddMedia,
        onAddAnnouncement = onAddAnnouncement,
        onAddBibleVerse = onAddBibleVerse,
        onConnected = onConnected,
        onDisconnect = onDisconnect,
    )
}

/** The app's side of the import: this build's OAuth client, the primary Bible and the slide counter. */
internal fun appPlanningCenterServices(bible: PlanningCenterPrimaryBible) = PlanningCenterImportServices(
    clientId = BuildConfig.PLANNING_CENTER_CLIENT_ID,
    clientSecret = BuildConfig.PLANNING_CENTER_CLIENT_SECRET,
    detectScriptures = bible::detect,
    countSlides = ::countDeckSlides,
)

/** A downloaded deck's slide count from a metadata parse -- no rasterization -- or 0 when it will not load. */
internal fun countDeckSlides(deck: File): Int =
    (PresentationLoader.load(deck) as? LoadResult.Success)?.deck?.slideCount ?: 0
