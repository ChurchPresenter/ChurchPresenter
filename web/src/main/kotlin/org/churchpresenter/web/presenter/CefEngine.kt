package org.churchpresenter.web.presenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.cef.CefClient
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.diagnostics.Log
import java.io.File

private const val ASCII_MAX = 128

/**
 * The web engine's state, and every decision about it: whether to install at all, what an install's
 * outcome means for the session, and recovering when a client cannot be made.
 *
 * [CefManager] holds the one the app runs on. A test builds its own, so none of this has to touch
 * the process-wide engine — whose [initialized] the Web tab's own defaults read.
 *
 * @param cleanupLegacy reclaims the old install footprint once a relocated install works.
 * @param reportFailure reports an install that ran and failed for a reason that is ours.
 * @param reportWarning files a recovered failure, with its tags.
 */
internal class CefEngine(
    private val cleanupLegacy: (File) -> Unit = CefManager::cleanupLegacyJcef,
    private val reportFailure: (Throwable) -> Unit = { CrashReporter.reportException(it, context = "CefManager.init") },
    private val reportWarning: (String, Throwable, Map<String, String>) -> Unit = { message, t, tags ->
        CrashReporter.reportWarning(message, throwable = t, tags = tags)
    },
) {
    /** Whether a usable engine exists right now; Compose state, since it can drop mid-session. */
    var initialized by mutableStateOf(false)
        private set

    var macOsUnsupported = false
        private set

    var windowsUnsupported = false
        private set

    @Volatile var installRoot: File? = null
        private set

    var blockedByPolicy = false
        private set

    /** Makes a client from the installed engine, or null while there is none to make one from. */
    @Volatile internal var clientSource: (() -> CefClient)? = null

    /**
     * Installs the engine unless it already is, or this OS cannot run it: [install] is not called on
     * an unsupported macOS or Windows, which are flagged instead.
     */
    fun init(unsupportedMacOS: Boolean, unsupportedWindows: Boolean, install: () -> JcefInstall.Outcome) {
        if (initialized) return
        if (unsupportedMacOS) {
            macOsUnsupported = true
            return
        }
        if (unsupportedWindows) {
            windowsUnsupported = true
            return
        }
        applyInstallOutcome(install())
    }

    /** Sets the engine's state from [outcome], reporting only a failure that is ours. */
    internal fun applyInstallOutcome(outcome: JcefInstall.Outcome) {
        when (outcome) {
            is JcefInstall.Outcome.Installed -> {
                initialized = true
                installRoot = outcome.root
                runCatching { CrashReporter.setTag("jcef.install_root", outcome.root.name) }
                // Now that the relocated install works, reclaim the orphaned old footprint.
                cleanupLegacy(outcome.root)
            }
            is JcefInstall.Outcome.Blocked -> {
                engineUnavailable()
                Log.warn("JCEF", "Not installing: ${outcome.reason}")
                // No event: web features simply stay unavailable, which jcef.available already
                // says. The tag rides along on anything else this session reports.
                runCatching { CrashReporter.setTag("jcef.blocked", outcome.reason) }
            }
            is JcefInstall.Outcome.Failed -> {
                engineUnavailable()
                reportInstallFailure(outcome)
            }
        }
    }

    /** Leaves the web engine off for this session. */
    private fun engineUnavailable() {
        clientSource = null
        initialized = false
    }

    /**
     * Reports an install that ran and threw, after every candidate root had its turn — so this is
     * the engine being genuinely unavailable, not one directory being unusable.
     *
     * JCEF native load can fail with UnsatisfiedLinkError (an Error, not an Exception) — e.g. a
     * broken/partial chrome_elf.dll install, a missing VC++ runtime, or a non-ASCII install path.
     * All of it is caught so the app does not crash at startup; embedded web features simply stay
     * unavailable.
     */
    private fun reportInstallFailure(outcome: JcefInstall.Outcome.Failed) {
        val installDir = File(outcome.root, "jcef")
        // Best-effort telemetry: distinguish the accented-path theory from the
        // VC++-runtime theory at a glance in Sentry. Never let telemetry throw.
        runCatching {
            CrashReporter.setTag("jcef.path_ascii", installDir.path.all { it.code < ASCII_MAX }.toString())
            CrashReporter.setTag("jcef.install_root", outcome.root.name)
            CrashReporter.setContext("jcef", mapOf(
                "installDir" to installDir.path,
                "os" to (System.getProperty("os.name") ?: ""),
                "arch" to (System.getProperty("os.arch") ?: "")
            ))
        }
        // A policy block is the machine, not a defect, and it recurs on every launch of every
        // affected install — so it is tagged and told to the operator rather than reported,
        // exactly as the two blockers checked before the download are.
        val policy = JcefInstall.policyBlock(outcome.cause.message)
        if (policy != null) {
            blockedByPolicy = true
            Log.warn("JCEF", "Blocked by this machine's software policy: ${outcome.cause.message}")
            runCatching { CrashReporter.setTag("jcef.blocked", policy) }
            return
        }
        reportFailure(outcome.cause)
    }

    /**
     * A client for a new browser, or null when the engine cannot provide one.
     *
     * An engine that built is not a promise that the native side came up: jcefmaven hands back the
     * process-wide singleton, whose startup can fail afterwards and asynchronously, and making a
     * client then throws — inside [EmbeddedWebView]'s `remember`, on the UI thread. Recovering is
     * returning null, which every caller already handles by drawing nothing; [initialized] is cleared
     * with it so the tab falls back to its explanatory panel and nothing asks a second time. Filed as
     * a warning, not an exception: the condition is recovered.
     */
    fun createClient(): CefClient? {
        val source = clientSource ?: return null
        return try {
            source()
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            // JCEF's native side fails with Errors (UnsatisfiedLinkError and the like), not Exceptions.
            engineUnavailable()
            runCatching { CrashReporter.setTag("jcef.blocked", "client_creation_failed") }
            reportWarning(
                "JCEF client creation failed; web features disabled for this session",
                t,
                mapOf("subsystem" to "webview", "jcef.recovered" to "true"),
            )
            null
        }
    }
}
