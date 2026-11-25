package org.churchpresenter.app.churchpresenter

import org.churchpresenter.canvas.ResourceCensus
import org.churchpresenter.sharedui.utils.RunRecord
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.sharedui.utils.addGuardedShutdownHook
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * At shutdown, what this run tells the next launch's ping about itself: how long it lasted, how long
 * an output was live and how many live stalls passed half a second. Together they say how often a
 * service stalls, which decides whether outputs move to a process of their own (docs/SHOW_CONTROL.md,
 * Output isolation).
 */
internal fun recordRunAtShutdown(startedAt: Long) = addGuardedShutdownHook("session") {
    UsageEvents.recordRun(runSoFar(startedAt))
    // Shutdown is when the high-water marks are final. Reports only when they are higher than a
    // scene can account for — see ResourceCensus, and why counts rather than CPU or heap.
    ResourceCensus.reportIfLeaky()
}

/** This run as it stands: how long it has lasted, been live, and stalled while live. */
private fun runSoFar(startedAt: Long) = RunRecord(
    sessionMinutes = ((System.currentTimeMillis() - startedAt) / MILLIS_PER_MINUTE).toInt(),
    liveMinutes = uiStallReporter.liveMinutes(),
    liveStalls = uiStallReporter.liveStallsPastGate(),
)

/**
 * Saves this run once a minute as well as at shutdown: a crash, a forced quit or a machine switched
 * off at the wall runs no shutdown hook, and that run would otherwise report nothing. The next launch
 * then reports the run as it stood at its last minute.
 */
internal fun recordRunEveryMinute(startedAt: Long) {
    Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "run-record").apply { isDaemon = true } }
        .scheduleAtFixedRate({ UsageEvents.recordRun(runSoFar(startedAt)) }, 1, 1, TimeUnit.MINUTES)
}
