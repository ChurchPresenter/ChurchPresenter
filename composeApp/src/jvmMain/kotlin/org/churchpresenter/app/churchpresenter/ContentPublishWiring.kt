package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import org.churchpresenter.sharedui.composables.FontPreviewText
import java.io.File

/**
 * Publishes what the tabs have loaded — slides, scenes, pictures — to the server and the rest of
 * the app, and keeps the loaded content in step with remote changes.
 */
@Composable
internal fun MainDesktopScope.ContentPublishWiring() {
    LaunchedEffect(
        presentationViewModel.selectedSlideIndex,
        presentationViewModel.slideFiles.size,
        presentationViewModel.isPlaying,
    ) {
        val f = presentationViewModel.selectedPresentation ?: return@LaunchedEffect
        val id = stableFileId(f)
        publish.onSlideChanged?.invoke(
            id,
            presentationViewModel.selectedSlideIndex,
            presentationViewModel.slideFiles.size,
            presentationViewModel.isPlaying,
        )
    }
    LaunchedEffect(appSettings.presentationRemoteSettings.remoteControlEnabled) {
        val f = presentationViewModel.selectedPresentation
        if (!shouldPublishPresentation(
                appSettings.presentationRemoteSettings.remoteControlEnabled,
                f != null,
                presentationViewModel.slideFiles.size,
            ) || f == null
        ) return@LaunchedEffect
        val id = stableFileId(f)
        publish.onSlideChanged?.invoke(
            id,
            presentationViewModel.selectedSlideIndex,
            presentationViewModel.slideFiles.size,
            presentationViewModel.isPlaying
        )
        publish.onPresentationSlidesLoaded?.invoke(
            id,
            f.absolutePath,
            f.nameWithoutExtension,
            f.extension.lowercase(),
            presentationViewModel.slideFiles.toList(),
            presentationViewModel.slideNotes.toList()
        )
    }

    // Publish the scene list out for InstanceLink CANVAS mirroring — the callback pattern keeps
    // SceneViewModel owned here (only the plain Scene list crosses the boundary).
    val currentOnScenesChanged by rememberUpdatedState(publish.onScenesChanged)
    LaunchedEffect(sceneViewModel.scenes.toList()) {
        currentOnScenesChanged?.invoke(sceneViewModel.scenes.toList())
    }

    // Sync remote section changes (e.g. from mobile) back to the songs UI
    LaunchedEffect(Unit) {
        snapshotFlow { presenterManager.songDisplaySectionIndex.value }
            .collect { index ->
                if (shouldFollowRemoteSection(
                        presenterManager.presentingMode.value,
                        songsViewModel.selectedSectionIndex.value,
                        index,
                    )
                ) {
                    songsViewModel.selectSection(index)
                }
            }
    }

    // Genesis 1:1 out of whatever is loaded, for the font pickers to preview. Pushed rather than
    // read: the pickers sit inside settings dialogs that are windows of their own, and none of them
    // may be handed the ViewModel.
    val loadedTranslations = bibleViewModel.loadedTranslations.value
    LaunchedEffect(loadedTranslations) {
        FontPreviewText.update(loadedTranslations.map { it.bible })
    }

    LaunchedEffect(
        appSettings.bibleSettings.storageDirectory,
        appSettings.bibleSettings.customNameKey(),
    ) {
        dictionaryViewModel.loadAvailableBibles(
            appSettings.bibleSettings.storageDirectory,
            appSettings.bibleSettings.customNames(),
        )
    }

    PictureWiring()
}

@Composable
private fun MainDesktopScope.PictureWiring() {
    // Notify server whenever the picture folder, image list, or image order changes
    val currentOnPicturesLoaded by rememberUpdatedState(publish.onPicturesLoaded)
    val pictureImages = picturesViewModel.images
    val pictureFolder = picturesViewModel.selectedFolder
    val pictureOrderVersion = picturesViewModel.imageOrderVersion
    LaunchedEffect(pictureFolder, pictureImages.size, pictureOrderVersion) {
        val folder = pictureFolder ?: return@LaunchedEffect
        if (pictureImages.isEmpty()) return@LaunchedEffect
        val folderId = stableFileId(folder)
        // Through the ViewModel's own lock: walking the state list directly races the download
        // coroutine and the folder watcher, and the CME lands on the event thread.
        currentOnPicturesLoaded?.invoke(
            folderId, folder.name, folder.absolutePath, picturesViewModel.imagesSnapshot(),
        )
    }

    // Load picture folder when a picture schedule item is selected (works even before Pictures tab is composed)
    LaunchedEffect(state.selectedPictureItem) {
        state.selectedPictureItem?.let { pictureItem ->
            val folder = File(pictureItem.folderPath)
            if (isLoadablePictureFolder(folder)) {
                picturesViewModel.selectFolder(folder)
            }
        }
    }
}
