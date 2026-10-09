package org.churchpresenter.telemetry

/**
 * What the reports say about the build that sends them — the app's `BuildConfig`, handed in so this
 * module carries no generated code of its own.
 *
 * @param appVersion The plain version ("26.1.0"), as a ping and a user agent carry it.
 * @param versionDisplay The version as the About box shows it, with any pre-release suffix.
 * @param isRelease True only for a packaged installer build; a `run`/IDE launch is a dev build.
 * @param repoSlug The GitHub repository the build came from, so a fork's pings are told apart.
 * @param commitHash The commit the build was made from.
 * @param buildType How the build was made (release, dev, CI).
 */
data class TelemetryIdentity(
    val appVersion: String,
    val versionDisplay: String,
    val isRelease: Boolean,
    val repoSlug: String,
    val commitHash: String,
    val buildType: String,
)
