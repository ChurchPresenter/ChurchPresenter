package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.liveshow.Layer
import org.churchpresenter.sharedui.models.Presenting

/**
 * Takes [layer] off air and leaves every other layer up: the slide or media content, one
 * overlay, or one of [liveShow]'s layers. A layer with nothing on it is left as it is; the
 * background follows the slide's content, so it is not cleared on its own.
 */
fun PresenterManager.clearLayer(layer: Layer) {
    when (layer) {
        Layer.CAPTIONS -> clearOverlay(Presenting.STT)
        Layer.GRAPHICS -> clearOverlay(Presenting.LOWER_THIRD)
        Layer.ANNOUNCEMENTS -> clearOverlay(Presenting.ANNOUNCEMENTS)
        Layer.SLIDE, Layer.MEDIA ->
            if (program.value[layer]?.content == slideContent.value && slideContent.value != Presenting.NONE) {
                putSlide(Presenting.NONE, lastLive = overlays.value.lastOrNull() ?: Presenting.NONE)
            }
        Layer.BACKGROUND -> Unit
        else -> liveShow.clear(layer)
    }
}
