package org.churchpresenter.updater

/** What an uninitialised updater reports as the running version. */
private const val UNKNOWN_VERSION = "unknown"

/**
 * What the updater is told this build is: the version it compares releases against and sends as
 * its `User-Agent`, and whether it is a release, which gates the download beacon.
 *
 * A parameter rather than a `BuildConfig` read because `BuildConfig` is generated into
 * `:composeApp` and does not exist here. `main.kt` passes the real values to
 * [UpdateChecker.initialize]; the defaults are a dev build of an unknown version, which no release
 * can fail to be newer than and which never sends the beacon.
 */
data class UpdaterIdentity(
    val appVersion: String = UNKNOWN_VERSION,
    val isRelease: Boolean = false,
)
