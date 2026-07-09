package org.churchpresenter.app.churchpresenter.utils

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Temporary diagnostic logger for the "lower third disappears mid-playback" investigation.
 * Writes to a plain local file (unlike [CrashReporter]'s breadcrumbs, which are Sentry-only and
 * produce nothing locally when the user has analytics reporting disabled) so it's readable
 * regardless of that setting. Remove once the root cause is confirmed and fixed — see AGENT.md's
 * debugging guidelines.
 */
object LowerThirdDebugLog {

    private val logFile = File(System.getProperty("user.home"), ".churchpresenter/lower-third-debug.log")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val lock = Any()

    fun log(message: String) {
        val line = "[${LocalDateTime.now().format(timeFormat)}] $message"
        synchronized(lock) {
            try {
                logFile.parentFile?.mkdirs()
                logFile.appendText(line + "\n")
            } catch (_: Exception) {
                // Best-effort only — never let diagnostic logging break playback.
            }
        }
    }

    fun logException(context: String, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        log("EXCEPTION in $context: ${throwable::class.simpleName}: ${throwable.message}\n$sw")
    }

    /** Current JVM heap usage, for correlating failures with memory pressure. */
    fun heapStats(): String {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1_000_000
        val totalMb = rt.totalMemory() / 1_000_000
        val maxMb = rt.maxMemory() / 1_000_000
        return "heap: ${usedMb}MB used / ${totalMb}MB allocated / ${maxMb}MB max"
    }
}
