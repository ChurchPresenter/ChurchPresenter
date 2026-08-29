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
 */
object UrlOpener {

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
            if (browseSupported()) {
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
