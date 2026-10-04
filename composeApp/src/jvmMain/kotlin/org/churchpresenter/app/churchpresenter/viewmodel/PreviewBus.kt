package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import java.io.File

/** The content that goes through Preview while preview mode is on; everything else still goes straight to air. */
internal val CUEABLE_MODES = setOf(Presenting.PICTURES, Presenting.LOWER_THIRD, Presenting.ANNOUNCEMENTS)

/**
 * The Preview bus: what is cued, not yet on air, and Take, which puts it on air.
 *
 * Preview is a second [PresenterManager], [manager], so it is drawn by the same renderer as every
 * output and can never show something Program would draw differently. With preview mode off --
 * the default -- nothing reaches it and every go-live goes straight to [program], as it always did.
 *
 * With it on, a **new item** goes to Preview and waits for [take]; **stepping** within the item
 * already on Program (the next picture in the same folder) still goes straight to air. Only
 * [CUEABLE_MODES] are cued; the rest still go live directly.
 */
class PreviewBus internal constructor(private val program: PresenterManager) {

    /** What Preview shows. Nothing on air reads it. */
    val manager: PresenterManager by lazy { PresenterManager(showPresenterWindowInitially = false) }

    private val _enabled = mutableStateOf(false)

    /** Whether preview mode is on. */
    val enabled: State<Boolean> = _enabled

    /** Turns preview mode on or off; off empties Preview. */
    fun setEnabled(on: Boolean) {
        if (_enabled.value == on) return
        _enabled.value = on
        if (!on) clear()
    }

    /** Whether [mode] is cued on Preview, waiting for [take]. */
    fun isCued(mode: Presenting): Boolean = _enabled.value && manager.isLive(mode)

    /** Whether anything is cued. */
    val anythingCued: Boolean get() = _enabled.value && manager.anythingLive

    /**
     * Where a new item of [mode] goes: Preview while preview mode is on and [mode] is one of
     * [CUEABLE_MODES], else Program.
     */
    internal fun forNewItem(mode: Presenting): PresenterManager =
        if (_enabled.value && mode in CUEABLE_MODES) manager else program

    /**
     * A go-live of [mode] whose content comes separately (a schedule row, a tab's Go Live): cued on
     * Preview when it is a new item, else on Program as before. Already cued, or [mode] already on
     * Program while its content is being stepped, it changes nothing here.
     */
    fun present(mode: Presenting) {
        when {
            forNewItem(mode) === program -> program.setPresentingMode(mode)
            // A timer's text ticks on air; its go-live goes there with it.
            mode == Presenting.ANNOUNCEMENTS && program.announcementTickerLive.value -> program.setPresentingMode(mode)
            manager.isLive(mode) -> Unit
            mode == Presenting.PICTURES && program.isLive(mode) -> Unit
            else -> manager.setPresentingMode(mode)
        }
    }

    /** Where a picture goes: see [present]. A picture from the folder already on air is a step. */
    internal fun forPicture(path: String?): PresenterManager = when {
        forNewItem(Presenting.PICTURES) === program -> program
        manager.isLive(Presenting.PICTURES) -> manager
        program.isLive(Presenting.PICTURES) && sameFolder(path, program.selectedImagePath.value) -> program
        else -> manager
    }

    /** Puts a lower third up, or cues it while preview mode is on. */
    fun showLowerThird(json: String, pauseAtFrame: Boolean, pauseFrame: Float, pauseDurationMs: Long, name: String) {
        val target = forNewItem(Presenting.LOWER_THIRD)
        target.setLottieContent(json, pauseAtFrame, pauseFrame, pauseDurationMs, name)
        target.setPresentingMode(Presenting.LOWER_THIRD)
        program.setShowPresenterWindow(true)
    }

    /**
     * Puts what is cued on air -- the slide first, as going live with one takes Program's overlays
     * down, then each overlay in the order it was cued -- and empties Preview.
     */
    fun take() {
        if (!anythingCued) return
        val slide = manager.presentingMode.value
        if (slide != Presenting.NONE) transfer(slide)
        manager.overlays.value.forEach(::transfer)
        program.setShowPresenterWindow(true)
        clear()
    }

    /** Empties Preview. */
    fun clear() {
        if (manager.anythingLive) manager.setPresentingMode(Presenting.NONE)
    }

    private fun transfer(mode: Presenting) {
        when (mode) {
            Presenting.PICTURES -> {
                program.setSelectedImagePath(manager.selectedImagePath.value)
                program.setNextImagePath(manager.nextImagePath.value)
            }
            Presenting.LOWER_THIRD -> program.setLottieContent(
                manager.lottieJsonContent.value,
                manager.lottiePauseAtFrame.value,
                manager.lottiePauseFrame.value,
                manager.lottiePauseDurationMs.value,
                manager.currentLowerThirdName.value,
            )
            Presenting.ANNOUNCEMENTS -> program.setAnnouncementText(manager.announcementText.value)
            else -> return
        }
        program.setPresentingMode(mode)
    }
}

private fun sameFolder(a: String?, b: String?): Boolean =
    a != null && b != null && File(a).parentFile == File(b).parentFile

/** [this] with preview mode turned [on] or off. */
internal fun AppSettings.withPreviewMode(on: Boolean): AppSettings =
    copy(projectionSettings = projectionSettings.copy(previewModeEnabled = on))
