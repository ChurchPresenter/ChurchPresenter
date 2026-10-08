package org.churchpresenter.liveoutput

import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.ClearGroup

/**
 * The layers an operator can clear one by one, bottom to top: the background follows the slide,
 * and audio is not drawn.
 */
val CLEARABLE_LAYERS: List<Layer> = Layer.entries - Layer.BACKGROUND - Layer.AUDIO

/** The layers [group] names that this build knows, in stack order. */
fun ClearGroup.knownLayers(): List<Layer> =
    CLEARABLE_LAYERS.filter { it.name in layers }

/**
 * Clears the outputs because an operator asked to -- the Clear button, its key or a remote client --
 * which also stops what is still running of any action list; a clear an action asks for does not.
 */
fun PresenterManager.clearFromOperator() {
    operatorClearCount.intValue++
    requestClearDisplay()
}

/** Takes every layer of [group] off air and leaves the rest up -- see [clearLayer]. */
fun PresenterManager.clearGroup(group: ClearGroup) {
    group.knownLayers().forEach(::clearLayer)
}
