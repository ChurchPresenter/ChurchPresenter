package org.churchpresenter.app.churchpresenter.utils

import io.sentry.Attachment
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import io.sentry.UserFeedback
import io.sentry.protocol.Message
import io.sentry.protocol.User
import org.churchpresenter.app.churchpresenter.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Global crash reporter that:
 *  1. Writes crash logs to ~/.churchpresenter/crash-reports/ (always)
 *  2. Forwards crashes to Sentry when a DSN is configured in sentry.properties
 *
 * Install as early as possible in main() via [initialize].
 */
object CrashReporter {

    private val crashDir = File(System.getProperty("user.home"), ".churchpresenter/crash-reports")
    private val runningFile = File(System.getProperty("user.home"), ".churchpresenter/.running")
    private val crashCountFile = File(System.getProperty("user.home"), ".churchpresenter/.crash_count")
    private val installIdFile = File(System.getProperty("user.home"), ".churchpresenter/.install_id")
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
    private const val MAX_AGE_DAYS = 30L
    private const val CRASH_THRESHOLD = 2 // disable video backgrounds after this many consecutive crashes

    /** True if the previous run crashed (lock file wasn't cleaned up). */
    @Volatile
    var didCrashLastRun: Boolean = false
        private set

    /** Number of consecutive crashes. */
    @Volatile
    var consecutiveCrashes: Int = 0
        private set

    /** True when video backgrounds are disabled due to repeated crashes. */
    @Volatile
    var videoBackgroundsDisabled: Boolean = false

    /**
     * Install the global uncaught exception handler, initialise Sentry
     * (unless the user has opted out of analytics reporting), and clean
     * up old crash logs. Call this as the very first line in main().
     */
    fun initialize(analyticsReportingEnabled: Boolean = true) {
        crashDir.mkdirs()

        if (analyticsReportingEnabled) {
            initSentry()
            setUser(getOrCreateInstallId())
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashLog(throwable, context = "Thread: ${thread.name}", fatal = true)
            // Flush Sentry synchronously so the event is delivered before the JVM exits
            try { Sentry.flush(3_000) } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        cleanOldLogs()

        // Check if previous run crashed (lock file still exists)
        didCrashLastRun = runningFile.exists()
        if (didCrashLastRun) {
            consecutiveCrashes = readCrashCount() + 1
            writeCrashCount(consecutiveCrashes)
            if (consecutiveCrashes >= CRASH_THRESHOLD) {
                videoBackgroundsDisabled = true
            } else {
                // previous run crashed but threshold not yet reached
            }
        } else {
            // Clean run — reset crash counter
            consecutiveCrashes = 0
            writeCrashCount(0)
        }

        // Create lock file for this run
        try { runningFile.parentFile?.mkdirs(); runningFile.createNewFile() } catch (_: Exception) {}

        // Delete lock file on clean exit
        Runtime.getRuntime().addShutdownHook(Thread {
            try { runningFile.delete() } catch (_: Exception) {}
        })
    }

    /**
     * Report a caught exception that is important enough to log.
     * Use this for significant errors that are handled gracefully but should be tracked.
     */
    fun reportException(throwable: Throwable, context: String = "") {
        writeCrashLog(throwable, context, fatal = false)
    }

    /** Re-enable video backgrounds (user override from the warning banner). */
    fun reEnableVideoBackgrounds() {
        videoBackgroundsDisabled = false
        writeCrashCount(0)
    }

    private fun readCrashCount(): Int = try {
        if (crashCountFile.exists()) crashCountFile.readText().trim().toIntOrNull() ?: 0 else 0
    } catch (_: Exception) { 0 }

    private fun writeCrashCount(count: Int) = try {
        crashCountFile.parentFile?.mkdirs()
        crashCountFile.writeText(count.toString())
    } catch (_: Exception) { }

    /**
     * Anonymous, stable per-install ID, creating it on first access. Exposed so
     * the live-map ping can dedupe by install (see LiveMapReporter). Same id used
     * for unique-user counting in Sentry.
     */
    fun installId(): String = getOrCreateInstallId()

    /** Anonymous, stable per-install ID used for unique-user counting in Sentry. */
    private fun getOrCreateInstallId(): String = try {
        if (installIdFile.exists()) {
            installIdFile.readText().trim()
        } else {
            val id = UUID.randomUUID().toString()
            installIdFile.parentFile?.mkdirs()
            installIdFile.writeText(id)
            id
        }
    } catch (_: Exception) { "" }

    /** True when Sentry is initialised with a valid DSN. */
    fun isEnabled(): Boolean = try { Sentry.isEnabled() } catch (_: Exception) { false }

    /** Sets the user identity used for unique-user counting in Sentry. */
    fun setUser(id: String) {
        try {
            if (!Sentry.isEnabled()) return
            Sentry.setUser(User().apply { this.id = id })
        } catch (_: Exception) {}
    }

    /**
     * Turns Sentry reporting on/off at runtime, reflecting the user's
     * analytics-opt-out preference without requiring an app restart.
     * Local crash log files on disk are unaffected by this setting.
     */
    fun setReportingEnabled(enabled: Boolean) {
        try {
            if (enabled) {
                if (!Sentry.isEnabled()) initSentry()
                setUser(getOrCreateInstallId())
            } else if (Sentry.isEnabled()) {
                Sentry.close()
            }
        } catch (_: Exception) {}
    }

    /** DSN from sentry.properties with the secret key partially masked. Empty when not configured. */
    fun maskedDsn(): String = try {
        val dsn = readDsn()
        if (dsn.isBlank()) return ""
        val atIdx = dsn.indexOf('@')
        if (atIdx < 0) return dsn.take(12) + "••••"
        val beforeAt = dsn.substring(0, atIdx)
        val afterAt  = dsn.substring(atIdx)
        val schemeEnd = beforeAt.indexOf("//") + 2
        val key = beforeAt.substring(schemeEnd)
        val scheme = beforeAt.substring(0, schemeEnd)
        "$scheme${key.take(6)}${"•".repeat(maxOf(0, key.length - 6))}$afterAt"
    } catch (_: Exception) { "" }

    /** Sends a test exception to Sentry and flushes. Returns true on success. */
    fun sendTestEvent(): Boolean = try {
        if (!isEnabled()) return false
        val version = try { BuildConfig.VERSION_DISPLAY } catch (_: Exception) { "unknown" }
        Sentry.captureException(RuntimeException("🧪 ChurchPresenter Sentry test event — v$version"))
        Sentry.flush(5_000)
        true
    } catch (_: Exception) { false }

    // ── Telemetry: breadcrumbs, tags, context, tracing, feedback ────────────────

    /** Records a breadcrumb — a lightweight trail of user actions shown on later crashes. */
    fun breadcrumb(message: String, category: String = "app", level: SentryLevel = SentryLevel.INFO) {
        try {
            if (!Sentry.isEnabled()) return
            Sentry.addBreadcrumb(Breadcrumb().apply {
                this.message = message
                this.category = category
                this.level = level
            })
        } catch (_: Exception) {}
    }

    /** Sets a searchable tag on all subsequent events (e.g. active tab, screen count). */
    fun setTag(key: String, value: String) {
        try { if (Sentry.isEnabled()) Sentry.setTag(key, value) } catch (_: Exception) {}
    }

    /** Attaches a structured context block (a named group of key/value data) to events. */
    fun setContext(name: String, data: Map<String, Any>) {
        try { if (Sentry.isEnabled()) Sentry.configureScope { it.setContexts(name, data) } } catch (_: Exception) {}
    }

    /**
     * Runs [block] inside a Sentry performance transaction so its duration shows up under
     * Performance. Falls back to running [block] directly when Sentry is disabled.
     */
    inline fun <T> trace(operation: String, name: String, block: () -> T): T {
        if (!isEnabled()) return block()
        val tx = try { Sentry.startTransaction(name, operation) } catch (_: Exception) { null } ?: return block()
        return try {
            block()
        } catch (e: Throwable) {
            tx.throwable = e
            tx.status = SpanStatus.INTERNAL_ERROR
            throw e
        } finally {
            try { tx.finish() } catch (_: Exception) {}
        }
    }

    /** Sends user-provided feedback (e.g. after a crash) as a Sentry event + user feedback. */
    fun sendUserFeedback(comment: String, name: String = "", email: String = "") {
        try {
            if (!Sentry.isEnabled() || comment.isBlank()) return
            val event = SentryEvent().apply {
                level = SentryLevel.INFO
                message = Message().apply { message = "User feedback" }
            }
            val eventId = Sentry.captureEvent(event)
            Sentry.captureUserFeedback(UserFeedback(eventId).apply {
                comments = comment
                if (name.isNotBlank()) this.name = name
                if (email.isNotBlank()) this.email = email
            })
            Sentry.flush(3_000)
        } catch (_: Exception) {}
    }

    /** Most recently written local crash-report file, if any (used for event attachments). */
    private fun latestCrashFile(): File? = try {
        crashDir.listFiles { f -> f.isFile && f.name.startsWith("crash_") }
            ?.maxByOrNull { it.lastModified() }
    } catch (_: Exception) { null }

    // ── Sentry ────────────────────────────────────────────────────────────────

    private fun readDsn(): String {
        val props = java.util.Properties()
        CrashReporter::class.java.classLoader
            ?.getResourceAsStream("sentry.properties")
            ?.use { props.load(it) }
        return props.getProperty("dsn", "").trim()
    }

    private fun initSentry() {
        try {
            val dsn = readDsn()
            if (dsn.isBlank()) return   // no DSN → stay disabled, nothing sent
            val appVersion = try { BuildConfig.APP_VERSION } catch (_: Exception) { "unknown" }
            Sentry.init { options ->
                options.dsn = dsn
                options.release = appVersion
                // Keep developer test runs out of production release-health stats.
                options.environment = if (BuildConfig.IS_RELEASE) "production" else "development"
                options.isEnableUncaughtExceptionHandler = true
                options.isAttachThreads = false
                options.isAttachStacktrace = true
                options.tracesSampleRate = 1.0
                // Capture CPU profiles for sampled transactions (see Performance → Profiling).
                options.profilesSampleRate = 1.0
                options.isEnableAutoSessionTracking = true
                // Privacy: don't send end-users' machine hostnames to Sentry.
                options.isAttachServerName = false
                // Mark our own packages as in-app so app frames stand out in stack traces.
                options.addInAppInclude("org.churchpresenter")
                // Attach the most recent local crash-report file to error/fatal events.
                options.beforeSend = SentryOptions.BeforeSendCallback { event, hint ->
                    try {
                        if (event.level == SentryLevel.ERROR || event.level == SentryLevel.FATAL) {
                            latestCrashFile()?.let { hint.addAttachment(Attachment(it.absolutePath)) }
                        }
                    } catch (_: Exception) { /* never block delivery */ }
                    event
                }
            }
            // Static context that helps triage: OS arch and dev/release build.
            setTag("os.arch", System.getProperty("os.arch", "unknown"))
            setTag("build.type", if (BuildConfig.IS_RELEASE) "release" else "dev")
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
