package org.churchpresenter.slides.viewmodel

import org.churchpresenter.slides.data.HiddenItemsStore
import org.churchpresenter.slides.data.firstVisibleIndex
import java.io.File

/** Hiding pictures from Next, Previous and the slideshow (#676). */
interface PictureHiding {
    /**
     * The names of the pictures in the selected folder the operator has hidden (#676): Next, Previous
     * and the slideshow pass over them, but a click still shows one. Remembered per folder.
     */
    val hiddenImageNames: Set<String>

    fun isHidden(file: File): Boolean

    /** Hides [file], or shows it again. */
    fun toggleHidden(file: File)
}

/** What starts and stops the pictures slideshow. */
interface PicturePlaybackControls {
    /**
     * A cue asking for this folder to play, from wherever the cue fired.
     *
     * [folderPath] is what makes it reliable. Without it the request was applied the moment it
     * arrived if *any* folder was loaded -- so a cue firing while last week's folder was still
     * open started that one, cleared the request, and the folder it was actually for arrived
     * stopped, which looked exactly like the slideshow ignoring the settings.
     */
    fun requestPlayback(plays: Int, folderPath: String? = null)

    fun togglePlayPause()
}

/** The hidden pictures of the selected folder, remembered per folder in [hiddenStore]. */
internal class HiddenPictures(
    private val state: PicturesState,
    private val hiddenStore: HiddenItemsStore,
) : PictureHiding {

    override val hiddenImageNames: Set<String>
        get() = state.hiddenNames.value

    override fun isHidden(file: File): Boolean = file.name in state.hiddenNames.value

    override fun toggleHidden(file: File) {
        val folder = state.selectedFolder.value?.absolutePath ?: return
        val hidden = state.hiddenNames.value
        state.hiddenNames.value = if (file.name in hidden) hidden - file.name else hidden + file.name
        hiddenStore.setHiddenPictures(folder, state.hiddenNames.value)
    }

    /** Starts [folder]'s hidden pictures from what was remembered for it. */
    fun loadFor(folder: File) {
        state.hiddenNames.value = hiddenStore.hiddenPictures(folder.absolutePath)
    }
}

/** A calendar cue's "play N times", and the passes counted against it. */
internal class PicturePlayback(private val state: PicturesState) : PicturePlaybackControls {

    /**
     * How many passes a calendar cue asked for: 0 keeps going, N stops after the Nth.
     * The tab's own Loop toggle is 0.
     */
    private var passesWanted = 0
    private var passesDone = 0

    /**
     * A calendar cue's "play N times", remembered until the folder it fired for has loaded —
     * [PicturesViewModel.selectFolder] clears the playing flag on the way in, so setting it here
     * directly would be undone a moment later. Consumed by [applyPendingPlayback].
     */
    private var pendingPlays: Int? = null

    /** The folder [pendingPlays] was asked for, so the request cannot land on a different one. */
    private var pendingFolder: String? = null

    override fun requestPlayback(plays: Int, folderPath: String?) {
        pendingPlays = plays
        pendingFolder = folderPath
        applyPendingPlayback()
    }

    override fun togglePlayPause() {
        // The operator taking over ends a cue's request: without this, re-opening that folder
        // later would start it playing again on its own.
        clearPlaybackRequest()
        state.isPlaying.value = !state.isPlaying.value
    }

    fun applyPendingPlayback() {
        val plays = pendingPlays ?: return
        val wanted = pendingFolder
        // Waits for the folder it was meant for, and for that folder to have images: a request
        // spent on an empty list would set `isPlaying` with nothing to advance through.
        if (wanted != null && state.selectedFolder.value?.absolutePath != wanted) return
        if (state.images.isEmpty()) return
        // The request is deliberately *not* consumed here. Selecting a folder clears playback on
        // the way in, and the tab re-selects the folder a cue asked for right after the cue has
        // started it -- so a request spent on the first application was wiped a moment later and
        // the slideshow sat on image 1. It stays armed for its folder until the run ends or the
        // operator takes over; see [clearPlaybackRequest].
        passesWanted = plays
        passesDone = 0
        state.isLooping.value = plays != 1
        state.selectedImageIndex.value = firstVisibleIndex(state.images.size, state.hiddenIndexes())
        state.isPlaying.value = true
    }

    /** Whether wrapping round is still owed: the Loop toggle never runs out, a cue's N passes do. */
    fun hasAnotherPass(): Boolean = passesWanted == 0 || passesDone + 1 < passesWanted

    /** Counts a wrap back to the start as one pass done. */
    fun passCompleted() {
        passesDone++
    }

    /** Stops at the end of the run — not looping, or after the pass a cue asked for. */
    fun endRun() {
        clearPlaybackRequest()
        state.isPlaying.value = false
        passesWanted = 0
        passesDone = 0
    }

    /** Forgets a cue's play request: the run it asked for is over, or the operator has taken over. */
    private fun clearPlaybackRequest() {
        pendingPlays = null
        pendingFolder = null
    }
}
