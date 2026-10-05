package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.sharedui.models.Presenting

/** The props that are up, by id (`docs/SHOW_CONTROL.md`, Props). */
val PresenterManager.propsOnAir: Set<String>
    get() = (liveShow.program.value[Layer.PROPS] as? Cue.Props)?.on.orEmpty()

/**
 * Puts the prop [id] up or takes it down, leaving every other layer -- and every other prop -- as it
 * is. Props stay up while the content changes under them; clearing the display or a message going up
 * takes them all down.
 */
fun PresenterManager.setPropOn(id: String, on: Boolean) {
    val now = propsOnAir
    val next = if (on) now + id else now - id
    if (next == now) return
    if (next.isEmpty()) liveShow.clear(Layer.PROPS) else liveShow.set(Cue.Props(next))
    onLiveStateChanged?.invoke(this, Presenting.PROPS)
}

/** Switches the prop [id] the other way. */
fun PresenterManager.toggleProp(id: String) = setPropOn(id, id !in propsOnAir)
