package org.churchpresenter.slides.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File

/**
 * Everything [PicturesViewModel] and its parts read and write, in one place so each part can be its
 * own class without any of them holding a copy.
 *
 * The concurrency rules travel with the state, not with whichever part happens to touch it: every
 * structural change to [images] is made under [imagesLock], and every snapshot write from a
 * background thread is confined to the view model's main dispatcher by the part making it.
 */
internal class PicturesState(appSettings: AppSettings?) {

    val selectedFolder = mutableStateOf<File?>(null)

    /**
     * The primary's own folder path when this folder came from
     * [PicturesViewModel.loadPictureFromRemote], against the [File] it was stored under.
     *
     * Kept as the string the primary sent, because a foreign path must not be re-separated by the
     * local platform: `File("/Volumes/primary-only/Sunday")` renders as `\Volumes\primary-only\Sunday`
     * on a Windows follower. Keyed by the [File] rather than held loose so it cannot outlive the
     * selection it describes — a display path is only ever returned for the exact folder it was
     * recorded against.
     */
    val remoteFolderPath = mutableStateOf<Pair<File, String>?>(null)

    val images: SnapshotStateList<File> = mutableStateListOf()

    /**
     * Guards every structural change to [images], and any index read taken in order to make one.
     *
     * The list is a [SnapshotStateList], which makes a write visible to composition but does not
     * make one atomic against another thread. Writers arrive from three directions: the caller's
     * thread ([PicturesViewModel.loadImagesFromFolder], [PicturesViewModel.clearImages],
     * [PictureNavigator.moveImage]), the download coroutine in
     * [PicturesViewModel.loadPictureFromRemote], and the folder watcher. More than one watcher can be
     * live at a time — [PicturesViewModel.selectFolder] cancels the previous watch job and refills the
     * list immediately, but cancellation is cooperative, so the outgoing watcher can still be inside
     * `pollEvents()` working through a batch of events against a list that has already been emptied
     * and repopulated underneath it.
     *
     * `indexOf` followed by `removeAt(index)` is the shape that fails: the index is read, the list
     * shrinks, and the removal throws `IndexOutOfBoundsException` from the folder watcher — where
     * nothing catches it. It took CI red intermittently (run 31793721082 on `main`, and again on
     * PR #298) as `index: 5, size: 5`, blamed on whichever screenshot test happened to be running
     * when a previous test's leaked watcher woke up.
     */
    val imagesLock = Any()

    val thumbnails: SnapshotStateMap<File, ImageBitmap> = SnapshotStateMap()

    /**
     * Files whose thumbnail could not be decoded, against the reason.
     *
     * The grid draws "Loading…" for any file with no entry in [thumbnails], so before this existed a
     * decode that threw was indistinguishable from one still running — and since the failure was
     * swallowed, the tile said "Loading…" for the rest of the session. A corrupt or truncated image
     * meant a permanent placeholder and no error anywhere.
     *
     * Every file therefore ends up in exactly one of [thumbnails] or here, which is also what lets a
     * test wait for a positive signal instead of for the absence of a label.
     */
    val thumbnailFailures: SnapshotStateMap<File, String> = SnapshotStateMap()

    val hiddenNames = mutableStateOf<Set<String>>(emptySet())
    val selectedImageIndex = mutableStateOf(0)
    val isPlaying = mutableStateOf(false)
    val autoScrollInterval = mutableStateOf(appSettings?.pictureSettings?.autoScrollInterval ?: 5f)
    val isLooping = mutableStateOf(appSettings?.pictureSettings?.isLooping ?: true)
    val transitionDuration = mutableStateOf(appSettings?.pictureSettings?.transitionDuration ?: 500f)
    val animationType = mutableStateOf(
        when (appSettings?.pictureSettings?.animationType) {
            Constants.ANIMATION_FADE -> AnimationType.FADE
            Constants.ANIMATION_SLIDE_LEFT -> AnimationType.SLIDE_LEFT
            Constants.ANIMATION_SLIDE_RIGHT -> AnimationType.SLIDE_RIGHT
            Constants.ANIMATION_NONE -> AnimationType.NONE
            else -> AnimationType.CROSSFADE
        }
    )
    val imageOrderVersion = mutableStateOf(0)

    /** A copy of [images] taken under [imagesLock]; see [PicturesViewModel.imagesSnapshot]. */
    fun imagesSnapshot(): List<File> = synchronized(imagesLock) { images.toList() }

    /** The image at the selected index, read under [imagesLock]. */
    fun currentImage(): File? = synchronized(imagesLock) { images.getOrNull(selectedImageIndex.value) }

    /**
     * Adds [file] unless its path is already listed, answering whether it went in. Caller holds
     * [imagesLock].
     *
     * Compared by `absolutePath` — the exact string `PicturesTab`'s grid uses as its item key —
     * rather than by [File.equals], which compares the *unnormalised* path. Two `File`s naming one
     * picture, one relative and one absolute, are unequal to `equals` and identical as keys, so the
     * membership tests passed and the grid still threw "Key ... was already used".
     */
    fun addUniqueLocked(file: File, index: Int? = null): Boolean {
        if (images.any { it.absolutePath == file.absolutePath }) return false
        if (index != null) images.add(index, file) else images.add(file)
        return true
    }

    /** The positions of the hidden pictures in the current order. */
    fun hiddenIndexes(): Set<Int> {
        val hidden = hiddenNames.value
        if (hidden.isEmpty()) return emptySet()
        return imagesSnapshot().withIndex().filter { it.value.name in hidden }.map { it.index }.toSet()
    }
}
