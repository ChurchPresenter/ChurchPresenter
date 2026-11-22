package org.churchpresenter.app.churchpresenter

import kotlinx.coroutines.runBlocking
import org.churchpresenter.diagnostics.BuildIdentity
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.install_damaged
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.MissingResourceException
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/*
 * A jar can go missing from an installed app -- an update cut short, an antivirus quarantine -- and
 * the app then fails the first time it reaches a class from it, often mid-start with a
 * NoClassDefFoundError that says nothing about the install. Checked once at startup instead, as soon
 * as crash reporting is up to hear about it.
 */

/** Sets up crash reporting for this build and records the start, the first breadcrumb of the run. */
internal fun startCrashReporting(enabled: Boolean) {
    CrashReporter.initialize(
        enabled,
        // BuildConfig is generated into :composeApp, so :diagnostics is told what this build is
        // rather than reading it.
        BuildIdentity(
            versionDisplay = BuildConfig.VERSION_DISPLAY,
            appVersion = BuildConfig.APP_VERSION,
            isRelease = BuildConfig.IS_RELEASE,
            buildType = BuildConfig.BUILD_TYPE,
            buildChannel = BuildConfig.BUILD_CHANNEL,
        ),
    )
    CrashReporter.breadcrumb("Application started", category = "lifecycle")
}

/** The class-path entries that are not on disk; a wildcard entry names a folder's jars, not a file. */
internal fun missingClassPathEntries(
    classPath: String = System.getProperty("java.class.path").orEmpty(),
    exists: (File) -> Boolean = File::exists,
): List<File> = classPath.split(File.pathSeparator)
    .filter { it.isNotBlank() && !it.endsWith("*") }
    .map(::File)
    .filterNot(exists)

/** What Sentry is told about a damaged install: which files, by name only, so no user folder is sent. */
internal fun damagedInstallTags(missing: List<File>): Map<String, String> = mapOf(
    "install.missing_count" to missing.size.toString(),
    "install.missing" to missing.joinToString(",") { it.name }.take(MAX_TAG_LENGTH),
)

/** Quits with a message to reinstall when a file the app was installed with is gone. */
internal fun stopIfInstallDamaged() {
    val missing = missingClassPathEntries()
    if (missing.isEmpty()) return
    reportDamagedInstall(missing)
    exitProcess(DAMAGED_INSTALL_EXIT)
}

/** Reports [missing] and tells the operator to reinstall. */
internal fun reportDamagedInstall(missing: List<File>, show: (String) -> Unit = ::showDamagedInstallDialog) {
    Log.error("Startup", "Installation damaged; missing: ${missing.joinToString { it.name }}")
    CrashReporter.reportWarning("Installation damaged: files missing", tags = damagedInstallTags(missing))
    show(damagedInstallMessage())
}

/**
 * The message, from the strings -- or in English when the strings jar is itself one of the files
 * that went missing, which is exactly when the message is needed most.
 */
internal fun damagedInstallMessage(
    load: () -> String = { runBlocking { getString(Res.string.install_damaged) } },
): String =
    try {
        load()
    } catch (_: LinkageError) {
        DAMAGED_INSTALL_FALLBACK
    } catch (_: MissingResourceException) {
        DAMAGED_INSTALL_FALLBACK
    }

private fun showDamagedInstallDialog(message: String) =
    JOptionPane.showMessageDialog(null, message, "ChurchPresenter", JOptionPane.ERROR_MESSAGE)

/** The same words as `install_damaged` in strings.xml. */
internal const val DAMAGED_INSTALL_FALLBACK =
    "ChurchPresenter's installation is damaged: some of its files are missing.\n\n" +
        "Please reinstall ChurchPresenter. Your settings are kept."

/** The exit status of a start stopped by a damaged install. */
internal const val DAMAGED_INSTALL_EXIT = 3

private const val MAX_TAG_LENGTH = 200
