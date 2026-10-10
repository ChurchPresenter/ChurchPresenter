package org.churchpresenter.dialogs

import org.churchpresenter.telemetry.TelemetryIdentity

internal val TEST_IDENTITY = TelemetryIdentity(
    appVersion = "0.0.0",
    versionDisplay = "0.0.0 (0000000)",
    isRelease = false,
    repoSlug = "ChurchPresenter/ChurchPresenter",
    commitHash = "0000000",
    buildType = "test",
)
