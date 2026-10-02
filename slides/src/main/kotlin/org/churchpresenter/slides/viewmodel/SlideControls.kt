package org.churchpresenter.slides.viewmodel

import org.churchpresenter.slides.data.HiddenItemsStore
import org.churchpresenter.slides.data.firstVisibleIndex
import org.churchpresenter.slides.data.nextVisibleIndex
import java.io.File

/** Moving through the selected deck's slides. */
interface SlideNavigation {
    /** [onInstanceLinkSendNext] — Instance Link Controller mode, non-null only when connected and
     *  controlling. Invoked unconditionally (even when this Controller's own slides are empty,
     *  which is the normal case — Controller mode doesn't mirror the primary's content) so next/prev
     *  still reaches the primary's own currently-live presentation. See Constants.WS_CMD_NEXT_SLIDE. */
    fun nextSlide(onInstanceLinkSendNext: (() -> Unit)? = null)

    fun previousSlide(onInstanceLinkSendPrevious: (() -> Unit)? = null)

    /** Shows slide [index] -- hidden or not, since picking one is deliberate. */
    fun selectSlide(index: Int)

    /** Reads and resets the backward-navigation flag — call exactly once per slide-index change. */
    fun consumeEnteredViaPreviousSlide(): Boolean
}

/** What starts and stops the presentation slideshow. */
interface SlidePlaybackControls {
    /** A cue asking for this deck to play -- see `PicturesViewModel.requestPlayback` for why the file matters. */
    fun requestPlayback(plays: Int, filePath: String? = null)

    fun togglePlayPause()
}

/** The hidden slides of the selected deck (#676), remembered per file in [hiddenStore]. */
internal class HiddenSlides(
    private val state: PresentationState,
    private val hiddenStore: HiddenItemsStore,
) {
    /** Hides slide [index] of the selected presentation, or shows it again. */
    fun toggle(index: Int) {
        val path = state.selectedPresentation.value?.absolutePath ?: return
        val hidden = state.hiddenSlides.value
        state.hiddenSlides.value = if (index in hidden) hidden - index else hidden + index
        hiddenStore.setHiddenSlides(path, state.hiddenSlides.value)
    }

    /** Starts the selected deck's hidden slides from what was remembered for [file]. */
    fun loadFor(file: File) {
        state.hiddenSlides.value = hiddenStore.hiddenSlides(file.absolutePath)
        state.pickFirstShownSlide = true
    }

    /** Moves off a hidden slide 0 once a shown one has loaded; see [PresentationState.pickFirstShownSlide]. */
    fun settleOnShownSlide() {
        if (!state.pickFirstShownSlide) return
        val hidden = state.hiddenSlides.value
        if (state.selectedSlideIndex.value !in hidden) {
            state.pickFirstShownSlide = false
            return
        }
        val shown = state.slideFiles.indices.firstOrNull { it !in hidden } ?: return
        state.selectedSlideIndex.value = shown
        state.pickFirstShownSlide = false
    }
}

/** A calendar cue's "play N times", and the passes counted against it. */
internal class SlidePlayback(private val state: PresentationState) : SlidePlaybackControls {

    /**
     * How many passes a calendar cue asked for: 0 keeps going, N stops after the Nth.
     * The tab's own Loop toggle is 0.
     */
    private var passesWanted = 0
    private var passesDone = 0

    /**
     * A calendar cue's "play N times". Slides render after
     * [PresentationViewModel.selectPresentation] returns, and the playing flag set before they exist
     * would advance nothing, so this is applied once slides have content — see [applyPendingPlayback].
     */
    private var pendingPlays: Int? = null

    /** The deck [pendingPlays] was asked for, so the request cannot land on a different one. */
    private var pendingFile: String? = null

    override fun requestPlayback(plays: Int, filePath: String?) {
        pendingPlays = plays
        pendingFile = filePath
        applyPendingPlayback()
    }

    override fun togglePlayPause() {
        state.isPlaying.value = !state.isPlaying.value
    }

    fun applyPendingPlayback() {
        val plays = pendingPlays ?: return
        val wanted = pendingFile
        if (wanted != null && state.selectedPresentation.value?.absolutePath != wanted) return
        if (state.slideFiles.isEmpty()) return
        pendingPlays = null
        pendingFile = null
        passesWanted = plays
        passesDone = 0
        state.isLooping.value = plays != 1
        state.selectedSlideIndex.value = firstVisibleIndex(state.slideFiles.size, state.hiddenSlides.value)
        state.isPlaying.value = true
    }

    /**
     * Whether another pass through the deck is still owed.
     *
     * A cue asking for N plays stops after the Nth; the tab's own Loop toggle asks for 0, which
     * means "keep going" and never runs out.
     */
    fun hasAnotherPass(): Boolean = passesWanted == 0 || passesDone + 1 < passesWanted

    /** Counts a wrap back to the first slide as one pass done. */
    fun passCompleted() {
        passesDone++
    }

    /** Stops at the end of the run — not looping, or after the passes a cue asked for. */
    fun endRun() {
        state.isPlaying.value = false
        passesWanted = 0
        passesDone = 0
    }
}

/** Next, Previous and picking a slide, over [PresentationState] and the cue in [playback]. */
internal class SlideNavigator(
    private val state: PresentationState,
    private val playback: SlidePlayback,
) : SlideNavigation {

    override fun nextSlide(onInstanceLinkSendNext: (() -> Unit)?) {
        state.enteredViaPreviousSlide.value = false
        state.pickFirstShownSlide = false
        val current = state.selectedSlideIndex.value
        val count = state.slideFiles.size
        val hidden = state.hiddenSlides.value
        val ahead = nextVisibleIndex(current, 1, count, hidden, wrap = false)
        val wrapped = if (ahead == null && state.isLooping.value && playback.hasAnotherPass()) {
            nextVisibleIndex(current, 1, count, hidden, wrap = true)
        } else {
            null
        }
        when {
            ahead != null -> state.selectedSlideIndex.value = ahead
            wrapped != null -> {
                playback.passCompleted()
                state.selectedSlideIndex.value = wrapped
            }
            else -> playback.endRun()
        }
        onInstanceLinkSendNext?.invoke()
    }

    override fun previousSlide(onInstanceLinkSendPrevious: (() -> Unit)?) {
        state.pickFirstShownSlide = false
        val count = state.slideFiles.size
        val hidden = state.hiddenSlides.value
        val current = state.selectedSlideIndex.value
        val target = nextVisibleIndex(current, -1, count, hidden, wrap = false)
            ?: if (state.isLooping.value) nextVisibleIndex(current, -1, count, hidden, wrap = true) else null
        if (target != null) {
            state.enteredViaPreviousSlide.value = true
            state.selectedSlideIndex.value = target
        }
        onInstanceLinkSendPrevious?.invoke()
    }

    override fun selectSlide(index: Int) {
        if (index in state.slideFiles.indices) {
            state.enteredViaPreviousSlide.value = false
            state.pickFirstShownSlide = false
            state.selectedSlideIndex.value = index
        }
    }

    override fun consumeEnteredViaPreviousSlide(): Boolean {
        val value = state.enteredViaPreviousSlide.value
        state.enteredViaPreviousSlide.value = false
        return value
    }
}
