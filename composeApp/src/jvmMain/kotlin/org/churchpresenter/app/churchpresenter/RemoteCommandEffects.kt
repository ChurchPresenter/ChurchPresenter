package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.RecentPresentationFiles
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.SelectBibleVerseRequest
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager

import java.io.File
import org.churchpresenter.app.churchpresenter.viewmodel.logLiveReference

/**
 * Everything the app does because a *remote* asked it to — a Companion button, a phone, a linked
 * instance — collected out of `MainDesktop`'s body.
 *
 * Each block is one command flow and what it drives. They are wiring by nature: the bodies are
 * calls onto the view models that own the state, so this takes those rather than a callback per
 * command, which would only move the same lines to the call site. `MainDesktop` is the documented
 * place where view models are wired top-down; this stays inside that, and no other file should
 * take one.
 */
@Composable
internal fun RemoteCommandEffects(
    appSettings: AppSettings,
    picturesViewModel: PicturesViewModel,
    presentationViewModel: PresentationViewModel,
    bibleViewModel: BibleViewModel,
    presenterManager: PresenterManager,
    onSongItemVersionBump: () -> Unit,
    resolveImageFile: ((folderId: String, index: Int) -> File?)?,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onSongItemSelected: (ScheduleItem.SongItem) -> Unit,
    onPictureItemSelected: (ScheduleItem.PictureItem) -> Unit,
    onPresentationItemSelected: (ScheduleItem.PresentationItem) -> Unit,
    onMediaItemSelected: (ScheduleItem.MediaItem) -> Unit,
    onSelectTab: (Tabs) -> Unit,
    pushCurrentSlideIfLive: suspend () -> Unit,
    remotePresentationPlayPauseFlow: Flow<Unit>? = null,
    remotePresentationLoopToggleFlow: Flow<Unit>? = null,
    remotePresentationGotoFlow: Flow<Int>? = null,
    selectPictureImageFlow: Flow<Pair<String, Int>>? = null,
    nextPictureFlow: Flow<Unit>? = null,
    previousPictureFlow: Flow<Unit>? = null,
    nextSlideFlow: Flow<Unit>? = null,
    previousSlideFlow: Flow<Unit>? = null,
    selectSlideFlow: Flow<Pair<String, Int>>? = null,
    selectBibleVerseFlow: Flow<SelectBibleVerseRequest>? = null,
    remoteSelectSongFlow: Flow<ScheduleItem.SongItem>? = null,
    remoteSelectPictureFlow: Flow<ScheduleItem.PictureItem>? = null,
    remoteSelectPresentationFlow: Flow<ScheduleItem.PresentationItem>? = null,
    remoteSelectMediaFlow: Flow<ScheduleItem.MediaItem>? = null,
    uploadPresentationFlow: Flow<File>? = null,
) {
    RemotePresentationEffects(
        presentationViewModel = presentationViewModel,
        presenterManager = presenterManager,
        onSettingsChange = onSettingsChange,
        onSelectTab = onSelectTab,
        pushCurrentSlideIfLive = pushCurrentSlideIfLive,
        remotePresentationPlayPauseFlow = remotePresentationPlayPauseFlow,
        remotePresentationLoopToggleFlow = remotePresentationLoopToggleFlow,
        remotePresentationGotoFlow = remotePresentationGotoFlow,
        nextSlideFlow = nextSlideFlow,
        previousSlideFlow = previousSlideFlow,
        selectSlideFlow = selectSlideFlow,
        uploadPresentationFlow = uploadPresentationFlow,
    )
    RemotePictureEffects(
        picturesViewModel = picturesViewModel,
        presenterManager = presenterManager,
        resolveImageFile = resolveImageFile,
        selectPictureImageFlow = selectPictureImageFlow,
        nextPictureFlow = nextPictureFlow,
        previousPictureFlow = previousPictureFlow,
    )
    RemoteBibleEffects(
        appSettings = appSettings,
        bibleViewModel = bibleViewModel,
        presenterManager = presenterManager,
        selectBibleVerseFlow = selectBibleVerseFlow,
    )
    RemoteTabSelectionEffects(
        onSongItemSelected = onSongItemSelected,
        onSongItemVersionBump = onSongItemVersionBump,
        onPictureItemSelected = onPictureItemSelected,
        onPresentationItemSelected = onPresentationItemSelected,
        onMediaItemSelected = onMediaItemSelected,
        onSelectTab = onSelectTab,
        remoteSelectSongFlow = remoteSelectSongFlow,
        remoteSelectPictureFlow = remoteSelectPictureFlow,
        remoteSelectPresentationFlow = remoteSelectPresentationFlow,
        remoteSelectMediaFlow = remoteSelectMediaFlow,
    )
}

/**
 * The deck: play/pause, loop, go to a slide, step through, and a deck uploaded from a phone.
 *
 * Every one of these ends at [PresentationViewModel] or at the presenter, and `pushCurrentSlideIfLive`
 * is what tells the clients where the deck ended up.
 */
@Composable
private fun RemotePresentationEffects(
    presentationViewModel: PresentationViewModel,
    presenterManager: PresenterManager,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onSelectTab: (Tabs) -> Unit,
    pushCurrentSlideIfLive: suspend () -> Unit,
    remotePresentationPlayPauseFlow: Flow<Unit>? = null,
    remotePresentationLoopToggleFlow: Flow<Unit>? = null,
    remotePresentationGotoFlow: Flow<Int>? = null,
    nextSlideFlow: Flow<Unit>? = null,
    previousSlideFlow: Flow<Unit>? = null,
    selectSlideFlow: Flow<Pair<String, Int>>? = null,
    uploadPresentationFlow: Flow<File>? = null,
) {
    LaunchedEffect(remotePresentationPlayPauseFlow) {
        remotePresentationPlayPauseFlow?.collect { presentationViewModel.togglePlayPause() }
    }
    LaunchedEffect(remotePresentationLoopToggleFlow) {
        remotePresentationLoopToggleFlow?.collect {
            presentationViewModel.isLooping = !presentationViewModel.isLooping
            onSettingsChange { s -> s.copy(presentationSettings = s.presentationSettings.copy(isLooping = presentationViewModel.isLooping)) }
        }
    }
    LaunchedEffect(remotePresentationGotoFlow) {
        remotePresentationGotoFlow?.collect { index ->
            if (isValidSlideIndex(index, presentationViewModel.slideFiles.size)) {
                presentationViewModel.selectSlide(index)
            }
        }
    }
    LaunchedEffect(nextSlideFlow) {
        nextSlideFlow?.collect {
            presentationViewModel.nextSlide()
            pushCurrentSlideIfLive()
        }
    }
    LaunchedEffect(previousSlideFlow) {
        previousSlideFlow?.collect {
            presentationViewModel.previousSlide()
            pushCurrentSlideIfLive()
        }
    }
    LaunchedEffect(selectSlideFlow) {
        selectSlideFlow?.collect { (_, index) ->
            if (index in presentationViewModel.slideFiles.indices) {
                presentationViewModel.selectSlide(index)
                val (bitmap, nextBitmap) = decodeSlideBitmaps(
                    presentationViewModel.slideFiles,
                    index,
                    presentationViewModel.nextShownSlideIndex(index),
                )
                presenterManager.setSelectedSlide(bitmap)
                presenterManager.setNextSlide(nextBitmap)
                presenterManager.setPresenterNotes(presenterNotesAt(presentationViewModel.slideNotes, index))
                if (shouldTakePresentationLive(presenterManager.presentingMode.value)) {
                    presenterManager.setPresentingMode(Presenting.PRESENTATION)
                    presenterManager.setShowPresenterWindow(true)
                }
                presentationViewModel.deck?.let { presenterManager.presentationShowSlide(it, index) }
                    ?: presenterManager.clearPresentationPlayback()
            }
        }
    }
    LaunchedEffect(uploadPresentationFlow) {
        uploadPresentationFlow?.collect { file ->
            presentationViewModel.addPresentation(file)
            RecentPresentationFiles.add(file.absolutePath)
            // Switch to the Presentations tab so the user can see the newly loaded file
            onSelectTab(Tabs.PRESENTATION)
        }
    }
}

/**
 * The slideshow: one image chosen by id, and stepping through the folder.
 *
 * The chosen-image path is the long one because a phone can pick from a folder this machine is not
 * showing — see the comments inside it.
 */
@Composable
private fun RemotePictureEffects(
    picturesViewModel: PicturesViewModel,
    presenterManager: PresenterManager,
    resolveImageFile: ((folderId: String, index: Int) -> File?)?,
    selectPictureImageFlow: Flow<Pair<String, Int>>? = null,
    nextPictureFlow: Flow<Unit>? = null,
    previousPictureFlow: Flow<Unit>? = null,
) {
    LaunchedEffect(selectPictureImageFlow) {
        selectPictureImageFlow?.collect { (folderId, index) ->
            // Derive the folderId of the currently loaded Pictures-tab folder (same hash as
            // CompanionServer.updatePictures and the LaunchedEffect(pictureFolder, …) above).
            val activeFolderId = picturesViewModel.selectedFolder?.let { stableFileId(it) }

            // Resolve the file from the server's file map so selections from any folder
            // (including session-only device_uploads) go to the correct image.
            val imageFile = resolveImageFile?.invoke(folderId, index)
            if (isUsableImageFile(imageFile) && imageFile != null) {
                // When the selection is from a DIFFERENT folder (e.g. device_uploads), load
                // that folder into picturesViewModel NOW, before changing the presenting mode.
                // This prevents PicturesTab's syncWithPresenter LaunchedEffect from firing with
                // stale files and overwriting the correct image path in the presenter.
                if (shouldSwitchPictureFolder(folderId, activeFolderId)) {
                    picturesViewModel.selectFolder(imageFile.parentFile)
                }
                // Set the selected index (images are synchronously populated by selectFolder).
                if (index in picturesViewModel.images.indices) {
                    picturesViewModel.selectedImageIndex = index
                }
                // Now syncWithPresenter will read the correct file via getCurrentImageFile().
                presenterManager.setSelectedImagePath(imageFile.absolutePath)
                val nextIdx = nextImageIndex(index, picturesViewModel.images.size)
                presenterManager.setNextImagePath(picturesViewModel.images.getOrNull(nextIdx)?.absolutePath)
                presenterManager.setPresentingMode(Presenting.PICTURES)
                presenterManager.setShowPresenterWindow(true)
            } else {
                // Fallback: resolveImageFile not wired or file not found — use VM directly.
                val images = picturesViewModel.images
                if (index in images.indices) {
                    picturesViewModel.selectedImageIndex = index
                    val currentImage = picturesViewModel.getCurrentImageFile()
                    if (currentImage != null) {
                        presenterManager.setSelectedImagePath(currentImage.absolutePath)
                        presenterManager.setNextImagePath(
                            picturesViewModel.images.getOrNull(nextImageIndex(index, images.size))?.absolutePath
                        )
                        presenterManager.setPresentingMode(Presenting.PICTURES)
                        presenterManager.setShowPresenterWindow(true)
                    }
                }
            }
        }
    }
    LaunchedEffect(nextPictureFlow) {
        nextPictureFlow?.collect {
            picturesViewModel.nextImage()
            picturesViewModel.syncWithPresenter(presenterManager)
        }
    }
    LaunchedEffect(previousPictureFlow) {
        previousPictureFlow?.collect {
            picturesViewModel.previousImage()
            picturesViewModel.syncWithPresenter(presenterManager)
        }
    }
}

/** A verse put on screen by a remote, and the reference logged as having gone live. */
@Composable
private fun RemoteBibleEffects(
    appSettings: AppSettings,
    bibleViewModel: BibleViewModel,
    presenterManager: PresenterManager,
    selectBibleVerseFlow: Flow<SelectBibleVerseRequest>? = null,
) {
    LaunchedEffect(selectBibleVerseFlow) {
        selectBibleVerseFlow?.collect { req ->
            val primaryBible = bibleViewModel.primaryBible.value

            // Resolve bookId from book name using the primary Bible's book list
            val bookIndex = primaryBible?.getBooks()?.let { resolveBookIndex(it, req.bookName) } ?: -1

            val resolved = bibleViewModel.getVersesForDisplay(req.bookName, req.chapter, req.verseNumber)
            val verses = remoteSelectedVerses(
                resolved = resolved,
                request = req,
                translationFileName = appSettings.bibleSettings.translationList().firstOrNull()?.fileName.orEmpty(),
                bibleAbbreviation = primaryBible?.getBibleAbbreviation() ?: "",
                bibleName = primaryBible?.getBibleTitle() ?: "",
            )

            presenterManager.setSelectedVerses(verses)
            presenterManager.setPresentingMode(Presenting.BIBLE)
            presenterManager.setShowPresenterWindow(true)
            if (bookIndex >= 0) {
                // Capture the full span the client asked for: parse req.verseRange ("1-3", "2,4,5")
                // and take its max as the end, rather than hardcoding null (which dropped the range).
                val verseEnd = parseVerseRangeEnd(req.verseRange, req.verseNumber)
                bibleViewModel.logLiveReference(
                    displayBookIndex = bookIndex,
                    chapter    = req.chapter,
                    verseStart = req.verseNumber,
                    verseEnd   = verseEnd,
                    source     = "remote",
                    autoFollow = bibleViewModel.autoFollowEnabled.value,
                )
            }
        }
    }
}

/**
 * The four content types a remote can hand to a tab.
 *
 * Each is the same shape — take the item, then bring its tab forward — and each exists because a
 * remote go-live only flips the presenting mode; the tab still has to load the real content.
 */
@Composable
private fun RemoteTabSelectionEffects(
    onSongItemSelected: (ScheduleItem.SongItem) -> Unit,
    onSongItemVersionBump: () -> Unit,
    onPictureItemSelected: (ScheduleItem.PictureItem) -> Unit,
    onPresentationItemSelected: (ScheduleItem.PresentationItem) -> Unit,
    onMediaItemSelected: (ScheduleItem.MediaItem) -> Unit,
    onSelectTab: (Tabs) -> Unit,
    remoteSelectSongFlow: Flow<ScheduleItem.SongItem>? = null,
    remoteSelectPictureFlow: Flow<ScheduleItem.PictureItem>? = null,
    remoteSelectPresentationFlow: Flow<ScheduleItem.PresentationItem>? = null,
    remoteSelectMediaFlow: Flow<ScheduleItem.MediaItem>? = null,
) {
    LaunchedEffect(remoteSelectSongFlow) {
        remoteSelectSongFlow?.collect { songItem ->
            onSongItemSelected(songItem)
            onSongItemVersionBump()
            onSelectTab(Tabs.SONGS)
        }
    }
    LaunchedEffect(remoteSelectPictureFlow) {
        remoteSelectPictureFlow?.collect { pictureItem ->
            onPictureItemSelected(pictureItem)
            onSelectTab(Tabs.PICTURES)
        }
    }
    LaunchedEffect(remoteSelectPresentationFlow) {
        remoteSelectPresentationFlow?.collect { presentationItem ->
            onPresentationItemSelected(presentationItem)
            onSelectTab(Tabs.PRESENTATION)
        }
    }
    LaunchedEffect(remoteSelectMediaFlow) {
        remoteSelectMediaFlow?.collect { mediaItem ->
            onMediaItemSelected(mediaItem)
            onSelectTab(Tabs.MEDIA)
        }
    }
}
