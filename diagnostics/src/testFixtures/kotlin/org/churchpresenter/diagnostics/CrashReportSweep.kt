package org.churchpresenter.diagnostics

import java.io.File

/** A report written in the same second as [CrashReportSweep.mark] must still be swept. */
private const val SWEEP_SLACK_MS = 1_000L

/**
 * Removes the crash reports a test caused, and nothing else.
 *
 * [org.churchpresenter.diagnostics.CrashReporter] resolves its report directory from
 * `user.home` once per JVM, when the object is first touched, so a test cannot redirect it by
 * swapping the property afterwards — a test that reports an exception really does write into the
 * developer's own `~/.churchpresenter/crash-reports/`. Any test that exercises a path which reports
 * one has to put that right itself.
 *
 * Deleting by modification time rather than by wiping the directory is the point: a developer's
 * real crash reports are the reason the directory exists, and a test suite must not be the thing
 * that throws away the report they were about to send in.
 *
 * ```
 * private val sweep = CrashReportSweep()
 * @BeforeTest fun mark() = sweep.mark()
 * @AfterTest fun clean() = sweep.sweep()
 * ```
 */
class CrashReportSweep {

    private val crashDir = File(System.getProperty("user.home"), ".churchpresenter/crash-reports")
    private var markedAt = 0L

    /** Call at the start of a test, before anything that might report. */
    fun mark() {
        // A second of slack: file modification times have coarser resolution than the clock on
        // some filesystems, and a report written in the same second must still be swept.
        markedAt = System.currentTimeMillis() - SWEEP_SLACK_MS
    }

    /** Deletes every crash report written since [mark]. */
    fun sweep() {
        crashDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash_") && it.lastModified() >= markedAt }
            ?.forEach { it.delete() }
    }
}
