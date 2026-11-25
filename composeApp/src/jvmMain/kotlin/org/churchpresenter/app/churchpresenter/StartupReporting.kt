package org.churchpresenter.app.churchpresenter

import org.churchpresenter.diagnostics.BuildIdentity
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.songs.bundledSongCount
import org.churchpresenter.telemetry.LiveMapReporter
import org.churchpresenter.telemetry.SongLibraryTally
import org.churchpresenter.telemetry.mostSongs
import java.io.File

/*
 * A jar can go missing from an installed app -- an update cut short, an antivirus quarantine -- and
 * the app then fails the first time it reaches a class from it, often mid-start with a
 * NoClassDefFoundError that says nothing about the install. Checked once at startup, as soon as
 * crash reporting is up, so the report that follows says which file went missing.
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

/**
 * The song library the launch ping reports: the song folder as it is at launch or the Songs tab's
 * library once it has loaded, whichever has more songs. Logged, and left in the crash report's
 * trail, so a report says what library the app was running on.
 */
internal suspend fun launchSongCounts(settings: AppSettings, tally: SongLibraryTally): Pair<Int, Int> {
    val folder = LiveMapReporter.gatherSongCounts(settings, ::bundledSongCount)
    val tab = tally.await(SongLibraryTally.FIRST_LOAD_WAIT, SongLibraryTally.settleFor(settings))
    val counts = mostSongs(folder, tab) ?: folder
    val line = "Song library: ${counts.second} songs in ${counts.first} songbooks " +
        "(folder ${folder.second}, Songs tab ${tab?.second ?: "not loaded"})"
    Log.info("Startup", line)
    CrashReporter.breadcrumb(line, category = "lifecycle")
    return counts
}

/**
 * The jars on the class path that are not on disk. Only jars: a run from Gradle also lists build
 * folders, some of which are never made, and those say nothing about an install.
 */
internal fun missingClassPathJars(
    classPath: String = System.getProperty("java.class.path").orEmpty(),
    exists: (File) -> Boolean = File::exists,
): List<File> = classPath.split(File.pathSeparator)
    .filter { it.endsWith(".jar", ignoreCase = true) }
    .map(::File)
    .filterNot(exists)

/** What Sentry is told about a damaged install: which files, by name only, so no user folder is sent. */
internal fun damagedInstallTags(missing: List<File>): Map<String, String> = mapOf(
    "install.missing_count" to missing.size.toString(),
    "install.missing" to missing.joinToString(",") { it.name }.take(MAX_TAG_LENGTH),
)

/**
 * Reports a jar the app was installed with that is gone, and carries on: the app may well run
 * without it, and a check is never a reason to keep an operator from their service.
 */
internal fun reportIfInstallDamaged(missing: List<File> = missingClassPathJars()) {
    if (missing.isEmpty()) return
    Log.warn("Startup", "Installation damaged; missing: ${missing.joinToString { it.name }}")
    CrashReporter.reportWarning("Installation damaged: files missing", tags = damagedInstallTags(missing))
}

private const val MAX_TAG_LENGTH = 200
