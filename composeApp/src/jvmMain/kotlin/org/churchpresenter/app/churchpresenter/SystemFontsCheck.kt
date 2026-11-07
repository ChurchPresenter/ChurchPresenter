package org.churchpresenter.app.churchpresenter

import kotlinx.coroutines.runBlocking
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.diagnostics.EXIT_CODE_FATAL_CRASH
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.system_fonts_failed_body
import org.churchpresenter.strings.generated.resources.system_fonts_failed_old_windows_fix
import org.churchpresenter.strings.generated.resources.system_fonts_failed_old_windows_reason
import org.churchpresenter.strings.generated.resources.system_fonts_failed_title
import org.jetbrains.compose.resources.getString
import org.jetbrains.skia.FontMgr
import javax.swing.JOptionPane
import kotlin.system.exitProcess

internal fun exitIfSystemFontsUnavailable(olderThanWindows10: Boolean) {
    try {
        // Compose reads this while building the first window, and a failed class initializer stays failed.
        FontMgr.default
    } catch (e: ExceptionInInitializerError) {
        CrashReporter.recordFatal(e, context = "Loading the system fonts")
        showSystemFontsFailure(olderThanWindows10)
        exitProcess(EXIT_CODE_FATAL_CRASH)
    }
}

private fun showSystemFontsFailure(olderThanWindows10: Boolean) {
    val (title, lines) = runBlocking {
        getString(Res.string.system_fonts_failed_title) to systemFontsFailureLines(
            olderThanWindows10,
            body = getString(Res.string.system_fonts_failed_body),
            oldWindowsReason = getString(Res.string.system_fonts_failed_old_windows_reason),
            oldWindowsFix = getString(Res.string.system_fonts_failed_old_windows_fix),
        )
    }
    JOptionPane.showMessageDialog(null, lines.toTypedArray(), title, JOptionPane.ERROR_MESSAGE)
}

/**
 * What the dialog says: what went wrong, and — only where it is the likely cause — that this
 * Windows is older than the app supports and what to do about it.
 *
 * Split from [showSystemFontsFailure] so the choice can be tested without a dialog.
 */
internal fun systemFontsFailureLines(
    olderThanWindows10: Boolean,
    body: String,
    oldWindowsReason: String,
    oldWindowsFix: String,
): List<String> = if (olderThanWindows10) listOf(body, oldWindowsReason, oldWindowsFix) else listOf(body)
