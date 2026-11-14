package org.churchpresenter.canvas

/**
 * Runs [work] on a daemon thread named [name] -- for a stop that waits on another process to exit
 * (a browser, ffmpeg), reached when a source is removed on the event thread.
 */
internal fun runInBackground(name: String, work: () -> Unit) {
    Thread(work, name).apply { isDaemon = true }.start()
}
