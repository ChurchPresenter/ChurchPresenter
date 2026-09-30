package org.churchpresenter.diagnostics

import io.sentry.SentryLevel

/** How much a log line matters: [INFO] is progress, [WARN] a degraded path, [ERROR] a failure. */
enum class LogLevel { INFO, WARN, ERROR }

/**
 * The app's diagnostic log: one `[tag] message` line per call on stderr — the same lines the app
 * printed before this existed — and, for warnings and errors, a breadcrumb, so the trail leading up
 * to a crash report carries them. Breadcrumbs are PII-scrubbed with the rest of the event.
 *
 * [sink] and [trail] are parameters so a test can read what was written; the app uses [Log].
 */
open class Logger(
    private val sink: (String) -> Unit = { System.err.println(it) },
    private val trail: (message: String, category: String, level: LogLevel) -> Unit = ::crashReporterTrail,
) {
    fun info(tag: String, message: String) = write(LogLevel.INFO, tag, message)

    fun warn(tag: String, message: String) = write(LogLevel.WARN, tag, message)

    fun error(tag: String, message: String) = write(LogLevel.ERROR, tag, message)

    private fun write(level: LogLevel, tag: String, message: String) {
        val line = "[$tag] $message"
        sink(line)
        if (level != LogLevel.INFO) trail(line, tag, level)
    }
}

/** The app's logger. */
object Log : Logger()

private fun crashReporterTrail(message: String, category: String, level: LogLevel) {
    CrashReporter.breadcrumb(
        message,
        category = category,
        level = if (level == LogLevel.ERROR) SentryLevel.ERROR else SentryLevel.WARNING,
    )
}
