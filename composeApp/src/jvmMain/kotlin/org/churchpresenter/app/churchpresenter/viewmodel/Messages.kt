package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.sharedui.models.Presenting

/**
 * Puts [message] up (`docs/SHOW_CONTROL.md`, Messages): every other layer comes down at once, then
 * the message goes up alone. It comes down when its duration runs out ([MessageExpiry]), when it is
 * cleared, or when slide content goes live.
 */
fun PresenterManager.showMessage(message: Cue.Message) {
    overlays.value.toList().forEach(::clearOverlay)
    putSlide(Presenting.NONE, lastLive = Presenting.MESSAGE)
    liveShow.set(message)
    onLiveStateChanged?.invoke(this, Presenting.MESSAGE)
}

/** The message on air, or null. */
val PresenterManager.messageOnAir: Cue.Message?
    get() = liveShow.program.value[Layer.MESSAGES] as? Cue.Message

/** Takes the message down, leaving whatever else is up. */
fun PresenterManager.clearMessage() {
    if (messageOnAir == null) return
    clearLayer(Layer.MESSAGES)
    onLiveStateChanged?.invoke(this, Presenting.MESSAGE)
}

/** Takes the message on air down once its duration has run out; one cleared or replaced first is left alone. */
@Composable
internal fun MessageExpiry(presenterManager: PresenterManager) {
    val message = presenterManager.messageOnAir
    LaunchedEffect(message) {
        val seconds = message?.durationSeconds?.takeIf { it > 0 } ?: return@LaunchedEffect
        delay(seconds * MILLIS_PER_SECOND)
        if (presenterManager.messageOnAir == message) presenterManager.clearMessage()
    }
}

private const val MILLIS_PER_SECOND = 1000L
