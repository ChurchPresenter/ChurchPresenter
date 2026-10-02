package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel

/**
 * One layer of a crossfade: the page it draws and how opaque it is.
 *
 * A layer is an object of its own, composed under its own key, so a page keeps the layer -- and the
 * alpha -- it already had when the next one arrives. The crossfade used to keep a "current" and a
 * "previous" slot instead, moving the outgoing page from one to the other and dropping the current
 * slot's alpha to 0 in the same write. The alpha is read while drawing and the slot's contents only
 * while composing, so a frame drawn between the two showed the outgoing page at the incoming page's
 * alpha: one blank frame at the start of every crossfade, read on screen as the words fading twice.
 */
@Stable
internal class FadeLayer<T>(page: T, alpha: Float) {
    var page by mutableStateOf(page)
    var alpha by mutableFloatStateOf(alpha)
}

/**
 * The layers of a fade between pages, oldest first, to draw in that order under `key(layer)`.
 *
 * A [target] the top layer is already showing changes nothing. One [samePage] accepts is drawn in
 * place: a restatement of the page, or a move within it, never fades. Anything else crossfades in
 * over [durationMs] when [crossfade] is on, or replaces the top layer outright when it is not. The
 * queue is conflated, so a fade runs to its end and then goes straight to the newest target rather
 * than through every page skipped past meanwhile.
 */
@Composable
internal fun <T> rememberFadeLayers(
    target: T,
    crossfade: Boolean,
    durationMs: Int,
    samePage: (shown: T, target: T) -> Boolean,
): List<FadeLayer<T>> {
    val layers = remember { mutableStateListOf(FadeLayer(target, 1f)) }
    val pending = remember { Channel<T>(Channel.CONFLATED) }
    val isSamePage by rememberUpdatedState(samePage)
    val isCrossfade by rememberUpdatedState(crossfade)
    val duration by rememberUpdatedState(durationMs)

    LaunchedEffect(target) {
        // Always queued, even when the top layer already shows it: the newest target has to replace
        // one still waiting. Skipping it left the waiting page to play once the fade in flight ended,
        // so going to a slide and straight back during a crossfade landed on the one left.
        pending.send(target)
        // A restatement of the page coming up is drawn at once rather than after the fade.
        val top = layers.last()
        if (top.page != target && isSamePage(top.page, target)) top.page = target
    }

    LaunchedEffect(Unit) {
        for (next in pending) {
            val outgoing = layers.last()
            when {
                outgoing.page == next -> Unit
                isSamePage(outgoing.page, next) -> outgoing.page = next
                !isCrossfade -> outgoing.page = next
                else -> {
                    val incoming = FadeLayer(next, 0f)
                    layers.add(incoming)
                    Animatable(0f).animateTo(1f, tween(durationMillis = duration)) {
                        incoming.alpha = value
                        outgoing.alpha = 1f - value
                    }
                    incoming.alpha = 1f
                    layers.removeAll { it !== incoming }
                }
            }
        }
    }
    return layers
}
