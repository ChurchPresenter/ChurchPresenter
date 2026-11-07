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
        val advice = if (olderThanWindows10) {
            listOf(
                getString(Res.string.system_fonts_failed_old_windows_reason),
                getString(Res.string.system_fonts_failed_old_windows_fix),
            )
        } else {
            emptyList()
        }
        val body = getString(Res.string.system_fonts_failed_body)
        getString(Res.string.system_fonts_failed_title) to listOf(body) + advice
    }
    JOptionPane.showMessageDialog(null, lines.toTypedArray(), title, JOptionPane.ERROR_MESSAGE)
}
