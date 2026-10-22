package org.churchpresenter.server

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.churchpresenter.settings.utils.Constants

/**
 * [CompanionServer.start], off the caller's thread. A start probes ports and binds Netty, which the
 * event thread must not wait on; [CompanionServer.isRunning] and [CompanionServer.serverUrl] say
 * when it is up. The returned job completes once it has run, after every request made before it.
 */
fun CompanionServer.requestStart(port: Int = Constants.SERVER_DEFAULT_PORT, hostOverride: String = ""): Job =
    lifecycleScope.launch { start(port, hostOverride) }

/**
 * [CompanionServer.stop], off the caller's thread. A stop waits up to its grace and timeout for
 * Netty to close — over a second on the app's defaults, which froze the Options dialog when it ran
 * on the event thread (CHURCH-PRESENTER-DESKTOP-EK).
 */
fun CompanionServer.requestStop(): Job = lifecycleScope.launch { stop() }

/** [CompanionServer.stop] then [CompanionServer.start], off the caller's thread, with nothing between. */
fun CompanionServer.requestRestart(port: Int = Constants.SERVER_DEFAULT_PORT, hostOverride: String = ""): Job =
    lifecycleScope.launch {
        stop()
        start(port, hostOverride)
    }
