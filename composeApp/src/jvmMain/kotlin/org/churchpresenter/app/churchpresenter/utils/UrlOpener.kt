package org.churchpresenter.app.churchpresenter.utils

import java.awt.Desktop
import java.net.URI

/**
 * Opens a link in the operator's browser, on every desktop the app ships to.
 *
 * `Desktop.getDesktop().browse(...)` is the obvious call and the wrong one to make alone: AWT
 * answers `Desktop.Action.BROWSE` only where it can find a freedesktop.org helper, so on a Linux
 * box without one — and under `java.awt.headless=true` — it throws
 * `UnsupportedOperationException: The BROWSE action is not supported on the current platform!`.
 * That reached Sentry from the Planning Center import dialog, where the throw escaped a coroutine
 * and the operator's consent page simply never opened.
 *
 * Every platform this ships to has a shell command that does work, so the fallback is to ask the
 * OS directly rather than to give up. [browse] and [exec] are parameters so the decision can be
 * tested without a desktop session; the one call that genuinely needs a display stays behind them.
 *
 * It also opens the OS's own settings URIs, which are not browser links at all — see [isWebUrl] for
 * why those must not be handed to AWT.
 */
object UrlOpener {

    /**
     * Whether AWT should be offered [url] at all.
     *
     * **Only a web link.** `Desktop.browse` means "open this in the browser", and it takes that
     * literally: handed `x-apple.systempreferences:…`, macOS AWT passes the whole string to Safari,
     * which opens a blank tab and asks *"Do you want to allow this website to open System
     * Settings?"*. It then returns normally, so nothing looks like a failure and the shell fallback
     * — which opens the pane directly — is never reached. That is the Camera privacy button doing
     * nothing but opening a webpage; `ms-settings:` on Windows goes the same way through Edge.
     *
     * A scheme the OS routes itself belongs to the OS, so those skip AWT entirely.
     */
    internal fun isWebUrl(url: String): Boolean {
        val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase()
        return scheme == "http" || scheme == "https"
    }

    /** Per-platform "open this URL" commands, tried in order after AWT declines. */
    internal fun fallbackCommands(osName: String, url: String): List<List<String>> {
        val name = osName.lowercase()
        return when {
            name.contains("mac") || name.contains("darwin") -> listOf(listOf("open", url))
            name.contains("win") -> listOf(listOf("rundll32", "url.dll,FileProtocolHandler", url))
            // xdg-open is the standard and is what a desktop without AWT's helper usually still
            // has; the browser variables are what a minimal window manager tends to set instead.
            else -> listOfNotNull(
                listOf("xdg-open", url),
                System.getenv("BROWSER")?.takeIf { it.isNotBlank() }?.let { listOf(it, url) },
            )
        }
    }

    /**
     * Opens [url], returning whether anything accepted it.
     *
     * Never throws: a link that will not open is a disappointment, not a crash, and every caller
     * here is a button in a dialog rather than something with a failure path of its own.
     */
    fun open(
        url: String,
        osName: String = System.getProperty("os.name", ""),
        browseSupported: () -> Boolean = {
            Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
        },
        browse: (URI) -> Unit = { Desktop.getDesktop().browse(it) },
        exec: (List<String>) -> Boolean = { command ->
            runCatching { ProcessBuilder(command).start(); true }.getOrDefault(false)
        },
    ): Boolean {
        if (url.isBlank()) return false
        val opened = runCatching {
            if (isWebUrl(url) && browseSupported()) {
                browse(URI(url))
                true
            } else {
                false
            }
        }.getOrDefault(false)
        if (opened) return true
        return fallbackCommands(osName, url).any { exec(it) }
    }
}
