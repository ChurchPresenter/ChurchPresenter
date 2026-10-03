package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import org.churchpresenter.app.churchpresenter.presenter.PresentationPlayer
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.presenter.PresentationFrame

private const val PLAYER_SETTLE_MS = 100L
private const val FRAME_INTERVAL_MS = 33L

/**
 * Animated presentation playback. The player is a rendering bridge like `LottieFrameStream`: one
 * evaluation per display frame, published here, drawn by every output window's
 * `PresentationPresenter`. Part of [PresenterManager].
 */
interface PresentationPlayback {
    val presentationFrame: State<PresentationFrame?>

    /**
     * Points playback at [deck]/[slideIndex]. Decks with no timing at all (plain PDFs, static
     * exports) clear the player — the static bitmap path with the app's configured slide
     * transition then renders as before. A deck with any timeline or deck-defined transition
     * plays entirely through the player so its own transitions win consistently.
     *
     * [enterAtLastStep] is true only for genuine backward navigation (the operator stepping to
     * the *previous* slide) — real PowerPoint/Keynote show that slide fully built, as the
     * audience last saw it, rather than resetting it to the pre-click state.
     */
    fun presentationShowSlide(deck: Deck, slideIndex: Int, enterAtLastStep: Boolean = false)

    /**
     * Advances one build step of the live animated slide. Identity-guarded: acts only when the
     * player is showing exactly [deck]/[slideIndex] AND that content is actually visible —
     * otherwise the keypress must fall through to plain slide navigation instead of being
     * silently eaten by a player the operator can't see (cleared display, different deck, or a
     * player created ahead of the grid selection).
     * False = caller changes the slide instead.
     */
    fun advancePresentationStep(deck: Deck, slideIndex: Int): Boolean

    /** Steps one build back. Same identity guard; false = caller changes the slide instead. */
    fun rewindPresentationStep(deck: Deck, slideIndex: Int): Boolean

    fun clearPresentationPlayback()

    /**
     * Frame clock body, driven from main.kt (same pattern as the Lottie clock): publishes the
     * evaluated frame on every display frame while a step animates, and idles cheaply when
     * settled or when no animated presentation is live.
     */
    suspend fun runPresentationClock()
}

internal class PresentationPlaybackState(private val context: PresenterContext) : PresentationPlayback {

    private val _presentationFrame = mutableStateOf<PresentationFrame?>(null)
    override val presentationFrame: State<PresentationFrame?> = _presentationFrame

    internal var presentationPlayer: PresentationPlayer? = null

    override fun presentationShowSlide(deck: Deck, slideIndex: Int, enterAtLastStep: Boolean) {
        val deckIsAnimated = deck.slides.any { it.timeline != null || it.transition != null }
        if (!deckIsAnimated) {
            clearPresentationPlayback()
            return
        }
        // Same-slide idempotence: when the player is already showing exactly this slide AND it
        // is visible on the output, keep its build state untouched — re-selecting the live
        // slide's thumbnail, focus-recovery paths, or a repeated Go Live must never restart the
        // slide's animations mid-service. Slide changes, cleared displays and different decks
        // fall through to the full (re)show, where starting from the pre-click state is correct.
        if (steppablePlayer(deck, slideIndex) != null) return
        val player = presentationPlayer?.takeIf { it.deck === deck }
            ?: PresentationPlayer(deck).also { fresh ->
                presentationPlayer?.close()
                presentationPlayer = fresh
            }
        player.showSlide(slideIndex, enterAtLastStep)
        _presentationFrame.value = null // published by the clock once layers are rasterized
    }

    override fun advancePresentationStep(deck: Deck, slideIndex: Int): Boolean =
        steppablePlayer(deck, slideIndex)?.advance(System.nanoTime()) ?: false

    override fun rewindPresentationStep(deck: Deck, slideIndex: Int): Boolean =
        steppablePlayer(deck, slideIndex)?.rewind() ?: false

    internal fun steppablePlayer(deck: Deck, slideIndex: Int): PresentationPlayer? {
        val player = presentationPlayer ?: return null
        if (player.deck !== deck || player.currentSlideIndex != slideIndex) return null
        // A cleared display blanks the mode-driven output — steps would be invisible there.
        // (Screen-lock visibility is the caller's gate, as with all lock-aware behavior.)
        val visibleViaMode = context.presentingMode.value == Presenting.PRESENTATION &&
            !context.clearDisplayRequested.value
        val visibleViaLock = context.screenLocks.value.values.any { it == Presenting.PRESENTATION }
        return if (visibleViaMode || visibleViaLock) player else null
    }

    override fun clearPresentationPlayback() {
        presentationPlayer?.close()
        presentationPlayer = null
        _presentationFrame.value = null
    }

    override suspend fun runPresentationClock() {
        while (true) {
            val player = presentationPlayer
            if (player == null) {
                delay(PLAYER_SETTLE_MS)
                continue
            }
            withFrameNanos { now ->
                if (presentationPlayer === player) {
                    _presentationFrame.value = player.frame(now)
                }
            }
            if (!player.isAnimating(System.nanoTime())) {
                // Settled: keep the last frame, poll for the next advance/slide change.
                delay(FRAME_INTERVAL_MS)
            }
        }
    }
}
