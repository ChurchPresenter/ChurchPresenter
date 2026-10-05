package org.churchpresenter.app.churchpresenter.remote

import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.server.LiveStateDto
import org.churchpresenter.settings.LinkLayers
import org.churchpresenter.sharedui.models.Presenting

/**
 * What goes live on this follower for a change of [mode] on the primary. A primary that says what
 * is on air ([LiveStateDto.liveSlide], [LiveStateDto.overlays]) is followed exactly: its slide goes
 * live when this change is that slide's, an overlay goes up when this change is that overlay's, and
 * an overlay it took down comes down here. An older primary says only [mode], which goes live as
 * it always did. Layers this follower does not follow are left as they are -- though its own
 * overlays still come down when a followed slide goes live, as going live with a slide does.
 */
internal fun followAir(
    state: LiveStateDto,
    mode: Presenting,
    presenterManager: PresenterManager,
    follows: (Presenting) -> Boolean,
) {
    val overlays = state.overlays?.mapNotNull { runCatching { Presenting.valueOf(it) }.getOrNull() }
    if (overlays == null) {
        if (follows(mode)) presenterManager.setPresentingMode(mode)
        return
    }
    val slide = state.liveSlide?.let { runCatching { Presenting.valueOf(it) }.getOrNull() }
    if (mode == slide && follows(slide) && presenterManager.slideContent.value != slide) {
        presenterManager.setPresentingMode(slide)
    }
    if (mode in overlays && follows(mode)) presenterManager.setPresentingMode(mode)
    (presenterManager.overlays.value - overlays.toSet()).filter(follows).forEach(presenterManager::clearOverlay)
}

/** Which of `LinkLayers` [mode] is on, for a follower choosing what to mirror. */
internal fun linkLayerOf(mode: Presenting): String = when (mode) {
    Presenting.MEDIA -> LinkLayers.MEDIA
    Presenting.LOWER_THIRD -> LinkLayers.LOWER_THIRD
    Presenting.STT -> LinkLayers.CAPTIONS
    Presenting.ANNOUNCEMENTS -> LinkLayers.ANNOUNCEMENTS
    else -> LinkLayers.SLIDE
}
