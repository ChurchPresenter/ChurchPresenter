package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard

/**
 * A [Clipboard] that answers instead of throwing when the OS clipboard cannot be taken.
 *
 * The system clipboard is a single OS-wide lock any running application can be holding. Windows
 * raises `IllegalStateException: cannot open system clipboard` from `WClipboard.openClipboard0`
 * when it cannot take that lock, and Compose's own paste path does nothing about it: a reported
 * crash went
 *
 *     TextFieldSelectionManager.paste -> AwtPlatformClipboard.getClipEntry -> WClipboard.openClipboard0
 *
 * and arrived in Sentry at **fatal** — an operator pressed Ctrl+V in a field and the app died, mid
 * session, on the Songs tab.
 *
 * The app's own copy buttons were guarded separately (`SystemClipboard`); this covers the half of
 * the problem that is not the app's code, by wrapping whatever [Clipboard] the platform installed
 * and swallowing exactly the failure that is not ours to prevent. Everything else delegates
 * untouched, so a working clipboard behaves precisely as it did.
 *
 * A copy or paste that could not happen is one the operator repeats. It is not worth ending what
 * they were doing.
 */
internal class SafeClipboard(private val delegate: Clipboard) : Clipboard {

    override suspend fun getClipEntry(): ClipEntry? = try {
        delegate.getClipEntry()
    } catch (_: IllegalStateException) {
        null
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        try {
            delegate.setClipEntry(clipEntry)
        } catch (_: IllegalStateException) {
            // Nothing to report and nothing to do: the operator presses the key again.
        }
    }

    /**
     * Delegated as-is. This is the raw AWT clipboard, handed to callers that want to do their own
     * thing with it — wrapping it would be a lie about what they were given, and the failure this
     * class exists for happens on use rather than on access.
     */
    override val nativeClipboard: Any get() = delegate.nativeClipboard
}

/**
 * Installs [SafeClipboard] over whatever the platform provided, for everything in [content].
 *
 * Belongs at a window root, which is what `AppWindowRoot` uses it for — every Compose window is its
 * own composition, so a provider in one does not reach another.
 */
@Composable
internal fun ProvideSafeClipboard(content: @Composable () -> Unit) {
    val platform = LocalClipboard.current
    val safe = remember(platform) { SafeClipboard(platform) }
    CompositionLocalProvider(LocalClipboard provides safe, content = content)
}
