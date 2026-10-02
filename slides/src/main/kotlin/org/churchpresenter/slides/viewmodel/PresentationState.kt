package org.churchpresenter.slides.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.core.models.presentation.PresentationLoadError
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File

/**
 * Everything [PresentationViewModel] and its parts read and write, in one place so each part can be
 * its own class without any of them holding a copy.
 */
internal class PresentationState(appSettings: AppSettings?) {

    val presentations: SnapshotStateList<File> = mutableStateListOf()
    val selectedPresentation = mutableStateOf<File?>(null)

    /**
     * The primary's own file path when this deck came from
     * [PresentationViewModel.loadPresentationFromRemote], against the [File] it was stored under.
     *
     * Kept as the string the primary sent: a foreign path must not be re-separated by the local
     * platform, and `absolutePath` additionally prepends a drive letter (Windows) or the working
     * directory (POSIX, for a path with no leading `/`). Keyed by the [File] so it cannot outlive
     * the selection it describes.
     */
    val remotePresentationPath = mutableStateOf<Pair<File, String>?>(null)

    val slideFiles: SnapshotStateList<File> = mutableStateListOf()
    val totalSlides = mutableStateOf(0)

    /** Incremented each time slides finish loading (fresh render or cache hit). */
    val loadGeneration = mutableStateOf(0)
    val slideNotes: SnapshotStateList<String> = mutableStateListOf()

    /**
     * The parsed deck of the selected presentation (null while loading, for remote-mirrored
     * slides, and on load failure); see [PresentationViewModel.deck].
     */
    val deck = mutableStateOf<Deck?>(null)
    val selectedSlideIndex = mutableStateOf(0)
    val hiddenSlides = mutableStateOf<Set<Int>>(emptySet())

    /**
     * Whether the selection should still move off a hidden first slide as the deck arrives. Slides
     * load one at a time, so the first shown one may not exist yet when the deck is selected; once
     * the operator moves, or a shown slide is selected, this stops.
     */
    var pickFirstShownSlide = false

    /** One-shot: set by Previous; see [PresentationViewModel.consumeEnteredViaPreviousSlide]. */
    val enteredViaPreviousSlide = mutableStateOf(false)

    val isPlaying = mutableStateOf(false)
    val isLoading = mutableStateOf(false)
    val loadError = mutableStateOf<PresentationLoadError?>(null)
    val autoScrollInterval = mutableStateOf(appSettings?.presentationSettings?.autoScrollInterval ?: 5f)
    val isLooping = mutableStateOf(appSettings?.presentationSettings?.isLooping ?: true)
    val transitionDuration = mutableStateOf(appSettings?.presentationSettings?.transitionDuration ?: 500f)
    val animationType = mutableStateOf(
        when (appSettings?.presentationSettings?.animationType) {
            Constants.ANIMATION_FADE -> AnimationType.FADE
            Constants.ANIMATION_SLIDE_LEFT -> AnimationType.SLIDE_LEFT
            Constants.ANIMATION_SLIDE_RIGHT -> AnimationType.SLIDE_RIGHT
            Constants.ANIMATION_NONE -> AnimationType.NONE
            else -> AnimationType.CROSSFADE
        }
    )

    fun clearCurrentSlideState() {
        slideFiles.clear()
        slideNotes.clear()
        deck.value = null
    }
}
