package org.churchpresenter.app.churchpresenter

import kotlinx.coroutines.runBlocking
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.diagnostics.EXIT_CODE_FATAL_CRASH
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.system_fonts_failed_body
import org.churchpresenter.strings.generated.resources.system_fonts_failed_old_windows_fix
import org.churchpresenter.strings.generated.resources.system_fonts_failed_old_windows_reason
import org.churchpresenter.strings.generated.resources.system_fonts_failed_title
import org.churchpresenter.diagnostics.Log
import org.jetbrains.compose.resources.DensityQualifier
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.LanguageQualifier
import org.jetbrains.compose.resources.RegionQualifier
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.ScriptQualifier
import org.jetbrains.compose.resources.ThemeQualifier
import org.jetbrains.compose.resources.getString
import org.jetbrains.skia.FontMgr
import java.util.Locale
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
    val env = try {
        environmentWithoutSkiko()
    } catch (e: ReflectiveOperationException) {
        // Without an environment the strings cannot be read, so the app exits without its dialog.
        Log.warn("Startup", "Cannot read the system fonts failure message: $e")
        return
    }
    val (title, lines) = runBlocking {
        getString(env, Res.string.system_fonts_failed_title) to systemFontsFailureLines(
            olderThanWindows10,
            body = getString(env, Res.string.system_fonts_failed_body),
            oldWindowsReason = getString(env, Res.string.system_fonts_failed_old_windows_reason),
            oldWindowsFix = getString(env, Res.string.system_fonts_failed_old_windows_fix),
        )
    }
    JOptionPane.showMessageDialog(null, lines.toTypedArray(), title, JOptionPane.ERROR_MESSAGE)
}

/**
 * The strings' environment, from the locale alone. The default one asks skiko for the system theme,
 * and skiko's native library is what just failed to load. Its constructor is internal to Compose
 * but public on the JVM, so it is reached by reflection.
 */
@OptIn(InternalResourceApi::class)
internal fun environmentWithoutSkiko(locale: Locale = Locale.getDefault()): ResourceEnvironment =
    ResourceEnvironment::class.java.getConstructor(
        LanguageQualifier::class.java,
        ScriptQualifier::class.java,
        RegionQualifier::class.java,
        ThemeQualifier::class.java,
        DensityQualifier::class.java,
    ).newInstance(
        LanguageQualifier(locale.language),
        ScriptQualifier(locale.script),
        RegionQualifier(locale.country),
        ThemeQualifier.LIGHT,
        DensityQualifier.MDPI,
    )

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
