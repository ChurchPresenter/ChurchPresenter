package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.updater.UpdateCheckResult
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.diagnostics.Logger

/**
 * The decisions `main.kt` makes at startup, held apart from the entry point that makes them.
 *
 * Everything here is pure: no windows, no sockets, no Compose. What is left in `main.kt` opens real
 * AWT windows and binds real ports, which a headless test cannot do — so the rules those paths obey
 * live here instead, where they can be stated and checked directly.
 *
 * This file holds the startup decisions; the rest sit beside it by topic, in the `*Logic.kt` files:
 * windows, outputs, transitions, announcements, Lottie playback, live state, Instance Link, remote
 * clients, mirrored assets and the bundled Bible.
 */

/**
 * The skiko render API to pin, or null to leave the choice to skiko.
 *
 * **This is the only place the render API is chosen, for every platform.** It deliberately does not
 * live in `build.gradle.kts`: a `jvmArgs` there is evaluated against the machine running the *build*
 * and is baked into the artifact, and — because `jvmArgs` is declared only on `JvmApplication` — a
 * call written inside a `macOS { }` / `windows { }` / `linux { }` block silently applies to every
 * platform instead of that one. A pin written here is read on the machine that actually runs the
 * app, so it is right whoever built it and wherever it ran.
 *
 * - **macOS** is pinned to Metal: left to choose, skiko falls back to OpenGL there and crashes on
 *   some machines.
 * - **Linux** is pinned to OpenGL, which is what guards it against a software fallback.
 * - **Windows** is deliberately **not** pinned. It used to be forced to OpenGL, which is the only
 *   reason a GPU driver fault landed in `WindowsOpenGLRedrawer.swapBuffers`; skiko's own Direct3D
 *   default is the better first rung. Note skiko's `fallbackRenderApiQueue` covers *context
 *   creation* only, so a fault during `swapBuffers` never triggers it — the default has to be right
 *   rather than recoverable.
 *
 * [override] is the operator's escape hatch, so a machine that does worse on the platform default
 * has a way out without waiting for a build. It is taken when this platform can run it and ignored,
 * with a warning to [log], when it cannot: skiko rejects an API outside the platform's
 * `fallbackRenderApiQueue` with an `IllegalArgumentException` before the first window opens, so an
 * override this platform cannot run (OPENGL on a Mac) would stop the app starting at all.
 *
 * Matched on the name rather than a platform enum because that is what the property carries.
 */
internal fun preferredRenderApi(osName: String, override: String?, log: Logger = Log): String? {
    val name = osName.lowercase()
    val (platformDefault, supported) = when {
        name.contains("mac") -> "METAL" to MAC_RENDER_APIS
        name.contains("win") -> null to WINDOWS_RENDER_APIS
        else -> "OPENGL" to OTHER_RENDER_APIS
    }
    val requested = override?.trim()?.uppercase()
    if (requested.isNullOrEmpty()) return platformDefault
    if (requested in supported) return requested
    log.warn("Startup", "Render API override $requested is not supported on $osName; using the platform default")
    return platformDefault
}

/*
 * The `skiko.renderApi` values skiko accepts on each platform: what its `SkikoProperties` parses
 * there and keeps in that platform's `fallbackRenderApiQueue`. SOFTWARE is SOFTWARE_COMPAT on macOS
 * and SOFTWARE_FAST elsewhere; DIRECT_SOFTWARE is SOFTWARE_FAST.
 */
private val MAC_RENDER_APIS = setOf("METAL", "SOFTWARE", "SOFTWARE_COMPAT")
private val WINDOWS_RENDER_APIS =
    setOf("DIRECT3D", "ANGLE", "OPENGL", "SOFTWARE", "SOFTWARE_FAST", "SOFTWARE_COMPAT", "DIRECT_SOFTWARE")
private val OTHER_RENDER_APIS = setOf("OPENGL", "SOFTWARE", "SOFTWARE_FAST", "SOFTWARE_COMPAT", "DIRECT_SOFTWARE")

/** The port the single-instance lock binds, honouring the override a second dev instance sets. */
internal fun singleInstanceLockPort(override: String?, default: Int): Int =
    override?.toIntOrNull() ?: default

/**
 * The language to start in: the saved one when it is still a language this build has, English when
 * it is not — a settings file naming a language since removed must not stop the app starting.
 */
internal fun resolveStartupLanguage(savedCode: String): Language =
    Language.entries.find { it.code == savedCode } ?: Language.ENGLISH

/** Whether this is the first update check this install has ever run. */
internal fun isFirstEverUpdateCheck(lastCheckTimestamp: Long): Boolean = lastCheckTimestamp == 0L

/**
 * Whether an update check's outcome should be put in front of the operator.
 *
 * The first check ever is shown whatever it found — that is the one chance to ask how often they
 * want checking done. Every check after it only interrupts when there is actually an update, so a
 * routine "you are up to date" never appears unasked.
 */
internal fun shouldShowUpdateResult(firstEverCheck: Boolean, result: UpdateCheckResult): Boolean =
    firstEverCheck || result is UpdateCheckResult.Available

/**
 * Whether the window should be restored to the position and size it was left at.
 *
 * Only a floating window has its own geometry worth restoring, and only once it has actually been
 * saved: a negative coordinate is the "never saved" value, and restoring to it would put the window
 * off-screen.
 */
internal fun shouldRestoreWindowGeometry(isFloating: Boolean, savedX: Int): Boolean =
    isFloating && savedX >= 0

/** Whether the licence has already been accepted, at this build's version of it or a later one. */
internal fun isEulaAccepted(acceptedVersion: Int, currentVersion: Int): Boolean =
    acceptedVersion >= currentVersion

/**
 * Whether first-run setup should be offered.
 *
 * Not once it has been dismissed, and not to an install that already has both a Bible and a song
 * folder — that is a working setup, whatever the flag says, and interrupting it would be noise.
 */
internal fun shouldShowSetupWizard(settings: AppSettings): Boolean {
    val bibleReady = settings.bibleSettings.primaryBible.isNotEmpty()
    val songsReady = settings.songSettings.storageDirectory.isNotEmpty()
    return !settings.setupWizardShown && !(bibleReady && songsReady)
}

/**
 * Whether the Developer menu is shown.
 *
 * Always in a dev build, and in a packaged one only when deliberately asked for — the forced-window
 * flag, or the secret keypress, which unlocks it for that session alone.
 */
internal fun shouldShowDeveloperMenu(
    isRelease: Boolean,
    forceDevWindow: Boolean,
    unlocked: Boolean,
): Boolean = !isRelease || forceDevWindow || unlocked

/**
 * The identifier the open-ping carries, or none.
 *
 * An opted-out install still pings — the count of churches opening the app is what the map is for —
 * but carries nothing that follows it between launches. Taken as a function rather than a value
 * because minting the id writes it to disk: computing one for an opted-out install and discarding
 * it would leave the very identifier that was opted out of sitting in their home directory.
 */
internal fun analyticsInstallId(enabled: Boolean, installId: () -> String): String? =
    if (enabled) installId() else null
