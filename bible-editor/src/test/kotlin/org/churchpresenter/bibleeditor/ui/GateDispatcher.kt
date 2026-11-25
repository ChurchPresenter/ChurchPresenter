package org.churchpresenter.bibleeditor.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/** Runs the first [allowed] reads inline, as Unconfined would, and holds every later one until [release]. */
internal class GateDispatcher(private var allowed: Int) : CoroutineDispatcher() {
    private val held = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (allowed > 0) {
            allowed--
            block.run()
        } else {
            held += block
        }
    }

    fun release() {
        allowed = Int.MAX_VALUE
        while (held.isNotEmpty()) held.removeFirst().run()
    }
}
