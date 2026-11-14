package org.churchpresenter.liveoutput

import java.awt.EventQueue

/**
 * Runs [work] on a daemon thread named [name] when called on the event thread, and right here
 * otherwise.
 *
 * For a release that waits on something it does not control -- a native sender's destroy, a
 * rasterizer still drawing a slide -- reached from a composable's disposal, where every output
 * window would freeze for as long as it took. A shutdown hook calls the same release off the event
 * thread, so it still finishes before the process exits.
 */
internal fun offEventThread(name: String, work: () -> Unit) {
    if (EventQueue.isDispatchThread()) {
        Thread(work, name).apply { isDaemon = true }.start()
    } else {
        work()
    }
}
