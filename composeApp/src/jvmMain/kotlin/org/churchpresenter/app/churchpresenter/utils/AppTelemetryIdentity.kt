package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.telemetry.TelemetryIdentity

/** This build, as the live-map ping, the contact form and the device report describe it. */
val appTelemetryIdentity = TelemetryIdentity(
    appVersion = BuildConfig.APP_VERSION,
    versionDisplay = BuildConfig.VERSION_DISPLAY,
    isRelease = BuildConfig.IS_RELEASE,
    repoSlug = BuildConfig.REPO_SLUG,
    commitHash = BuildConfig.COMMIT_HASH,
    buildType = BuildConfig.BUILD_TYPE,
)
