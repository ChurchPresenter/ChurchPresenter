package org.churchpresenter.slides.viewmodel

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.SlidesOutput
import org.churchpresenter.slides.data.nextVisibleIndex
import java.io.File
import java.util.UUID

/** Moving through the loaded pictures. */
interface PictureNavigation {
    /**
     * A copy of the images taken under the list's lock, for a caller that needs to walk the whole
     * list.
     *
     * Iterating the [androidx.compose.runtime.snapshots.SnapshotStateList] directly -- `toList()`,
     * a `for` loop, anything that takes its iterator -- throws `ConcurrentModificationException`
     * the moment one of the three writers changes it mid-walk, and on the event thread that is
     * fatal. Reading a single index cannot fail that way, which is why the other callers are fine;
     * only a whole-list read needs this.
     */
    fun imagesSnapshot(): List<File>

    /** [onInstanceLinkSendNext] — Instance Link Controller mode, non-null only when connected and
     *  controlling. Invoked unconditionally (even when this Controller's own images are empty,
     *  which is the normal case — Controller mode doesn't mirror the primary's content) so next/prev
     *  still reaches the primary's own currently-live folder. See Constants.WS_CMD_NEXT_PICTURE. */
    fun nextImage(onInstanceLinkSendNext: (() -> Unit)? = null)

    fun previousImage(onInstanceLinkSendPrevious: (() -> Unit)? = null)

    fun selectImage(index: Int)

    /** Selects the loaded image at [path]; blank, or not in this folder, changes nothing. */
    fun selectImagePath(path: String)

    fun moveImage(from: Int, to: Int)

    fun getCurrentImageFile(): File?
}

/** Putting the selected picture on the outputs. */
interface PicturesPresenting {
    /**
     * Presents the current image in the presenter window.
     *
     * [onInstanceLinkSendProject] — Instance Link Controller mode, non-null only when connected and
     * controlling. Always sends the whole folder via WS_CMD_PROJECT (never the narrower
     * WS_CMD_SELECT_PICTURE): unlike Bible/Songs, the primary only recognizes a `folderId` it
     * assigned itself when the folder was added to *its own* schedule — since `addPicture` there
     * generates a fresh id rather than preserving one a client sent, a Controller has no reliable way
     * to predict it, so every go-live goes through the schedule-add-and-present path instead.
     *
     * [onWentLive] is the folder as a schedule row, reported as it goes on screen -- see
     * `LiveDurationLog`.
     */
    fun goLive(
        presenterManager: SlidesOutput,
        onInstanceLinkSendProject: ((ScheduleItem) -> Unit)? = null,
        onWentLive: ((ScheduleItem) -> Unit)? = null,
    )

    /**
     * Returns folder data for adding to the schedule, or null if no folder is selected.
     * The caller is responsible for passing this to ScheduleViewModel.
     */
    fun getScheduleData(): Triple<String, String, Int>?

    /** Syncs the currently selected image with the presenter if pictures are being presented. */
    fun syncWithPresenter(presenterManager: SlidesOutput)
}

/** Next, Previous, picking and reordering, over [PicturesState] and the cue in [playback]. */
internal class PictureNavigator(
    private val state: PicturesState,
    private val playback: PicturePlayback,
) : PictureNavigation {

    override fun imagesSnapshot(): List<File> = state.imagesSnapshot()

    override fun nextImage(onInstanceLinkSendNext: (() -> Unit)?) {
        val images = state.images
        if (images.isNotEmpty()) {
            val current = state.selectedImageIndex.value
            val count = images.size
            val hidden = state.hiddenIndexes()
            val ahead = nextVisibleIndex(current, 1, count, hidden, wrap = false)
            val wrapped = if (ahead == null && state.isLooping.value && playback.hasAnotherPass()) {
                nextVisibleIndex(current, 1, count, hidden, wrap = true)
            } else {
                null
            }
            if (ahead != null) {
                state.selectedImageIndex.value = ahead
            } else if (wrapped != null) {
                playback.passCompleted()
                state.selectedImageIndex.value = wrapped
            } else {
                // Stop playing if at the end and not looping — or after the pass a cue asked for.
                playback.endRun()
            }
        }
        onInstanceLinkSendNext?.invoke()
    }

    override fun previousImage(onInstanceLinkSendPrevious: (() -> Unit)?) {
        if (state.images.isNotEmpty()) {
            nextVisibleIndex(state.selectedImageIndex.value, -1, state.images.size, state.hiddenIndexes(), wrap = true)
                ?.let { state.selectedImageIndex.value = it }
        }
        onInstanceLinkSendPrevious?.invoke()
    }

    override fun selectImage(index: Int) {
        if (index in state.images.indices) {
            state.selectedImageIndex.value = index
        }
    }

    override fun selectImagePath(path: String) {
        if (path.isBlank()) return
        selectImage(state.images.indexOfFirst { it.absolutePath == path })
    }

    override fun moveImage(from: Int, to: Int) {
        if (from == to) return
        val currentFile = getCurrentImageFile()
        // Both indices are re-checked under the lock: a watcher can remove a file between the
        // caller reading these positions off the grid and the move landing.
        val moved = synchronized(state.imagesLock) {
            val images = state.images
            if (from !in images.indices || to !in images.indices) {
                false
            } else {
                images.add(to, images.removeAt(from))
                true
            }
        }
        if (!moved) return
        state.imageOrderVersion.value++
        currentFile?.let { file ->
            val newIndex = synchronized(state.imagesLock) { state.images.indexOf(file) }
            if (newIndex >= 0) state.selectedImageIndex.value = newIndex
        }
    }

    override fun getCurrentImageFile(): File? = state.currentImage()
}

/** Going live with the selected picture, and keeping a live output in step with it. */
internal class PicturesLive(private val state: PicturesState) : PicturesPresenting {

    override fun goLive(
        presenterManager: SlidesOutput,
        onInstanceLinkSendProject: ((ScheduleItem) -> Unit)?,
        onWentLive: ((ScheduleItem) -> Unit)?,
    ) {
        val currentImage = state.currentImage() ?: return
        presenterManager.setSelectedImagePath(currentImage.absolutePath)
        val nextIndex = state.selectedImageIndex.value + 1
        presenterManager.setNextImagePath(state.images.getOrNull(nextIndex)?.absolutePath)
        presenterManager.setPresentingMode(Presenting.PICTURES)
        presenterManager.setShowPresenterWindow(true)
        val row = getScheduleData()?.let { (folderPath, folderName, imageCount) ->
            ScheduleItem.PictureItem(
                id = UUID.randomUUID().toString(),
                folderPath = folderPath,
                folderName = folderName,
                imageCount = imageCount,
            )
        }
        if (row != null) {
            onWentLive?.invoke(row)
            onInstanceLinkSendProject?.invoke(row)
        }
    }

    override fun getScheduleData(): Triple<String, String, Int>? {
        val folder = state.selectedFolder.value ?: return null
        return Triple(folder.absolutePath, folder.name, state.images.size)
    }

    override fun syncWithPresenter(presenterManager: SlidesOutput) {
        val anyScreenOnPictures = presenterManager.isLive(Presenting.PICTURES) ||
            presenterManager.picturesCued ||
            presenterManager.screenLocks.value.values.any { it == Presenting.PICTURES }
        if (anyScreenOnPictures && state.images.isNotEmpty()) {
            val currentImage = state.currentImage()
            if (currentImage != null) {
                presenterManager.setSelectedImagePath(currentImage.absolutePath)
                val nextIndex = state.selectedImageIndex.value + 1
                presenterManager.setNextImagePath(state.images.getOrNull(nextIndex)?.absolutePath)
            }
        }
    }
}
