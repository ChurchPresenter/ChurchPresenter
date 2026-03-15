package org.churchpresenter.app.churchpresenter.utils

import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import org.churchpresenter.app.churchpresenter.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Global crash reporter that:
 *  1. Writes crash logs to ~/.churchpresenter/crash-reports/ (always)
 *  2. Forwards crashes to Sentry when a DSN is configured in sentry.properties
 *
 * Install as early as possible in main() via [initialize].
 */
object CrashReporter {

    private val crashDir = File(System.getProperty("user.home"), ".churchpresenter/crash-reports")
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
    private const val MAX_AGE_DAYS = 30L

    /**
     * Install the global uncaught exception handler, initialise Sentry,
     * and clean up old crash logs.
     * Call this as the very first line in main().
     */
    fun initialize() {
        crashDir.mkdirs()

        initSentry()

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

    // ── Sentry ────────────────────────────────────────────────────────────────

    private fun initSentry() {
        try {
            val appVersion = try { BuildConfig.APP_VERSION } catch (_: Exception) { "unknown" }
            Sentry.init { options ->
                // sentry.properties on the classpath sets the DSN automatically.
                // If dsn is blank Sentry stays disabled — no data is ever sent.
                options.release = appVersion
                options.environment = "production"
                // Capture all unhandled exceptions automatically
                options.isEnableUncaughtExceptionHandler = true
                // Attach the current thread info to each event
                options.isAttachThreads = false
                options.isAttachStacktrace = true
                // Avoid performance overhead — capture errors only, no tracing
                options.tracesSampleRate = null
            }
        } catch (_: Exception) {
            // Sentry failing to init must never prevent the app from starting
        }
    }

    private fun sendToSentry(throwable: Throwable, context: String, fatal: Boolean) {
        try {
            if (!Sentry.isEnabled()) return
            val event = SentryEvent(throwable).apply {
                level = if (fatal) SentryLevel.FATAL else SentryLevel.ERROR
                if (context.isNotBlank()) {
                    message = Message().apply { message = context }
                }
            }
            Sentry.captureEvent(event)
        } catch (_: Exception) {
            // Never let Sentry errors surface to the user
        }
    }

    // ── Local file logging ────────────────────────────────────────────────────

    private fun writeCrashLog(throwable: Throwable, context: String, fatal: Boolean) {
        writeLocalLog(throwable, context, fatal)
        sendToSentry(throwable, context, fatal)
    }

    private fun writeLocalLog(throwable: Throwable, context: String, fatal: Boolean) {
        try {
            val now = LocalDateTime.now()
            val tag = if (fatal) "fatal" else "error"
            val filename = "crash_${now.format(timestampFormat)}_$tag.txt"
            val file = File(crashDir, filename)

            val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()

            val osName = System.getProperty("os.name", "unknown")
            val osVersion = System.getProperty("os.version", "")
            val javaVersion = System.getProperty("java.version", "unknown")
            val appVersion = try { BuildConfig.VERSION_DISPLAY } catch (_: Exception) { "unknown" }

            val report = buildString {
                appendLine("=== ChurchPresenter Crash Report ===")
                appendLine("Timestamp: ${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
                appendLine("Version: $appVersion")
                appendLine("OS: $osName $osVersion")
                appendLine("Java: $javaVersion")
                appendLine("Fatal: $fatal")
                if (context.isNotBlank()) appendLine("Context: $context")
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
