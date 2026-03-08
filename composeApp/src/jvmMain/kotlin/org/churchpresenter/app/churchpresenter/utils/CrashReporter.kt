package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.app.churchpresenter.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Global crash reporter that writes crash logs to ~/.churchpresenter/crash-reports/.
 * Install as early as possible in main() via [initialize].
 */
object CrashReporter {

    private val crashDir = File(System.getProperty("user.home"), ".churchpresenter/crash-reports")
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
    private const val MAX_AGE_DAYS = 30L

    /**
     * Install the global uncaught exception handler and clean up old crash logs.
     * Call this as the very first line in main().
     */
    fun initialize() {
        crashDir.mkdirs()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashLog(throwable, context = "Thread: ${thread.name}", fatal = true)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        cleanOldLogs()
    }

    /**
     * Report a caught exception that is important enough to log.
     * Use this for significant errors that are handled gracefully but should be tracked.
     */
    fun reportException(throwable: Throwable, context: String = "") {
        writeCrashLog(throwable, context, fatal = false)
    }

    private fun writeCrashLog(throwable: Throwable, context: String, fatal: Boolean) {
        try {
            val now = LocalDateTime.now()
            val tag = if (fatal) "fatal" else "error"
            val filename = "crash_${now.format(timestampFormat)}_$tag.txt"
            val file = File(crashDir, filename)

            val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()

            val osName = System.getProperty("os.name", "unknown")
            val osVersion = System.getProperty("os.version", "")
            val javaVersion = System.getProperty("java.version", "unknown")
            val appVersion = try {
                BuildConfig.VERSION_DISPLAY
            } catch (_: Exception) {
                "unknown"
            }

            val report = buildString {
                appendLine("=== ChurchPresenter Crash Report ===")
                appendLine("Timestamp: ${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
                appendLine("Version: $appVersion")
                appendLine("OS: $osName $osVersion")
                appendLine("Java: $javaVersion")
                appendLine("Fatal: $fatal")
                if (context.isNotBlank()) {
                    appendLine("Context: $context")
                }
                appendLine()
                append(stackTrace)
            }

            file.writeText(report)
        } catch (_: Exception) {
            // Last resort — don't let crash reporting itself crash the app
        }
    }

    private fun cleanOldLogs() {
        try {
            val cutoff = System.currentTimeMillis() - (MAX_AGE_DAYS * 24 * 60 * 60 * 1000)
            crashDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("crash_") && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
            // Non-critical — skip cleanup silently
        }
    }
}
